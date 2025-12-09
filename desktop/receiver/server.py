import argparse
import asyncio
import json
import logging
import signal
from typing import List

import aiohttp
from aiohttp import web
from aiortc import RTCPeerConnection, RTCSessionDescription, RTCIceServer, RTCConfiguration
from aiortc.contrib.media import MediaBlackhole
import pyvirtualcam

logging.basicConfig(level=logging.INFO)
LOG = logging.getLogger("receiver")

pcs: List[RTCPeerConnection] = []


async def consume_video(track, width: int, height: int, fps: int):
    """Read frames from track and push to virtual camera."""
    cam = None
    try:
        cam = pyvirtualcam.Camera(width=width, height=height, fps=fps, fmt=pyvirtualcam.PixelFormat.BGR)
        LOG.info("Virtual camera opened: %s", cam.device)
        while True:
            frame = await track.recv()
            img = frame.to_ndarray(format="bgr24")
            cam.send(img)
            cam.sleep_until_next_frame()
    except asyncio.CancelledError:
        LOG.info("Video consumer cancelled")
    finally:
        if cam:
            cam.close()
            LOG.info("Virtual camera closed")


async def consume_audio(track):
    """Discard audio for now (hook for future)."""
    sink = MediaBlackhole()
    while True:
        frame = await track.recv()
        await sink._handle_track(frame)


async def offer(request: web.Request):
    params = await request.json()
    offer_sdp = params["sdp"]
    offer_type = params.get("type", "offer")

    pc = RTCPeerConnection(request.app["rtc_config"])
    pcs.append(pc)
    LOG.info("Created PeerConnection %s", id(pc))

    @pc.on("track")
    def on_track(track):
        LOG.info("Track %s kind=%s", track.id, track.kind)
        if track.kind == "video":
            # Assume 1080p60 input; pyvirtualcam will scale if needed.
            asyncio.create_task(consume_video(track, width=1920, height=1080, fps=60))
        else:
            asyncio.create_task(consume_audio(track))

        @track.on("ended")
        async def on_ended():
            LOG.info("Track %s ended", track.id)

    await pc.setRemoteDescription(RTCSessionDescription(sdp=offer_sdp, type=offer_type))
    
    answer = await pc.createAnswer()
    await pc.setLocalDescription(answer)
    LOG.info("Answer created for %s", id(pc))

    return web.json_response({"sdp": pc.localDescription.sdp, "type": pc.localDescription.type})


async def on_shutdown(app: web.Application):
    LOG.info("Shutting down, closing %d peer(s)", len(pcs))
    coros = [pc.close() for pc in pcs]
    if coros:
        await asyncio.gather(*coros, return_exceptions=True)


def create_app(args: argparse.Namespace) -> web.Application:
    stun_servers = []
    if args.stun:
        stun_servers.append(RTCIceServer(args.stun))
    rtc_config = RTCConfiguration(iceServers=stun_servers)

    app = web.Application()
    app["rtc_config"] = rtc_config
    app.router.add_post("/offer", offer)
    app.on_shutdown.append(on_shutdown)
    return app


def main():
    parser = argparse.ArgumentParser(description="PhoneCam receiver")
    parser.add_argument("--host", default="0.0.0.0", help="Host/IP to bind")
    parser.add_argument("--port", type=int, default=8000, help="Port to bind")
    parser.add_argument("--stun", default=None, help="Optional STUN server, e.g. stun:stun.l.google.com:19302")
    args = parser.parse_args()

    app = create_app(args)
    runner = web.AppRunner(app)

    async def start():
        await runner.setup()
        site = web.TCPSite(runner, args.host, args.port)
        await site.start()
        LOG.info("Receiver listening on http://%s:%d", args.host, args.port)

    async def run():
        await start()
        try:
            # Keep running until interrupted
            await asyncio.Event().wait()
        except (KeyboardInterrupt, asyncio.CancelledError):
            pass
        finally:
            await runner.cleanup()

    try:
        asyncio.run(run())
    except KeyboardInterrupt:
        LOG.info("Shutting down...")


if __name__ == "__main__":
    main()

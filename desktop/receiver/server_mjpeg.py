import argparse
import asyncio
import logging
from typing import List, Optional
import cv2
import numpy as np

from aiohttp import web
from aiortc import RTCPeerConnection, RTCSessionDescription, RTCIceServer, RTCConfiguration
from aiortc.contrib.media import MediaBlackhole

logging.basicConfig(level=logging.INFO)
LOG = logging.getLogger("receiver")

pcs: List[RTCPeerConnection] = []
latest_frame: Optional[np.ndarray] = None
frame_lock = asyncio.Lock()


async def consume_video(track):
    """Read frames from track and store latest frame."""
    global latest_frame
    try:
        while True:
            frame = await track.recv()
            img = frame.to_ndarray(format="bgr24")
            async with frame_lock:
                latest_frame = img
    except asyncio.CancelledError:
        LOG.info("Video consumer cancelled")


async def consume_audio(track):
    """Discard audio for now."""
    sink = MediaBlackhole()
    try:
        while True:
            frame = await track.recv()
    except asyncio.CancelledError:
        pass


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
            asyncio.create_task(consume_video(track))
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


async def video_feed(request: web.Request):
    """MJPEG stream endpoint - view at http://localhost:8000/video"""
    response = web.StreamResponse()
    response.content_type = 'multipart/x-mixed-replace; boundary=frame'
    await response.prepare(request)

    try:
        while True:
            async with frame_lock:
                frame = latest_frame
            
            if frame is not None:
                # Encode frame as JPEG
                _, buffer = cv2.imencode('.jpg', frame, [cv2.IMWRITE_JPEG_QUALITY, 80])
                frame_bytes = buffer.tobytes()
                
                # Send MJPEG frame
                await response.write(
                    b'--frame\r\n'
                    b'Content-Type: image/jpeg\r\n\r\n' + frame_bytes + b'\r\n'
                )
            
            await asyncio.sleep(1/30)  # 30 FPS output
    except asyncio.CancelledError:
        LOG.info("Video feed client disconnected")
    
    return response


async def index(request: web.Request):
    """Simple HTML viewer for the video stream"""
    html = """
    <!DOCTYPE html>
    <html>
    <head>
        <title>PhoneCam Stream</title>
        <style>
            body { 
                margin: 0; 
                background: #000; 
                display: flex; 
                justify-content: center; 
                align-items: center; 
                height: 100vh;
                font-family: Arial, sans-serif;
            }
            #container {
                text-align: center;
            }
            img { 
                max-width: 100vw; 
                max-height: 90vh;
                border: 2px solid #333;
            }
            h1 {
                color: #fff;
                margin-bottom: 20px;
            }
            .info {
                color: #888;
                margin-top: 10px;
            }
        </style>
    </head>
    <body>
        <div id="container">
            <h1>PhoneCam Live Stream</h1>
            <img src="/video" alt="Video Stream">
            <div class="info">Stream URL: http://YOUR_PC_IP:8000/video</div>
        </div>
    </body>
    </html>
    """
    return web.Response(text=html, content_type='text/html')


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
    app.router.add_get("/video", video_feed)
    app.router.add_get("/", index)
    app.on_shutdown.append(on_shutdown)
    return app


def main():
    parser = argparse.ArgumentParser(description="PhoneCam receiver with MJPEG stream")
    parser.add_argument("--host", default="0.0.0.0", help="Host to bind (default: 0.0.0.0)")
    parser.add_argument("--port", type=int, default=8000, help="Port to bind (default: 8000)")
    parser.add_argument("--stun", help="STUN server URL (e.g., stun:stun.l.google.com:19302)")
    args = parser.parse_args()

    app = create_app(args)
    LOG.info("Receiver listening on http://%s:%d", args.host, args.port)
    LOG.info("View stream at: http://%s:%d/ or http://%s:%d/video", args.host, args.port, args.host, args.port)
    web.run_app(app, host=args.host, port=args.port, access_log=None)


if __name__ == "__main__":
    main()

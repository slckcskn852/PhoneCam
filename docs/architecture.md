# Architecture

## High-level
- Transport: WebRTC (SRTP) for low-latency, congestion-controlled, NAT-friendly media. STUN (public) configurable; TURN optional later.
- Signaling: simple HTTP+WebSocket service hosted on the PC receiver (Python `aiohttp`). Android posts an SDP offer to `/offer`; server responds with SDP answer and ICE candidates via the same connection.
- Media pipeline:
  - Android: CameraX -> SurfaceTexture -> libwebrtc video capturer -> H.264 encoder (MediaCodec) @ 1920x1080p60 -> WebRTC PeerConnection.
  - PC: aiortc PeerConnection -> decode via PyAV/FFmpeg -> RGB frames -> pyvirtualcam -> OBS Virtual Camera device exposed to system.
- Control: WebRTC data channel for remote control (toggle torch, switch cameras, start/stop) and stats (bitrate/fps loss reports).
- Security: DTLS-SRTP end-to-end; optional auth token on signaling requests.

## Components
- `android/` — Native Android app (Kotlin) using CameraX + libwebrtc AAR.
- `desktop/receiver/` — Python 3.11+ app using `aiortc`, `aiohttp`, `pyvirtualcam`, `av` (FFmpeg), exposing a simple UI-less server plus a preview window.
- `docs/` — Requirements, architecture, runbook.

## Constraints & notes
- 1080p60 requires H.264 hardware encode on phone and hardware-accelerated decode on PC (FFmpeg). Fixed 15 Mbps bitrate for optimal quality and stability; ensure strong Wi‑Fi (5 GHz/6E) or wired Ethernet for PC.
- Virtual webcam on Windows relies on OBS Virtual Camera driver (install OBS Studio and enable "Start Virtual Camera" at least once to register device).
- Latency tuning: prefer `googCpuOveruseDetection`, disable simulcast, set `maxBitrate` and `minBitrate`, prefer UDP ICE candidates; consider `setLowLatency(true)` on encoders where available.

## Signaling flow
1) Receiver starts Python server (listens on `0.0.0.0:8000`).
2) Android app discovers receiver (manual IP entry or mDNS). Sends POST `/offer` with SDP offer.
3) Server creates `RTCPeerConnection`, sets remote description, adds transceivers (video, optional audio), gathers ICE, creates answer.
4) Server responds with SDP answer; ICE candidates flow via WebSocket or included in SDP (trickle optional).
5) Media flows; server writes frames to virtual cam and optional preview.

## Future enhancements
- TURN server integration for NAT traversal.
- Desktop UI (Electron/Qt) for connection management and settings.
- USB tether fallback transport.
- HEVC and 4K modes.

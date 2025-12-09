# Requirements

- Goal: Use an Android phone camera as a PC webcam (Windows target), delivering 1080p @ 60 fps with low latency.
- Latency target: sub-150 ms glass-to-glass on wired LAN or high-quality Wi‑Fi; tolerate up to 250 ms on average Wi‑Fi.
- Video: 1920x1080, 60 fps, H.264 (baseline/main) preferred for broad decode support; fallback to VP8 if H.264 unavailable. Bitrate fixed at 15 Mbps for optimal quality and stability.
- Audio: optional microphone forwarding (mono, 48 kHz Opus) with enable/disable toggle.
- Transport: end-to-end encrypted; resilient to packet loss; adaptive bitrate; NAT-friendly.
- Control: start/stop stream, resolution/fps selection, fixed 15 Mbps bitrate, camera switch (front/rear), torch toggle, autofocus/AE lock, framing guides.
- Desktop output: expose as virtual webcam device; allow preview window; configurable mirror; optional recording.
- Platforms: Android 10+; Windows 10/11 receiver (goal). Linux/macOS optional later.
- Network: same LAN recommended; remote over internet acceptable if port-forwarding/STUN/TURN available.
- Dependencies allowed: WebRTC stack (libwebrtc/aiortc), FFmpeg on PC for decode assistance, OBS Virtual Camera driver for virtual cam sink.
- Non-goals (for v1): USB tethering, HDR, manual exposure/ISO control, HEVC, background segmentation.

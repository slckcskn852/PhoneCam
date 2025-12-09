# Requirements

## Goal
Use an Android phone camera as a PC webcam (Windows), delivering 1080p @ 60 fps with low latency.

## Video
- Resolution: 1920x1080 @ 60 fps
- Codec: H.264 (hardware-accelerated encoding on phone)
- Bitrate: Adjustable 5-30 Mbps via phone UI
- Output: Unity Capture virtual camera (DirectShow)

## Latency
- Target: < 100ms glass-to-glass on 5GHz WiFi
- Acceptable: < 200ms on average WiFi conditions
- Optimizations: Multi-threaded CPU decoding, low-delay flags, minimal buffering

## Transport
- Protocol: Direct TCP connection (no WebRTC overhead)
- Port: 5000 (configurable)
- Framing: H.264 NAL units with 4-byte start codes
- Rotation: Out-of-band 5-byte messages with validation

## Features
- Real-time rotation following phone orientation
- Pinch-to-zoom on phone
- Adjustable bitrate slider
- FPS and bitrate monitoring on both ends
- One-click connect/disconnect

## Platforms
- **Android**: Android 7.0+ (API 24+)
- **PC**: Windows 10/11 with Unity Capture driver

## Dependencies

### Android
- CameraX for camera capture
- MediaCodec for H.264 hardware encoding
- Kotlin coroutines for async operations

### PC (Windows)
- Python 3.10+
- PyAV for H.264 decoding (FFmpeg bindings)
- pyvirtualcam for Unity Capture output
- OpenCV for frame manipulation
- tkinter for GUI

## Non-goals (v1)
- Audio forwarding
- USB tethering
- GPU-accelerated decoding
- HEVC/H.265
- Multiple simultaneous connections
- Remote/internet streaming (same LAN only)

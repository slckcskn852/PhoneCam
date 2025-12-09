# Architecture

## High-level
- Transport: Direct TCP connection for low-latency H.264 streaming. No WebRTC overhead.
- Connection: Phone connects to PC receiver on port 5000. Simple client-server model.
- Media pipeline:
  - Android: CameraX → SurfaceTexture → MediaCodec H.264 encoder @ 1920x1080p60 → TCP socket with NAL unit framing
  - PC: TCP receiver → PyAV decoder (8 threads, low-delay) → BGR frames → pyvirtualcam → Unity Capture virtual camera
- Rotation: Out-of-band rotation messages embedded in stream (5-byte protocol with validation)
- Virtual Camera: Unity Capture DirectShow driver for maximum compatibility

## Components
- `android/` — Native Android app (Kotlin) using CameraX + MediaCodec H.264 encoder
- `desktop/receiver/` — Python 3.10+ app using PyAV, pyvirtualcam, tkinter GUI
- `UnityCapture-master/` — Virtual camera DirectShow driver
- `docs/` — Requirements, architecture, testing guides

## Protocol Details

### H.264 NAL Unit Framing
- Standard 4-byte start codes: `00 00 00 01`
- NAL units sent sequentially over TCP
- SPS/PPS sent at stream start and periodically

### Rotation Messages
- **Format**: 5 bytes
  - Byte 0: `0xFF` (prefix, rare in H.264)
  - Byte 1-2: `RT` (ASCII)
  - Byte 3: rotation value (0=0°, 1=90°, 2=180°, 3=270°)
  - Byte 4: `0xAA` (suffix checksum)
- **Validation**: All 3 conditions must match to prevent false positives from H.264 data

## Decoder Configuration
- **Library**: PyAV (FFmpeg Python bindings)
- **Codec**: H.264 software decoder
- **Thread Type**: SLICE (parallel slice decoding)
- **Thread Count**: 8 threads
- **Options**: `flags=low_delay`, `flags2=fast`
- **Output**: BGR24 numpy arrays

## Constraints & Notes
- 1080p60 requires H.264 hardware encode on phone and multi-threaded CPU decode on PC
- Bitrate adjustable 5-30 Mbps via phone UI
- Virtual webcam on Windows requires Unity Capture driver (run Install.bat as admin)
- Latency target: < 100ms on good WiFi with low bitrate
- Use 5GHz WiFi for best performance

## Connection Flow
1. PC receiver starts, binds TCP socket on port 5000
2. Android app connects to PC IP:5000
3. Android sends SPS/PPS NAL units
4. Android streams encoded H.264 frames continuously
5. Android sends rotation messages when orientation changes
6. PC decodes frames and writes to virtual camera

## Future Enhancements
- GPU-accelerated decoding (requires different approach than FFmpeg subprocess)
- Audio forwarding
- USB tethering support
- Multiple camera support
- HEVC/H.265 for better compression

# PhoneCam Android App

Android client that captures 1080p60 video via CameraX and streams to the PC receiver over direct TCP connection.

## Prerequisites
- Android Studio (latest stable) with Android SDK 24+.
- Android device running Android 7.0+ (API 24+) with camera support.
- USB debugging enabled or wireless debugging configured.

## Build & Run
1. Open the `android/` folder in Android Studio.
2. Sync Gradle (Android Studio will prompt; allow it to download dependencies).
3. Connect your Android device via USB or wireless.
4. Build and run (Run → Run 'app' or Shift+F10).
5. Grant Camera permission when prompted.
6. Enter your PC's IP address in the app.
7. Tap **Start** to begin streaming.

## Configuration
- **Server Address**: Enter your PC's local IP address (e.g., `192.168.1.10`). Port 5000 is used by default.
- **Resolution/FPS**: Configured for 1920x1080@60 in the streaming code.
- **Bitrate**: Adjustable via slider in the UI (5-30 Mbps range).
- **Camera selection**: Currently uses back camera; tap switch icon to toggle.

## Streaming Protocol
- **Transport**: Direct TCP socket connection to PC on port 5000
- **Video Format**: H.264 NAL units with 4-byte start codes (0x00000001)
- **Rotation**: 5-byte out-of-band messages (0xFF + "RT" + rotation_value + 0xAA)

## Troubleshooting
- **No camera preview**: Ensure permissions granted; check Logcat for errors.
- **Connection fails**: Verify PC receiver is running and reachable; check firewall rules for port 5000.
- **Poor quality/lag**: Use 5 GHz Wi-Fi; lower bitrate if network is unstable.
- **Build errors**: Ensure Gradle sync completed; check `build.gradle` dependencies resolve.

## Architecture
- `MainActivity.kt`: UI setup, permission handling, streaming lifecycle.
- `RtspStreamer.kt`: Camera capture (CameraX), H.264 encoding (MediaCodec), TCP streaming, orientation handling.

## Features
- Real-time camera preview
- Pinch-to-zoom
- Adjustable bitrate slider
- Orientation-aware streaming (rotation follows phone)
- Connection status display
- FPS and bitrate monitoring

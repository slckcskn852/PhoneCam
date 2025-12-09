# PhoneCam Android App

Android client that captures 1080p60 video via CameraX and streams to the PC receiver over WebRTC.

## Prerequisites
- Android Studio (latest stable) with Android SDK 24+.
- Android device running Android 10+ with camera support.
- USB debugging enabled or wireless debugging configured.

## Build & Run
1. Open the `android/` folder in Android Studio.
2. Sync Gradle (Android Studio will prompt; allow it to download dependencies including `org.webrtc:google-webrtc`).
3. Connect your Android device via USB or wireless.
4. Edit `app/src/main/res/values/strings.xml` and set `default_signaling_url` to your PC's IP: `http://<pc-ip>:8000/offer`.
5. Build and run (Run → Run 'app' or Shift+F10).
6. Grant Camera and Microphone permissions when prompted.
7. Tap **Connect** in the app.

## Configuration
- **Signaling URL**: The app posts an SDP offer to this endpoint (defaults to the value in `strings.xml`). You can override in the UI.
- **Resolution/FPS**: Hard-coded to 1920x1080@60 in `WebRtcClient.kt` line ~95. Adjust `capturer.startCapture(width, height, fps)` if needed.
- **Camera selection**: Currently prefers back camera; modify `createCapturer()` in `WebRtcClient.kt` to force front.

## Troubleshooting
- **No camera preview**: Ensure permissions granted; check Logcat for errors.
- **Connection fails**: Verify PC receiver is running and reachable; check firewall rules for port 8000.
- **Poor quality/lag**: Use 5 GHz Wi-Fi; ensure 20+ Mbps upload speed (fixed 15 Mbps bitrate).
- **Build errors**: Ensure Gradle sync completed; check `build.gradle` dependencies resolve (WebRTC AAR is ~100 MB).

## Architecture
- `MainActivity.kt`: UI setup, permission handling, WebRTC client lifecycle.
- `WebRtcClient.kt`: Camera capture (CameraX + libwebrtc), PeerConnection management, offer/answer exchange.
- `Signaling.kt`: HTTP client for SDP exchange with receiver.
- `PhoneCamApp.kt`: Application class initializing WebRTC factory.

## Next Steps
- Add UI controls for torch, camera switch, resolution picker.
- Implement data channel for remote control from PC.
- Add stats overlay showing connection quality.

# PhoneCam – Complete Setup & Testing Guide

## Overview
PhoneCam streams your Android camera (1080p @ 60 fps) to Windows PC as a virtual webcam over WebRTC (low latency, encrypted). Suitable for video calls, streaming, recording.

---

## Part 1: Desktop Receiver Setup (Windows)

### Prerequisites
- **Windows 10/11** (x64).
- **Python 3.10+** (verify: `python --version`).
- **OBS Studio** installed (download: https://obsproject.com/download).
- **FFmpeg** (optional but recommended; ensure `ffmpeg.exe` in PATH or installed system-wide).

### Step 1: Install OBS Studio and Unity Capture
1. **Install OBS Studio** (download: https://obsproject.com/download).
2. **Install Unity Capture** virtual camera driver:
   - Download from: https://github.com/schellingb/UnityCapture/releases
   - Extract the ZIP file
   - Run **Install.cmd as Administrator**
   - The virtual camera device "Unity Video Capture" is now available

**Note:** Unity Capture is required for this app. It provides a standalone virtual camera that can be used in OBS Studio, NVIDIA Broadcast, and other applications simultaneously.

### Step 2: Install Python Dependencies
```powershell
cd D:\Projects\PhoneCam\desktop\receiver
python -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install --upgrade pip
python -m pip install -r requirements.txt
```

If `av` install fails, see `notes.md` for troubleshooting.

### Step 3: Start the Receiver Server
```powershell
python server_highquality.py --host 0.0.0.0 --port 8000 --stun stun:stun.l.google.com:19302
```

You should see:
```
======================================================================
PhoneCam Virtual Camera Server (High Quality)
======================================================================
Server listening on: http://0.0.0.0:8000
Status endpoint: http://0.0.0.0:8000/status

REQUIRES Unity Capture virtual camera driver!
Download: https://github.com/schellingb/UnityCapture/releases
Run Install.cmd as Administrator

Features:
  - 1920x1080@60fps capture
  - Bitrate: Fixed 15 Mbps (optimal quality and stability)
  - Hardware-accelerated H.264 encoding/decoding
  - Dynamic resolution handling
======================================================================
```

**Find your PC's local IP** (same LAN as phone):
```powershell
ipconfig
```
Look for the `IPv4 Address` on your active adapter (e.g., `192.168.1.10`).

**Firewall:** Allow port 8000 inbound if prompted.

---

## Part 2: Android App Setup

### Prerequisites
- **Android Studio** (latest stable, includes SDK/NDK).
- **Android device** running Android 10+ with camera and Wi‑Fi.
- **USB cable** for deploying APK or wireless debugging.

### Step 1: Open Project in Android Studio
1. Launch Android Studio.
2. **File → Open**, navigate to `D:\Projects\PhoneCam\android`, click **OK**.
3. Wait for Gradle sync (downloads dependencies, including `org.webrtc:google-webrtc:1.0.32006`).

### Step 2: Configure Signaling URL (Optional)
By default, the app uses `http://192.168.1.10:8000/offer` (edit in `app/src/main/res/values/strings.xml` or enter at runtime in the UI).

### Step 3: Build & Run
1. Connect your Android device via USB and enable **USB Debugging** (Settings → Developer Options).
2. Select your device in Android Studio toolbar.
3. Click **Run** (green ▶ icon) or press **Shift+F10**.
4. Grant **Camera** and **Microphone** permissions when prompted.

---

## Part 3: Connecting & Testing

### Android App UI Features
The app features a modern, camera-first interface:

1. **Camera Preview Background:** The entire screen shows your camera feed in real-time
2. **Transparent Controls:** Input fields and buttons are overlaid with semi-transparent backgrounds
3. **Auto-Dimming Display:** 
   - After 5 seconds of inactivity when connected, the screen darkens to save battery
   - Tap anywhere on the screen to restore controls
   - Useful for extended streaming sessions
4. **Fixed 15 Mbps Bitrate:**
   - Optimized for quality and stability
   - Perfect balance for most Wi-Fi networks
   - Prevents encoder corruption and ensures consistent quality

### Connect the Stream
1. On the **Android app**:
   - Enter your PC IP in the **Signaling URL** field (e.g., `http://192.168.1.10:8000/offer`).
   - Tap **Connect** (streams at fixed 15 Mbps for optimal quality).
   - Watch the status: `Starting local capture…` → `Building peer connection…` → `Creating offer…` → `Connected (answer set)`.
   - The camera preview fills the entire screen.
   - After 5 seconds of inactivity, the screen will dim. Tap anywhere to restore controls.

2. On the **PC**:
   - Server logs show `Created PeerConnection`, `Track video`, and `Virtual camera initialized (Unity Capture): 1920x1080@60fps`.

### Verify Virtual Webcam
1. Open any webcam app on Windows:
   - **OBS Studio** (add **Video Capture Device** source, select "Unity Video Capture")
   - **NVIDIA Broadcast** (select "Unity Video Capture" as input)
   - **Camera** (built-in Windows app)
   - **Zoom**, **Teams**, **Skype**
   - **VLC** (Media → Open Capture Device)
2. Select **Unity Video Capture** from the camera list.
3. You should see the phone's camera feed in real-time at 1920x1080@60fps.

### Performance Check
- **Target:** 1920x1080@60fps with <150 ms latency.
- **Network:** 5 GHz Wi‑Fi or wired Ethernet for PC recommended; phone on same LAN.
- **Bitrate:** Fixed at 15 Mbps for optimal quality and stability
  - Provides excellent quality for most home Wi-Fi networks
  - Requires minimum 20 Mbps upload bandwidth on phone
  - Prevents encoder corruption and network congestion
- **Monitor stats:** Server logs track reception; Android logs show ICE/PC state changes.
- If choppy or laggy: ensure stable network connection with 20+ Mbps upload speed.
- **Battery:** The auto-dimming feature helps conserve battery during long streaming sessions.

---

## Part 4: Troubleshooting

### Desktop Receiver Issues

**"ModuleNotFoundError: No module named 'av'"**
- Re-run: `python -m pip install -r requirements.txt`
- Check venv is active (prompt shows `(.venv)`).

**"No OBS Virtual Camera found"** or **"Unity Capture not found"**
- Ensure Unity Capture is installed: download from https://github.com/schellingb/UnityCapture/releases
- Run **Install.cmd as Administrator** from the Unity Capture release
- Restart the receiver after installing Unity Capture
- Verify installation: the "Unity Video Capture" device should appear in Device Manager under "Sound, video and game controllers"

**"Connection refused" from Android**
- Verify PC IP and firewall allow port 8000.
- Test with browser on phone: `http://<pc-ip>:8000` (should show 404, proving server reachable).

**No video in webcam apps**
- Restart the receiver; wait for "Virtual camera initialized (Unity Capture)" log.
- Close and reopen the webcam app.
- Check **Unity Video Capture** is selected (not default/built-in cam).

**Video quality issues or stuttering**
- Ensure your network provides stable 20+ Mbps upload speed (fixed 15 Mbps bitrate).
- Ensure phone and PC are on same network, preferably 5 GHz Wi-Fi.
- Check network congestion (pause other downloads/streams).
- Monitor server logs for connection stability.
- Use wired Ethernet for PC for best stability.

### Android App Issues

**"Gradle sync failed"**
- Check internet connection; Gradle downloads dependencies.
- Invalidate caches: **File → Invalidate Caches / Restart**.

**"PeerConnection creation failed"**
- Ensure WebRTC library resolved: check `app/build.gradle` has `org.webrtc:google-webrtc:1.0.32006`.
- Rebuild: **Build → Clean Project**, then **Build → Rebuild Project**.

**"Bad response 404"**
- Server not running or wrong URL. Verify server logs show `listening`.
- Confirm URL ends in `/offer` (e.g., `http://192.168.1.10:8000/offer`).

**No preview or black screen**
- Grant camera permission.
- Check device camera works in other apps.
- Logcat: filter `PhoneCam` for errors.
- Try tapping the screen to restore controls if auto-dimming has activated.

**ICE failed / connection timeout**
- Phone and PC must be on same LAN; NAT traversal via STUN may not work across complex firewalls.
- Try wired Ethernet for PC and strong Wi‑Fi (5 GHz) for phone.

---

## Part 5: Advanced Configuration

### Change Resolution/FPS (Android)
Edit `WebRtcClient.kt`, line ~130:
```kotlin
capturer.startCapture(1920, 1080, 60)  // Change to 1280, 720, 30 for lower bandwidth
```

### Change Bitrate
The bitrate is fixed at 15 Mbps for optimal quality and stability. To change it, modify `MainActivity.kt`:
```kotlin
private var currentBitrateMbps = 15 // Change default here
```
And update the cap in `WebRtcClient.kt`:
```kotlin
val cappedBitrate = minOf(targetBitrateMbps, 15) // Change cap here
```

### Disable Auto-Dimming
Edit `MainActivity.kt`, modify the delay or remove the feature:
```kotlin
dimHandler.postDelayed(dimRunnable, 10000) // Change 5000 to 10000 for 10 seconds
// Or remove scheduleDimming() calls to disable entirely
```

### Add TURN Server (for remote/internet streaming)
In `server_highquality.py`, modify `create_app()`:
```python
stun_servers.append(RTCIceServer("turn:your-turn-server.com:3478", username="user", credential="pass"))
```

And in `WebRtcClient.kt`, add TURN to `iceServers` list.

### Record Stream on PC
In `consume_video()` (server_highquality.py), before `virtual_cam.send(img)`, write frames to file using OpenCV or PyAV (not included by default).

---

## Part 6: Known Limitations & Features (v0.2)

### Current Features
- ✅ **Full-screen camera preview** with transparent overlay controls
- ✅ **Auto-dimming display** after 5 seconds of inactivity (tap to restore)
- ✅ **Fixed 15 Mbps bitrate** for optimal quality and stability
- ✅ **1920x1080@60fps** high-quality capture
- ✅ **Unity Capture** virtual camera for OBS Studio compatibility
- ✅ **Consistent quality** with fixed bitrate preventing encoder issues

### Known Limitations
- **USB tether not supported** (Wi‑Fi/LAN only).
- **No HDR, manual exposure, or advanced camera controls** (future enhancement).
- **No camera switching UI** (front/back camera - defaults to back camera).
- **No torch/flashlight toggle** in UI yet (WebRTC data channel ready for extension).
- **No data channel commands** (torch toggle, camera switch) implemented in UI yet (WebRTC data channel code ready for extension).
- **Fixed bitrate** (15 Mbps optimized for quality and stability; not user-adjustable).
- **No multi-client support** (one phone → one PC at a time; close existing peer before reconnecting).
- **Windows only** (Linux/macOS receiver requires `v4l2loopback` or alternative virtual cam driver).
- **Network requirements** (20+ Mbps upload recommended for stable streaming).

---

## Part 7: Testing Checklist

- [ ] Receiver starts without errors; logs show listening with Unity Capture requirements.
- [ ] Unity Capture driver installed (Install.cmd run as Administrator).
- [ ] Android app builds and installs; permissions granted.
- [ ] Camera preview fills entire screen with transparent controls overlaid.
- [ ] App connects; status shows "Connected (answer set)".
- [ ] Local preview on phone displays camera feed smoothly.
- [ ] Auto-dimming activates after 5 seconds of inactivity; tap restores controls.
- [ ] Windows webcam app shows phone feed via Unity Video Capture device.
- [ ] Video is smooth at fixed 15 Mbps bitrate.
- [ ] Connection remains stable with consistent quality.
- [ ] Latency is sub-200 ms.
- [ ] Disconnect/reconnect cycles work (stop app, restart, re-connect).
- [ ] Firewall allows traffic; no "connection refused" errors.
- [ ] OBS Studio can use Unity Video Capture as Video Capture Device source.
- [ ] NVIDIA Broadcast recognizes Unity Video Capture device (if installed).

---

## Next Steps

- **Production hardening:** Add authentication token to signaling, TLS for HTTPS, TURN for internet access.
- **UI improvements:** Add camera switch button (front/back), torch toggle, exposure controls.
- **Desktop UI:** Replace CLI server with Electron/Qt app for connection management and preview window.
- **Audio forwarding:** Enable `audioTrack` on Android and decode on PC (optional microphone as audio input).
- **Multi-platform:** Port receiver to Linux (v4l2loopback) and macOS (CameraExtension or similar).
- **Bitrate optimization:** Add network quality detection and auto-adjustment recommendations.
- **Recording:** Add built-in recording feature to save streams directly on PC.

---

**Congratulations!** You now have a professional-grade 1080p60 WebRTC phone-to-PC webcam system with fixed 15 Mbps bitrate for optimal quality, full-screen camera preview, auto-dimming display, and Unity Capture virtual camera support for OBS Studio streaming. Perfect for content creation, streaming, video calls, and NVIDIA Broadcast integration.

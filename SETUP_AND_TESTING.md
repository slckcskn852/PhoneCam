# PhoneCam – Complete Setup & Testing Guide

## Overview
PhoneCam streams your Android camera (1080p @ 60 fps) to Windows PC as a virtual webcam over direct TCP connection. Low latency, simple setup. Suitable for video calls, streaming, recording.

---

## Part 1: Desktop Receiver Setup (Windows)

### Prerequisites
- **Windows 10/11** (x64).
- **Python 3.10+** (verify: `python --version`).
- **Unity Capture** virtual camera driver.

### Step 1: Install Unity Capture Virtual Camera
1. Navigate to `UnityCapture-master/Install/`
2. Right-click `Install.bat` → **Run as Administrator**
3. The virtual camera device "Unity Video Capture" is now available

**Note:** Unity Capture provides a standalone virtual camera that works in OBS Studio, NVIDIA Broadcast, Zoom, and other applications.

### Step 2: Install Python Dependencies
```powershell
cd D:\Projects\PhoneCam-nvdec-decode\desktop\receiver
python -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install --upgrade pip
python -m pip install -r requirements.txt
```

### Step 3: Start the Receiver
```powershell
python rtsp_receiver_gui.py
```

Or use the standalone executable:
```powershell
.\dist\PhoneCam-RTSP-Receiver.exe
```

You should see the GUI window with:
- Your PC's IP address displayed
- Start/Stop button
- Status indicator
- Connection info

**Find your PC's local IP** (same LAN as phone):
```powershell
ipconfig
```
Look for the `IPv4 Address` on your active adapter (e.g., `192.168.1.10`).

**Firewall:** Allow port 5000 inbound if prompted.

---

## Part 2: Android App Setup

### Prerequisites
- **Android Studio** (latest stable, includes SDK).
- **Android device** running Android 7.0+ (API 24+) with camera.
- **USB cable** for deploying APK or wireless debugging.

### Step 1: Open Project in Android Studio
1. Launch Android Studio.
2. **File → Open**, navigate to `D:\Projects\PhoneCam-nvdec-decode\android`, click **OK**.
3. Wait for Gradle sync to complete.

### Step 2: Build & Run
1. Connect your Android device via USB and enable **USB Debugging** (Settings → Developer Options).
2. Select your device in Android Studio toolbar.
3. Click **Run** (green ▶ icon) or press **Shift+F10**.
4. Grant **Camera** permission when prompted.

---

## Part 3: Connecting & Testing

### Android App UI Features
The app features a modern, camera-first interface:

1. **Camera Preview Background:** The entire screen shows your camera feed in real-time
2. **Transparent Controls:** Input field, buttons, and sliders overlaid with semi-transparent backgrounds
3. **Pinch-to-Zoom:** Pinch gesture to zoom camera
4. **Bitrate Slider:** Adjust streaming quality (5-30 Mbps)

### Connect the Stream
1. On the **Android app**:
   - Enter your PC's IP address in the text field (e.g., `192.168.1.10`).
   - Tap **Start** to begin streaming.
   - Watch the status: `Connecting...` → `Streaming`.
   - The camera preview fills the entire screen.

2. On the **PC**:
   - Click **Start** in the receiver GUI.
   - Status shows "Waiting for connection..." → "Connected".
   - Frame count increases as video is received.

### Verify Virtual Webcam
1. Open any webcam app on Windows:
   - **OBS Studio** (add **Video Capture Device** source, select "Unity Video Capture")
   - **NVIDIA Broadcast** (select "Unity Video Capture" as input)
   - **Camera** (built-in Windows app)
   - **Zoom**, **Teams**, **Skype**
2. Select **Unity Video Capture** from the camera list.
3. You should see the phone's camera feed in real-time.

### Performance Check
- **Target:** 1920x1080@60fps with <150 ms latency.
- **Network:** 5 GHz Wi‑Fi recommended; phone and PC on same LAN.
- **Bitrate:** Adjustable 5-30 Mbps via slider in Android app.
- **Decoding:** CPU-based with 8 threads (low CPU usage, ~10-15%).

---

## Part 4: Troubleshooting

### Desktop Receiver Issues

**"Unity Capture not found"**
- Ensure Unity Capture is installed: run `UnityCapture-master/Install/Install.bat` as Administrator.
- Restart the receiver after installing Unity Capture.
- Verify installation: "Unity Video Capture" device appears in Device Manager.

**"Connection refused" from Android**
- Verify PC IP address is correct.
- Check firewall allows port 5000 inbound.
- Ensure receiver is running and showing "Waiting for connection...".

**No video in webcam apps**
- Restart the receiver.
- Close and reopen the webcam app.
- Check **Unity Video Capture** is selected (not default/built-in cam).

**Video quality issues or stuttering**
- Lower bitrate in Android app.
- Ensure phone and PC are on same network, preferably 5 GHz Wi-Fi.
- Check network congestion (pause other downloads/streams).
- Use wired Ethernet for PC for best stability.

### Android App Issues

**"Connection failed"**
- Verify PC receiver is running.
- Check IP address is correct.
- Ensure both devices are on same network.

**No preview or black screen**
- Grant camera permission.
- Check device camera works in other apps.
- Logcat: filter `PhoneCam` for errors.

**Rotation not working correctly**
- Ensure you're using the latest version with 5-byte rotation protocol.
- Rotation changes require holding phone steady for 500ms to take effect.

---

## Part 5: Advanced Configuration

### Change Resolution/FPS
Edit `RtspStreamer.kt` in the streaming setup code to adjust resolution and frame rate.

### Change Default Port
- **Android:** Modify the port constant in `RtspStreamer.kt`
- **PC:** Modify the `PORT` variable in `rtsp_receiver_gui.py`

### Building Standalone Executable
```powershell
cd desktop\receiver
python -m pip install pyinstaller
pyinstaller PhoneCam-RTSP-Receiver.spec
```
Output: `dist\PhoneCam-RTSP-Receiver.exe`

---

## Part 6: Known Limitations & Features

### Current Features
- ✅ **Full-screen camera preview** with transparent overlay controls
- ✅ **Pinch-to-zoom** on Android
- ✅ **Adjustable bitrate** (5-30 Mbps slider)
- ✅ **1920x1080@60fps** high-quality capture
- ✅ **Unity Capture** virtual camera for OBS Studio compatibility
- ✅ **Orientation-aware** streaming (rotation follows phone)
- ✅ **Low latency** (~80-120ms glass-to-glass)

### Known Limitations
- **Same network required** (Wi‑Fi/LAN only, no internet streaming).
- **Windows only** (Linux/macOS receiver not implemented).
- **No audio** forwarding.
- **No USB tethering** support.
- **Single connection** (one phone → one PC at a time).

---

## Part 7: Testing Checklist

- [ ] Unity Capture driver installed (Install.bat run as Administrator).
- [ ] Receiver starts without errors; GUI shows local IP address.
- [ ] Android app builds and installs; camera permission granted.
- [ ] Camera preview displays on phone screen.
- [ ] App connects; status shows "Streaming".
- [ ] Receiver shows "Connected"; frame counter increases.
- [ ] Windows webcam app shows phone feed via Unity Video Capture.
- [ ] Video is smooth and responsive.
- [ ] Rotation changes work when rotating phone.
- [ ] Zoom works via pinch gesture.
- [ ] Bitrate slider affects quality.
- [ ] Disconnect/reconnect cycles work.

---

## Architecture

### Protocol
- **Transport:** Direct TCP connection on port 5000
- **Video:** H.264 NAL units with 4-byte start codes
- **Rotation:** 5-byte out-of-band messages (0xFF + "RT" + value + 0xAA)

### Decoding
- **Codec:** PyAV with H.264 CPU decoder
- **Threading:** 8 threads for parallel decoding
- **Latency:** Low-delay flags minimize buffering

### Virtual Camera
- **Driver:** Unity Capture (DirectShow)
- **Format:** BGRA output
- **Compatibility:** Works with any Windows app that uses webcams

---

**Congratulations!** You now have a working phone-to-PC webcam system with low latency, adjustable quality, and wide application compatibility.

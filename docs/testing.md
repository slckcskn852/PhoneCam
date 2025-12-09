# PhoneCam Testing & Validation Guide

## Desktop Receiver Tests

### 1. Installation & Startup
**Goal**: Verify all dependencies install and receiver starts cleanly.

```powershell
cd desktop/receiver
python -m venv .venv
.\.venv\Scripts\activate
python -m pip install --upgrade pip
pip install -r requirements.txt
python rtsp_receiver_gui.py
```

**Expected**: 
- All packages install (av, pyvirtualcam, numpy, opencv-python).
- GUI opens with "Waiting for connection..." status.
- Your PC's IP address is displayed.

**Prerequisites**:
- Unity Capture driver installed (run Install.bat as Administrator).

### 2. Virtual Camera Detection
**Goal**: Confirm pyvirtualcam can create a Unity Capture device.

```python
import pyvirtualcam
cam = pyvirtualcam.Camera(width=1920, height=1080, fps=60, 
                          fmt=pyvirtualcam.PixelFormat.BGR,
                          backend='unitycapture')
print("Device:", cam.device)
cam.close()
```

**Expected**: Prints "Unity Video Capture" device name; no exceptions.

### 3. H.264 Decoder Test
**Goal**: Verify PyAV decoder initializes with correct settings.

```python
import av
codec = av.CodecContext.create('h264', 'r')
codec.thread_type = 'SLICE'
codec.thread_count = 8
codec.options = {'flags': 'low_delay', 'flags2': 'fast'}
print(f"Decoder: {codec.codec.name}, Threads: {codec.thread_count}")
```

**Expected**: Prints "Decoder: h264, Threads: 8".

### 4. End-to-End 1080p60 Stream
**Goal**: Stream from Android phone to PC and view in a webcam application.

**Steps**:
1. Start receiver (`python rtsp_receiver_gui.py` or run EXE).
2. Note the IP address shown in the GUI.
3. On Android app, enter the PC's IP address and tap Connect.
4. Wait for connection (status shows "Connected").
5. Open a webcam app (OBS, Zoom, Teams) and select "Unity Video Capture".

**Expected**:
- Android shows camera preview and streaming status.
- PC GUI shows FPS and bitrate stats.
- Webcam app displays 1080p video from phone.
- Rotation follows phone orientation.

**Metrics**:
- Latency: < 150ms on 5GHz WiFi.
- Frame drops: < 5% under good conditions.
- CPU usage: Monitor Task Manager during stream.

### 5. Rotation Test
**Goal**: Verify rotation messages are correctly received and applied.

**Steps**:
1. Start streaming in landscape mode.
2. Rotate phone to opposite landscape.
3. Observe video rotation in PC app.

**Expected**:
- Rotation changes within 500ms of stable orientation.
- No spurious rotation changes while phone is stationary.
- Both landscape orientations work correctly.

---

## Android App Tests

### 1. Build & Install
**Goal**: Verify Gradle build succeeds and APK installs.

```powershell
cd android
.\gradlew.bat assembleDebug
```

**Expected**: `app/build/outputs/apk/debug/app-debug.apk` created; no Gradle errors.

Install via:
```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 2. Camera Permissions
**Goal**: Ensure app requests and handles permissions correctly.

**Steps**:
1. Launch app; system prompts for Camera permission.
2. Grant permissions.
3. Preview should show camera feed.

**Expected**: No crashes; preview renders after permissions granted.

### 3. Server Address Validation
**Goal**: Test custom server address input.

**Steps**:
1. Enter invalid address (e.g., `999.999.999.999`).
2. Tap **Start**.

**Expected**: Status text shows connection error or timeout; no crash.

### 4. Camera Capture at 1080p60
**Goal**: Verify capture runs at target resolution and frame rate.

**Steps**:
1. Build with logging enabled; check Logcat for capture resolution.
2. Connect to receiver; check frame size in GUI (should show 1920x1080 or similar).

**Expected**: Receiver shows expected resolution; frames decode successfully.

### 5. TCP Connection States
**Goal**: Monitor connection establishment and data flow.

**Steps**:
1. Start receiver on PC with GUI.
2. Connect from Android app.
3. Observe status in both app UI and PC GUI.

**Expected**:
- Android: Status shows "Streaming".
- PC: Status shows "Connected", frames are decoded and displayed.

---

## Known Limitations & Future Work

- **USB tethering**: Not directly supported; use ADB port forwarding.
- **HEVC/4K**: Encoder not configured; H.264 1080p60 is current max.
- **Audio**: Not currently implemented.
- **GPU decoding**: Was tested but FFmpeg subprocess added latency; CPU decoding with 8 threads is fast enough.
- **Network**: Requires same WiFi network or port forwarding for remote access.

---

## Performance Baselines (Reference)

| Metric                  | Target              | Typical (good Wi-Fi) |
|-------------------------|---------------------|----------------------|
| Resolution              | 1920x1080           | 1920x1080            |
| Frame rate              | 60 fps              | 55–60 fps            |
| Bitrate                 | Fixed 8 Mbps        | 8 Mbps               |
| Latency (glass-to-glass)| < 150 ms            | 80–120 ms            |
| CPU (PC, decode)        | < 20%               | 10–15% (8 threads)   |

Test on your hardware; results vary by network and device performance.

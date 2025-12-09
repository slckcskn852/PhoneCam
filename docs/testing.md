# PhoneCam Testing & Validation Guide

## Desktop Receiver Tests

### 1. Installation & Startup
**Goal**: Verify all dependencies install and server starts cleanly.

```powershell
cd desktop/receiver
python -m venv .venv
.\.venv\Scripts\activate
python -m pip install --upgrade pip
pip install -r requirements.txt
python server.py --host 0.0.0.0 --port 8000
```

**Expected**: 
- All packages install (aiortc, av, pyvirtualcam, numpy, aiohttp).
- Server logs `Receiver listening on http://0.0.0.0:8000`.

**Prerequisites**:
- OBS Studio installed; run once and click "Start Virtual Camera" to register the DirectShow device.
- FFmpeg binaries on PATH or bundled with `av` wheels.

### 2. Virtual Camera Detection
**Goal**: Confirm pyvirtualcam can create a virtual camera device.

```python
import pyvirtualcam
cam = pyvirtualcam.Camera(width=1920, height=1080, fps=60, fmt=pyvirtualcam.PixelFormat.BGR)
print("Device:", cam.device)
cam.close()
```

**Expected**: Prints OBS Virtual Camera device name; no exceptions.

### 3. SDP Offer/Answer Exchange
**Goal**: Validate signaling endpoint responds correctly.

Use `curl` or PowerShell to POST an SDP offer:

```powershell
$body = '{"sdp":"v=0\r\no=- 0 0 IN IP4 0.0.0.0\r\ns=-\r\nt=0 0\r\n","type":"offer"}'
Invoke-RestMethod -Uri http://localhost:8000/offer -Method Post -Body $body -ContentType "application/json"
```

**Expected**: JSON response with `sdp` and `type` fields; server logs `Created PeerConnection`.

### 4. End-to-End 1080p60 Stream
**Goal**: Stream from Android phone to PC and view in a webcam application.

**Steps**:
1. Start receiver (`python server.py --host 0.0.0.0 --port 8000`).
2. Run Android app on device; enter `http://<pc-ip>:8000/offer` as signaling URL.
3. Tap **Connect**; wait for ICE gathering and connection.
4. Open a webcam app (e.g., Windows Camera, OBS Studio, Zoom) and select "OBS Virtual Camera" as source.

**Expected**:
- Android preview shows camera feed.
- PC app displays 1080p video from phone at ~60 fps.
- Latency < 250 ms (glass-to-glass).
- Receiver logs `Virtual camera opened`, `Track <id> kind=video`.

**Metrics**:
- Bitrate: Fixed 15 Mbps for optimal quality and stability.
- Frame drops: < 5% under good Wi-Fi.
- CPU usage on PC: varies by codec/HW decode; monitor Task Manager.

### 5. Network Stress Test
**Goal**: Evaluate robustness under packet loss and bandwidth constraints.

Use network emulation tools (e.g., `clumsy` on Windows) to introduce:
- 1–5% packet loss.
- 50–100 ms added latency.
- Bandwidth cap at 10 Mbps.

**Expected**:
- Stream remains stable; minor frame drops acceptable.
- WebRTC congestion control adapts bitrate.

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
1. Launch app; system prompts for Camera and Microphone.
2. Grant permissions.
3. Preview should show camera feed.

**Expected**: No crashes; preview renders after permissions granted.

### 3. Signaling URL Validation
**Goal**: Test custom signaling URL input.

**Steps**:
1. Enter invalid URL (e.g., `http://999.999.999.999:8000/offer`).
2. Tap **Connect**.

**Expected**: Status text shows connection error or timeout; no crash.

### 4. Camera Capture at 1080p60
**Goal**: Verify capture runs at target resolution and frame rate.

**Steps**:
1. Build with logging enabled; check Logcat for `startCapture(1920, 1080, 60)`.
2. Connect to receiver; inspect WebRTC stats on receiver side for incoming resolution/fps.

**Expected**: Receiver logs confirm 1920x1080 frames at ~60 fps.

### 5. WebRTC Connection States
**Goal**: Monitor ICE and PeerConnection state transitions.

**Steps**:
1. Connect to receiver; observe status text in app UI.
2. Check Logcat for `ICE <state>` and `PC <state>` logs.

**Expected**:
- ICE transitions: `NEW` → `CHECKING` → `CONNECTED`.
- PeerConnection: `NEW` → `CONNECTING` → `CONNECTED`.

---

## Known Limitations & Future Work

- **USB tethering**: Not supported; requires ADB reverse or custom transport.
- **HEVC/4K**: Encoder not configured; H.264 1080p60 is current max.
- **Audio**: Microphone capture implemented but untested; may need gain/noise suppression tuning.
- **Latency tuning**: No jitter buffer config exposed; default WebRTC settings used.
- **Multi-peer**: Receiver supports only one active PeerConnection; additional offers overwrite.
- **TURN fallback**: STUN-only; TURN server needed for restrictive NATs.

---

## Performance Baselines (Reference)

| Metric                  | Target              | Typical (good Wi-Fi) |
|-------------------------|---------------------|----------------------|
| Resolution              | 1920x1080           | 1920x1080            |
| Frame rate              | 60 fps              | 55–60 fps            |
| Bitrate                 | Fixed 15 Mbps       | 15 Mbps              |
| Latency (glass-to-glass)| < 250 ms            | 150–200 ms           |
| Packet loss tolerance   | < 5%                | Stable up to 3%      |
| CPU (PC, decode)        | < 30%               | 15–25% (HW accel)    |

Test on your hardware; results vary by network, device, and codec support.

# PhoneCam (RTSP Version)

Transform your Android phone into a high-quality wireless webcam for your PC using direct H.264 streaming over TCP.

---

## Architecture

```
┌─────────────────┐         TCP/H.264          ┌─────────────────┐
│   Android App   │ ───────────────────────▶   │   PC Receiver   │
│  (Camera+H.264) │      Port 5000             │ (Decode+VCam)   │
└─────────────────┘                            └─────────────────┘
         │                                              │
         ▼                                              ▼
   1080p60 H.264                              Unity Capture
   15 Mbps CBR                                Virtual Camera
                                                    │
                                                    ▼
                                              OBS / Zoom / etc.
```

**This version streams raw H.264 Annex B over TCP** - simpler than WebRTC, lower latency on LAN, no STUN/TURN needed.

---

## Features

### 📱 Android App
- **1920x1080 @ 60fps** hardware H.264 encoding
- **15 Mbps** fixed bitrate (CBR)
- Low-latency encoder settings
- Full-screen camera preview
- Auto-dimming display (5s timeout)
- One-tap connect/disconnect

### 💻 PC Receiver
- **Real-time H.264 decoding** via FFmpeg/PyAV
- **Unity Capture** virtual camera output
- Simple TCP server (no complex protocols)
- GUI and command-line versions
- Shows FPS and bitrate stats

### ⚡ Performance
- **~50-100ms latency** on LAN (vs 100-200ms WebRTC)
- **Lower CPU usage** than WebRTC
- **No NAT issues** on local network
- **Simpler debugging** - it's just TCP

---

## Requirements

### Android
- Android 7.0 (API 24) or higher
- Camera permission

### PC (Windows)
- Windows 10/11
- Python 3.10+ (for running from source)
- [Unity Capture](https://github.com/schellingb/UnityCapture/releases) virtual camera driver

---

## Quick Start

### 1. Install Unity Capture on PC

```powershell
# Download from: https://github.com/schellingb/UnityCapture/releases
# Extract and run Install.bat as Administrator
```

### 2. Start PC Receiver

**Option A: GUI Version**
```powershell
cd desktop/receiver
pip install -r requirements_rtsp.txt
python rtsp_receiver_gui.py
```

**Option B: Command Line**
```powershell
python rtsp_receiver.py --host 0.0.0.0 --port 5000
```

### 3. Install Android App

```powershell
cd android
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 4. Connect

1. Note your PC's IP address (shown in receiver GUI)
2. On Android app, enter: `YOUR_PC_IP:5000`
3. Tap **Connect**
4. Open OBS/Zoom and select "Unity Video Capture"

---

## Configuration

### Server Port
Default is `5000`. To change:

**PC Receiver:**
```powershell
python rtsp_receiver.py --port 5001
```

**Android App:** Edit `res/values/strings.xml`:
```xml
<string name="default_rtsp_url">192.168.1.100:5001</string>
```

### Video Settings (Android)
Edit `RtspStreamer.kt`:
```kotlin
private const val WIDTH = 1920      // Resolution width
private const val HEIGHT = 1080     // Resolution height
private const val FPS = 60          // Frame rate
private const val BITRATE = 15_000_000  // 15 Mbps
```

---

## Troubleshooting

### Connection Failed
- Ensure PC and phone are on same network
- Check Windows Firewall allows port 5000 inbound
- Verify PC IP address is correct
- Try disabling VPN

### No Video in OBS/Zoom
- Verify Unity Capture is installed (run Install.bat as Admin)
- Restart OBS/Zoom after installing Unity Capture
- Select "Unity Video Capture" as camera source
- Check receiver shows "Connected" status

### Choppy Video
- Ensure 5GHz WiFi (not 2.4GHz)
- Check network has 20+ Mbps bandwidth
- Reduce distance to router
- Try wired Ethernet for PC

### High Latency
- This solution is optimized for LAN only
- For internet streaming, use the WebRTC version instead
- Check for network congestion

---

## How It Works

1. **Android App** captures camera using CameraX
2. Frames are encoded to H.264 using MediaCodec (hardware encoder)
3. H.264 NAL units are sent over TCP with Annex B framing (0x00000001 start codes)
4. **PC Receiver** parses NAL units and decodes with PyAV/FFmpeg
5. Decoded BGR frames are sent to Unity Capture virtual camera
6. Any app can use the virtual camera

### Protocol Details
- **Transport**: Raw TCP socket
- **Framing**: H.264 Annex B (0x00000001 start codes)
- **Codec**: H.264 Baseline/Main profile
- **SPS/PPS**: Sent with each keyframe for resilience

---

## Comparison: RTSP vs WebRTC

| Feature | RTSP (this version) | WebRTC (original) |
|---------|---------------------|-------------------|
| Latency (LAN) | 50-100ms | 100-200ms |
| Latency (Internet) | Not supported | 200-500ms |
| NAT Traversal | No | Yes (STUN/TURN) |
| Complexity | Simple | Complex |
| CPU Usage | Lower | Higher |
| Browser Support | No | Yes |
| Encryption | No (LAN only) | Yes (DTLS-SRTP) |

**Use RTSP version for**: Local network, lowest latency, simplicity

**Use WebRTC version for**: Internet streaming, NAT traversal, encryption

---

## Building Standalone Executable

```powershell
cd desktop/receiver
pip install pyinstaller
pyinstaller --onefile --windowed rtsp_receiver_gui.py -n PhoneCam-RTSP
```

Output: `dist/PhoneCam-RTSP.exe`

---

## Project Structure

```
PhoneCam/
├── android/
│   └── app/src/main/java/com/phonecam/
│       ├── RtspMainActivity.kt    # Main UI
│       ├── RtspStreamer.kt        # H.264 encoder + TCP sender
│       └── ...
├── desktop/receiver/
│   ├── rtsp_receiver.py           # CLI receiver
│   ├── rtsp_receiver_gui.py       # GUI receiver
│   └── requirements_rtsp.txt      # Python dependencies
└── README.md
```

---

## License

MIT License - See LICENSE file

## Credits

- [PyAV](https://github.com/PyAV-Org/PyAV) - FFmpeg Python bindings
- [Unity Capture](https://github.com/schellingb/UnityCapture) - Virtual camera driver
- [pyvirtualcam](https://github.com/letmaik/pyvirtualcam) - Virtual camera interface

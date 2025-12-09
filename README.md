# PhoneCam

Transform your Android phone into a high-quality wireless webcam for your PC. Stream 1080p@60fps video with hardware-accelerated H.264 encoding over WebRTC.

---

## Support Me

If I saved you some headaches from dealing with all these phone to webcam apps working insufficiently, please consider supporting me!

[![Donate with PayPal](https://img.shields.io/badge/Donate-PayPal-blue.svg)](https://www.paypal.com/donate/?hosted_button_id=&business=scitalia852@gmail.com&currency_code=USD)


Your support helps maintain and improve PhoneCam. Thank you! ❤️

---

## Features

### 🎥 High-Quality Streaming
- **1920x1080@60fps** video capture
- **Fixed 15 Mbps** bitrate for optimal quality and stability
- Hardware-accelerated H.264 encoding
- Low-latency WebRTC streaming

### 📱 Android App
- Full-screen camera preview
- Auto-dimming AMOLED black overlay (5s timeout)
- Fixed 15 Mbps bitrate for consistent quality
- Sensor landscape orientation (both directions)
- Connect/Disconnect button toggle
- User-friendly status messages

### 💻 PC Server
- **Standalone executable** with modern GUI (Frutiger Aero theme)
- Real-time connection indicator (⚫ No Connection / ⚫ Waiting / ⚫ Connected)
- One-click Start/Stop server
- Single connection at a time (prevents conflicts)
- Unity Capture virtual camera integration
- Auto-detects Unity Capture installation

### 🛠️ Technical
- WebRTC peer-to-peer connection
- STUN server support (Google STUN default)
- Dynamic resolution handling
- Automatic reconnection on camera loss
- Encoder stability (15 Mbps cap for optimal quality)

## Requirements

### Android
- Android 7.0 (API 24) or higher
- Camera and microphone permissions

### PC (Windows)
- Windows 10/11
- [Unity Capture](https://github.com/schellingb/UnityCapture/releases) virtual camera driver
  - Download and run `Install.cmd` as Administrator
  - Must be installed from a permanent location (not portable)

## Installation

### PC Server

1. Download `PhoneCam-Server.exe` from releases
2. Install Unity Capture:
   - Download from [UnityCapture releases](https://github.com/schellingb/UnityCapture/releases)
   - Extract to permanent location
   - Right-click `Install.cmd` → Run as Administrator
3. Double-click `PhoneCam-Server.exe`
4. Click "Start Server"

### Android App

1. Build APK using Android Studio or Gradle:
   ```bash
   cd android
   gradlew assembleRelease
   ```
2. Install APK on your phone
3. Grant camera and microphone permissions

## Usage

1. **Start the server** on your PC
   - Server listens on `http://0.0.0.0:8000`
   - Connection indicator shows status

2. **Connect your phone**
   - Ensure phone and PC are on same network
   - Enter PC IP address (e.g., `192.168.1.100:8000`)
   - Press "Connect" (streams at 15 Mbps)

3. **Use the virtual camera**
   - Camera appears as "Unity Video Capture" in:
     - OBS Studio (Video Capture Device)
     - NVIDIA Broadcast
     - Zoom, Teams, Discord, etc.

4. **Disconnect** when done
   - Press "Disconnect" button on phone
   - Or click "Stop Server" on PC

## Configuration

### Bitrate Settings
- **Fixed at 15 Mbps**: Optimal balance of quality and stability
- Provides excellent 1080p60 quality for most use cases
- Prevents encoder corruption and network congestion

### Network Requirements
- **Minimum**: 20 Mbps upload on phone for stable streaming
- **Recommended**: 25+ Mbps upload for best results
- Use 5GHz WiFi for optimal performance

## Troubleshooting

### Camera Not Appearing
- Verify Unity Capture is installed (run `Install.cmd` as admin)
- Restart applications using the camera
- Check server connection indicator shows "Connected" (green)

### Connection Issues
- Ensure phone and PC are on same network
- Check firewall allows port 8000
- Try connecting with IP:port format (e.g., `192.168.1.100:8000`)

### Video Quality Issues
- Ensure stable network connection (15 Mbps minimum)
- Use 5GHz WiFi instead of 2.4GHz
- Reduce distance between phone and router
- Check NVIDIA Broadcast/OBS settings for 60fps

### Port Already in Use
- Wait 30 seconds for port to release
- Close other PhoneCam instances
- Use Task Manager to end stuck processes

## Building from Source

### Android App
```bash
cd android
./gradlew assembleDebug  # or assembleRelease
# APK: android/app/build/outputs/apk/
```

### PC Server Executable
```bash
cd desktop/receiver
pip install -r requirements_build.txt
./build_exe.ps1
# EXE: dist/PhoneCam-Server.exe
```

### Python Server (Development)
```bash
cd desktop/receiver
pip install -r requirements_build.txt
python server_highquality.py --host 0.0.0.0 --port 8000
```

## Project Structure

```
PhoneCam/
├── android/                    # Android app (Kotlin)
│   ├── app/src/main/
│   │   ├── java/com/phonecam/
│   │   │   ├── MainActivity.kt       # UI and lifecycle
│   │   │   ├── WebRtcClient.kt       # WebRTC streaming
│   │   │   └── Signaling.kt          # HTTP signaling
│   │   └── res/layout/
│   │       └── activity_main.xml     # Full-screen UI layout
│   └── build.gradle
├── desktop/receiver/           # PC server (Python)
│   ├── server_gui.py          # Tkinter GUI wrapper
│   ├── server_highquality.py  # Core WebRTC server
│   ├── build_exe.ps1          # PyInstaller build script
│   └── requirements_build.txt # Python dependencies
└── README.md
```

## Technical Details

### Video Pipeline
1. **Android**: CameraX → Hardware H.264 Encoder → WebRTC
2. **Network**: WebRTC peer connection (SRTP/DTLS)
3. **PC**: WebRTC → PyAV/FFmpeg Decoder → Unity Capture → Applications

### Key Technologies
- **Android**: WebRTC SDK 125.6422.04, CameraX, Kotlin Coroutines
- **PC**: Python 3.10+, aiohttp, aiortc, PyAV, pyvirtualcam
- **Protocols**: WebRTC, STUN, H.264, RTP

### Encoder Configuration
- **Codec**: H.264 (hardware-accelerated)
- **Profile**: Baseline/Main (device-dependent)
- **Bitrate**: Fixed 15 Mbps for optimal stability
- **Degradation**: MAINTAIN_RESOLUTION (no downscaling)
- **Consistent quality**: Fixed bitrate prevents encoder issues

## Known Limitations

- **Single connection**: Server accepts one stream at a time
- **Unity Capture required**: Not portable, must install separately
- **Windows only**: Server executable is Windows-specific
- **Same network**: Phone and PC must be on same local network
- **Fixed bitrate**: 15 Mbps cap for optimal quality and stability

## Developer

**ScSyn** - 2025

## License

MIT License - See LICENSE file for details

## Credits

- [WebRTC](https://webrtc.org/) - Real-time communication
- [Unity Capture](https://github.com/schellingb/UnityCapture) - Virtual camera driver
- [aiortc](https://github.com/aiortc/aiortc) - Python WebRTC implementation
- [PyAV](https://github.com/PyAV-Org/PyAV) - FFmpeg bindings for Python



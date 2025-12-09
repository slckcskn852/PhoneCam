# PhoneCam

Transform your Android phone into a high-quality wireless webcam for your PC. Stream 1080p@60fps video with hardware-accelerated H.264 encoding over direct TCP connection.

---

## Support Me

If I saved you some headaches from dealing with all these phone to webcam apps working insufficiently, please consider supporting me!

[![Donate with PayPal](https://img.shields.io/badge/Donate-PayPal-blue.svg)](https://www.paypal.com/donate/?hosted_button_id=&business=scitalia852@gmail.com&currency_code=USD)

**PayPal**: scitalia852@gmail.com

Your support helps maintain and improve PhoneCam. Thank you! ❤️

---

## Features

### 🎥 High-Quality Streaming
- **1920x1080@60fps** video capture
- **Adjustable bitrate** (5-30 Mbps) for optimal quality
- Hardware-accelerated H.264 encoding on phone
- Low-latency direct TCP streaming
- Real-time rotation support (follows phone orientation)

### 📱 Android App
- Full-screen camera preview with dark theme
- Real-time bitrate slider (5-30 Mbps)
- Pinch-to-zoom camera control
- Automatic orientation detection and streaming
- Stability-based rotation (prevents jitter)
- Connect/Disconnect toggle with status display
- FPS and bitrate monitoring

### 💻 PC Receiver
- **Standalone executable** with modern dark GUI
- Real-time FPS and bitrate display
- Low-latency CPU decoding (PyAV, 8 threads)
- Unity Capture virtual camera output
- Automatic rotation handling
- One-click Start/Stop server

### 🛠️ Technical
- Direct TCP H.264 NAL unit streaming
- Hardware MediaCodec encoding on Android
- Multi-threaded CPU decoding with low-delay flags
- Unity Capture DirectShow virtual camera
- Rotation protocol with false-positive protection

## Requirements

### Android
- Android 7.0 (API 24) or higher
- Camera permission

### PC (Windows)
- Windows 10/11
- [Unity Capture](https://github.com/schellingb/UnityCapture/releases) virtual camera driver
  - Download and run `Install.bat` as Administrator
  - Must be installed from a permanent location

## Installation

### PC Receiver

1. Download `PhoneCam-RTSP-Receiver.exe` from releases
2. Install Unity Capture:
   - Download from [UnityCapture releases](https://github.com/schellingb/UnityCapture/releases)
   - Extract to permanent location
   - Right-click `Install.bat` → Run as Administrator
3. Double-click `PhoneCam-RTSP-Receiver.exe`
4. Click "Start Server"

### Android App

1. Build APK using Android Studio or Gradle:
   ```bash
   cd android
   ./gradlew assembleRelease
   ```
2. Install APK on your phone
3. Grant camera permission

## Usage

1. **Start the receiver** on your PC
   - Server listens on port 5000
   - Status shows "Waiting for connection..."

2. **Connect your phone**
   - Ensure phone and PC are on same network
   - Enter PC IP address (shown in receiver)
   - Press "Connect"
   - Adjust bitrate slider as needed (5-30 Mbps)

3. **Use the virtual camera**
   - Camera appears as "Unity Video Capture" in:
     - OBS Studio (Video Capture Device)
     - NVIDIA Broadcast
     - Zoom, Teams, Discord, etc.

4. **Rotate freely**
   - Rotation automatically follows phone orientation
   - Both landscape orientations supported
   - Smooth transitions with jitter prevention

4. **Disconnect** when done
   - Press "Disconnect" button on phone
   - Or click "Stop Server" on PC

## Configuration

### Bitrate Settings
- **Adjustable**: 5-30 Mbps via slider on phone
- **Default**: 15 Mbps (good balance of quality and stability)
- Higher bitrate = better quality but needs faster network
- Lower bitrate = more stable on slower networks

### Network Requirements
- **Minimum**: 10 Mbps for stable 1080p streaming
- **Recommended**: 20+ Mbps for high bitrate settings
- Use 5GHz WiFi for optimal performance
- Phone and PC must be on same local network

## Troubleshooting

### Camera Not Appearing
- Verify Unity Capture is installed (run `Install.bat` as admin)
- Restart applications using the camera
- Check receiver shows "Connected" status

### Connection Issues
- Ensure phone and PC are on same network
- Check firewall allows port 5000
- Try entering just IP address (e.g., `192.168.1.100`)

### Video Quality Issues
- Increase bitrate slider on phone
- Use 5GHz WiFi instead of 2.4GHz
- Reduce distance between phone and router

### High Latency
- Lower the bitrate for faster encoding/decoding
- Ensure strong WiFi signal
- Close other bandwidth-heavy applications

## Building from Source

### Android App
```bash
cd android
./gradlew assembleDebug  # or assembleRelease
# APK: android/app/build/outputs/apk/
```

### PC Receiver Executable
```bash
cd desktop/receiver
pip install -r requirements.txt
pip install pyinstaller
pyinstaller --clean PhoneCam-RTSP-Receiver.spec
# EXE: dist/PhoneCam-RTSP-Receiver.exe
```

### Python Receiver (Development)
```bash
cd desktop/receiver
pip install -r requirements.txt
python rtsp_receiver_gui.py
```

## Project Structure

```
PhoneCam/
├── android/                    # Android app (Kotlin)
│   ├── app/src/main/
│   │   ├── java/com/phonecam/
│   │   │   ├── MainActivity.kt       # UI and lifecycle
│   │   │   └── RtspStreamer.kt       # H.264 TCP streaming
│   │   └── res/layout/
│   │       └── activity_main.xml     # Dark theme UI
│   └── build.gradle
├── desktop/receiver/           # PC receiver (Python)
│   ├── rtsp_receiver_gui.py   # Main GUI application
│   ├── PhoneCam-RTSP-Receiver.spec  # PyInstaller spec
│   └── requirements.txt       # Python dependencies
├── UnityCapture-master/       # Virtual camera driver
└── README.md
```

## Technical Details

### Video Pipeline
1. **Android**: CameraX → Hardware H.264 Encoder (MediaCodec) → TCP Socket
2. **Network**: Direct TCP with NAL unit framing + rotation messages
3. **PC**: TCP Receiver → PyAV Decoder (8 threads) → Unity Capture → Applications

### Key Technologies
- **Android**: CameraX, MediaCodec H.264, Kotlin
- **PC**: Python 3.10+, PyAV (FFmpeg), pyvirtualcam, tkinter
- **Virtual Camera**: Unity Capture DirectShow driver

### Decoder Configuration
- **Library**: PyAV (FFmpeg bindings)
- **Thread Type**: SLICE (parallel slice decoding)
- **Thread Count**: 8 threads
- **Flags**: low_delay, fast (minimal buffering)

### Rotation Protocol
- **Format**: 5 bytes (`0xFF` + `RT` + rotation_value + `0xAA`)
- **Values**: 0=0°, 1=90°, 2=180°, 3=270°
- **Protection**: Prefix/suffix validation prevents false positives

## Known Limitations

- **Single connection**: Receiver accepts one stream at a time
- **Unity Capture required**: Must install separately (not portable)
- **Windows only**: Receiver executable is Windows-specific
- **Same network**: Phone and PC must be on same local network
- **CPU decoding**: Uses multi-threaded CPU (no GPU acceleration)

## Developer

**ScSyn** - 2025

## License

MIT License - See LICENSE file for details

## Credits

- [Unity Capture](https://github.com/schellingb/UnityCapture) - Virtual camera driver
- [PyAV](https://github.com/PyAV-Org/PyAV) - FFmpeg bindings for Python
- [CameraX](https://developer.android.com/training/camerax) - Android camera library



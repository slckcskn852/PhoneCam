# PhoneCam Receiver (Windows)

Python-based TCP receiver that decodes the H.264 stream from your phone and outputs to a Unity Capture virtual webcam.

## Features
- Low-latency H.264 decoding (PyAV with 8-thread CPU)
- Unity Capture virtual camera output
- Real-time rotation support
- Modern dark theme GUI
- FPS and bitrate monitoring

## Prerequisites
- Windows 10/11
- Python 3.10+
- Unity Capture virtual camera driver installed

## Setup
```powershell
python -m venv .venv
.\.venv\Scripts\activate
pip install -r requirements.txt
python rtsp_receiver_gui.py
```

The receiver listens on port 5000 for incoming H.264 streams.

## Building Standalone EXE
```powershell
pip install pyinstaller
pyinstaller --clean PhoneCam-RTSP-Receiver.spec
# Output: dist/PhoneCam-RTSP-Receiver.exe
```

## Usage
1. Start the receiver (run the script or EXE)
2. Note your PC's IP address (shown in the GUI)
3. In the Android app, enter your PC's IP address and tap Connect
4. Open any app that supports webcams and select "Unity Video Capture"

## Technical Details
- **Decoder**: PyAV (FFmpeg) with 8 threads, low-delay mode
- **Virtual Camera**: Unity Capture DirectShow driver
- **Protocol**: TCP with raw H.264 NAL units + rotation messages
- **Rotation**: 5-byte protocol with validation (0xFF + RT + value + 0xAA)

## Notes
- Unity Capture must be installed separately (run Install.bat as admin)
- Phone and PC must be on the same local network
- Port 5000 must be allowed through Windows Firewall
- For best results, use 5GHz WiFi

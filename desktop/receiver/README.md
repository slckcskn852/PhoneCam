# PhoneCam Receiver (Windows)

Python-based WebRTC receiver that decodes the phone stream and writes frames to a virtual webcam via pyvirtualcam.

## Prereqs
- Windows 10/11.
- OBS Studio installed; run once and click "Start Virtual Camera" to register the DirectShow sink.
- FFmpeg installed and on PATH.
- Python 3.11+.

## Setup
```powershell
python -m venv .venv
.\.venv\Scripts\activate
pip install -r requirements.txt
python server.py --host 0.0.0.0 --port 8000 --stun stun:stun.l.google.com:19302
```

The server binds on port 8000 and exposes `/offer` for SDP exchange.

## Use
1) Start the server (above).
2) Find your PC IP address on the same LAN as the phone.
3) In the Android app, set the signaling URL to `http://<pc-ip>:8000/offer` and tap Connect.
4) Open any app that supports webcams and select the OBS Virtual Camera device.

## Notes
- 1080p60 requires a strong Wi‑Fi link (5 GHz/6E) and enough CPU/GPU for decode.
- If FFmpeg codecs are missing, install the full build or ensure `av` can locate `ffmpeg.exe`.
- Add a firewall rule if the phone cannot reach port 8000.

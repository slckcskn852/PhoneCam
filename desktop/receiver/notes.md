## Notes

### Python Environment Setup
If installation fails on Windows:
- Upgrade pip: `python -m pip install --upgrade pip`
- Install requirements: `python -m pip install -r requirements.txt`
- Ensure Unity Capture virtual camera is installed for pyvirtualcam.

### Dependencies
- PyAV: FFmpeg bindings for H.264 decoding
- pyvirtualcam: Virtual camera output (Unity Capture backend)
- OpenCV: Frame manipulation and resizing
- tkinter: GUI (included with Python)

### Unity Capture Installation
1. Navigate to `UnityCapture-master/Install/`
2. Run `Install.bat` as Administrator
3. The "Unity Video Capture" device will appear in Windows

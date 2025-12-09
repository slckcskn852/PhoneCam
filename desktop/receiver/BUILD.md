# Building PhoneCam Receiver Executable

## Quick Build (Recommended)

1. Open PowerShell in this directory (`desktop/receiver`)
2. Run the build script:
   ```powershell
   .\build_exe.ps1
   ```
3. Find the executable in `dist\PhoneCam-RTSP-Receiver.exe`

## Manual Build

If the automated script doesn't work:

```powershell
# Activate virtual environment (if using one)
.\.venv\Scripts\Activate.ps1

# Install PyInstaller
python -m pip install pyinstaller

# Build executable
pyinstaller PhoneCam-RTSP-Receiver.spec
```

Or build without a spec file:
```powershell
pyinstaller --name "PhoneCam-RTSP-Receiver" --onefile --windowed rtsp_receiver_gui.py
```

## Running the Executable

1. Ensure Unity Capture virtual camera driver is installed
2. Double-click `PhoneCam-RTSP-Receiver.exe`
3. Click "Start" button
4. Connect from your Android app to the displayed IP address

## Notes

- The executable includes all dependencies (PyAV, OpenCV, pyvirtualcam, etc.)
- No Python installation needed to run the .exe
- Unity Capture driver still required (run `UnityCapture-master/Install/Install.bat` as Admin)
- Default TCP port: 5000

## Size

The final executable is approximately 80-85 MB (includes PyAV with FFmpeg codecs).

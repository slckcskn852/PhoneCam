# Building PhoneCam Server Executable

## Quick Build (Recommended)

1. Open PowerShell in this directory (`desktop/receiver`)
2. Run the build script:
   ```powershell
   .\build_exe.ps1
   ```
3. Find the executable in `dist\PhoneCam-Server.exe`

## Manual Build

If the automated script doesn't work:

```powershell
# Install PyInstaller
python -m pip install pyinstaller

# Build executable
pyinstaller --name "PhoneCam-Server" --onefile --windowed server_gui.py
```

## Running the Executable

1. Double-click `PhoneCam-Server.exe`
2. Click "Start Server" button
3. Connect from your Android app to the displayed address

## Notes

- The executable includes all dependencies
- No Python installation needed to run the .exe
- Unity Capture driver still required (same as before)
- Default server address: http://0.0.0.0:8000

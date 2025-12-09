# PhoneCam Portable Distribution Guide

## Why Unity Capture Can't Be Fully Portable

Unity Capture is a **Windows kernel-mode driver** that requires system-level installation. It cannot be bundled into a portable executable because:

- Must register with Windows DirectShow filters
- Requires administrator privileges
- Needs to be in Windows system directories
- Must integrate with Windows registry

## Distribution Strategy

### Option 1: Bundled Installer (Recommended)
1. Download Unity Capture from: https://github.com/schellingb/UnityCapture/releases
2. Extract the release files
3. Include in your distribution folder:
   ```
   PhoneCam-Distribution/
   ├── PhoneCam-Server.exe
   ├── UnityCapture/
   │   ├── Install.cmd
   │   ├── Uninstall.cmd
   │   └── (other Unity Capture files)
   └── README.txt
   ```

4. In README.txt, instruct users:
   - Run `UnityCapture\Install.cmd` as Administrator (one-time setup)
   - Then run `PhoneCam-Server.exe` normally

### Option 2: Auto-Detect and Prompt
Modify the server to detect if Unity Capture is installed and provide helpful guidance:

```python
def check_unity_capture():
    """Check if Unity Capture is installed"""
    try:
        cam = pyvirtualcam.Camera(width=640, height=480, fps=30, backend='unitycapture')
        cam.close()
        return True
    except:
        return False

if not check_unity_capture():
    messagebox.showerror(
        "Unity Capture Not Found",
        "Unity Capture virtual camera driver is not installed.\n\n"
        "Please run UnityCapture\\Install.cmd as Administrator,\n"
        "then restart this application."
    )
    sys.exit(1)
```

### Option 3: Alternative Virtual Camera (Fully Portable)
Consider using **OBS Virtual Camera** which can be more easily distributed:
- OBS provides a standalone virtual camera plugin
- Can potentially be packaged as a portable installation
- However, still requires some system integration

## Current Best Practice

**Semi-Portable Solution:**
1. Create a distribution package with both PhoneCam-Server.exe and Unity Capture installer
2. Add a "First Run" check in the GUI that detects Unity Capture
3. Show clear instructions with a button to open the UnityCapture folder
4. After Unity Capture is installed once, PhoneCam-Server.exe is fully portable

This gives users the best balance of portability (exe) and functionality (system driver).

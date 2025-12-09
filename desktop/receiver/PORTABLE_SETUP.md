# PhoneCam Portable Distribution Guide

## Why Unity Capture Can't Be Fully Portable

Unity Capture is a **Windows kernel-mode driver** that requires system-level installation. It cannot be bundled into a portable executable because:

- Must register with Windows DirectShow filters
- Requires administrator privileges
- Needs to be in Windows system directories
- Must integrate with Windows registry

## Distribution Strategy

### Option 1: Bundled with Installer (Recommended)
Include Unity Capture from the project's `UnityCapture-master` folder:

```
PhoneCam-Distribution/
├── PhoneCam-RTSP-Receiver.exe
├── UnityCapture/
│   ├── Install.bat
│   ├── Uninstall.bat
│   └── (other Unity Capture files from Install folder)
└── README.txt
```

In README.txt, instruct users:
1. Run `UnityCapture\Install.bat` as Administrator (one-time setup)
2. Then run `PhoneCam-RTSP-Receiver.exe` normally

### Option 2: Auto-Detect and Prompt
The receiver already detects if Unity Capture is installed and shows an error if not found:

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
        "Please run UnityCapture\\Install.bat as Administrator,\n"
        "then restart this application."
    )
    sys.exit(1)
```

## Building the EXE

```powershell
cd desktop\receiver
pyinstaller PhoneCam-RTSP-Receiver.spec
```

Output: `dist\PhoneCam-RTSP-Receiver.exe` (~80-85 MB)

The EXE includes all Python dependencies (PyAV, OpenCV, pyvirtualcam, etc.)

## Current Best Practice

**Semi-Portable Solution:**
1. Create a distribution package with both PhoneCam-Server.exe and Unity Capture installer
2. Add a "First Run" check in the GUI that detects Unity Capture
3. Show clear instructions with a button to open the UnityCapture folder
4. After Unity Capture is installed once, PhoneCam-Server.exe is fully portable

This gives users the best balance of portability (exe) and functionality (system driver).

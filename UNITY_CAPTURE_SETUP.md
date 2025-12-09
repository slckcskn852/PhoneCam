# Unity Capture Setup for PhoneCam

Unity Capture is a free virtual camera driver that works independently of OBS.

## Installation

1. **Download Unity Capture**:
   - Go to: https://github.com/schellingb/UnityCapture/releases
   - Download `UnityCaptureFilter.zip` (latest release)

2. **Install the driver**:
   ```powershell
   # Extract the zip file
   # Right-click Install.cmd and select "Run as Administrator"
   ```

3. **Verify installation**:
   - Open Windows Camera app
   - You should see "Unity Video Capture" in the camera list

## Usage with PhoneCam

Once installed, Unity Capture provides a virtual camera device that:
- Works independently of OBS
- Can be used as a camera source in OBS for streaming
- Appears as "Unity Video Capture" in all applications
- Supports 1080p60 streaming

The `server_highquality.py` will automatically use Unity Capture if OBS Virtual Camera is not available.

## Using in OBS for Streaming

1. Start PhoneCam server and connect your phone
2. In OBS Studio:
   - Add Source → Video Capture Device
   - Select "Unity Video Capture"
   - Configure your stream settings
   - Start streaming!

You can now use your phone as a camera source in OBS while streaming to Twitch/YouTube!

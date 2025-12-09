# Build script for PhoneCam Server GUI executable
# Run this to create a standalone .exe file

Write-Host "Building PhoneCam Server..." -ForegroundColor Green
Write-Host ""

# Activate virtual environment if exists
if (Test-Path ".\.venv\Scripts\Activate.ps1") {
    Write-Host "Activating virtual environment..." -ForegroundColor Yellow
    .\.venv\Scripts\Activate.ps1
}

# Install pyinstaller if needed
Write-Host "Installing build dependencies..." -ForegroundColor Yellow
python -m pip install pyinstaller --quiet

# Build the executable
Write-Host ""
Write-Host "Building executable..." -ForegroundColor Yellow
pyinstaller --name "PhoneCam-Server" `
    --onefile `
    --windowed `
    --icon=NONE `
    --add-data "server_highquality.py;." `
    --hidden-import=aiohttp `
    --hidden-import=aiohttp.web `
    --hidden-import=aiortc `
    --hidden-import=aiortc.contrib.media `
    --hidden-import=av `
    --hidden-import=pyvirtualcam `
    --hidden-import=numpy `
    --hidden-import=cv2 `
    --hidden-import=multidict `
    --hidden-import=yarl `
    --hidden-import=aiosignal `
    --hidden-import=frozenlist `
    --hidden-import=async_timeout `
    --hidden-import=asyncio `
    --collect-all=aiohttp `
    --collect-all=aiortc `
    --collect-all=av `
    server_gui.py

if ($LASTEXITCODE -eq 0) {
    Write-Host ""
    Write-Host "Build successful!" -ForegroundColor Green
    Write-Host "Executable location: dist\PhoneCam-Server.exe" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "You can now:" -ForegroundColor Yellow
    Write-Host "  1. Double-click dist\PhoneCam-Server.exe to launch" -ForegroundColor White
    Write-Host "  2. Click 'Start Server' in the GUI" -ForegroundColor White
    Write-Host "  3. Connect from your Android app" -ForegroundColor White
} else {
    Write-Host ""
    Write-Host "Build failed! Check error messages above." -ForegroundColor Red
}

# Building PC Explorer Server

This document describes how to build the PC Explorer server as a portable Windows executable.

## Prerequisites

1. Python 3.10 or later
2. PyInstaller: `pip install pyinstaller`
3. (Optional) ADB Platform Tools for bundling

## Quick Build

For a basic build without bundled ADB:

```bash
pyinstaller --onefile --name pc-explorer-server server.py
```

## Recommended Build with Bundled ADB

For the best user experience, bundle ADB with the executable:

### Step 1: Download ADB Platform Tools

Download from: https://developer.android.com/tools/releases/platform-tools

Extract the following files:
- `adb.exe`
- `AdbWinApi.dll`
- `AdbWinUsbApi.dll`

### Step 2: Create Build Directory Structure

```
pc-server/
├── server.py
├── protocol.py
├── file_handler.py
├── adb_helper.py
├── adb/           <- Create this folder
│   ├── adb.exe
│   ├── AdbWinApi.dll
│   └── AdbWinUsbApi.dll
└── libusb/        <- Optional, for USB mode
    └── libusb-1.0.dll
```

### Step 3: Build with PyInstaller

```bash
# From the pc-server directory
pyinstaller --onefile \
    --name pc-explorer-server \
    --add-binary "adb/adb.exe;." \
    --add-binary "adb/AdbWinApi.dll;." \
    --add-binary "adb/AdbWinUsbApi.dll;." \
    --add-binary "libusb/libusb-1.0.dll;." \
    server.py
```

Or use the spec file:

```bash
pyinstaller pc-explorer-server.spec
```

### Step 4: Distribute

The built executable will be in `dist/pc-explorer-server.exe`

For distribution, you can either:
1. Distribute the single exe file (ADB bundled inside)
2. Create a folder distribution with the exe and ADB files next to it

## Build Options

### Minimal Build (simulation mode only)

If you don't need USB or ADB modes:

```bash
pyinstaller --onefile --name pc-explorer-server server.py
```

Users will need to manually run `adb forward tcp:5555 tcp:5555` before using.

### Full Build with USB Support

Add libusb for direct USB mode (requires Zadig on Windows):

```bash
pyinstaller --onefile \
    --name pc-explorer-server \
    --add-binary "libusb/libusb-1.0.dll;." \
    server.py
```

## Troubleshooting

### "No backend available" error in USB mode
- Install libusb via Zadig: https://zadig.akeo.ie/
- Or use `--adb` mode instead (recommended)

### ADB not found
- Make sure ADB files are bundled or in system PATH
- Or use `--simulate` mode and run `adb forward` manually

### Connection issues
- Enable USB debugging on Android device
- Accept the USB debugging prompt on the phone
- Try `--simulate` mode for manual setup

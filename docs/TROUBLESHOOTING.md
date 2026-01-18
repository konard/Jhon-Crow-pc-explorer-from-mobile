# Troubleshooting Guide for PC Explorer

## "Nothing happens when pressing Connect" on Android

### Common Causes and Solutions

#### 1. USB Direct Mode Limitations

USB direct mode has significant limitations due to the complexity of Android USB communication. The recommended solution is to use **simulation mode** with ADB:

**Solution: Use Simulation Mode**

1. Enable USB debugging on your Android device:
   - Go to Settings > About phone
   - Tap "Build number" 7 times to enable Developer options
   - Go to Settings > Developer options
   - Enable "USB debugging"

2. Connect your Android device to PC via USB cable

3. Run the server in simulation mode:
   ```
   pc-explorer-server.exe --simulate
   ```

4. On your PC, run ADB port forwarding:
   ```
   adb forward tcp:5555 tcp:5555
   ```

5. Open the Android app and press Connect

#### 2. Device Not Detected (USB Mode)

If using USB direct mode and your device isn't detected:

**Check the logs:** Look in the `logs` folder next to the exe for detailed logs.

**Supported manufacturers:** Samsung, Google, Xiaomi, Huawei, OnePlus, LG, Sony, HTC, Motorola, ZTE, Vivo, OPPO, ASUS, Lenovo, and more.

**If your device has an unknown vendor ID:**
- The server logs will show all connected USB devices
- Check if your device appears in the list
- If using an unusual device, use simulation mode instead

#### 3. USB Permission Issues (Android)

When connecting for the first time, Android should prompt for USB permission.

**If no permission dialog appears:**
- Disconnect and reconnect the USB cable
- Restart the Android app
- Try a different USB port on your PC

**If permission was denied:**
- Go to Android Settings > Apps > PC Explorer
- Clear app data and try again

#### 4. USB Debugging Authorization

When connecting with USB debugging enabled, you may see an "Allow USB debugging?" dialog on your Android device.

**Make sure to:**
- Check "Always allow from this computer"
- Tap "Allow"

#### 5. USB Cable Issues

Not all USB cables support data transfer. Some only provide charging.

**Try:**
- Using the original cable that came with your phone
- Using a different USB cable
- Connecting to a different USB port (preferably USB 3.0)

#### 6. Driver Issues (Windows)

Some Android devices need special USB drivers on Windows.

**Solutions:**
- Install your device manufacturer's USB drivers
- Try installing Google USB Driver from Android SDK
- Use Windows Device Manager to check for driver issues

## Log File Locations

### PC Server Logs
Logs are created in a `logs` folder next to the executable:
```
pc-explorer-server.exe
logs/
  pc-explorer-server_YYYYMMDD_HHMMSS.log
```

### Android App Logs
Use Android Studio's Logcat to view app logs. Filter by tag `UsbConnectionRepo`.

## Getting Help

If you're still having issues:

1. Collect the PC server log file
2. Note your Android device model and manufacturer
3. Describe the exact steps you followed
4. Open an issue at: https://github.com/Jhon-Crow/pc-explorer-from-mobile/issues

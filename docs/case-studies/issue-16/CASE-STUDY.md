# Case Study: Issue #16 - "Phone Not Connecting After PR #15" (телефон не подключается)

## Issue Summary

**Issue Title (Russian):** телефон не подключается (Phone not connecting)
**Issue URL:** https://github.com/Jhon-Crow/pc-explorer-from-mobile/issues/16
**Date Reported:** 2026-01-18
**Reporter:** Jhon-Crow
**Related PR:** https://github.com/Jhon-Crow/pc-explorer-from-mobile/pull/15
**Device:** Huawei DUA-L22

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [Log Analysis](#log-analysis)
3. [Timeline of Events](#timeline-of-events)
4. [Root Cause Analysis](#root-cause-analysis)
5. [PR #15 Background](#pr-15-background)
6. [Proposed Solutions](#proposed-solutions)
7. [Conclusion](#conclusion)

---

## Executive Summary

After PR #15 was merged, the Android app stopped connecting to the PC server. The server logs show that:
1. The server starts successfully
2. TCP server binds to localhost:5555 successfully
3. ADB reverse forwarding is set up correctly
4. **The server waits indefinitely - Android app never connects**

### Root Cause (CRITICAL)

**The Android app does not have TCP socket connection capability.**

PR #15 switched from `adb forward` (PC listens on 5555, forwards to phone) to `adb reverse` (phone's localhost:5555 forwards to PC's localhost:5555).

The `adb reverse` approach requires the Android app to:
1. Open a TCP socket connection to `localhost:5555`
2. Communicate over this socket connection

However, the Android app (`UsbConnectionRepositoryImpl.kt`) **only supports direct USB connections** using Android's UsbManager API. There is no TCP socket client implementation.

---

## Log Analysis

### Log Files Provided (2 files)

| Log File | Timestamp | Server Status | Issue |
|----------|-----------|---------------|-------|
| pc-explorer-server_20260118_115535.log | 11:55:35 | Waiting for connection | App never connects |
| pc-explorer-server_20260118_115644.log | 11:56:44 | Waiting for connection | App never connects |

### Log #1 (11:55:35) - Detailed Analysis

```
2026-01-18 11:55:35,289 - INFO - PC Explorer USB Server - Starting
2026-01-18 11:55:35,293 - INFO - ADB found: I:\Загрузки\Mobile to pc\platform-tools\adb.exe
2026-01-18 11:55:35,553 - INFO - ADB device check: Device ready: DUA-L22
2026-01-18 11:55:35,554 - INFO - Starting TCP server on port 5555 (ADB reverse mode)
2026-01-18 11:55:35,555 - INFO - Checking for existing ADB port forwards...
2026-01-18 11:55:35,590 - INFO - Found existing ADB forward on port 5555, removing it...
2026-01-18 11:55:35,626 - INFO - Removed port forwarding on port 5555
2026-01-18 11:55:35,627 - INFO - Successfully removed existing forward on port 5555
2026-01-18 11:55:35,629 - INFO - TCP server bound to localhost:5555
2026-01-18 11:55:35,903 - INFO - ADB reverse forwarding set up: device:5555 -> localhost:5555
2026-01-18 11:55:35,904 - INFO - ADB reverse forwarding: ADB reverse forwarding ready for DUA-L22
2026-01-18 11:55:35,905 - INFO - Waiting for Android app to connect...
2026-01-18 11:55:35,905 - INFO - On your phone, the app should connect to localhost:5555
```

**Key Observations:**
1. ✅ ADB is found and working
2. ✅ Device (DUA-L22) is ready
3. ✅ Existing ADB forward on port 5555 was cleaned up (from previous issue #14 attempts)
4. ✅ TCP server successfully binds to localhost:5555
5. ✅ ADB reverse forwarding is set up correctly
6. ❌ Server waits indefinitely - **Android app never connects**

### Log #2 (11:56:44) - Similar Pattern

```
2026-01-18 11:56:44,279 - INFO - ADB found: I:\Загрузки\Mobile to pc\platform-tools\adb.exe
2026-01-18 11:56:44,552 - INFO - ADB device check: Device ready: DUA-L22
2026-01-18 11:56:44,554 - INFO - TCP server bound to localhost:5555
2026-01-18 11:56:44,851 - INFO - ADB reverse forwarding set up: device:5555 -> localhost:5555
2026-01-18 11:56:44,852 - INFO - Waiting for Android app to connect...
```

Same pattern - server starts correctly, sets up reverse forwarding, waits indefinitely.

---

## Timeline of Events

### Issue #14 → PR #15 → Issue #16

| Date/Time | Event | Status |
|-----------|-------|--------|
| 2026-01-18 ~09:50 | User reports issue #14 - phone not connecting | Port 5555 conflict |
| 2026-01-18 ~10:10 | User has ADB working but WinError 10013 (port blocked) | `adb forward` blocking port |
| 2026-01-18 09:06 | PR #15 merged - switches to `adb reverse` | Fix for port conflict |
| 2026-01-18 ~11:55 | User tries new version (after PR #15) | Server works, app doesn't connect |
| 2026-01-18 ~11:56 | User tries again | Same issue - app doesn't connect |
| 2026-01-18 ~11:56 | User reports issue #16 | This case study |

---

## Root Cause Analysis

### The Architecture Mismatch

**Before PR #15 (using `adb forward`):**
```
┌──────────────────┐         ┌──────────────────┐
│   Android App    │   USB   │   PC Server      │
│                  │  ───►   │                  │
│ USB Connection   │         │ TCP Server :5555 │
│ (UsbManager)     │         │ (but ADB blocks) │
└──────────────────┘         └──────────────────┘

Problem: adb forward tcp:5555 tcp:5555 binds to port 5555
         Python server cannot also bind to port 5555
         → WinError 10013
```

**After PR #15 (using `adb reverse`):**
```
┌──────────────────┐         ┌──────────────────┐
│   Android App    │   USB   │   PC Server      │
│                  │  ───►   │                  │
│ TCP Socket to    │◄───────►│ TCP Server :5555 │
│ localhost:5555   │ tunnel  │ (works!)         │
│                  │         │                  │
│ ❌ NOT IMPLEMENTED│         │ ✅ Implemented    │
└──────────────────┘         └──────────────────┘

Problem: Android app uses UsbManager for direct USB
         Android app does NOT have TCP socket client
         → App cannot connect to localhost:5555
```

### Code Evidence

**Android App (`UsbConnectionRepositoryImpl.kt`):**
```kotlin
// Only USB imports - NO socket/network imports
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager

// Connection uses UsbManager.openDevice() - direct USB only
connection = usbManager.openDevice(device)
```

**PC Server (`server.py` after PR #15):**
```python
# Server binds to TCP socket
self.sim_socket.bind(("127.0.0.1", 5555))

# Uses adb reverse for tunneling
# adb reverse tcp:5555 tcp:5555
# This requires Android app to connect via TCP to localhost:5555
```

### Why This Matters

| Connection Method | Android App Support | PC Server Support | Status |
|------------------|---------------------|-------------------|--------|
| Direct USB | ✅ Yes | ✅ Yes | Works (but Windows driver issues) |
| ADB Forward | ❌ No TCP socket | ✅ Yes | Broken (PR #15 removed) |
| ADB Reverse | ❌ No TCP socket | ✅ Yes | **CURRENT ISSUE** |
| Wi-Fi TCP | ❌ No TCP socket | ✅ Yes | Never worked |

---

## PR #15 Background

### What PR #15 Fixed (Issue #14)

**Problem:** `adb forward tcp:5555 tcp:5555` causes ADB to bind to port 5555 on the PC. When the Python server tries to also bind to port 5555, it fails with WinError 10013.

**Solution in PR #15:** Use `adb reverse` instead:
1. Python server binds to localhost:5555 first
2. Then `adb reverse tcp:5555 tcp:5555` makes phone's localhost:5555 forward to PC's localhost:5555
3. This avoids the port conflict

### What PR #15 Broke

The solution assumes the Android app can make TCP socket connections to localhost:5555. But the Android app only uses Android's UsbManager API for direct USB communication.

### Files Changed in PR #15

1. `pc-server/adb_helper.py`:
   - Added `setup_reverse_forward()` and `remove_reverse_forward()`
   - Added `check_adb_device_ready()` to check device without setting up forwarding
   - Added `auto_setup_adb_reverse()` to set up reverse after server is listening
   - Added `cleanup_adb_port()` to remove existing forwards

2. `pc-server/server.py`:
   - Added `_start_tcp_server_with_adb_reverse()` method
   - Changed ADB mode to use reverse forwarding
   - Clean up reverse forwarding on server stop

**Missing:** No changes to the Android app to add TCP socket support.

---

## Proposed Solutions

### Solution 1: Add TCP Socket Support to Android App (RECOMMENDED)

Add a TCP socket connection implementation alongside the existing USB connection:

```kotlin
// New file: TcpConnectionRepositoryImpl.kt
class TcpConnectionRepositoryImpl : UsbConnectionRepository {
    private var socket: Socket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    override suspend fun connect(): Result<Unit> {
        return try {
            // Connect to localhost:5555 (tunneled through ADB reverse)
            socket = Socket("localhost", 5555)
            inputStream = socket?.getInputStream()
            outputStream = socket?.getOutputStream()

            // Send handshake
            val handshake = UsbPacket(
                command = UsbProtocol.Commands.HANDSHAKE,
                payload = "PCEX-Android-1.0".toByteArray()
            )
            sendData(handshake.toBytes())

            // Receive response
            val response = receiveData(UsbProtocol.DEFAULT_BUFFER_SIZE)
            // ... validate response

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun sendData(data: ByteArray): Result<Unit> {
        return try {
            outputStream?.write(data)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun receiveData(bufferSize: Int): Result<ByteArray> {
        return try {
            val buffer = ByteArray(bufferSize)
            val bytesRead = inputStream?.read(buffer) ?: 0
            Result.success(buffer.copyOf(bytesRead))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
```

**Changes Required:**
1. Create `TcpConnectionRepositoryImpl.kt` with socket connection logic
2. Update DI module to provide TCP implementation
3. Add connection mode selector in settings
4. Handle connection errors appropriately

### Solution 2: Revert to Simulation Mode (Workaround)

For immediate use, users can run the server in simulation mode and manually set up port forwarding:

```bash
# On PC
python server.py --simulate

# In another terminal
adb forward tcp:5555 tcp:5555
```

**Limitation:** This brings back the original issue #14 problem - the server cannot bind to port 5555 because `adb forward` is using it. The user would need to run `adb forward` after the server starts, which is not ideal.

### Solution 3: Use Different Ports

Modify the server to use a different port for binding:
- Server binds to port 5556
- `adb reverse tcp:5555 tcp:5556` forwards phone's 5555 to PC's 5556

This avoids conflicts but still requires Android app TCP support.

---

## Conclusion

### Summary

| Aspect | Status | Notes |
|--------|--------|-------|
| PC Server | ✅ Working | TCP server starts, ADB reverse works |
| ADB Reverse | ✅ Working | Port tunneling is set up correctly |
| Android App | ❌ Missing TCP | Only USB connection, no socket client |

### Required Fix

The Android app needs TCP socket connection capability to work with the `adb reverse` approach implemented in PR #15.

### Immediate Recommendation

Until the Android app is updated with TCP socket support:
1. Keep the direct USB mode as an option
2. Add TCP socket client to the Android app
3. Let users choose between connection modes

### Files That Need Changes

1. **New file:** `core/data/src/main/java/com/pcexplorer/core/data/repository/TcpConnectionRepositoryImpl.kt`
2. **Update:** `core/data/src/main/java/com/pcexplorer/core/data/di/DataModule.kt` - Add TCP binding
3. **Update:** `features/connection/` - Add connection mode selector
4. **Update:** `core/domain/src/main/java/com/pcexplorer/core/domain/model/ConnectionState.kt` - Add TCP states

### Log Files Analyzed

- `pc-explorer-server_20260118_115535.log` - Server waits, app doesn't connect
- `pc-explorer-server_20260118_115644.log` - Server waits, app doesn't connect

---

## Addendum: Test Failure Analysis (2026-01-18)

### CI Build Failure

After implementing the TCP socket connection support (PR #17), CI tests failed with 17 failures in `SettingsViewModelTest`.

**Error:** `io.mockk.MockKException at SettingsViewModelTest.kt:60`

**Root Cause:** The `SettingsViewModel` was updated to include new connection mode settings (`connectionMode`, `wifiHost`, `wifiPort`), but the test mocks were not updated to include these new SharedPreferences keys.

### Missing Mocks

The test setup was missing mocks for:
1. `getString("connection_mode", any())` - Connection mode preference
2. `getString("wifi_host", any())` - Wi-Fi host preference
3. `getInt("wifi_port", any())` - Wi-Fi port preference

### Fix Applied

Updated `SettingsViewModelTest.kt` to:
1. Import `ConnectionMode` from core domain
2. Add missing SharedPreferences mocks in setUp()
3. Update `SettingsUiState` tests to include new fields
4. Add new tests for `setConnectionMode()`, `setWifiHost()`, and `setWifiPort()`

### Log Files

- CI build log: https://github.com/Jhon-Crow/pc-explorer-from-mobile/actions/runs/21109372681/job/60705639325
- `logs/pc-explorer-server_20260118_115535.log` - Server log from original issue report
- `logs/pc-explorer-server_20260118_115644.log` - Server log from original issue report

---

## Addendum: Socket Permission Denied Error (2026-01-18 - User Report #2)

### Error Reported

User reported new error after testing PR #17:

**Phone Error:** `connection failed: socket failed: EACCES (Permission denied). Make sure the PC server is running and ADB is connected.`

### PC Server Logs (User Report #2)

Two new log files were provided:
- `logs/user-report-20260118/pc-explorer-server_20260118_125220.log`
- `logs/user-report-20260118/pc-explorer-server_20260118_124822.log`

Both logs show the server is working correctly:
```
2026-01-18 12:52:20,884 - INFO - Starting TCP server on port 5555 (ADB reverse mode)
2026-01-18 12:52:20,921 - INFO - TCP server bound to localhost:5555
2026-01-18 12:52:21,176 - INFO - ADB reverse forwarding set up: device:5555 -> localhost:5555
2026-01-18 12:52:21,177 - INFO - ADB reverse forwarding: ADB reverse forwarding ready for DUA-L22
2026-01-18 12:52:21,178 - INFO - Waiting for Android app to connect...
```

### Root Cause Analysis

The `EACCES (Permission denied)` error when creating a socket on Android is caused by **missing the `android.permission.INTERNET` permission** in the AndroidManifest.xml.

**Evidence:**
1. The error message `socket failed: EACCES` is the standard Android error when an app attempts to create network sockets without the INTERNET permission
2. The AndroidManifest.xml was checked and did not contain the INTERNET permission
3. This is a well-documented Android behavior - see references below

### References

- [GitHub Issue: java.net.socketexception socket failed eacces](https://github.com/gturri/aXMLRPC/issues/54)
- [GitHub Issue: Missing android.permission.INTERNET](https://github.com/ConnectSDK/Simple-Photo-Share-Android/issues/1)
- [GitHub Issue: appium-espresso-driver EACCES](https://github.com/appium/appium-espresso-driver/issues/234)

### Fix Applied

Added the following permissions to `app/src/main/AndroidManifest.xml`:

```xml
<!-- Network permissions for TCP socket connections (ADB reverse mode) -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

**Why ACCESS_NETWORK_STATE:** This permission is commonly used alongside INTERNET to check network availability before attempting connections, improving error handling.

### Why This Was Missed

The TCP socket implementation (`TcpConnectionRepositoryImpl.kt`) was created but the corresponding AndroidManifest.xml was not updated with the required INTERNET permission. The `TcpConnectionRepositoryImpl.hasPermission()` method incorrectly returned `true` without checking for actual Android network permissions:

```kotlin
override fun hasPermission(): Boolean {
    // TCP connections don't require special permissions
    // ^^^ This comment was incorrect - INTERNET permission IS required
    return true
}
```

### Lesson Learned

When adding network/socket functionality to an Android app, always ensure:
1. `android.permission.INTERNET` is declared in AndroidManifest.xml
2. Consider adding `android.permission.ACCESS_NETWORK_STATE` for network state checks
3. These permissions are "normal" permissions (not dangerous), so they don't require runtime permission requests

---

## Addendum: ECONNREFUSED Port 0 Error (2026-01-18 - User Report #3)

### Error Reported

User reported new error after testing PR #17 (with INTERNET permission fix):

**Phone Error:** `failed to connect to localhost/127.0.0.1 (port 0) from /127.0.0.1 (port 55356) after 5000ms: isConnected failed: ECONNREFUSED (Connection refused)`

### Key Observation

The error message shows **port 0** as the destination port, which indicates the socket address was not properly configured or DNS resolution of "localhost" failed on the Android device.

### PC Server Logs (User Report #3)

Four new log files were provided showing the server was working correctly:
- `logs/user-report-20260118-econnrefused/pc-explorer-server_20260118_131058.log`
- `logs/user-report-20260118-econnrefused/pc-explorer-server_20260118_131211.log`
- `logs/user-report-20260118-econnrefused/pc-explorer-server_20260118_131255.log`
- `logs/user-report-20260118-econnrefused/pc-explorer-server_20260118_131310.log`

Example server log:
```
2026-01-18 13:13:11,182 - INFO - Starting TCP server on port 5555 (ADB reverse mode)
2026-01-18 13:13:11,221 - INFO - TCP server bound to localhost:5555
2026-01-18 13:13:11,486 - INFO - ADB reverse forwarding set up: device:5555 -> localhost:5555
2026-01-18 13:13:11,488 - INFO - Waiting for Android app to connect...
```

### Root Cause Analysis

**Issue 1: DNS Resolution of "localhost"**

The `InetSocketAddress` constructor with a hostname (like "localhost") performs DNS resolution. On some Android devices, "localhost" may not resolve correctly, resulting in an unresolved address with port 0.

According to [Android InetSocketAddress documentation](https://developer.android.com/reference/java/net/InetSocketAddress):
> If resolution fails then the address is said to be **unresolved** but can still be used on some circumstances like connecting through a proxy.

**Issue 2: SharedPreferences Mismatch**

The `SettingsViewModel` uses SharedPreferences file named `"settings"` while `ConnectionProvider` uses `"connection_settings"`. This means settings changes made in the UI don't propagate to the actual connection implementation.

```kotlin
// SettingsViewModel.kt
private val prefs = context.getSharedPreferences("settings", ...)

// ConnectionProvider.kt (BEFORE FIX)
private const val PREFS_NAME = "connection_settings"  // WRONG!
```

### Fixes Applied

**Fix 1: Use IP Address Instead of Hostname**

Changed `DEFAULT_HOST` from `"localhost"` to `"127.0.0.1"` in `TcpConnectionRepositoryImpl.kt`:

```kotlin
companion object {
    // Use IP address directly to avoid DNS resolution issues
    private const val DEFAULT_HOST = "127.0.0.1"  // Was "localhost"
    private const val DEFAULT_PORT = 5555
}
```

Also added normalization in `configure()` method to convert any "localhost" input to "127.0.0.1".

**Fix 2: Sync SharedPreferences Files**

Changed `ConnectionProvider` to use the same SharedPreferences file as `SettingsViewModel`:

```kotlin
// ConnectionProvider.kt (AFTER FIX)
private const val PREFS_NAME = "settings"  // Match SettingsViewModel
```

**Fix 3: Added Validation and Logging**

Added port validation to prevent port 0 connections:
```kotlin
if (port !in 1..65535) {
    val message = "Invalid port configuration: $port. Please check settings."
    Logger.e(TAG, message)
    return@withContext Result.failure(Exception(message))
}
```

Added logging to track socket address state:
```kotlin
Logger.d(TAG, "Socket address created: ${socketAddress.hostString}:${socketAddress.port}, unresolved=${socketAddress.isUnresolved}")
```

### References

- [InetSocketAddress (Android Developers)](https://developer.android.com/reference/java/net/InetSocketAddress) - Documentation on unresolved addresses
- [InetSocketAddress source code (AOSP)](https://android.googlesource.com/platform/libcore/+/fe39951/luni/src/main/java/java/net/InetSocketAddress.java) - Implementation details

### Lesson Learned

When creating TCP connections on Android:
1. **Use IP addresses directly** (e.g., `127.0.0.1`) instead of hostnames (e.g., `localhost`) to avoid DNS resolution issues
2. **Ensure SharedPreferences consistency** - all components using shared settings must use the same SharedPreferences file
3. **Add validation** for port numbers before attempting connections
4. **Log the InetSocketAddress state** to help debug unresolved address issues

---

*Case study compiled on 2026-01-18*
*Updated with test failure analysis on 2026-01-18*
*Updated with socket permission denied analysis on 2026-01-18*
*Updated with ECONNREFUSED/port 0 analysis on 2026-01-18*
*Related to Issue #14, PR #15, and PR #17*

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

*Case study compiled on 2026-01-18*
*Updated with test failure analysis on 2026-01-18*
*Related to Issue #14, PR #15, and PR #17*

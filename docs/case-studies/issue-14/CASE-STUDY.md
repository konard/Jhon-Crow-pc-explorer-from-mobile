# Case Study: Issue #14 - "Phone Not Connecting" (не подключается телефон)

## Issue Summary

**Issue Title (Russian):** не подключается телефон
**Issue Title (English):** Phone not connecting
**Issue URL:** https://github.com/Jhon-Crow/pc-explorer-from-mobile/issues/14
**Date Reported:** 2026-01-18
**Reporter:** Jhon-Crow
**Device:** Huawei DUA-L22 (VendorID 0x12D1, ProductID 0x107F)

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [Log Analysis](#log-analysis)
3. [Timeline of Events](#timeline-of-events)
4. [Root Cause Analysis](#root-cause-analysis)
5. [Online Research Findings](#online-research-findings)
6. [Proposed Solutions](#proposed-solutions)
7. [Conclusion](#conclusion)

---

## Executive Summary

The user reports that their phone is not connecting to the PC Explorer server. Analysis of 9 log files reveals a **progression of issues** across multiple attempts:

### Phase 1: No ADB - USB Mode Fails (09:50 - 09:55)
Without ADB installed, the server falls back to USB mode, which fails with **"Operation not supported or unimplemented on this platform"** - a known Windows USB driver incompatibility.

**Status:** 🔴 EXPECTED FAILURE - USB direct mode has known Windows limitations (documented in Issue #12)

### Phase 2: ADB Installed but Not Authorized (10:09)
With ADB installed (`platform-tools` folder), the server detects ADB but fails with **"Device connected but not authorized"** - the user needs to authorize USB debugging on their phone.

**Status:** 🟡 USER ACTION REQUIRED - Need to accept USB debugging prompt on phone

### Phase 3: ADB Works but Port 5555 Blocked (10:10 - 10:11)
After authorization, ADB port forwarding succeeds, but the server crashes immediately with **WinError 10013: Socket access forbidden** - port 5555 is blocked or in use.

**Status:** 🔴 CRITICAL ISSUE - Port conflict or firewall blocking

---

## Log Analysis

### Log Files Provided (9 files)

| Log File | Timestamp | ADB Status | Error Type |
|----------|-----------|------------|------------|
| pc-explorer-server_20260118_095008.log | 09:50:08 | Not found | USB driver error |
| pc-explorer-server_20260118_095416.log | 09:54:16 | Not found | USB driver error |
| pc-explorer-server_20260118_095514.log | 09:55:14 | Not found | USB driver error |
| pc-explorer-server_20260118_100955.log | 10:09:55 | Found, not authorized | USB access denied |
| **pc-explorer-server_20260118_101031.log** | **10:10:31** | **Found, authorized** | **Port 5555 blocked** |
| pc-explorer-server_20260118_101037.log | 10:10:37 | Found, authorized | Port 5555 blocked |
| pc-explorer-server_20260118_101040.log | 10:10:40 | Found, authorized | Port 5555 blocked |
| pc-explorer-server_20260118_101042.log | 10:10:42 | Found, authorized | Port 5555 blocked |
| pc-explorer-server_20260118_101112.log | 10:11:12 | Found, authorized | Port 5555 blocked |

### Phase 1 Logs (09:50 - 09:55) - No ADB, USB Mode

All three initial logs show identical behavior:

```
2026-01-18 09:50:08,032 - INFO - ============================================================
2026-01-18 09:50:08,032 - INFO - PC Explorer USB Server - Starting
2026-01-18 09:50:08,048 - INFO - ADB not found in bundle or system PATH
2026-01-18 09:50:08,079 - INFO - Auto-detecting connection mode...
2026-01-18 09:50:08,092 - INFO - Trying USB mode...
2026-01-18 09:50:08,124 - INFO - Loaded libusb backend from: C:\...\libusb-1.0.dll
2026-01-18 09:50:08,214 - INFO - Found Huawei device: VendorID=0x12D1, ProductID=0x107F
2026-01-18 09:50:08,216 - ERROR - USB error: Operation not supported or unimplemented on this platform
[Pattern repeats every ~1 second]
```

**Analysis:**
- Server correctly reports "ADB not found"
- Falls back to USB mode (expected behavior)
- Device is found (Huawei 0x12D1:0x107F)
- USB communication fails due to Windows driver incompatibility
- This is the same issue documented in Issue #12

### Phase 2 Log (10:09:55) - ADB Found but Not Authorized

```
2026-01-18 10:09:55,118 - INFO - ADB found: I:\Загрузки\Mobile to pc\platform-tools\adb.exe
2026-01-18 10:09:55,151 - INFO - Using ADB: I:\Загрузки\Mobile to pc\platform-tools\adb.exe
2026-01-18 10:09:59,259 - INFO - ADB setup failed: Device connected but not authorized. Check your phone for USB debugging prompt.
2026-01-18 10:09:59,261 - INFO - Trying USB mode...
2026-01-18 10:09:59,379 - INFO - Found Huawei device: VendorID=0x12D1, ProductID=0x107F
2026-01-18 10:09:59,381 - ERROR - USB error: [Errno 13] Access denied (insufficient permissions)
```

**Analysis:**
- User installed ADB platform-tools in `I:\Загрузки\Mobile to pc\platform-tools\`
- ADB is correctly detected by the server
- ADB setup fails: "Device connected but not authorized"
- The user did not accept the USB debugging authorization prompt on their phone
- Server falls back to USB mode, which also fails
- New error: "Access denied (insufficient permissions)" instead of "Operation not supported"

### Phase 3 Logs (10:10:31 - 10:11:12) - ADB Works, Port Blocked

```
2026-01-18 10:10:31,634 - INFO - Using ADB: I:\Загрузки\Mobile to pc\platform-tools\adb.exe
2026-01-18 10:10:31,971 - INFO - ADB port forwarding set up: localhost:5555 -> device:5555
2026-01-18 10:10:31,972 - INFO - ADB setup successful: ADB forwarding ready for DUA-L22
2026-01-18 10:10:31,973 - INFO - Starting TCP server on port 5555
2026-01-18 10:10:32,009 - ERROR - Unexpected error: [WinError 10013] Сделана попытка доступа к сокету методом, запрещенным правами доступа
Traceback (most recent call last):
  File "server.py", line 859, in main
  File "server.py", line 226, in start
  File "server.py", line 240, in _start_auto_mode
  File "server.py", line 290, in _start_tcp_server
PermissionError: [WinError 10013]...
2026-01-18 10:10:32,013 - INFO - Server stopped
```

**Analysis:**
- User authorized USB debugging on their phone (between 09:59 and 10:10)
- ADB port forwarding succeeds: "ADB forwarding ready for DUA-L22"
- Device is correctly identified as Huawei DUA-L22
- **Critical failure:** Server cannot bind to port 5555
- Error: WinError 10013 = "An attempt was made to access a socket in a way forbidden by its access permissions"
- This happens 5 times in rapid succession (10:10:31 → 10:11:12)

---

## Timeline of Events

### Complete Timeline

| Time | Event | Result |
|------|-------|--------|
| 09:50:08 | First server start | ADB not found, USB fails with driver error |
| 09:54:16 | Second server start | ADB not found, USB fails with driver error |
| 09:55:14 | Third server start | ADB not found, USB fails with driver error |
| *~09:55-10:09* | *User downloads ADB platform-tools* | - |
| 10:09:55 | Fourth server start | ADB found, but not authorized on phone |
| *~10:09-10:10* | *User authorizes USB debugging on phone* | - |
| **10:10:31** | **Fifth server start** | **ADB works, PORT 5555 BLOCKED** |
| 10:10:37 | Sixth server start | Same error - port 5555 blocked |
| 10:10:40 | Seventh server start | Same error - port 5555 blocked |
| 10:10:42 | Eighth server start | Same error - port 5555 blocked |
| 10:11:12 | Ninth server start | Same error - port 5555 blocked |

### User Actions Interpretation

1. **09:50-09:55 (5 min):** User tries the server 3 times without ADB, sees USB errors
2. **09:55-10:09 (~14 min):** User downloads and extracts ADB platform-tools
3. **10:09-10:10 (~1 min):** User sees "not authorized" message, accepts prompt on phone
4. **10:10-10:11 (~1 min):** User rapidly retries server 5 times, all fail with port error

---

## Root Cause Analysis

### Root Cause #1: Windows Port 5555 Blocking (CRITICAL)

**Error:** `[WinError 10013] Сделана попытка доступа к сокету методом, запрещенным правами доступа`
**Translation:** "An attempt was made to access a socket in a way forbidden by its access permissions"

**Possible Causes:**

1. **Port 5555 Already in Use**
   - ADB daemon (`adb.exe`) may have started a server on port 5555
   - Another application might be using port 5555
   - Windows may have reserved this port for Hyper-V

2. **Windows Firewall Blocking**
   - Windows Defender Firewall may be blocking the socket bind
   - Third-party antivirus/firewall software may be interfering

3. **ADB Port Forwarding Conflict**
   - The `adb forward tcp:5555 tcp:5555` command creates a forwarding rule
   - When the Python server tries to bind to 127.0.0.1:5555, ADB is already listening there
   - **This is likely the root cause** - the ADB forward command and the server are competing for the same port

**Code Location (`server.py:482-489`):**
```python
def _start_tcp_server(self) -> None:
    """Start TCP server (used by both ADB and simulation modes)."""
    import socket

    logger.info("Starting TCP server on port 5555")
    self.sim_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    self.sim_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    self.sim_socket.bind(("127.0.0.1", 5555))  # ← FAILS HERE
```

### Root Cause #2: ADB Forward Architecture Misunderstanding

**The Problem:**

The current ADB mode implementation has a **fundamental architecture issue**:

1. `adb forward tcp:5555 tcp:5555` makes ADB listen on `localhost:5555`
2. When data arrives on `localhost:5555`, ADB forwards it to the Android device's port 5555
3. The Python server then tries to **also** bind to `localhost:5555` to receive connections
4. **CONFLICT:** Both ADB and the Python server cannot listen on the same port!

**Correct Architecture Should Be:**

```
                 CURRENT (WRONG)                              CORRECT

┌──────────────┐                              ┌──────────────┐
│ Python Server│                              │ Python Server│
│ bind(:5555)  │ ← CONFLICT                   │ connect to   │
└──────────────┘                              │ localhost:   │
       ↕                                      │ 5555         │
┌──────────────┐                              └──────────────┘
│ ADB Forward  │                                     ↓
│ listen(:5555)│ ← CONFLICT                   ┌──────────────┐
└──────────────┘                              │ ADB Forward  │
                                              │ listen(:5555)│
                                              └──────────────┘
                                                     ↓
                                              ┌──────────────┐
                                              │ Android App  │
                                              │ TCP Server   │
                                              │ port 5555    │
                                              └──────────────┘
```

**The Fix:**
- ADB forward creates the tunnel from PC:5555 → Android:5555
- Android app should run a TCP server on port 5555
- PC server should **connect** to localhost:5555 (not bind/listen)
- OR: Use `adb reverse` instead of `adb forward` to have Android connect to PC

### Root Cause #3: USB Driver Incompatibility (Known Issue)

Same as documented in Issue #12:
- Windows uses MTP driver for Huawei device
- libusb cannot claim interfaces from MTP driver
- Direct USB mode will not work without driver replacement

---

## Online Research Findings

### Windows Error 10013 (Socket Permission Denied)

From [The Windows Club](https://www.thewindowsclub.com/error-10013-an-attempt-was-made-to-access-a-socket):
> "Error 10013 comes when your Windows OS or any other third-party app tries to access a port that is already in use."

From [Flutter Issue #62589](https://github.com/flutter/flutter/issues/62589):
> "adb.exe: error: cannot bind listener: cannot listen on socket: An attempt was made to access a socket in a way forbidden by its access permissions. (10013)"

### Huawei Device USB Identification

From [Device Hunt](https://devicehunt.com/view/type/usb/vendor/12D1):
> USB Vendor ID 0x12D1 belongs to Huawei Technologies Co., Ltd.

The Product ID 0x107F corresponds to a Huawei Y5/DUA-L22 device in MTP mode.

### ADB Port Forwarding Behavior

From [Google Issue Tracker](https://issuetracker.google.com/issues/36944754):
> "By default, ADB binds port forwarding to localhost (127.0.0.1)"

From [Scrcpy Issues](https://github.com/Genymobile/scrcpy/issues/2468):
> "ERROR: Could not listen/forward on any port in range"

This confirms that port conflicts with ADB are common issues.

### Sources

- [Windows Error 10013 - The Windows Club](https://www.thewindowsclub.com/error-10013-an-attempt-was-made-to-access-a-socket)
- [Flutter ADB Error #62589](https://github.com/flutter/flutter/issues/62589)
- [ADB Port Forwarding on All Interfaces](https://www.codestudy.net/blog/adb-port-forwarding-to-listen-on-all-interfaces/)
- [Scrcpy Port Issues #2468](https://github.com/Genymobile/scrcpy/issues/2468)
- [Huawei USB VID 12D1 - Device Hunt](https://devicehunt.com/view/type/usb/vendor/12D1)
- [libmtp Huawei Device Bug #1550](https://sourceforge.net/p/libmtp/bugs/1550/)

---

## Proposed Solutions

### Solution 1: Fix ADB Mode Architecture (RECOMMENDED)

**Problem:** Both ADB forward and Python server try to bind to the same port.

**Fix Options:**

#### Option A: Use `adb reverse` Instead of `adb forward`

```python
# Instead of: adb forward tcp:5555 tcp:5555
# Use:        adb reverse tcp:5555 tcp:5555

# This makes:
# - Android connects to its localhost:5555
# - ADB tunnels this to PC's localhost:5555
# - Python server listens on PC localhost:5555 ← No conflict!
```

**Changes Required:**
1. Modify `adb_helper.py` to use `adb reverse` instead of `adb forward`
2. Modify Android app to **connect** to localhost:5555 instead of listening

#### Option B: Change Python Server to Connect (Not Listen)

If using `adb forward`, the Python server should not try to bind/listen:

```python
# Current (wrong):
self.sim_socket.bind(("127.0.0.1", 5555))  # Conflicts with ADB
self.sim_socket.listen(1)

# Fixed:
# Don't bind! ADB forward already forwards to Android's listening socket
# The Android app should be the server, PC should be the client
self.client_socket.connect(("127.0.0.1", 5555))
```

#### Option C: Use Different Ports

```python
# ADB forward on port 5555:
adb forward tcp:5555 tcp:5556  # Forward PC:5555 → Android:5556

# Python server listens on different port:
self.sim_socket.bind(("127.0.0.1", 5556))  # Use 5556 instead
```

### Solution 2: Immediate User Workaround

Until the architecture is fixed, the user can try:

1. **Kill ADB and use simulation mode manually:**
   ```cmd
   adb kill-server
   python pc-explorer-server.exe --simulate
   ```
   Then manually run `adb forward tcp:5555 tcp:5555` in another terminal.

2. **Use a different port:**
   - Modify the server to use port 5556 or another available port
   - Run: `adb forward tcp:5556 tcp:5555`

3. **Check for port conflicts:**
   ```cmd
   netstat -ano | findstr :5555
   ```
   Kill any process using port 5555.

### Solution 3: Add Port Conflict Detection

Enhance the server to detect and handle port conflicts:

```python
def _start_tcp_server(self, port: int = 5555) -> None:
    import socket

    # Check if port is available before binding
    test_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    try:
        test_socket.bind(("127.0.0.1", port))
        test_socket.close()
    except OSError as e:
        if e.winerror == 10013:
            logger.error(f"Port {port} is blocked or in use!")
            logger.info("Common causes:")
            logger.info("  1. Another application is using this port")
            logger.info("  2. Windows Firewall is blocking the port")
            logger.info("  3. ADB daemon is already listening on this port")
            logger.info("")
            logger.info("Try these solutions:")
            logger.info("  1. Run: netstat -ano | findstr :5555")
            logger.info("  2. Kill conflicting process or use different port")
            logger.info("  3. Run: adb kill-server")
            return
```

---

## Conclusion

### Summary of Issues

| Issue | Status | Root Cause | Solution |
|-------|--------|------------|----------|
| USB mode fails | 🟡 Known | Windows MTP driver | Use ADB mode |
| ADB not authorized | ✅ Resolved | User didn't accept prompt | User accepted USB debugging |
| Port 5555 blocked | 🔴 CRITICAL | Architecture conflict | Fix ADB/Server design |

### Immediate Recommendations

1. **For the User (Jhon-Crow):**
   - Close all running instances of pc-explorer-server
   - Run `adb kill-server` in Command Prompt
   - Run `netstat -ano | findstr :5555` to check what's using port 5555
   - Try using simulation mode: `pc-explorer-server.exe --simulate`

2. **For the Developers:**
   - Fix the ADB mode architecture to avoid port conflict
   - Consider using `adb reverse` instead of `adb forward`
   - Add better error messages for port conflicts

### Files Analyzed

- `pc-explorer-server_20260118_095008.log` - USB mode failure (no ADB)
- `pc-explorer-server_20260118_095416.log` - USB mode failure (no ADB)
- `pc-explorer-server_20260118_095514.log` - USB mode failure (no ADB)
- `pc-explorer-server_20260118_100955.log` - ADB found, not authorized
- `pc-explorer-server_20260118_101031.log` - ADB works, port 5555 blocked
- `pc-explorer-server_20260118_101037.log` - Port 5555 blocked
- `pc-explorer-server_20260118_101040.log` - Port 5555 blocked
- `pc-explorer-server_20260118_101042.log` - Port 5555 blocked
- `pc-explorer-server_20260118_101112.log` - Port 5555 blocked

---

*Case study compiled on 2026-01-18*

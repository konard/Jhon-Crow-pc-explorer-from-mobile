# Case Study: Issue #12 - "When Pressing Connect on Phone, Nothing Happens"

## Issue Summary

**Issue Title (Russian):** при нажатии connect на телефоне ничего не происходит
**Issue Title (English):** When pressing connect on phone, nothing happens
**Issue URL:** https://github.com/Jhon-Crow/pc-explorer-from-mobile/issues/12
**Date Reported:** 2026-01-18
**Reporter:** Jhon-Crow
**Device:** Huawei (VendorID 0x12D1)

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [Log Analysis](#log-analysis)
3. [Timeline of Events](#timeline-of-events)
4. [System Architecture Overview](#system-architecture-overview)
5. [Root Cause Analysis](#root-cause-analysis)
6. [Online Research Findings](#online-research-findings)
7. [Proposed Solutions](#proposed-solutions)
8. [Conclusion](#conclusion)

---

## Executive Summary

The user reports that when pressing the "Connect" button on the Android app, nothing happens. This case study tracks TWO PHASES of debugging:

### Phase 1: Device Not Detected (Original Issue)
The PC server originally only searched for Google's vendor ID (0x18D1), missing the user's Huawei device (0x12D1).

**Status:** ✅ FIXED - Multi-vendor support was added in commit 2ec999a.

### Phase 2: Driver Incompatibility (New Issue Discovered)
After the multi-vendor fix, a NEW error emerged: **"Operation not supported or unimplemented on this platform"**

This is a **Windows USB driver issue** where libusb cannot claim the USB interface because the Huawei phone is using Windows' default MTP/PTP driver instead of WinUSB.

**Status:** 🔴 NOT FIXED - Requires driver installation or alternative approach.

---

## Log Analysis

### Log Files Provided

| Log File | Timestamp | Server Version | Outcome |
|----------|-----------|----------------|---------|
| pc-explorer-server_20260118_082232.log | 08:22:32 | Original | Stuck waiting (no device found) |
| pc-explorer-server_20260118_082318.log | 08:23:18 | Original | Stuck waiting (no device found) |
| pc-explorer-server_20260118_082639.log | 08:26:39 | Original | Stuck waiting (no device found) |
| pc-explorer-server_20260118_082709.log | 08:27:09 | Original | Stuck waiting (no device found) |
| **pc-explorer-server_20260118_085249.log** | **08:52:49** | **With multi-vendor fix** | **Device found, driver error** |

### Phase 1 Logs (08:22 - 08:27) - Original Server

All four initial log files show identical behavior:

```
2026-01-18 08:22:32,498 - INFO - ============================================================
2026-01-18 08:22:32,499 - INFO - PC Explorer USB Server - Starting
...
2026-01-18 08:22:32,534 - INFO - Loaded libusb backend from: C:\...\libusb-1.0.dll
2026-01-18 08:22:32,535 - INFO - Starting in USB mode
2026-01-18 08:22:32,536 - INFO - Waiting for USB device...
[LOG ENDS - No further entries]
```

**Analysis:** The original server only looked for `idVendor=0x18D1` (Google), so the Huawei device was never found.

### Phase 2 Log (08:52:49) - After Multi-Vendor Fix

```
2026-01-18 08:52:49,246 - INFO - ============================================================
2026-01-18 08:52:49,247 - INFO - PC Explorer USB Server - Starting
...
2026-01-18 08:52:49,285 - INFO - Starting in USB mode
2026-01-18 08:52:49,286 - INFO - Waiting for USB device...
2026-01-18 08:52:49,381 - INFO - Found Huawei device: VendorID=0x12D1, ProductID=0x107F
2026-01-18 08:52:49,382 - ERROR - USB error: Operation not supported or unimplemented on this platform
2026-01-18 08:52:50,448 - INFO - Found Huawei device: VendorID=0x12D1, ProductID=0x107F
2026-01-18 08:52:50,449 - ERROR - USB error: Operation not supported or unimplemented on this platform
[Pattern repeats every ~1 second for 34+ attempts]
```

**Analysis:**
- ✅ Multi-vendor detection is working (Huawei 0x12D1 found)
- ❌ New error: "Operation not supported or unimplemented on this platform"
- The error occurs when trying to `set_configuration()` on the USB device
- This is libusb error code `-12` (LIBUSB_ERROR_NOT_SUPPORTED)

### Key Observations from Logs

1. **Server Environment:**
   - Python version: 3.11.9 (64-bit)
   - Platform: Windows (win32)
   - Running as frozen PyInstaller executable
   - libusb backend loads successfully from bundled DLL

2. **Device Information:**
   - Vendor ID: 0x12D1 (Huawei)
   - Product ID: 0x107F (indicates MTP/PTP mode, not ADB or Accessory mode)

3. **Error Pattern:**
   - Error is deterministic - happens every single attempt
   - ~1 second retry interval
   - Server never gives up, loops forever

---

## Timeline of Events

### Complete Timeline

| Time | Event | Result |
|------|-------|--------|
| 08:22:32 | First server start (original) | Stuck waiting - no device found |
| 08:23:18 | Second server start (original) | Stuck waiting - no device found |
| 08:26:39 | Third server start (original) | Stuck waiting - no device found |
| 08:27:09 | Fourth server start (original) | Stuck waiting - no device found |
| ~08:27-08:52 | *User downloads new server with multi-vendor fix* | - |
| **08:52:49** | **Fifth server start (with fix)** | **Device found, driver error** |
| 08:52:49 → 08:53:23 | 34+ connection attempts | All fail with driver error |

### Interpretation

1. **First 5 minutes (08:22-08:27):** User tried the original server 4 times, each time it got stuck waiting for a device that would never be found (wrong vendor ID).

2. **Gap (~25 minutes):** User likely downloaded the updated server with multi-vendor support.

3. **08:52:49 onward:** New server correctly identifies the Huawei device, but fails at a later stage - the Windows USB driver prevents libusb from claiming the interface.

---

## System Architecture Overview

### Current Design

```
┌─────────────────────────────────────────────────────────────────┐
│                         ANDROID DEVICE                          │
├─────────────────────────────────────────────────────────────────┤
│  Connection Screen UI (Jetpack Compose)                         │
│         │                                                       │
│         ▼                                                       │
│  ConnectionViewModel → ConnectToDeviceUseCase                   │
│         │                                                       │
│         ▼                                                       │
│  UsbConnectionRepositoryImpl                                    │
│    ├─ Query usbManager.deviceList                               │
│    ├─ Select first device with ANY vendor ID                    │
│    ├─ Request permission if needed                              │
│    ├─ Open connection, claim interface                          │
│    ├─ Find bulk endpoints (IN/OUT)                              │
│    └─ Send handshake packet "PCEX-Android-1.0"                  │
├─────────────────────────────────────────────────────────────────┤
│                      USB CABLE CONNECTION                        │
├─────────────────────────────────────────────────────────────────┤
│                           PC SERVER                              │
├─────────────────────────────────────────────────────────────────┤
│  server.py (PyInstaller EXE)                                    │
│    ├─ Setup libusb backend                                      │
│    ├─ Poll: usb.core.find() with multiple vendor IDs ✅         │
│    ├─ Try device.set_configuration() ← FAILS HERE ❌            │
│    ├─ Configure device endpoints                                │
│    └─ Handle incoming packets                                   │
└─────────────────────────────────────────────────────────────────┘
```

### USB Communication Model Mismatch

The current architecture has a **fundamental USB mode confusion**:

| Component | Current Behavior | Expected for USB Accessory |
|-----------|------------------|---------------------------|
| PC Server | Acts as USB **host**, looking for Android devices | Should act as USB **accessory** |
| Android App | Acts as USB **host**, looking for any USB device | Should be the USB **device** being controlled |

**Problem:** Both sides are trying to be the USB host!

---

## Root Cause Analysis

### Root Cause #1: Vendor ID Mismatch (FIXED ✅)

**Original Problem:** PC Server only looked for Google's vendor ID (0x18D1).

**Fix Applied:** Added support for 20+ Android vendor IDs including Huawei (0x12D1).

**Current Status:** ✅ Fixed in commit 2ec999a

### Root Cause #2: Windows USB Driver Incompatibility (NEW 🔴)

**Error:** `Operation not supported or unimplemented on this platform`

**Technical Details:**
- libusb error code: `-12` (LIBUSB_ERROR_NOT_SUPPORTED)
- Occurs when calling `device.set_configuration()` in `server.py:328`
- The Huawei device (ProductID 0x107F) is in MTP/PTP mode
- Windows uses its built-in MTP driver for this device
- libusb **cannot claim interfaces from devices using non-WinUSB drivers**

**Code Location (`server.py:323-328`):**
```python
try:
    # Detach kernel driver if necessary
    if device.is_kernel_driver_active(0):  # Not applicable on Windows
        device.detach_kernel_driver(0)

    device.set_configuration()  # ← FAILS HERE
```

**Why This Happens:**
1. When an Android phone connects to Windows via USB cable, it typically appears in MTP (Media Transfer Protocol) mode
2. Windows automatically assigns its built-in MTP driver to the device
3. libusb can **enumerate** devices regardless of driver, but cannot **communicate** with them unless they use a libusb-compatible driver (WinUSB, libusb-win32, or libusbK)
4. The `set_configuration()` call requires claiming the USB interface, which the Windows MTP driver won't allow

### Root Cause #3: No AOA Protocol Implementation (CRITICAL 🔴)

Even if the driver issue were resolved, the current implementation wouldn't work because:

1. **Android as USB Host Mode:** Android uses `UsbManager` to connect to USB accessories. This is what the current Android code does.

2. **Android as USB Accessory Mode:** An external USB host (PC) sends special control requests to switch Android into accessory mode.

**Current code does neither correctly:**
- Android code tries to be USB host
- PC code tries to be USB host
- Neither implements AOA protocol handshake

### Root Cause #4: Android Device List Empty at Connection Time

**Android Code (UsbConnectionRepositoryImpl.kt):**
```kotlin
val deviceList = usbManager.deviceList
if (deviceList.isEmpty()) {
    return Result.failure(Exception("No USB devices found"))
}
```

**Problem:** `usbManager.deviceList` returns devices that Android can see as USB **host**. When connected to a PC via USB:
- Android sees itself as a **device** connected to the PC (USB host)
- Android does NOT see the PC as a USB device in its device list
- Therefore, `deviceList` is empty

---

## Online Research Findings

### libusb Error Analysis

Based on research from multiple sources:

#### From [PyUSB Issue #370](https://github.com/pyusb/pyusb/issues/370)

> "composite_claim_interface] unsupported API call for 'claim_interface' (unrecognized device driver)"

This occurs because:
- The device interface is managed by Windows' default driver (MTP/CDC), not WinUSB
- PyUSB's libusb backend cannot manage devices with incompatible drivers
- The solution requires installing WinUSB driver for the device

#### From [libusb Issue #445](https://github.com/libusb/libusb/issues/445)

> "For USB composite devices, try to use Zadig to install the WinUSB to the USB composite parent"

The recommended fix involves using the **Zadig** tool to replace Windows' default driver with WinUSB.

#### From [Scrcpy Issue #3654](https://github.com/Genymobile/scrcpy/issues/3654)

> "OTG mode works, except when it does not. Try without USB debugging enabled, or try on Linux."

This confirms the issue is Windows-specific and related to driver conflicts.

### Android USB Documentation

From [Android USB Accessory Overview](https://developer.android.com/develop/connectivity/usb/accessory):

> "USB accessory mode allows users to connect USB host hardware specifically designed for Android-powered devices."

The AOA protocol requires the USB host (PC) to:
1. Send control request 51 (Get Protocol) to check AOA support
2. Send strings for manufacturer, model, description, version, URI, and serial number
3. Send control request 53 (Start Accessory) to switch Android to accessory mode
4. Android device re-enumerates with product ID 0x2D00 or 0x2D01

### Sources

- [PyUSB Issue #370 - Operation not supported](https://github.com/pyusb/pyusb/issues/370)
- [libusb Issue #445 - LIBUSB_ERROR_NOT_SUPPORTED](https://github.com/libusb/libusb/issues/445)
- [Scrcpy Issue #3654 - OTG on Windows](https://github.com/Genymobile/scrcpy/issues/3654)
- [USB host and accessory overview | Android Developers](https://developer.android.com/develop/connectivity/usb)
- [USB accessory overview | Android Developers](https://developer.android.com/develop/connectivity/usb/accessory)
- [Android Open Accessory 1.0 | AOSP](https://source.android.com/docs/core/interaction/accessories/aoa)

---

## Proposed Solutions

### Solution 1: Use ADB Port Forwarding (Recommended Workaround)

Instead of raw USB, use ADB (Android Debug Bridge) for port forwarding:

**Implementation:**
1. User enables USB debugging on Android
2. User runs: `adb forward tcp:5555 tcp:5555`
3. PC server runs in simulation/TCP mode: `pc-explorer-server.exe --simulate`
4. Communication happens over TCP socket (already implemented!)

**Advantages:**
- ✅ Works with all Android devices and all manufacturers
- ✅ No driver installation required
- ✅ No vendor ID issues
- ✅ Reliable, well-tested protocol
- ✅ Already implemented in simulation mode!

**Disadvantages:**
- ❌ Requires USB debugging enabled
- ❌ Requires ADB installed on PC

### Solution 2: Install WinUSB Driver via Zadig

Use [Zadig](https://zadig.akeo.ie/) to replace the default Windows driver:

**Steps:**
1. Download Zadig from https://zadig.akeo.ie/
2. Connect Android phone
3. In Zadig, select the phone's USB interface
4. Replace driver with WinUSB
5. Run PC Explorer server

**Advantages:**
- ✅ Enables direct libusb communication
- ✅ No code changes needed

**Disadvantages:**
- ❌ Requires manual driver installation
- ❌ May break MTP file transfer functionality
- ❌ Complex for non-technical users
- ❌ Driver needs reinstalling after Windows updates

### Solution 3: Implement AOA Protocol

Make the PC server implement Android Open Accessory protocol:

1. Detect Android device in any USB mode
2. Send AOA control requests to switch device to accessory mode
3. Wait for device re-enumeration with accessory product ID (0x2D00/0x2D01)
4. Then communicate via bulk transfers

**Implementation Complexity:** Very High - requires significant changes to both PC and Android code.

### Solution 4: Wi-Fi Based Communication

Use local network instead of USB:

1. Both devices connect to same Wi-Fi network
2. PC server broadcasts presence via mDNS/Bonjour
3. Android app discovers PC server
4. Communication over TCP socket

**Advantages:**
- ✅ No USB complexity
- ✅ Works wirelessly
- ✅ No driver issues

**Disadvantages:**
- ❌ Requires same network
- ❌ Potential firewall issues
- ❌ Needs new discovery mechanism

---

## Conclusion

### Summary of Issues

| Issue | Status | Solution |
|-------|--------|----------|
| Vendor ID mismatch (Google only) | ✅ Fixed | Multi-vendor support added |
| Windows driver incompatibility | 🔴 Open | Use ADB or install WinUSB |
| USB architecture mismatch | 🔴 Open | Fundamental redesign needed |
| No user feedback | 🟡 Partial | Improved logging added |

### Recommended Immediate Action

**Use simulation mode with ADB port forwarding:**

```bash
# On PC (with ADB installed)
adb forward tcp:5555 tcp:5555

# Run server in simulation mode
pc-explorer-server.exe --simulate
```

This is already implemented, bypasses all USB driver issues, and works reliably with any Android device.

### Long-term Recommendations

1. **Document the ADB method** as the primary connection method
2. **Consider Wi-Fi discovery** as an alternative
3. **If USB is required:** Implement proper AOA protocol or provide Zadig instructions

---

## Phase 3: Portable Solution Analysis

### User Requirements

The user requested an approach that is:
1. **Simpler** - easier to use without technical knowledge
2. **Portable** - can be packaged into a single portable exe
3. **No installation required** - works out of the box

### Solution Comparison Matrix

| Solution | Portable? | No Driver Install? | Ease of Use | Implementation Effort |
|----------|-----------|-------------------|-------------|----------------------|
| **1. Direct USB (libusb)** | ✅ Yes | ❌ No (requires Zadig) | Low | Already done |
| **2. ADB with bundled tools** | ✅ Yes | ⚠️ Partial* | Medium | Medium |
| **3. Wi-Fi/TCP direct** | ✅ Yes | ✅ Yes | High | High |
| **4. AOA Protocol** | ✅ Yes | ❌ No | Low | Very High |

*ADB requires USB debugging enabled but works with built-in Windows drivers for most devices

### Recommended Portable Solution: Bundled ADB

After research, the **simplest portable solution** that can work is:

#### Option A: Bundle ADB Platform Tools (RECOMMENDED)

**Concept:**
- Bundle `adb.exe`, `AdbWinApi.dll`, `AdbWinUsbApi.dll` with the PyInstaller exe
- Server automatically detects ADB and uses port forwarding
- Falls back to manual instructions if ADB not available

**Pros:**
- Works with most Android devices (Windows 10/11 has built-in MTP support)
- Only requires user to enable USB debugging
- No manual driver installation for most users
- Can be packaged into a single folder distribution

**Cons:**
- Still requires USB debugging to be enabled
- Some older devices may need driver installation

**Implementation:**
1. Download Android SDK Platform Tools (~15MB)
2. Bundle the 3 required files
3. Modify server to auto-run `adb forward tcp:5555 tcp:5555`
4. Use TCP mode by default

#### Option B: Pure Wi-Fi/Network Discovery

**Concept:**
- PC server broadcasts presence via UDP multicast
- Android app discovers server on local network
- Communication over TCP socket

**Pros:**
- No USB drivers needed at all
- Works wirelessly
- Truly portable

**Cons:**
- Requires same Wi-Fi network
- Potential firewall issues
- More complex implementation

### Research Sources

- [libusb Windows Wiki](https://github.com/libusb/libusb/wiki/Windows) - Windows driver requirements
- [Zadig USB Driver Installation](https://zadig.akeo.ie/) - Driver installation tool
- [Android SDK Platform Tools](https://developer.android.com/tools/releases/platform-tools) - ADB download
- [PyUSB Issue #370](https://github.com/pyusb/pyusb/issues/370) - Windows driver compatibility
- [Scrcpy portable](https://github.com/Genymobile/scrcpy) - Example of portable ADB bundling
- [Universal ADB Drivers](https://github.com/koush/UniversalAdbDriver) - Single driver for all Android devices

---

## Implementation Plan for Phase 3

### Step 1: Download ADB Platform Tools
- Download from: https://developer.android.com/tools/releases/platform-tools
- Extract: `adb.exe`, `AdbWinApi.dll`, `AdbWinUsbApi.dll`

### Step 2: Update server.py
- Add auto-detection of bundled ADB
- Implement automatic `adb forward` when device detected
- Default to TCP mode with ADB forwarding
- Keep USB mode as fallback option

### Step 3: Update PyInstaller build
- Bundle ADB files with `--add-binary`
- Create single-folder distribution

### Step 4: Update Documentation
- Add clear instructions for USB debugging setup
- Document the automatic ADB mode

---

## Files Analyzed

### PC Server (Python)
- `pc-server/server.py` - Main server with USB/TCP handling
- `pc-server/protocol.py` - Protocol definitions
- `pc-server/file_handler.py` - File system operations

### Android App (Kotlin)
- `core/data/src/main/java/com/pcexplorer/core/data/repository/UsbConnectionRepositoryImpl.kt` - USB connection logic
- `core/data/src/main/java/com/pcexplorer/core/data/protocol/UsbProtocol.kt` - Protocol definitions
- `features/connection/src/main/java/com/pcexplorer/features/connection/ConnectionViewModel.kt` - UI state management
- `app/src/main/res/xml/usb_device_filter.xml` - USB device filter

### Logs (in `docs/case-studies/issue-12/logs/`)
- `pc-explorer-server_20260118_082232.log` - First attempt (original server)
- `pc-explorer-server_20260118_082318.log` - Second attempt (original server)
- `pc-explorer-server_20260118_082639.log` - Third attempt (original server)
- `pc-explorer-server_20260118_082709.log` - Fourth attempt (original server)
- `pc-explorer-server_20260118_085249.log` - **Fifth attempt (with multi-vendor fix)** - Shows new driver error
- `pc-explorer-server_20260118_095514.log` - **Sixth attempt (with ADB mode)** - ADB not bundled, USB fallback fails

---

## Phase 4: ADB Mode Testing Analysis

### New Log File Analysis

**Log File:** `pc-explorer-server_20260118_095514.log`
**Timestamp:** 09:55:14
**Server Version:** With ADB mode support

### Log Contents Analysis

```
2026-01-18 09:55:14,768 - INFO - ============================================================
2026-01-18 09:55:14,769 - INFO - PC Explorer USB Server - Starting
2026-01-18 09:55:14,771 - INFO - Mode: auto
2026-01-18 09:55:14,783 - INFO - ADB not found in bundle or system PATH
2026-01-18 09:55:14,815 - INFO - Auto-detecting connection mode...
2026-01-18 09:55:14,828 - INFO - Trying USB mode...
2026-01-18 09:55:14,859 - INFO - Loaded libusb backend from: C:\...\libusb-1.0.dll
2026-01-18 09:55:14,862 - INFO - Waiting for USB device...
2026-01-18 09:55:14,944 - INFO - Found Huawei device: VendorID=0x12D1, ProductID=0x107F
2026-01-18 09:55:14,946 - ERROR - USB error: Operation not supported or unimplemented on this platform
[Pattern repeats 10+ times]
```

### Issue Identified

The user is using the portable EXE with ADB mode support, BUT:

1. **ADB is NOT bundled** with the executable
2. **ADB is NOT in system PATH** on the user's machine
3. **Auto mode fallback** correctly tries USB mode next
4. **USB mode fails** with the same Windows driver error as before

### Root Cause: Missing ADB in Portable Distribution

The server correctly reports:
```
ADB not found in bundle or system PATH
```

The user downloaded the .exe file but the ADB platform tools (adb.exe, AdbWinApi.dll, AdbWinUsbApi.dll) were not bundled with it.

### Current Behavior vs Expected

| Aspect | Current | Expected |
|--------|---------|----------|
| ADB Detection | ✅ Correctly reports ADB not found | ✅ Good |
| Fallback to USB | ✅ Correctly falls back | ⚠️ USB fails on Windows |
| User Guidance | ❌ No clear instructions | ❌ User doesn't know what to do |
| Error Recovery | ❌ Loops forever with error | ❌ Should provide alternatives |

### Required Improvements

1. **Better User Guidance** - When ADB is not found and USB mode fails, provide clear instructions
2. **Downloadable ADB** - Tell user where to get ADB platform tools
3. **Graceful Failure** - Stop looping after USB errors, offer simulation mode

### Updated Solution Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    PC EXPLORER SERVER (auto mode)               │
└─────────────────────────────────────────────────────────────────┘
                               │
                               ▼
               ┌───────────────────────────────┐
               │     Check for bundled ADB     │
               └───────────────────────────────┘
                    │                    │
               Found ✓                Not Found
                    ▼                    ▼
         ┌──────────────────┐  ┌──────────────────────────┐
         │  ADB Mode Ready  │  │  Check System PATH ADB   │
         └──────────────────┘  └──────────────────────────┘
                                     │            │
                                Found ✓       Not Found
                                     ▼            ▼
                          ┌──────────────────┐  ┌────────────────────┐
                          │ ADB Mode Ready   │  │ Try USB Mode       │
                          └──────────────────┘  └────────────────────┘
                                                       │
                                               ┌───────┴──────────┐
                                           Success            USB Error
                                               ▼                    ▼
                                      ┌────────────────┐  ┌─────────────────────┐
                                      │ USB Mode Ready │  │ SHOW USER GUIDANCE: │
                                      └────────────────┘  │ • How to get ADB    │
                                                          │ • How to use --sim  │
                                                          │ • How to use Zadig  │
                                                          └─────────────────────┘
```

### Implementation Changes Needed

1. **server.py modifications:**
   - Add USB error counter to stop after N failures
   - Print clear guidance when all modes fail
   - Suggest downloading ADB platform tools
   - Offer simulation mode as fallback

2. **BUILD.md updates:**
   - Add instructions for downloading ADB
   - Document portable deployment options

### References

- [Android SDK Platform Tools Downloads](https://developer.android.com/tools/releases/platform-tools)
- [Scrcpy portable builds](https://github.com/Genymobile/scrcpy/releases) - Example of bundled ADB distribution

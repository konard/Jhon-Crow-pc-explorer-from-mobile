#!/usr/bin/env python3
"""
PC Explorer USB Server

This server runs on the PC and handles USB communication with the Android app.
It provides file system access through a custom USB protocol.

Usage:
    python server.py              # Auto-detect mode (tries ADB first, then USB)
    python server.py --adb        # Use ADB port forwarding (recommended)
    python server.py --usb        # Direct USB mode (requires driver setup)
    python server.py --simulate   # TCP simulation mode (for testing)

Requirements:
    - Python 3.10+
    - For ADB mode: ADB executable (bundled or in PATH)
    - For USB mode: pyusb, libusb (and proper USB drivers)

The recommended mode is --adb, which uses Android Debug Bridge for communication.
This works with most Android devices without requiring special driver installation.
"""

import sys
import os
import time
import logging
import argparse
import threading
from typing import Optional
from datetime import datetime
from pathlib import Path

# Add parent directory to path for imports
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from protocol import (
    Packet, Command, Flags, ErrorCode,
    PayloadSerializer, MAGIC, MAX_PACKET_SIZE, DEFAULT_BUFFER_SIZE
)
from file_handler import FileHandler
from adb_helper import (
    find_adb_executable, auto_setup_adb_forwarding,
    list_devices, get_device_model, AdbConnection
)

# Determine if running as frozen exe (PyInstaller)
def is_frozen():
    """Check if running as a PyInstaller frozen executable."""
    return getattr(sys, 'frozen', False) and hasattr(sys, '_MEIPASS')


def get_app_dir():
    """Get the application directory (works for both frozen exe and script)."""
    if is_frozen():
        return Path(sys._MEIPASS)
    return Path(__file__).parent


def get_log_dir():
    """Get the directory for log files."""
    if is_frozen():
        # When running as exe, log to a 'logs' folder next to the exe
        exe_dir = Path(sys.executable).parent
        log_dir = exe_dir / "logs"
    else:
        log_dir = Path(__file__).parent / "logs"
    log_dir.mkdir(exist_ok=True)
    return log_dir


def setup_logging(verbose: bool = False):
    """Configure logging with both console and file handlers."""
    log_level = logging.DEBUG if verbose else logging.INFO
    log_format = '%(asctime)s - %(levelname)s - %(message)s'

    # Create root logger
    root_logger = logging.getLogger()
    root_logger.setLevel(log_level)

    # Console handler
    console_handler = logging.StreamHandler()
    console_handler.setLevel(log_level)
    console_handler.setFormatter(logging.Formatter(log_format))
    root_logger.addHandler(console_handler)

    # File handler - one log file per session
    log_dir = get_log_dir()
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    log_file = log_dir / f"pc-explorer-server_{timestamp}.log"

    file_handler = logging.FileHandler(log_file, encoding='utf-8')
    file_handler.setLevel(logging.DEBUG)  # Always log debug to file
    file_handler.setFormatter(logging.Formatter(log_format))
    root_logger.addHandler(file_handler)

    return log_file


def setup_libusb_backend():
    """
    Setup libusb backend for PyUSB, especially when running as frozen exe.

    This function handles the 'No backend available' error that occurs when
    pyinstaller bundles the application without the libusb DLL being discoverable.
    """
    if not is_frozen():
        return None  # Use default backend discovery when running as script

    import ctypes
    import ctypes.util

    # When running as frozen exe, look for libusb DLL in the bundle
    app_dir = get_app_dir()

    # Possible libusb DLL names and locations
    dll_names = [
        'libusb-1.0.dll',
        'libusb0.dll',
        'libusb-1.0.so',
        'libusb-1.0.dylib',
    ]

    # Search paths within the frozen bundle
    search_paths = [
        app_dir,
        app_dir / 'libusb',
        app_dir / 'usb1',
        Path(sys.executable).parent,
    ]

    for search_path in search_paths:
        for dll_name in dll_names:
            dll_path = search_path / dll_name
            if dll_path.exists():
                logger.debug(f"Found libusb at: {dll_path}")
                try:
                    # Try to load the library
                    ctypes.CDLL(str(dll_path))
                    # Add to PATH so pyusb can find it
                    os.environ['PATH'] = str(search_path) + os.pathsep + os.environ.get('PATH', '')
                    logger.info(f"Loaded libusb backend from: {dll_path}")
                    return str(dll_path)
                except OSError as e:
                    logger.warning(f"Failed to load {dll_path}: {e}")

    logger.warning("No bundled libusb found, relying on system installation")
    return None


# Configure basic logging (will be replaced in main())
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

# Chunk size for file transfers
CHUNK_SIZE = 32 * 1024  # 32KB

# Android vendor IDs for different manufacturers
# The server will search for any of these to find Android devices
ANDROID_VENDOR_IDS = {
    0x18D1: "Google",
    0x04E8: "Samsung",
    0x2717: "Xiaomi",
    0x12D1: "Huawei",
    0x2A70: "OnePlus",
    0x1004: "LG",
    0x0FCE: "Sony",
    0x0BB4: "HTC",
    0x22B8: "Motorola",
    0x0489: "Foxconn (various)",
    0x05C6: "Qualcomm (various)",
    0x1949: "Amazon",
    0x2916: "Yota",
    0x0E8D: "MediaTek (various)",
    0x1782: "Spreadtrum (various)",
    0x19D2: "ZTE",
    0x2B4C: "Vivo",
    0x2A45: "Meizu",
    0x1EBF: "OPPO",
    0x0B05: "ASUS",
    0x29A9: "Lenovo",
}


class UsbServer:
    """USB server that handles communication with the Android app."""

    def __init__(self, mode: str = 'auto'):
        """
        Initialize the USB server.

        Args:
            mode: Connection mode - 'auto', 'adb', 'usb', or 'simulate'
                - auto: Try ADB first, fall back to USB, then simulation
                - adb: Use ADB port forwarding (recommended)
                - usb: Direct USB connection (requires drivers)
                - simulate: TCP simulation mode for testing
        """
        self.file_handler = FileHandler()
        self.mode = mode
        self.running = False
        self.usb_device = None
        self.usb_endpoint_in = None
        self.usb_endpoint_out = None

        # For TCP modes (simulation and ADB)
        self.sim_socket = None
        self.sim_conn = None

        # For ADB mode
        self.adb_connection = None

    def start(self) -> None:
        """Start the USB server."""
        self.running = True

        if self.mode == 'simulate':
            self._start_simulation_mode()
        elif self.mode == 'adb':
            self._start_adb_mode()
        elif self.mode == 'usb':
            self._start_usb_mode()
        else:  # auto mode
            self._start_auto_mode()

    def _start_auto_mode(self) -> None:
        """Auto-detect the best connection mode."""
        logger.info("Auto-detecting connection mode...")

        # Try ADB first (most compatible)
        adb_path = find_adb_executable()
        if adb_path:
            logger.info(f"Found ADB at: {adb_path}")
            success, message = auto_setup_adb_forwarding(5555, 5555)
            if success:
                logger.info(f"ADB setup successful: {message}")
                self.mode = 'adb'
                self._start_tcp_server()
                return
            else:
                logger.info(f"ADB setup failed: {message}")
        else:
            # ADB not found - log guidance
            logger.warning("ADB not found - this is the recommended connection method")
            self._print_adb_installation_guide()

        # Try USB mode
        logger.info("Trying USB mode...")
        try:
            import usb.core
            # Quick test if USB backend is available
            usb.core.find()
            self.mode = 'usb'
            # Try USB mode with error limit
            if not self._start_usb_mode_with_fallback():
                # USB mode failed, fall back to simulation with guidance
                logger.info("USB mode failed repeatedly, falling back to simulation mode")
                self._print_connection_failure_guide()
                self.mode = 'simulate'
                self._start_simulation_mode()
            return
        except Exception as e:
            logger.info(f"USB mode not available: {e}")

        # Fall back to simulation mode
        logger.info("Falling back to simulation mode (TCP)")
        self._print_connection_failure_guide()
        self.mode = 'simulate'
        self._start_simulation_mode()

    def _print_adb_installation_guide(self) -> None:
        """Print instructions for installing ADB."""
        print()
        print("=" * 60)
        print("ADB NOT FOUND - RECOMMENDED SETUP")
        print("=" * 60)
        print()
        print("For the best experience, download Android Platform Tools:")
        print()
        print("  https://developer.android.com/tools/releases/platform-tools")
        print()
        print("Then either:")
        print("  1. Extract and place adb.exe next to this server")
        print("  2. Or add the platform-tools folder to your PATH")
        print()
        print("=" * 60)
        print()

    def _print_connection_failure_guide(self) -> None:
        """Print guidance when all automatic connection methods fail."""
        print()
        print("=" * 60)
        print("CONNECTION SETUP REQUIRED")
        print("=" * 60)
        print()
        print("Automatic connection could not be established.")
        print("Please use one of these methods:")
        print()
        print("METHOD 1: Install ADB (RECOMMENDED)")
        print("-" * 40)
        print("1. Download Android Platform Tools:")
        print("   https://developer.android.com/tools/releases/platform-tools")
        print("2. Extract and place adb.exe next to this server")
        print("3. Enable USB debugging on your phone:")
        print("   Settings > Developer Options > USB Debugging")
        print("4. Restart this server")
        print()
        print("METHOD 2: Manual ADB Setup")
        print("-" * 40)
        print("If you have ADB installed elsewhere:")
        print("1. Open Command Prompt")
        print("2. Run: adb forward tcp:5555 tcp:5555")
        print("3. The server will now accept connections")
        print()
        print("METHOD 3: Wi-Fi Connection")
        print("-" * 40)
        print("1. Connect phone and PC to the same Wi-Fi network")
        print("2. Find this PC's IP address (run: ipconfig)")
        print("3. In the Android app, enter the PC's IP address")
        print()
        print("=" * 60)
        print()
        logger.info("Connection guidance printed to console")

    def _start_usb_mode_with_fallback(self) -> bool:
        """
        Start USB mode with error detection and fallback.

        Returns:
            True if USB mode is working, False if should fall back to another mode.
        """
        try:
            import usb.core
            import usb.util
        except ImportError:
            logger.error("pyusb not installed")
            return False

        # Setup libusb backend
        setup_libusb_backend()

        logger.info("Starting in USB mode")
        logger.info("Waiting for USB device...")

        # Test if USB backend is available
        try:
            usb.core.find()
        except usb.core.NoBackendError as e:
            logger.error(f"No USB backend available: {e}")
            return False

        # Track errors to detect repeated failures
        consecutive_errors = 0
        max_consecutive_errors = 5
        last_error_message = None

        while self.running and consecutive_errors < max_consecutive_errors:
            device = None
            found_vendor = None

            try:
                for vendor_id, vendor_name in ANDROID_VENDOR_IDS.items():
                    device = usb.core.find(idVendor=vendor_id)
                    if device is not None:
                        found_vendor = vendor_name
                        break
            except usb.core.NoBackendError:
                logger.error("Lost USB backend connection")
                return False

            if device is None:
                time.sleep(1)
                consecutive_errors = 0  # Reset on no device (still searching)
                continue

            logger.info(f"Found {found_vendor} device: VendorID=0x{device.idVendor:04X}, "
                       f"ProductID=0x{device.idProduct:04X}")

            try:
                if device.is_kernel_driver_active(0):
                    device.detach_kernel_driver(0)

                device.set_configuration()
                cfg = device.get_active_configuration()
                intf = cfg[(0, 0)]

                self.usb_endpoint_out = usb.util.find_descriptor(
                    intf,
                    custom_match=lambda e: usb.util.endpoint_direction(e.bEndpointAddress) == usb.util.ENDPOINT_OUT
                )
                self.usb_endpoint_in = usb.util.find_descriptor(
                    intf,
                    custom_match=lambda e: usb.util.endpoint_direction(e.bEndpointAddress) == usb.util.ENDPOINT_IN
                )

                if self.usb_endpoint_out and self.usb_endpoint_in:
                    self.usb_device = device
                    logger.info("USB device configured successfully")
                    consecutive_errors = 0
                    self._handle_connection()
                    # If we get here, connection ended normally
                else:
                    logger.warning("Could not find USB endpoints")
                    consecutive_errors += 1

            except Exception as e:
                error_msg = str(e)
                if error_msg != last_error_message:
                    logger.error(f"USB error: {error_msg}")
                    last_error_message = error_msg

                consecutive_errors += 1

                if "not supported" in error_msg.lower() or "unimplemented" in error_msg.lower():
                    # This is a Windows driver issue - likely won't resolve itself
                    logger.warning(f"USB driver incompatibility detected ({consecutive_errors}/{max_consecutive_errors})")
                    if consecutive_errors >= max_consecutive_errors:
                        logger.error("USB mode failed due to Windows driver incompatibility")
                        self._print_usb_driver_error_guide()
                        return False

                time.sleep(1)

        if consecutive_errors >= max_consecutive_errors:
            logger.error("USB mode failed after repeated errors")
            return False

        return True

    def _print_usb_driver_error_guide(self) -> None:
        """Print guidance when USB driver error is detected."""
        print()
        print("=" * 60)
        print("USB DRIVER INCOMPATIBILITY DETECTED")
        print("=" * 60)
        print()
        print("Windows is using a driver that prevents direct USB access.")
        print("This is a known limitation on Windows with libusb.")
        print()
        print("SOLUTION: Use ADB mode instead (recommended)")
        print("-" * 40)
        print("1. Download Android Platform Tools:")
        print("   https://developer.android.com/tools/releases/platform-tools")
        print("2. Extract adb.exe to the same folder as this server")
        print("3. Enable USB debugging on your phone")
        print("4. Restart this server")
        print()
        print("ALTERNATIVE: Replace USB driver with Zadig")
        print("-" * 40)
        print("WARNING: This may disable file transfer (MTP) mode!")
        print("1. Download Zadig: https://zadig.akeo.ie/")
        print("2. Connect your phone")
        print("3. In Zadig, select your phone and install WinUSB driver")
        print()
        print("=" * 60)
        print()
        logger.info("USB driver error guidance printed to console")

    def _start_adb_mode(self) -> None:
        """Start in ADB mode with automatic port forwarding."""
        logger.info("Starting in ADB mode")

        # Set up ADB connection
        success, message = auto_setup_adb_forwarding(5555, 5555)

        if success:
            logger.info(f"ADB setup successful: {message}")
            self._start_tcp_server()
        else:
            logger.error(f"ADB setup failed: {message}")
            logger.info("To use ADB mode:")
            logger.info("  1. Enable USB debugging on your Android device")
            logger.info("  2. Connect your device via USB cable")
            logger.info("  3. Accept the USB debugging prompt on your phone")
            logger.info("")
            logger.info("Alternatively, use --simulate mode for manual setup")
            logger.info("Falling back to simulation mode...")
            self._start_simulation_mode()

    def _start_tcp_server(self) -> None:
        """Start TCP server (used by both ADB and simulation modes)."""
        import socket

        logger.info("Starting TCP server on port 5555")
        self.sim_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.sim_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        self.sim_socket.bind(("127.0.0.1", 5555))  # Bind to localhost only for ADB
        self.sim_socket.listen(1)

        logger.info("Waiting for Android app to connect...")

        while self.running:
            try:
                self.sim_conn, addr = self.sim_socket.accept()
                logger.info(f"Connection from {addr}")
                self._handle_connection()
            except Exception as e:
                logger.error(f"Connection error: {e}")

    def stop(self) -> None:
        """Stop the USB server."""
        self.running = False
        if self.sim_conn:
            self.sim_conn.close()
        if self.sim_socket:
            self.sim_socket.close()

    def _start_simulation_mode(self) -> None:
        """Start in simulation mode using TCP socket (manual ADB setup required)."""
        import socket

        logger.info("Starting in simulation mode (TCP)")
        self.sim_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.sim_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        self.sim_socket.bind(("0.0.0.0", 5555))  # Bind to all interfaces
        self.sim_socket.listen(1)

        logger.info("Listening on port 5555 (all interfaces)...")
        logger.info("For USB connection, run: adb forward tcp:5555 tcp:5555")
        logger.info("For Wi-Fi, connect to this PC's IP address on port 5555")

        while self.running:
            try:
                self.sim_conn, addr = self.sim_socket.accept()
                logger.info(f"Connection from {addr}")
                self._handle_connection()
            except Exception as e:
                logger.error(f"Connection error: {e}")

    def _start_usb_mode(self) -> None:
        """Start in USB mode using pyusb."""
        try:
            import usb.core
            import usb.util
        except ImportError:
            logger.error("pyusb not installed. Run: pip install pyusb")
            logger.info("Falling back to simulation mode...")
            self.simulate = True
            self._start_simulation_mode()
            return

        # Setup libusb backend (especially important for frozen exe)
        setup_libusb_backend()

        logger.info("Starting in USB mode")
        logger.info("Waiting for USB device...")

        # Test if USB backend is available before entering the main loop
        try:
            # This will raise NoBackendError if libusb is not available
            test_device = usb.core.find()
            logger.debug("USB backend is available")
        except usb.core.NoBackendError as e:
            logger.error("=" * 60)
            logger.error("USB BACKEND ERROR: No libusb backend available!")
            logger.error("=" * 60)
            logger.error("")
            logger.error("This error occurs when the libusb library is not installed")
            logger.error("or cannot be found by the application.")
            logger.error("")
            logger.error("SOLUTIONS:")
            logger.error("1. Install libusb using Zadig (https://zadig.akeo.ie/)")
            logger.error("2. Or place libusb-1.0.dll in the same folder as this exe")
            logger.error("3. Or run with --simulate flag to use TCP mode instead")
            logger.error("")
            logger.error(f"Technical details: {e}")
            logger.error("=" * 60)
            logger.info("")
            logger.info("Falling back to simulation mode (TCP)...")
            self.simulate = True
            self._start_simulation_mode()
            return

        # Track last log message to avoid spamming
        last_search_log_time = 0
        search_log_interval = 30  # Log search status every 30 seconds

        while self.running:
            # Find Android device - search for all known Android vendor IDs
            device = None
            found_vendor = None

            try:
                # First, try to find any known Android device
                for vendor_id, vendor_name in ANDROID_VENDOR_IDS.items():
                    device = usb.core.find(idVendor=vendor_id)
                    if device is not None:
                        found_vendor = vendor_name
                        break

                # If no known vendor found, list all connected USB devices for debugging
                current_time = time.time()
                if device is None and (current_time - last_search_log_time) >= search_log_interval:
                    last_search_log_time = current_time
                    all_devices = list(usb.core.find(find_all=True))
                    if all_devices:
                        logger.info(f"Searching for Android device... Found {len(all_devices)} USB device(s):")
                        for dev in all_devices:
                            vendor_name = ANDROID_VENDOR_IDS.get(dev.idVendor, "Unknown")
                            logger.info(f"  - Vendor: 0x{dev.idVendor:04X} ({vendor_name}), "
                                       f"Product: 0x{dev.idProduct:04X}")
                        logger.info("Hint: Make sure your Android device is connected and has USB debugging enabled")
                        logger.info("Hint: You may need to authorize USB debugging on your Android device")
                        logger.info("Hint: If using a non-standard device, try --simulate mode instead")
                    else:
                        logger.info("Searching for Android device... No USB devices found")
                        logger.info("Hint: Check USB cable connection and try a different port")

            except usb.core.NoBackendError:
                logger.error("Lost USB backend connection")
                break

            if device is None:
                time.sleep(1)
                continue

            logger.info(f"Found {found_vendor} device: VendorID=0x{device.idVendor:04X}, "
                       f"ProductID=0x{device.idProduct:04X}")

            try:
                # Detach kernel driver if necessary
                if device.is_kernel_driver_active(0):
                    device.detach_kernel_driver(0)

                device.set_configuration()
                cfg = device.get_active_configuration()
                intf = cfg[(0, 0)]

                # Find endpoints
                self.usb_endpoint_out = usb.util.find_descriptor(
                    intf,
                    custom_match=lambda e: usb.util.endpoint_direction(e.bEndpointAddress) == usb.util.ENDPOINT_OUT
                )
                self.usb_endpoint_in = usb.util.find_descriptor(
                    intf,
                    custom_match=lambda e: usb.util.endpoint_direction(e.bEndpointAddress) == usb.util.ENDPOINT_IN
                )

                if self.usb_endpoint_out and self.usb_endpoint_in:
                    self.usb_device = device
                    logger.info("USB device configured")
                    self._handle_connection()

            except Exception as e:
                logger.error(f"USB error: {e}")
                time.sleep(1)

    def _handle_connection(self) -> None:
        """Handle an active connection."""
        logger.info("Handling connection...")

        while self.running:
            try:
                # Receive packet
                data = self._receive_data()
                if not data:
                    continue

                packet = Packet.from_bytes(data)
                if not packet:
                    logger.warning("Invalid packet received")
                    continue

                # Handle packet
                response = self._handle_packet(packet)
                if response:
                    self._send_data(response.to_bytes())

            except ConnectionResetError:
                logger.info("Connection reset")
                break
            except Exception as e:
                logger.error(f"Error handling packet: {e}")

    def _receive_data(self) -> Optional[bytes]:
        """Receive data from the connection."""
        if self.simulate:
            try:
                # First receive header to get payload length
                header = self.sim_conn.recv(10)
                if len(header) < 10:
                    return None

                payload_len = int.from_bytes(header[6:10], 'little')
                remaining = payload_len + 4  # +4 for checksum

                data = header
                while remaining > 0:
                    chunk = self.sim_conn.recv(min(remaining, 4096))
                    if not chunk:
                        return None
                    data += chunk
                    remaining -= len(chunk)

                return data
            except socket.timeout:
                return None
        else:
            try:
                return self.usb_device.read(self.usb_endpoint_in, MAX_PACKET_SIZE, timeout=5000)
            except Exception:
                return None

    def _send_data(self, data: bytes) -> None:
        """Send data through the connection."""
        if self.simulate:
            self.sim_conn.sendall(data)
        else:
            self.usb_device.write(self.usb_endpoint_out, data, timeout=5000)

    def _handle_packet(self, packet: Packet) -> Optional[Packet]:
        """Handle a received packet and return response."""
        command = packet.command

        try:
            if command == Command.HANDSHAKE:
                return self._handle_handshake(packet)
            elif command == Command.LIST_DIR:
                return self._handle_list_dir(packet)
            elif command == Command.GET_FILE_INFO:
                return self._handle_get_file_info(packet)
            elif command == Command.READ_FILE:
                return self._handle_read_file(packet)
            elif command == Command.WRITE_FILE:
                return self._handle_write_file(packet)
            elif command == Command.CREATE_DIR:
                return self._handle_create_dir(packet)
            elif command == Command.DELETE:
                return self._handle_delete(packet)
            elif command == Command.RENAME:
                return self._handle_rename(packet)
            elif command == Command.SEARCH:
                return self._handle_search(packet)
            elif command == Command.GET_DRIVES:
                return self._handle_get_drives(packet)
            elif command == Command.GET_STORAGE_INFO:
                return self._handle_get_storage_info(packet)
            elif command == Command.DISCONNECT:
                logger.info("Disconnect requested")
                return Packet(command=Command.RESPONSE_OK)
            else:
                return self._error_response(ErrorCode.UNKNOWN_COMMAND, f"Unknown command: {command}")

        except FileNotFoundError as e:
            return self._error_response(ErrorCode.FILE_NOT_FOUND, str(e))
        except PermissionError as e:
            return self._error_response(ErrorCode.PERMISSION_DENIED, str(e))
        except FileExistsError as e:
            return self._error_response(ErrorCode.ALREADY_EXISTS, str(e))
        except Exception as e:
            logger.exception("Error handling packet")
            return self._error_response(ErrorCode.IO_ERROR, str(e))

    def _handle_handshake(self, packet: Packet) -> Packet:
        """Handle handshake request."""
        client_info = packet.payload.decode("utf-8")
        logger.info(f"Handshake from: {client_info}")
        return Packet(
            command=Command.RESPONSE_OK,
            payload=b"PCEX-Server-1.0"
        )

    def _handle_list_dir(self, packet: Packet) -> Packet:
        """Handle list directory request."""
        path = PayloadSerializer.deserialize_path(packet.payload)
        logger.info(f"List directory: {path}")

        files = self.file_handler.list_directory(path)
        return Packet(
            command=Command.RESPONSE_DATA,
            payload=PayloadSerializer.serialize_file_list(files)
        )

    def _handle_get_file_info(self, packet: Packet) -> Packet:
        """Handle get file info request."""
        path = PayloadSerializer.deserialize_path(packet.payload)
        logger.info(f"Get file info: {path}")

        info = self.file_handler.get_file_info(path)
        return Packet(
            command=Command.RESPONSE_DATA,
            payload=PayloadSerializer.serialize_file_list([info])[:PayloadSerializer.serialize_file_list([info]).__len__() - 4]
            # Actually just send single item
        )

    def _handle_read_file(self, packet: Packet) -> Packet:
        """Handle read file request."""
        path, offset, length = PayloadSerializer.deserialize_file_read_request(packet.payload)
        logger.info(f"Read file: {path} (offset={offset}, length={length})")

        file_info = self.file_handler.get_file_info(path)
        total_size = file_info["size"]

        if length < 0:
            length = total_size

        # Send file in chunks
        current_offset = offset
        remaining = min(length, total_size - offset)

        while remaining > 0:
            chunk_size = min(CHUNK_SIZE, remaining)
            chunk_data = self.file_handler.read_file_chunk(path, current_offset, chunk_size)

            is_last = remaining <= chunk_size
            chunk_packet = Packet(
                command=Command.RESPONSE_FILE_CHUNK,
                flags=Flags.FINAL if is_last else Flags.CONTINUATION,
                payload=chunk_data
            )
            self._send_data(chunk_packet.to_bytes())

            current_offset += chunk_size
            remaining -= chunk_size

        return Packet(command=Command.RESPONSE_END)

    def _handle_write_file(self, packet: Packet) -> Packet:
        """Handle write file request."""
        path, total_size, chunk_size = PayloadSerializer.deserialize_file_write_header(packet.payload)
        logger.info(f"Write file: {path} (size={total_size})")

        # Send OK to begin receiving chunks
        self._send_data(Packet(command=Command.RESPONSE_OK).to_bytes())

        # Receive file chunks
        received = 0
        while received < total_size:
            chunk_data = self._receive_data()
            if not chunk_data:
                return self._error_response(ErrorCode.IO_ERROR, "Connection lost during transfer")

            chunk_packet = Packet.from_bytes(chunk_data)
            if not chunk_packet:
                return self._error_response(ErrorCode.PROTOCOL_ERROR, "Invalid chunk packet")

            self.file_handler.write_file_chunk(path, chunk_packet.payload, received, create=(received == 0))
            received += len(chunk_packet.payload)

            if chunk_packet.flags & Flags.FINAL:
                break

        logger.info(f"File written: {path} ({received} bytes)")
        return Packet(command=Command.RESPONSE_OK)

    def _handle_create_dir(self, packet: Packet) -> Packet:
        """Handle create directory request."""
        path = PayloadSerializer.deserialize_path(packet.payload)
        logger.info(f"Create directory: {path}")

        info = self.file_handler.create_directory(path)
        return Packet(
            command=Command.RESPONSE_DATA,
            payload=PayloadSerializer.serialize_file_item(**info)
        )

    def _handle_delete(self, packet: Packet) -> Packet:
        """Handle delete request."""
        path = PayloadSerializer.deserialize_path(packet.payload)
        logger.info(f"Delete: {path}")

        self.file_handler.delete(path)
        return Packet(command=Command.RESPONSE_OK)

    def _handle_rename(self, packet: Packet) -> Packet:
        """Handle rename request."""
        path, new_name = PayloadSerializer.deserialize_rename(packet.payload)
        logger.info(f"Rename: {path} -> {new_name}")

        info = self.file_handler.rename(path, new_name)
        return Packet(
            command=Command.RESPONSE_DATA,
            payload=PayloadSerializer.serialize_file_item(**info)
        )

    def _handle_search(self, packet: Packet) -> Packet:
        """Handle search request."""
        query, path = PayloadSerializer.deserialize_search(packet.payload)
        logger.info(f"Search: '{query}' in {path}")

        files = self.file_handler.search_files(query, path)
        return Packet(
            command=Command.RESPONSE_DATA,
            payload=PayloadSerializer.serialize_file_list(files)
        )

    def _handle_get_drives(self, packet: Packet) -> Packet:
        """Handle get drives request."""
        logger.info("Get drives")

        drives = self.file_handler.get_drives()
        return Packet(
            command=Command.RESPONSE_DATA,
            payload=PayloadSerializer.serialize_drive_list(drives)
        )

    def _handle_get_storage_info(self, packet: Packet) -> Packet:
        """Handle get storage info request."""
        path = PayloadSerializer.deserialize_path(packet.payload) if packet.payload else ""
        logger.info(f"Get storage info: {path}")

        total, free, drive, volume = self.file_handler.get_storage_info(path)
        return Packet(
            command=Command.RESPONSE_DATA,
            payload=PayloadSerializer.serialize_storage_info(total, free, drive, volume)
        )

    def _error_response(self, code: ErrorCode, message: str) -> Packet:
        """Create an error response packet."""
        logger.error(f"Error {code}: {message}")
        return Packet(
            command=Command.RESPONSE_ERROR,
            payload=PayloadSerializer.serialize_error(code, message)
        )


def main():
    parser = argparse.ArgumentParser(
        description="PC Explorer USB Server",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Connection Modes:
  --adb       Use ADB port forwarding (RECOMMENDED)
              Works with most Android devices without driver installation.
              Requires USB debugging enabled on the phone.

  --usb       Direct USB connection
              Requires proper USB drivers (WinUSB via Zadig).
              Not recommended for most users.

  --simulate  TCP simulation mode (manual setup)
              Requires manual ADB port forwarding or Wi-Fi connection.

Examples:
  pc-explorer-server.exe              # Auto-detect best mode
  pc-explorer-server.exe --adb        # Use ADB (recommended)
  pc-explorer-server.exe --simulate   # Manual TCP mode
"""
    )

    mode_group = parser.add_mutually_exclusive_group()
    mode_group.add_argument(
        "--adb", "-a",
        action="store_true",
        help="Use ADB mode with automatic port forwarding (recommended)"
    )
    mode_group.add_argument(
        "--usb", "-u",
        action="store_true",
        help="Use direct USB mode (requires driver setup)"
    )
    mode_group.add_argument(
        "--simulate", "-s",
        action="store_true",
        help="Run in simulation mode using TCP socket (manual setup)"
    )

    parser.add_argument(
        "--verbose", "-v",
        action="store_true",
        help="Enable verbose logging"
    )
    args = parser.parse_args()

    # Determine mode
    if args.adb:
        mode = 'adb'
    elif args.usb:
        mode = 'usb'
    elif args.simulate:
        mode = 'simulate'
    else:
        mode = 'auto'

    # Setup logging with file output
    log_file = setup_logging(verbose=args.verbose)

    # Log startup information
    logger.info("=" * 60)
    logger.info("PC Explorer USB Server - Starting")
    logger.info("=" * 60)
    logger.info(f"Startup time: {datetime.now().isoformat()}")
    logger.info(f"Python version: {sys.version}")
    logger.info(f"Platform: {sys.platform}")
    logger.info(f"Frozen (exe): {is_frozen()}")
    if is_frozen():
        logger.info(f"Executable: {sys.executable}")
        logger.info(f"Bundle dir: {sys._MEIPASS}")
    logger.info(f"Working directory: {os.getcwd()}")
    logger.info(f"Log file: {log_file}")
    logger.info(f"Mode: {mode}")
    logger.info(f"Verbose: {args.verbose}")

    # Log ADB detection status
    adb_path = find_adb_executable()
    if adb_path:
        logger.info(f"ADB found: {adb_path}")
    else:
        logger.warning("ADB not found in bundle or system PATH")
        logger.info("To enable ADB mode, download Android Platform Tools from:")
        logger.info("  https://developer.android.com/tools/releases/platform-tools")
        logger.info("Then place adb.exe next to this server or add it to PATH")

    logger.info("-" * 60)

    server = UsbServer(mode=mode)

    print("=" * 50)
    print("PC Explorer USB Server")
    print("=" * 50)
    print()

    if mode == 'simulate':
        print("Running in SIMULATION mode (TCP socket)")
        print()
        print("For USB connection (manual):")
        print("  1. Enable USB debugging on your Android device")
        print("  2. Connect phone to PC via USB")
        print("  3. Run: adb forward tcp:5555 tcp:5555")
        print("  4. Open the Android app and press Connect")
        print()
        print("For Wi-Fi connection:")
        print("  Connect to this PC's IP address on port 5555")
    elif mode == 'adb':
        print("Running in ADB mode (recommended)")
        print()
        print("Prerequisites:")
        print("  1. Enable USB debugging on your Android device")
        print("  2. Connect phone to PC via USB")
        print("  3. Accept the USB debugging prompt on your phone")
        print()
        print("The server will automatically set up port forwarding.")
    elif mode == 'usb':
        print("Running in USB mode")
        print()
        print("Supported manufacturers: Samsung, Google, Xiaomi, Huawei, OnePlus,")
        print("  LG, Sony, HTC, Motorola, ZTE, Vivo, OPPO, ASUS, Lenovo, and more")
        print()
        print("WARNING: USB direct mode has known limitations on Windows!")
        print("If connection fails, try ADB mode instead:")
        print("  pc-explorer-server.exe --adb")
    else:  # auto mode
        print("Running in AUTO mode")
        print()
        print("The server will automatically detect the best connection method:")
        print("  1. Try ADB port forwarding (if ADB is available)")
        print("  2. Try direct USB connection (if drivers are installed)")
        print("  3. Fall back to simulation mode (manual setup required)")
        print()
        print("For best results, enable USB debugging on your Android device.")

    print()
    print("Press Ctrl+C to stop the server")
    print()
    print(f"Log file: {log_file}")
    print()

    try:
        server.start()
    except KeyboardInterrupt:
        print("\nShutting down...")
        logger.info("Server shutdown requested by user (Ctrl+C)")
        server.stop()
    except Exception as e:
        logger.exception(f"Unexpected error: {e}")
        raise
    finally:
        logger.info("Server stopped")
        logger.info("=" * 60)


if __name__ == "__main__":
    main()

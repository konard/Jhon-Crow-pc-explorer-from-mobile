"""
ADB Helper Module

This module provides helpers for working with Android Debug Bridge (ADB).
It enables automatic port forwarding for portable deployment without
requiring manual ADB commands from the user.

Features:
- Auto-detect bundled ADB executable
- Check for ADB in system PATH
- Set up TCP port forwarding
- List connected Android devices
"""

import os
import sys
import subprocess
import shutil
import logging
from pathlib import Path
from typing import Optional, List, Tuple

logger = logging.getLogger(__name__)


def is_frozen() -> bool:
    """Check if running as a PyInstaller frozen executable."""
    return getattr(sys, 'frozen', False) and hasattr(sys, '_MEIPASS')


def get_bundle_dir() -> Path:
    """Get the bundle directory (works for both frozen exe and script)."""
    if is_frozen():
        return Path(sys._MEIPASS)
    return Path(__file__).parent


def find_adb_executable() -> Optional[Path]:
    """
    Find the ADB executable.

    Search order:
    1. Bundled with the application (in the same folder as exe)
    2. Next to the executable (for non-bundled deployment)
    3. In system PATH

    Returns:
        Path to adb executable, or None if not found.
    """
    adb_names = ['adb.exe'] if sys.platform == 'win32' else ['adb']

    # Search locations
    search_paths = [
        get_bundle_dir(),                          # PyInstaller bundle
        get_bundle_dir() / 'adb',                  # adb subfolder in bundle
        get_bundle_dir() / 'platform-tools',       # platform-tools subfolder
    ]

    if is_frozen():
        # Also check next to the exe file
        exe_dir = Path(sys.executable).parent
        search_paths.extend([
            exe_dir,
            exe_dir / 'adb',
            exe_dir / 'platform-tools',
        ])

    # Search in specified paths
    for search_path in search_paths:
        for adb_name in adb_names:
            adb_path = search_path / adb_name
            if adb_path.exists():
                logger.debug(f"Found bundled ADB at: {adb_path}")
                return adb_path

    # Fall back to system PATH
    system_adb = shutil.which('adb')
    if system_adb:
        logger.debug(f"Found ADB in system PATH: {system_adb}")
        return Path(system_adb)

    logger.debug("ADB not found in any location")
    return None


def run_adb_command(adb_path: Path, args: List[str], timeout: int = 10) -> Tuple[bool, str, str]:
    """
    Run an ADB command and return the result.

    Args:
        adb_path: Path to the ADB executable.
        args: List of arguments to pass to ADB.
        timeout: Command timeout in seconds.

    Returns:
        Tuple of (success, stdout, stderr)
    """
    try:
        cmd = [str(adb_path)] + args
        logger.debug(f"Running ADB command: {' '.join(cmd)}")

        result = subprocess.run(
            cmd,
            capture_output=True,
            text=True,
            timeout=timeout,
            creationflags=subprocess.CREATE_NO_WINDOW if sys.platform == 'win32' else 0
        )

        return result.returncode == 0, result.stdout.strip(), result.stderr.strip()
    except subprocess.TimeoutExpired:
        logger.warning(f"ADB command timed out after {timeout}s")
        return False, "", "Command timed out"
    except Exception as e:
        logger.error(f"Error running ADB command: {e}")
        return False, "", str(e)


def check_adb_server(adb_path: Path) -> bool:
    """
    Check if ADB server is running, start it if not.

    Args:
        adb_path: Path to the ADB executable.

    Returns:
        True if ADB server is accessible, False otherwise.
    """
    success, stdout, stderr = run_adb_command(adb_path, ['start-server'], timeout=30)
    if not success:
        logger.warning(f"Failed to start ADB server: {stderr}")
        return False
    return True


def list_devices(adb_path: Path) -> List[dict]:
    """
    List connected Android devices.

    Args:
        adb_path: Path to the ADB executable.

    Returns:
        List of device dictionaries with 'serial' and 'state' keys.
    """
    success, stdout, stderr = run_adb_command(adb_path, ['devices'], timeout=10)

    if not success:
        logger.warning(f"Failed to list devices: {stderr}")
        return []

    devices = []
    lines = stdout.strip().split('\n')

    for line in lines[1:]:  # Skip "List of devices attached" header
        line = line.strip()
        if line and '\t' in line:
            parts = line.split('\t')
            if len(parts) >= 2:
                devices.append({
                    'serial': parts[0],
                    'state': parts[1]
                })

    return devices


def setup_port_forward(adb_path: Path, local_port: int = 5555, remote_port: int = 5555) -> bool:
    """
    Set up TCP port forwarding from PC to Android device.

    This creates a tunnel so that connections to localhost:local_port on the PC
    are forwarded to the Android device's remote_port.

    Args:
        adb_path: Path to the ADB executable.
        local_port: Port on the PC to listen on.
        remote_port: Port on the Android device to forward to.

    Returns:
        True if port forwarding was set up successfully, False otherwise.
    """
    # First, check if there are any devices
    devices = list_devices(adb_path)
    if not devices:
        logger.warning("No Android devices connected for port forwarding")
        return False

    # Filter for devices that are ready (state == 'device')
    ready_devices = [d for d in devices if d['state'] == 'device']
    if not ready_devices:
        logger.warning(f"No devices ready for port forwarding. Device states: {devices}")
        return False

    # Set up port forward
    success, stdout, stderr = run_adb_command(
        adb_path,
        ['forward', f'tcp:{local_port}', f'tcp:{remote_port}'],
        timeout=10
    )

    if success:
        logger.info(f"ADB port forwarding set up: localhost:{local_port} -> device:{remote_port}")
        return True
    else:
        logger.warning(f"Failed to set up port forwarding: {stderr}")
        return False


def setup_reverse_forward(adb_path: Path, remote_port: int = 5555, local_port: int = 5555) -> bool:
    """
    Set up TCP reverse forwarding from Android device to PC.

    This creates a tunnel so that connections to localhost:remote_port on the
    Android device are forwarded to localhost:local_port on the PC.

    This is the preferred method for ADB mode because:
    - The PC server binds to local_port first
    - Then adb reverse makes the phone's remote_port forward to PC's local_port
    - This avoids port conflicts (unlike adb forward, which also binds to the port)

    Args:
        adb_path: Path to the ADB executable.
        remote_port: Port on the Android device to listen on.
        local_port: Port on the PC to forward to.

    Returns:
        True if reverse forwarding was set up successfully, False otherwise.
    """
    # First, check if there are any devices
    devices = list_devices(adb_path)
    if not devices:
        logger.warning("No Android devices connected for reverse forwarding")
        return False

    # Filter for devices that are ready (state == 'device')
    ready_devices = [d for d in devices if d['state'] == 'device']
    if not ready_devices:
        logger.warning(f"No devices ready for reverse forwarding. Device states: {devices}")
        return False

    # Set up reverse forward
    success, stdout, stderr = run_adb_command(
        adb_path,
        ['reverse', f'tcp:{remote_port}', f'tcp:{local_port}'],
        timeout=10
    )

    if success:
        logger.info(f"ADB reverse forwarding set up: device:{remote_port} -> localhost:{local_port}")
        return True
    else:
        logger.warning(f"Failed to set up reverse forwarding: {stderr}")
        return False


def remove_reverse_forward(adb_path: Path, remote_port: int = 5555) -> bool:
    """
    Remove a previously set up reverse forwarding.

    Args:
        adb_path: Path to the ADB executable.
        remote_port: The remote port to remove forwarding from.

    Returns:
        True if forwarding was removed, False otherwise.
    """
    success, stdout, stderr = run_adb_command(
        adb_path,
        ['reverse', '--remove', f'tcp:{remote_port}'],
        timeout=10
    )

    if success:
        logger.info(f"Removed reverse forwarding on device port {remote_port}")
    return success


def remove_port_forward(adb_path: Path, local_port: int = 5555) -> bool:
    """
    Remove a previously set up port forwarding.

    Args:
        adb_path: Path to the ADB executable.
        local_port: The local port to remove forwarding from.

    Returns:
        True if forwarding was removed, False otherwise.
    """
    success, stdout, stderr = run_adb_command(
        adb_path,
        ['forward', '--remove', f'tcp:{local_port}'],
        timeout=10
    )

    if success:
        logger.info(f"Removed port forwarding on port {local_port}")
    return success


def cleanup_adb_port(adb_path: Path, port: int = 5555) -> bool:
    """
    Clean up any existing ADB port forwarding on a specific port.

    This function removes any existing 'adb forward' rules that may be
    occupying the port, allowing the TCP server to bind to it.

    This is important because:
    - 'adb forward tcp:5555 tcp:5555' causes ADB to listen on port 5555
    - If this is active, the Python server cannot bind to port 5555
    - Calling this cleanup before starting the server frees the port

    Args:
        adb_path: Path to the ADB executable.
        port: The port to clean up.

    Returns:
        True if cleanup was performed (or no cleanup needed), False on error.
    """
    try:
        # List all current forwards
        success, stdout, stderr = run_adb_command(adb_path, ['forward', '--list'], timeout=10)

        if not success:
            logger.debug(f"Could not list forwards: {stderr}")
            return True  # If we can't list, just continue

        # Check if our port is in the list
        port_pattern = f'tcp:{port}'
        if port_pattern in stdout:
            logger.info(f"Found existing ADB forward on port {port}, removing it...")
            remove_success = remove_port_forward(adb_path, port)
            if remove_success:
                logger.info(f"Successfully removed existing forward on port {port}")
                return True
            else:
                logger.warning(f"Failed to remove existing forward on port {port}")
                return False
        else:
            logger.debug(f"No existing ADB forward found on port {port}")
            return True

    except Exception as e:
        logger.warning(f"Error during ADB port cleanup: {e}")
        return True  # Continue anyway, let the server try to bind


def get_device_model(adb_path: Path, serial: Optional[str] = None) -> Optional[str]:
    """
    Get the model name of the connected device.

    Args:
        adb_path: Path to the ADB executable.
        serial: Device serial number (optional, uses first device if not specified).

    Returns:
        Device model string, or None if unavailable.
    """
    args = ['shell', 'getprop', 'ro.product.model']
    if serial:
        args = ['-s', serial] + args

    success, stdout, stderr = run_adb_command(adb_path, args, timeout=5)

    if success and stdout:
        return stdout.strip()
    return None


class AdbConnection:
    """
    Manager for ADB-based TCP connection.

    This class handles the lifecycle of an ADB port-forwarded connection,
    including setup, monitoring, and cleanup.
    """

    def __init__(self, local_port: int = 5555, remote_port: int = 5555):
        self.local_port = local_port
        self.remote_port = remote_port
        self.adb_path: Optional[Path] = None
        self.is_connected = False
        self.device_serial: Optional[str] = None
        self.device_model: Optional[str] = None

    def initialize(self) -> bool:
        """
        Initialize ADB connection manager.

        Returns:
            True if ADB is available and ready, False otherwise.
        """
        self.adb_path = find_adb_executable()
        if not self.adb_path:
            logger.warning("ADB executable not found")
            return False

        logger.info(f"Using ADB from: {self.adb_path}")

        # Start ADB server
        if not check_adb_server(self.adb_path):
            return False

        return True

    def wait_for_device(self, timeout: int = 30) -> bool:
        """
        Wait for an Android device to be connected.

        Args:
            timeout: Maximum time to wait in seconds.

        Returns:
            True if a device is connected and ready, False if timeout.
        """
        if not self.adb_path:
            return False

        import time
        start_time = time.time()

        while time.time() - start_time < timeout:
            devices = list_devices(self.adb_path)
            ready = [d for d in devices if d['state'] == 'device']

            if ready:
                self.device_serial = ready[0]['serial']
                self.device_model = get_device_model(self.adb_path, self.device_serial)
                logger.info(f"Device ready: {self.device_model} ({self.device_serial})")
                return True

            time.sleep(1)

        return False

    def setup_forwarding(self) -> bool:
        """
        Set up port forwarding to the connected device.

        Returns:
            True if forwarding is set up, False otherwise.
        """
        if not self.adb_path:
            return False

        if setup_port_forward(self.adb_path, self.local_port, self.remote_port):
            self.is_connected = True
            return True
        return False

    def cleanup(self):
        """Clean up port forwarding."""
        if self.adb_path and self.is_connected:
            remove_port_forward(self.adb_path, self.local_port)
            self.is_connected = False


def check_adb_device_ready(local_port: int = 5555, remote_port: int = 5555) -> Tuple[bool, str, Optional[Path]]:
    """
    Check if ADB is available and a device is ready for connection.

    This function does NOT set up port forwarding - it only checks readiness.
    Use this to verify ADB is available before starting the TCP server.

    Args:
        local_port: Local port on PC (for informational purposes).
        remote_port: Remote port on Android device (for informational purposes).

    Returns:
        Tuple of (success, message, adb_path)
    """
    # Find ADB
    adb_path = find_adb_executable()
    if not adb_path:
        return False, "ADB not found. Please install Android SDK Platform Tools.", None

    logger.info(f"Using ADB: {adb_path}")

    # Start ADB server
    if not check_adb_server(adb_path):
        return False, "Failed to start ADB server.", adb_path

    # Check for devices
    devices = list_devices(adb_path)
    if not devices:
        return False, "No Android devices found. Connect your device and enable USB debugging.", adb_path

    ready_devices = [d for d in devices if d['state'] == 'device']
    if not ready_devices:
        unauthorized = [d for d in devices if d['state'] == 'unauthorized']
        if unauthorized:
            return False, "Device connected but not authorized. Check your phone for USB debugging prompt.", adb_path
        return False, f"Device not ready. Current states: {devices}", adb_path

    # Get device info
    device_model = get_device_model(adb_path, ready_devices[0]['serial'])
    device_info = device_model or ready_devices[0]['serial']

    return True, f"Device ready: {device_info}", adb_path


def auto_setup_adb_reverse(adb_path: Path, local_port: int = 5555, remote_port: int = 5555) -> Tuple[bool, str]:
    """
    Set up ADB reverse forwarding after the TCP server is already listening.

    This should be called AFTER the TCP server has bound to local_port.
    It sets up reverse forwarding so the Android device's remote_port
    forwards to the PC's local_port.

    Args:
        adb_path: Path to the ADB executable.
        local_port: Local port on PC (server is listening on this).
        remote_port: Remote port on Android device.

    Returns:
        Tuple of (success, message)
    """
    # Check for devices (they should still be connected)
    devices = list_devices(adb_path)
    ready_devices = [d for d in devices if d['state'] == 'device']

    if not ready_devices:
        return False, "Device disconnected. Please reconnect and try again."

    # Get device info
    device_model = get_device_model(adb_path, ready_devices[0]['serial'])
    device_info = device_model or ready_devices[0]['serial']

    # Set up reverse forwarding
    if setup_reverse_forward(adb_path, remote_port, local_port):
        return True, f"ADB reverse forwarding ready for {device_info}"

    return False, "Failed to set up reverse forwarding."


def auto_setup_adb_forwarding(local_port: int = 5555, remote_port: int = 5555) -> Tuple[bool, str]:
    """
    Automatically set up ADB port forwarding if possible.

    NOTE: This function uses 'adb forward' which causes port conflicts!
    The PC server cannot bind to the same port that ADB forward uses.

    For new code, use check_adb_device_ready() + auto_setup_adb_reverse() instead:
    1. Call check_adb_device_ready() to verify device is connected
    2. Start TCP server on local_port
    3. Call auto_setup_adb_reverse() to set up reverse forwarding

    This function is kept for backwards compatibility but is deprecated.

    Args:
        local_port: Local port on PC.
        remote_port: Remote port on Android device.

    Returns:
        Tuple of (success, message)
    """
    # Find ADB
    adb_path = find_adb_executable()
    if not adb_path:
        return False, "ADB not found. Please install Android SDK Platform Tools."

    logger.info(f"Using ADB: {adb_path}")

    # Start ADB server
    if not check_adb_server(adb_path):
        return False, "Failed to start ADB server."

    # Check for devices
    devices = list_devices(adb_path)
    if not devices:
        return False, "No Android devices found. Connect your device and enable USB debugging."

    ready_devices = [d for d in devices if d['state'] == 'device']
    if not ready_devices:
        unauthorized = [d for d in devices if d['state'] == 'unauthorized']
        if unauthorized:
            return False, "Device connected but not authorized. Check your phone for USB debugging prompt."
        return False, f"Device not ready. Current states: {devices}"

    # Get device info
    device_model = get_device_model(adb_path, ready_devices[0]['serial'])
    device_info = device_model or ready_devices[0]['serial']

    # Set up forwarding
    if setup_port_forward(adb_path, local_port, remote_port):
        return True, f"ADB forwarding ready for {device_info}"

    return False, "Failed to set up port forwarding."

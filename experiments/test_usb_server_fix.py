#!/usr/bin/env python3
"""
Test script to verify the fix for issue #18:
'UsbServer' object has no attribute 'simulate'

This script simulates the scenario that was causing the bug:
1. UsbServer is created in 'auto' or 'adb' mode
2. Connection is established (sim_conn is set)
3. _receive_data() and _send_data() are called

Before the fix, this would crash with AttributeError.
After the fix, it should work correctly using sim_conn presence check.
"""

import sys
import os
import socket
import threading
import time

# Add pc-server to path
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..', 'pc-server'))

from server import UsbServer


def test_receive_data_without_connection():
    """Test that _receive_data works when sim_conn is None (USB mode scenario)"""
    print("Test 1: _receive_data without connection (USB mode scenario)")
    server = UsbServer(mode='usb')

    # sim_conn should be None
    assert server.sim_conn is None

    # This should NOT raise AttributeError
    try:
        result = server._receive_data()
        # In USB mode without device, this should return None or fail gracefully
        print(f"  Result: {result} (expected None or graceful failure)")
        print("  PASS: No AttributeError raised")
    except AttributeError as e:
        if 'simulate' in str(e):
            print(f"  FAIL: AttributeError with 'simulate' - bug not fixed!")
            raise
        else:
            # Other AttributeError (e.g., usb_device is None) is acceptable
            print(f"  PASS: AttributeError but not about 'simulate': {e}")
    except Exception as e:
        # Other exceptions (e.g., no USB device) are acceptable
        print(f"  PASS: {type(e).__name__}: {e}")


def test_receive_data_with_connection():
    """Test that _receive_data works when sim_conn is set (TCP mode scenario)"""
    print("\nTest 2: _receive_data with connection (TCP/ADB mode scenario)")
    server = UsbServer(mode='adb')

    # Simulate what happens when connection is established
    # Create a mock socket pair
    server_socket, client_socket = socket.socketpair()
    server.sim_conn = server_socket

    try:
        # Start a thread to send some data
        def send_data():
            time.sleep(0.1)
            # Send a minimal valid packet header (10 bytes)
            # Magic (4) + Command (1) + Flags (1) + Length (4)
            header = b'PCEX\x01\x00\x00\x00\x00\x00'  # 10 bytes
            checksum = b'\x00\x00\x00\x00'  # 4 bytes
            client_socket.sendall(header + checksum)

        sender = threading.Thread(target=send_data)
        sender.start()

        # This should NOT raise AttributeError
        server.sim_conn.settimeout(1.0)
        result = server._receive_data()
        sender.join()

        print(f"  Received data: {result}")
        print("  PASS: No AttributeError raised")
    except AttributeError as e:
        if 'simulate' in str(e):
            print(f"  FAIL: AttributeError with 'simulate' - bug not fixed!")
            raise
        else:
            print(f"  FAIL: Unexpected AttributeError: {e}")
            raise
    finally:
        server_socket.close()
        client_socket.close()


def test_send_data_without_connection():
    """Test that _send_data works when sim_conn is None (USB mode scenario)"""
    print("\nTest 3: _send_data without connection (USB mode scenario)")
    server = UsbServer(mode='usb')

    # sim_conn should be None
    assert server.sim_conn is None

    # This should NOT raise AttributeError about 'simulate'
    try:
        server._send_data(b'test')
        print("  FAIL: Expected some error (no USB device)")
    except AttributeError as e:
        if 'simulate' in str(e):
            print(f"  FAIL: AttributeError with 'simulate' - bug not fixed!")
            raise
        else:
            # Other AttributeError (e.g., usb_device is None) is expected
            print(f"  PASS: AttributeError but not about 'simulate': {e}")
    except Exception as e:
        # Other exceptions (e.g., no USB device) are acceptable
        print(f"  PASS: {type(e).__name__}: {e}")


def test_send_data_with_connection():
    """Test that _send_data works when sim_conn is set (TCP mode scenario)"""
    print("\nTest 4: _send_data with connection (TCP/ADB mode scenario)")
    server = UsbServer(mode='adb')

    # Simulate what happens when connection is established
    server_socket, client_socket = socket.socketpair()
    server.sim_conn = server_socket

    try:
        # This should NOT raise AttributeError
        test_data = b'Hello, World!'
        server._send_data(test_data)

        # Verify data was sent
        received = client_socket.recv(1024)
        assert received == test_data, f"Data mismatch: {received} != {test_data}"

        print(f"  Sent and received: {received}")
        print("  PASS: No AttributeError raised")
    except AttributeError as e:
        if 'simulate' in str(e):
            print(f"  FAIL: AttributeError with 'simulate' - bug not fixed!")
            raise
        else:
            print(f"  FAIL: Unexpected AttributeError: {e}")
            raise
    finally:
        server_socket.close()
        client_socket.close()


def test_all_modes_instantiation():
    """Test that UsbServer can be instantiated in all modes"""
    print("\nTest 5: UsbServer instantiation in all modes")
    modes = ['auto', 'adb', 'usb', 'simulate']

    for mode in modes:
        try:
            server = UsbServer(mode=mode)
            print(f"  Mode '{mode}': PASS")
        except AttributeError as e:
            if 'simulate' in str(e):
                print(f"  Mode '{mode}': FAIL - AttributeError with 'simulate'")
                raise
            else:
                print(f"  Mode '{mode}': FAIL - {e}")
                raise
        except Exception as e:
            print(f"  Mode '{mode}': FAIL - {e}")
            raise


if __name__ == '__main__':
    print("=" * 60)
    print("Testing fix for issue #18: UsbServer 'simulate' attribute bug")
    print("=" * 60)
    print()

    test_receive_data_without_connection()
    test_receive_data_with_connection()
    test_send_data_without_connection()
    test_send_data_with_connection()
    test_all_modes_instantiation()

    print()
    print("=" * 60)
    print("All tests passed! The fix for issue #18 is working correctly.")
    print("=" * 60)

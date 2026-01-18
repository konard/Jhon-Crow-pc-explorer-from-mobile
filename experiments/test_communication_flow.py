#!/usr/bin/env python3
"""
Test script to simulate the full communication flow between Android client and PC server.

This tests:
1. Handshake packet creation and parsing
2. Response packet creation and parsing
3. Simulated TCP communication
4. Checksum verification at each step
"""

import sys
import os
import threading
import socket
import time

# Add parent directory to path
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'pc-server'))

from protocol import Packet, Command, Flags, MAGIC, HEADER_SIZE


def simulate_kotlin_parse(data: bytes) -> dict:
    """
    Simulate how Kotlin parses a packet (from UsbPacket.fromBytes).

    This helps identify any parsing differences between Python and Kotlin.
    """
    import struct

    if len(data) < 14:
        return {"error": "Packet too small"}

    # Verify magic
    magic = data[:4]
    if magic != b"PCEX":
        return {"error": f"Invalid magic: {magic}"}

    command = data[4]
    flags = data[5]

    # Parse payload length (little endian)
    payload_length = struct.unpack("<I", data[6:10])[0]

    if len(data) < 14 + payload_length:
        return {"error": f"Incomplete payload: got {len(data)} bytes, need {14 + payload_length}"}

    payload = data[10:10 + payload_length]

    # Parse received checksum
    received_checksum = struct.unpack("<I", data[10 + payload_length:14 + payload_length])[0]

    # Calculate checksum (Kotlin style)
    data_for_checksum = data[:10 + payload_length]  # HEADER_SIZE - 4 + payloadLength = 10 + payloadLength
    calculated_checksum = kotlin_crc32(data_for_checksum)

    return {
        "magic": magic,
        "command": command,
        "flags": flags,
        "payload_length": payload_length,
        "payload": payload,
        "received_checksum": hex(received_checksum),
        "calculated_checksum": hex(calculated_checksum),
        "checksum_match": received_checksum == calculated_checksum,
    }


def kotlin_crc32(data: bytes) -> int:
    """Kotlin CRC32 implementation."""
    crc = 0xFFFFFFFF
    for byte in data:
        crc = crc ^ (byte & 0xFF)
        for _ in range(8):
            if crc & 1:
                crc = (crc >> 1) ^ 0xEDB88320
            else:
                crc = crc >> 1
    return (~crc) & 0xFFFFFFFF


def test_handshake_round_trip():
    """Test handshake packet creation and response parsing."""
    print("\n" + "=" * 60)
    print("Test: Handshake Round Trip")
    print("=" * 60)

    # 1. Create Android handshake packet (what Android sends)
    print("\n1. Creating Android handshake packet...")
    android_packet = Packet(
        command=Command.HANDSHAKE,
        payload=b"PCEX-Android-1.0"
    )
    android_bytes = android_packet.to_bytes()
    print(f"   Packet bytes: {android_bytes.hex()}")
    print(f"   Packet length: {len(android_bytes)} bytes")

    # 2. Verify Android packet can be parsed by Python (server side)
    print("\n2. Parsing handshake on server side (Python)...")
    parsed_by_python = Packet.from_bytes(android_bytes)
    if parsed_by_python:
        print(f"   Command: {parsed_by_python.command}")
        print(f"   Payload: {parsed_by_python.payload}")
        print("   Result: SUCCESS")
    else:
        print("   Result: FAILED")
        return False

    # 3. Create server response (what Python server sends back)
    print("\n3. Creating server response packet...")
    server_response = Packet(
        command=Command.RESPONSE_OK,
        payload=b"PCEX-Server-1.0"
    )
    response_bytes = server_response.to_bytes()
    print(f"   Response bytes: {response_bytes.hex()}")
    print(f"   Response length: {len(response_bytes)} bytes")

    # 4. Parse response as Kotlin would (Android side)
    print("\n4. Parsing response on Android side (Kotlin simulation)...")
    kotlin_parsed = simulate_kotlin_parse(response_bytes)
    print(f"   Magic: {kotlin_parsed.get('magic')}")
    print(f"   Command: {hex(kotlin_parsed.get('command', 0))}")
    print(f"   Payload: {kotlin_parsed.get('payload')}")
    print(f"   Received checksum: {kotlin_parsed.get('received_checksum')}")
    print(f"   Calculated checksum: {kotlin_parsed.get('calculated_checksum')}")
    print(f"   Checksum match: {kotlin_parsed.get('checksum_match')}")

    if kotlin_parsed.get("checksum_match"):
        print("\n   Result: SUCCESS - Kotlin can parse Python's response")
        return True
    else:
        print("\n   Result: FAILED - Checksum mismatch!")
        return False


def test_tcp_communication():
    """Test actual TCP communication between simulated client and server."""
    print("\n" + "=" * 60)
    print("Test: TCP Communication Simulation")
    print("=" * 60)

    port = 15555  # Use non-standard port to avoid conflicts
    result = {"server_ok": False, "client_ok": False, "error": None}

    def server_thread():
        """Simulated server."""
        try:
            server_sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            server_sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            server_sock.bind(("127.0.0.1", port))
            server_sock.listen(1)
            server_sock.settimeout(5)

            conn, addr = server_sock.accept()
            print(f"\n   Server: Connection from {addr}")

            # Receive handshake (simulating server's _receive_data)
            header = conn.recv(10)
            if len(header) < 10:
                result["error"] = "Server: Failed to receive header"
                return

            payload_len = int.from_bytes(header[6:10], 'little')
            remaining = payload_len + 4
            data = header
            while remaining > 0:
                chunk = conn.recv(min(remaining, 4096))
                if not chunk:
                    result["error"] = "Server: Connection closed during receive"
                    return
                data += chunk
                remaining -= len(chunk)

            print(f"   Server: Received {len(data)} bytes")

            # Parse and verify
            packet = Packet.from_bytes(data)
            if packet:
                print(f"   Server: Handshake from: {packet.payload.decode('utf-8')}")

                # Send response
                response = Packet(
                    command=Command.RESPONSE_OK,
                    payload=b"PCEX-Server-1.0"
                )
                response_bytes = response.to_bytes()
                conn.sendall(response_bytes)
                print(f"   Server: Sent response ({len(response_bytes)} bytes)")
                result["server_ok"] = True
            else:
                result["error"] = "Server: Failed to parse handshake"

            conn.close()
            server_sock.close()
        except Exception as e:
            result["error"] = f"Server error: {e}"

    def client_thread():
        """Simulated Android client."""
        time.sleep(0.1)  # Wait for server to start
        try:
            client_sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            client_sock.settimeout(5)
            client_sock.connect(("127.0.0.1", port))

            # Send handshake (simulating Android's sendData)
            handshake = Packet(
                command=Command.HANDSHAKE,
                payload=b"PCEX-Android-1.0"
            )
            handshake_bytes = handshake.to_bytes()
            client_sock.sendall(handshake_bytes)
            print(f"\n   Client: Sent handshake ({len(handshake_bytes)} bytes)")

            # Receive response (simulating Android's receiveData)
            header = client_sock.recv(10)
            if len(header) < 10:
                result["error"] = "Client: Failed to receive header"
                return

            payload_len = int.from_bytes(header[6:10], 'little')
            remaining = payload_len + 4
            data = header
            while remaining > 0:
                chunk = client_sock.recv(min(remaining, 4096))
                if not chunk:
                    result["error"] = "Client: Connection closed during receive"
                    return
                data += chunk
                remaining -= len(chunk)

            print(f"   Client: Received {len(data)} bytes")
            print(f"   Client: Response hex: {data.hex()}")

            # Parse as Kotlin would
            kotlin_parsed = simulate_kotlin_parse(data)
            print(f"   Client: Checksum match: {kotlin_parsed.get('checksum_match')}")

            if kotlin_parsed.get("checksum_match"):
                result["client_ok"] = True
                print(f"   Client: Response payload: {kotlin_parsed.get('payload')}")
            else:
                result["error"] = f"Client: Checksum mismatch - received {kotlin_parsed.get('received_checksum')}, calculated {kotlin_parsed.get('calculated_checksum')}"

            client_sock.close()
        except Exception as e:
            result["error"] = f"Client error: {e}"

    # Start threads
    server = threading.Thread(target=server_thread)
    client = threading.Thread(target=client_thread)

    server.start()
    client.start()

    server.join(timeout=10)
    client.join(timeout=10)

    print("\n   " + "-" * 40)
    if result["server_ok"] and result["client_ok"]:
        print("   Result: SUCCESS - Full communication flow works")
        return True
    else:
        print(f"   Result: FAILED - {result.get('error', 'Unknown error')}")
        return False


def test_socket_timeout_handling():
    """Test what happens when socket.timeout is not imported."""
    print("\n" + "=" * 60)
    print("Test: socket.timeout Exception Handling")
    print("=" * 60)

    # Check if socket.timeout can be caught
    print("\n1. Testing socket.timeout catch...")
    try:
        raise socket.timeout("Test timeout")
    except socket.timeout as e:
        print(f"   Caught socket.timeout: {e}")
        print("   Result: socket.timeout is importable via 'socket' module")

    # Test what happens in a scope where socket is not imported
    print("\n2. Testing scope without socket import...")

    def function_without_import():
        # This simulates the _receive_data method which doesn't have socket imported
        # but tries to catch socket.timeout
        try:
            # In real code, this would be: self.sim_conn.recv(10)
            raise TimeoutError("Simulated timeout")
        except TimeoutError:
            # This is what should happen - TimeoutError is the base class
            return "Caught TimeoutError"

    result = function_without_import()
    print(f"   Result: {result}")

    print("\n3. Analysis:")
    print("   - socket.timeout is a subclass of OSError (Python 3.3+)")
    print("   - If 'socket' is not imported, catching 'socket.timeout' will cause NameError")
    print("   - The server.py _receive_data uses 'socket.timeout' but socket is only imported locally")

    return True


def main():
    print("=" * 60)
    print("Communication Flow Test Suite")
    print("=" * 60)

    tests = [
        ("Handshake Round Trip", test_handshake_round_trip),
        ("TCP Communication", test_tcp_communication),
        ("Socket Timeout Handling", test_socket_timeout_handling),
    ]

    results = []
    for name, test_func in tests:
        try:
            result = test_func()
            results.append((name, result))
        except Exception as e:
            print(f"\nException in {name}: {e}")
            results.append((name, False))

    print("\n" + "=" * 60)
    print("Summary")
    print("=" * 60)
    for name, result in results:
        status = "PASSED" if result else "FAILED"
        print(f"  {name}: {status}")

    print()
    all_passed = all(r[1] for r in results)
    if all_passed:
        print("All tests passed!")
    else:
        print("Some tests failed. See above for details.")

    return all_passed


if __name__ == "__main__":
    sys.exit(0 if main() else 1)

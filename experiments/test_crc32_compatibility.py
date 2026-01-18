#!/usr/bin/env python3
"""
Test script to verify CRC32 compatibility between Python and Kotlin implementations.

The Kotlin implementation uses a manual CRC32 calculation that should match zlib.crc32.
This script tests both implementations with the same test data.
"""

import zlib
import struct


def kotlin_crc32(data: bytes) -> int:
    """
    Python implementation of the Kotlin CRC32 algorithm from UsbProtocol.kt.

    The algorithm uses the standard IEEE CRC-32 polynomial 0xEDB88320.
    """
    crc = 0xFFFFFFFF
    for byte in data:
        crc = crc ^ (byte & 0xFF)
        for _ in range(8):
            if crc & 1:
                crc = (crc >> 1) ^ 0xEDB88320
            else:
                crc = crc >> 1
    return (~crc) & 0xFFFFFFFF


def python_crc32(data: bytes) -> int:
    """
    Python zlib CRC32 calculation (as used in server's protocol.py).
    """
    return zlib.crc32(data) & 0xFFFFFFFF


def build_packet(command: int, flags: int, payload: bytes) -> bytes:
    """Build a packet according to the protocol."""
    data = bytearray()
    data.extend(b"PCEX")  # Magic
    data.append(command)   # Command
    data.append(flags)     # Flags
    data.extend(struct.pack("<I", len(payload)))  # Payload length (little endian)
    data.extend(payload)   # Payload

    # Calculate checksum over data so far
    checksum = python_crc32(bytes(data))
    data.extend(struct.pack("<I", checksum))  # Checksum (little endian)

    return bytes(data)


def parse_packet(data: bytes) -> dict:
    """Parse a packet and return its components with checksum verification."""
    if len(data) < 14:
        return {"error": "Packet too small"}

    magic = data[:4]
    command = data[4]
    flags = data[5]
    payload_length = struct.unpack("<I", data[6:10])[0]
    payload = data[10:10 + payload_length]
    received_checksum = struct.unpack("<I", data[10 + payload_length:14 + payload_length])[0]

    # Calculate checksums using both methods
    data_for_checksum = data[:10 + payload_length]
    python_checksum = python_crc32(data_for_checksum)
    kotlin_checksum = kotlin_crc32(data_for_checksum)

    return {
        "magic": magic,
        "command": command,
        "flags": flags,
        "payload_length": payload_length,
        "payload": payload,
        "received_checksum": received_checksum,
        "python_checksum": python_checksum,
        "kotlin_checksum": kotlin_checksum,
        "python_match": received_checksum == python_checksum,
        "kotlin_match": received_checksum == kotlin_checksum,
    }


def main():
    print("=" * 60)
    print("CRC32 Compatibility Test")
    print("=" * 60)
    print()

    # Test 1: Basic CRC32 calculation
    print("Test 1: Basic CRC32 calculation")
    print("-" * 40)

    test_vectors = [
        b"",
        b"hello",
        b"PCEX",
        b"\x00\x01\x02\x03",
        b"The quick brown fox jumps over the lazy dog",
    ]

    all_match = True
    for data in test_vectors:
        py_crc = python_crc32(data)
        kt_crc = kotlin_crc32(data)
        match = "MATCH" if py_crc == kt_crc else "MISMATCH"
        if py_crc != kt_crc:
            all_match = False
        print(f"  Data: {data[:20]}{'...' if len(data) > 20 else ''}")
        print(f"    Python: 0x{py_crc:08X}")
        print(f"    Kotlin: 0x{kt_crc:08X}")
        print(f"    Status: {match}")
        print()

    print(f"Test 1 Result: {'PASSED' if all_match else 'FAILED'}")
    print()

    # Test 2: Handshake response packet
    print("Test 2: Handshake Response Packet (RESPONSE_OK)")
    print("-" * 40)

    # Build the exact packet the server sends after handshake
    command = 0x80  # RESPONSE_OK
    flags = 0x00    # NONE
    payload = b"PCEX-Server-1.0"

    packet = build_packet(command, flags, payload)
    print(f"  Packet hex: {packet.hex()}")
    print(f"  Packet length: {len(packet)} bytes")

    parsed = parse_packet(packet)
    print(f"  Magic: {parsed['magic']}")
    print(f"  Command: 0x{parsed['command']:02X}")
    print(f"  Flags: 0x{parsed['flags']:02X}")
    print(f"  Payload length: {parsed['payload_length']}")
    print(f"  Payload: {parsed['payload']}")
    print(f"  Received checksum: 0x{parsed['received_checksum']:08X}")
    print(f"  Python checksum:   0x{parsed['python_checksum']:08X}")
    print(f"  Kotlin checksum:   0x{parsed['kotlin_checksum']:08X}")
    print(f"  Python match: {parsed['python_match']}")
    print(f"  Kotlin match: {parsed['kotlin_match']}")
    print()

    if parsed['python_match'] and parsed['kotlin_match']:
        print("Test 2 Result: PASSED")
    else:
        print("Test 2 Result: FAILED")
    print()

    # Test 3: Simulate what Android receives
    print("Test 3: Simulated Android Parsing")
    print("-" * 40)

    # The Kotlin code calculates checksum over: HEADER_SIZE - 4 + payloadLength = 10 + payloadLength
    # Let's verify this matches what Python sends
    header_size = 14  # As defined in Kotlin
    payload_length = len(payload)

    # Kotlin's dataForChecksum range
    kotlin_checksum_range_end = header_size - 4 + payload_length  # = 10 + payload_length
    print(f"  HEADER_SIZE (Kotlin): {header_size}")
    print(f"  Payload length: {payload_length}")
    print(f"  Kotlin checksum range: data[0:{kotlin_checksum_range_end}]")

    # Python's checksum range (from protocol.py)
    python_checksum_range_end = 10 + payload_length
    print(f"  Python checksum range: data[0:{python_checksum_range_end}]")

    if kotlin_checksum_range_end == python_checksum_range_end:
        print("  Checksum ranges: MATCH")
    else:
        print("  Checksum ranges: MISMATCH!")
        print("  THIS COULD CAUSE CHECKSUM ERRORS!")
    print()

    # Test 4: Edge case - empty payload
    print("Test 4: Empty Payload Packet")
    print("-" * 40)

    empty_packet = build_packet(0x80, 0x00, b"")
    parsed_empty = parse_packet(empty_packet)
    print(f"  Packet hex: {empty_packet.hex()}")
    print(f"  Python match: {parsed_empty['python_match']}")
    print(f"  Kotlin match: {parsed_empty['kotlin_match']}")

    if parsed_empty['python_match'] and parsed_empty['kotlin_match']:
        print("Test 4 Result: PASSED")
    else:
        print("Test 4 Result: FAILED")
    print()

    print("=" * 60)
    print("Summary")
    print("=" * 60)
    print()
    print("If all tests pass, CRC32 is compatible between Python and Kotlin.")
    print("The 'Checksum mismatch' error may be caused by:")
    print("  1. Network/transmission errors corrupting packets")
    print("  2. Partial reads on the Android side")
    print("  3. Buffer overflow/underflow issues")
    print("  4. Endianness issues in specific fields")
    print()


if __name__ == "__main__":
    main()

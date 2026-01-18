"""
USB Protocol definitions for PC Explorer.
"""

import struct
from dataclasses import dataclass
from enum import IntEnum
from typing import Optional
import zlib


MAGIC = b"PCEX"
HEADER_SIZE = 14
MAX_PACKET_SIZE = 64 * 1024  # 64KB
DEFAULT_BUFFER_SIZE = 4096
TIMEOUT_MS = 5000


class Command(IntEnum):
    """Command types for the protocol."""
    HANDSHAKE = 0x01
    LIST_DIR = 0x02
    GET_FILE_INFO = 0x03
    READ_FILE = 0x04
    WRITE_FILE = 0x05
    CREATE_DIR = 0x06
    DELETE = 0x07
    RENAME = 0x08
    SEARCH = 0x09
    GET_DRIVES = 0x0A
    GET_STORAGE_INFO = 0x0B
    DISCONNECT = 0xFF

    # Response commands
    RESPONSE_OK = 0x80
    RESPONSE_ERROR = 0x81
    RESPONSE_DATA = 0x82
    RESPONSE_FILE_CHUNK = 0x83
    RESPONSE_END = 0x84


class Flags(IntEnum):
    """Packet flags."""
    NONE = 0x00
    COMPRESSED = 0x01
    ENCRYPTED = 0x02
    CONTINUATION = 0x04
    FINAL = 0x08


class ErrorCode(IntEnum):
    """Error codes."""
    SUCCESS = 0
    UNKNOWN_COMMAND = 1
    INVALID_PATH = 2
    FILE_NOT_FOUND = 3
    PERMISSION_DENIED = 4
    ALREADY_EXISTS = 5
    NOT_EMPTY = 6
    NO_SPACE = 7
    IO_ERROR = 8
    TIMEOUT = 9
    PROTOCOL_ERROR = 10


@dataclass
class Packet:
    """Represents a USB protocol packet."""
    command: int
    flags: int = Flags.NONE
    payload: bytes = b""

    def to_bytes(self) -> bytes:
        """Serialize packet to bytes."""
        # Build packet without checksum
        data = bytearray()
        data.extend(MAGIC)
        data.append(self.command)
        data.append(self.flags)
        data.extend(struct.pack("<I", len(self.payload)))
        data.extend(self.payload)

        # Calculate and append CRC32 checksum
        checksum = zlib.crc32(bytes(data)) & 0xFFFFFFFF
        data.extend(struct.pack("<I", checksum))

        return bytes(data)

    @classmethod
    def from_bytes(cls, data: bytes) -> Optional["Packet"]:
        """Parse packet from bytes."""
        if len(data) < HEADER_SIZE:
            return None

        # Verify magic
        if data[:4] != MAGIC:
            return None

        command = data[4]
        flags = data[5]
        payload_length = struct.unpack("<I", data[6:10])[0]

        if len(data) < HEADER_SIZE + payload_length:
            return None

        payload = data[10:10 + payload_length]
        received_checksum = struct.unpack("<I", data[10 + payload_length:14 + payload_length])[0]

        # Verify checksum
        data_for_checksum = data[:10 + payload_length]
        calculated_checksum = zlib.crc32(data_for_checksum) & 0xFFFFFFFF

        if received_checksum != calculated_checksum:
            return None

        return cls(command=command, flags=flags, payload=payload)


class PayloadSerializer:
    """Serializes and deserializes payload data."""

    @staticmethod
    def deserialize_path(data: bytes) -> str:
        """Deserialize a path from payload bytes."""
        length = struct.unpack("<I", data[:4])[0]
        return data[4:4 + length].decode("utf-8")

    @staticmethod
    def serialize_path(path: str) -> bytes:
        """Serialize a path to payload bytes."""
        path_bytes = path.encode("utf-8")
        return struct.pack("<I", len(path_bytes)) + path_bytes

    @staticmethod
    def deserialize_rename(data: bytes) -> tuple[str, str]:
        """Deserialize rename request."""
        offset = 0
        path_len = struct.unpack("<I", data[offset:offset + 4])[0]
        offset += 4
        path = data[offset:offset + path_len].decode("utf-8")
        offset += path_len

        name_len = struct.unpack("<I", data[offset:offset + 4])[0]
        offset += 4
        new_name = data[offset:offset + name_len].decode("utf-8")

        return path, new_name

    @staticmethod
    def deserialize_search(data: bytes) -> tuple[str, str]:
        """Deserialize search request."""
        offset = 0
        query_len = struct.unpack("<I", data[offset:offset + 4])[0]
        offset += 4
        query = data[offset:offset + query_len].decode("utf-8")
        offset += query_len

        path_len = struct.unpack("<I", data[offset:offset + 4])[0]
        offset += 4
        path = data[offset:offset + path_len].decode("utf-8")

        return query, path

    @staticmethod
    def serialize_file_item(name: str, path: str, is_dir: bool, size: int, last_modified: int) -> bytes:
        """Serialize a file item."""
        name_bytes = name.encode("utf-8")
        path_bytes = path.encode("utf-8")
        flags = 0x01 if is_dir else 0x00

        data = bytearray()
        data.extend(struct.pack("<I", len(name_bytes)))
        data.extend(name_bytes)
        data.extend(struct.pack("<I", len(path_bytes)))
        data.extend(path_bytes)
        data.append(flags)
        data.extend(struct.pack("<Q", size))
        data.extend(struct.pack("<Q", last_modified))

        return bytes(data)

    @staticmethod
    def serialize_file_list(files: list) -> bytes:
        """Serialize a list of file items."""
        data = bytearray()
        data.extend(struct.pack("<I", len(files)))

        for f in files:
            data.extend(PayloadSerializer.serialize_file_item(
                name=f["name"],
                path=f["path"],
                is_dir=f["is_dir"],
                size=f["size"],
                last_modified=f["last_modified"]
            ))

        return bytes(data)

    @staticmethod
    def serialize_storage_info(total: int, free: int, drive: str, volume: str) -> bytes:
        """Serialize storage info."""
        drive_bytes = drive.encode("utf-8")
        volume_bytes = volume.encode("utf-8")

        data = bytearray()
        data.extend(struct.pack("<Q", total))
        data.extend(struct.pack("<Q", free))
        data.extend(struct.pack("<I", len(drive_bytes)))
        data.extend(drive_bytes)
        data.extend(struct.pack("<I", len(volume_bytes)))
        data.extend(volume_bytes)

        return bytes(data)

    @staticmethod
    def serialize_drive_list(drives: list[str]) -> bytes:
        """Serialize a list of drive paths."""
        data = bytearray()
        data.extend(struct.pack("<I", len(drives)))

        for drive in drives:
            drive_bytes = drive.encode("utf-8")
            data.extend(struct.pack("<I", len(drive_bytes)))
            data.extend(drive_bytes)

        return bytes(data)

    @staticmethod
    def serialize_error(code: int, message: str) -> bytes:
        """Serialize error response."""
        msg_bytes = message.encode("utf-8")
        data = bytearray()
        data.extend(struct.pack("<I", code))
        data.extend(struct.pack("<I", len(msg_bytes)))
        data.extend(msg_bytes)
        return bytes(data)

    @staticmethod
    def deserialize_file_read_request(data: bytes) -> tuple[str, int, int]:
        """Deserialize file read request."""
        offset = 0
        path_len = struct.unpack("<I", data[offset:offset + 4])[0]
        offset += 4
        path = data[offset:offset + path_len].decode("utf-8")
        offset += path_len

        file_offset = struct.unpack("<Q", data[offset:offset + 8])[0]
        offset += 8
        length = struct.unpack("<Q", data[offset:offset + 8])[0]

        return path, file_offset, length

    @staticmethod
    def deserialize_file_write_header(data: bytes) -> tuple[str, int, int]:
        """Deserialize file write header."""
        offset = 0
        path_len = struct.unpack("<I", data[offset:offset + 4])[0]
        offset += 4
        path = data[offset:offset + path_len].decode("utf-8")
        offset += path_len

        total_size = struct.unpack("<Q", data[offset:offset + 8])[0]
        offset += 8
        chunk_size = struct.unpack("<I", data[offset:offset + 4])[0]

        return path, total_size, chunk_size

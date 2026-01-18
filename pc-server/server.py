#!/usr/bin/env python3
"""
PC Explorer USB Server

This server runs on the PC and handles USB communication with the Android app.
It provides file system access through a custom USB protocol.

Usage:
    python server.py

Requirements:
    - Python 3.10+
    - pyusb
    - libusb (system library)

Note: This is a prototype implementation. In production, you might want to use
a more robust USB communication library or implement ADB-based communication.
"""

import sys
import os
import time
import logging
import argparse
import threading
from typing import Optional

# Add parent directory to path for imports
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from protocol import (
    Packet, Command, Flags, ErrorCode,
    PayloadSerializer, MAGIC, MAX_PACKET_SIZE, DEFAULT_BUFFER_SIZE
)
from file_handler import FileHandler

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

# Chunk size for file transfers
CHUNK_SIZE = 32 * 1024  # 32KB


class UsbServer:
    """USB server that handles communication with the Android app."""

    def __init__(self, simulate: bool = False):
        self.file_handler = FileHandler()
        self.simulate = simulate
        self.running = False
        self.usb_device = None
        self.usb_endpoint_in = None
        self.usb_endpoint_out = None

        # For simulation mode (TCP socket based)
        self.sim_socket = None
        self.sim_conn = None

    def start(self) -> None:
        """Start the USB server."""
        self.running = True

        if self.simulate:
            self._start_simulation_mode()
        else:
            self._start_usb_mode()

    def stop(self) -> None:
        """Stop the USB server."""
        self.running = False
        if self.sim_conn:
            self.sim_conn.close()
        if self.sim_socket:
            self.sim_socket.close()

    def _start_simulation_mode(self) -> None:
        """Start in simulation mode using TCP socket."""
        import socket

        logger.info("Starting in simulation mode (TCP)")
        self.sim_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.sim_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        self.sim_socket.bind(("0.0.0.0", 5555))
        self.sim_socket.listen(1)

        logger.info("Listening on port 5555...")
        logger.info("Connect with: adb forward tcp:5555 tcp:5555")

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

        logger.info("Starting in USB mode")
        logger.info("Waiting for USB device...")

        while self.running:
            # Find Android device
            # Note: Vendor/Product IDs vary by device manufacturer
            # This is a simplified example
            device = usb.core.find(idVendor=0x18D1)  # Google's vendor ID

            if device is None:
                time.sleep(1)
                continue

            logger.info(f"Found device: {device}")

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
    parser = argparse.ArgumentParser(description="PC Explorer USB Server")
    parser.add_argument(
        "--simulate", "-s",
        action="store_true",
        help="Run in simulation mode using TCP socket (for testing)"
    )
    parser.add_argument(
        "--verbose", "-v",
        action="store_true",
        help="Enable verbose logging"
    )
    args = parser.parse_args()

    if args.verbose:
        logging.getLogger().setLevel(logging.DEBUG)

    server = UsbServer(simulate=args.simulate)

    print("=" * 50)
    print("PC Explorer USB Server")
    print("=" * 50)
    print()

    if args.simulate:
        print("Running in SIMULATION mode (TCP socket)")
        print("Connect Android device via ADB:")
        print("  adb forward tcp:5555 tcp:5555")
    else:
        print("Running in USB mode")
        print("Connect Android device via USB cable")

    print()
    print("Press Ctrl+C to stop the server")
    print()

    try:
        server.start()
    except KeyboardInterrupt:
        print("\nShutting down...")
        server.stop()


if __name__ == "__main__":
    main()

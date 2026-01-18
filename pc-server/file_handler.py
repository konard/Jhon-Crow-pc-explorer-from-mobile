"""
File system operations handler for PC Explorer server.
"""

import os
import glob
import shutil
import platform
from pathlib import Path
from typing import Optional
import logging

logger = logging.getLogger(__name__)


class FileHandler:
    """Handles file system operations."""

    def __init__(self):
        self.system = platform.system()

    def get_drives(self) -> list[str]:
        """Get available drives on the system."""
        if self.system == "Windows":
            import string
            drives = []
            for letter in string.ascii_uppercase:
                drive = f"{letter}:\\"
                if os.path.exists(drive):
                    drives.append(drive)
            return drives
        else:
            # Unix-like systems
            return ["/"]

    def get_storage_info(self, path: str) -> tuple[int, int, str, str]:
        """Get storage info for a path.

        Returns: (total_bytes, free_bytes, drive_letter, volume_name)
        """
        if not path:
            path = self.get_drives()[0] if self.get_drives() else "/"

        try:
            usage = shutil.disk_usage(path)

            drive_letter = ""
            volume_name = ""

            if self.system == "Windows":
                drive_letter = os.path.splitdrive(path)[0] + "\\"
                try:
                    import ctypes
                    kernel32 = ctypes.windll.kernel32
                    volume_buffer = ctypes.create_unicode_buffer(1024)
                    kernel32.GetVolumeInformationW(
                        drive_letter,
                        volume_buffer,
                        ctypes.sizeof(volume_buffer),
                        None, None, None, None, 0
                    )
                    volume_name = volume_buffer.value
                except Exception:
                    volume_name = "Local Disk"
            else:
                drive_letter = "/"
                volume_name = "Root"

            return usage.total, usage.free, drive_letter, volume_name
        except Exception as e:
            logger.error(f"Error getting storage info: {e}")
            return 0, 0, "", ""

    def list_directory(self, path: str) -> list[dict]:
        """List files in a directory."""
        files = []

        if not path:
            path = self.get_drives()[0] if self.get_drives() else "/"

        try:
            for entry in os.scandir(path):
                try:
                    stat_info = entry.stat()
                    files.append({
                        "name": entry.name,
                        "path": entry.path,
                        "is_dir": entry.is_dir(),
                        "size": stat_info.st_size if not entry.is_dir() else 0,
                        "last_modified": int(stat_info.st_mtime * 1000)
                    })
                except (PermissionError, OSError) as e:
                    logger.warning(f"Cannot access {entry.path}: {e}")
                    continue
        except PermissionError as e:
            logger.error(f"Permission denied: {path}")
            raise
        except FileNotFoundError as e:
            logger.error(f"Directory not found: {path}")
            raise

        return files

    def get_file_info(self, path: str) -> dict:
        """Get information about a file or directory."""
        try:
            stat_info = os.stat(path)
            is_dir = os.path.isdir(path)

            return {
                "name": os.path.basename(path),
                "path": path,
                "is_dir": is_dir,
                "size": stat_info.st_size if not is_dir else 0,
                "last_modified": int(stat_info.st_mtime * 1000)
            }
        except FileNotFoundError:
            raise
        except PermissionError:
            raise

    def search_files(self, query: str, base_path: str = "") -> list[dict]:
        """Search for files matching a query."""
        if not base_path:
            base_path = self.get_drives()[0] if self.get_drives() else "/"

        results = []
        max_results = 100  # Limit results

        try:
            for root, dirs, files in os.walk(base_path):
                # Skip hidden and system directories
                dirs[:] = [d for d in dirs if not d.startswith('.') and d not in ['$RECYCLE.BIN', 'System Volume Information']]

                for name in files + dirs:
                    if query.lower() in name.lower():
                        full_path = os.path.join(root, name)
                        try:
                            stat_info = os.stat(full_path)
                            is_dir = os.path.isdir(full_path)
                            results.append({
                                "name": name,
                                "path": full_path,
                                "is_dir": is_dir,
                                "size": stat_info.st_size if not is_dir else 0,
                                "last_modified": int(stat_info.st_mtime * 1000)
                            })
                        except (PermissionError, OSError):
                            continue

                        if len(results) >= max_results:
                            return results

        except PermissionError:
            pass

        return results

    def create_directory(self, path: str) -> dict:
        """Create a new directory."""
        try:
            os.makedirs(path, exist_ok=False)
            return self.get_file_info(path)
        except FileExistsError:
            raise
        except PermissionError:
            raise

    def rename(self, old_path: str, new_name: str) -> dict:
        """Rename a file or directory."""
        parent = os.path.dirname(old_path)
        new_path = os.path.join(parent, new_name)

        try:
            os.rename(old_path, new_path)
            return self.get_file_info(new_path)
        except FileNotFoundError:
            raise
        except PermissionError:
            raise
        except FileExistsError:
            raise

    def delete(self, path: str) -> None:
        """Delete a file or directory."""
        try:
            if os.path.isdir(path):
                shutil.rmtree(path)
            else:
                os.remove(path)
        except FileNotFoundError:
            raise
        except PermissionError:
            raise

    def read_file_chunk(self, path: str, offset: int, length: int) -> bytes:
        """Read a chunk of a file."""
        try:
            with open(path, "rb") as f:
                f.seek(offset)
                if length < 0:
                    return f.read()
                return f.read(length)
        except FileNotFoundError:
            raise
        except PermissionError:
            raise

    def write_file_chunk(self, path: str, data: bytes, offset: int = 0, create: bool = False) -> None:
        """Write a chunk to a file."""
        mode = "wb" if create and offset == 0 else "r+b"

        if create and not os.path.exists(path):
            # Create the file
            os.makedirs(os.path.dirname(path), exist_ok=True)
            with open(path, "wb") as f:
                f.seek(offset)
                f.write(data)
        else:
            with open(path, mode) as f:
                f.seek(offset)
                f.write(data)

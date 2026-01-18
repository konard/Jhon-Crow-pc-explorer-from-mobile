package com.pcexplorer.core.data.protocol

import com.pcexplorer.core.domain.model.FileItem
import com.pcexplorer.core.domain.model.StorageInfo
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

/**
 * Serializes and deserializes payload data for the USB protocol.
 */
object PayloadSerializer {

    /**
     * Serialize a string path to payload bytes.
     */
    fun serializePath(path: String): ByteArray {
        val pathBytes = path.toByteArray(StandardCharsets.UTF_8)
        val buffer = ByteBuffer.allocate(4 + pathBytes.size)
            .order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(pathBytes.size)
        buffer.put(pathBytes)
        return buffer.array()
    }

    /**
     * Deserialize a path from payload bytes.
     */
    fun deserializePath(data: ByteArray): String {
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val length = buffer.getInt()
        val pathBytes = ByteArray(length)
        buffer.get(pathBytes)
        return String(pathBytes, StandardCharsets.UTF_8)
    }

    /**
     * Serialize rename request (old path + new name).
     */
    fun serializeRename(path: String, newName: String): ByteArray {
        val pathBytes = path.toByteArray(StandardCharsets.UTF_8)
        val nameBytes = newName.toByteArray(StandardCharsets.UTF_8)
        val buffer = ByteBuffer.allocate(8 + pathBytes.size + nameBytes.size)
            .order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(pathBytes.size)
        buffer.put(pathBytes)
        buffer.putInt(nameBytes.size)
        buffer.put(nameBytes)
        return buffer.array()
    }

    /**
     * Serialize search request (query + optional path).
     */
    fun serializeSearch(query: String, path: String): ByteArray {
        val queryBytes = query.toByteArray(StandardCharsets.UTF_8)
        val pathBytes = path.toByteArray(StandardCharsets.UTF_8)
        val buffer = ByteBuffer.allocate(8 + queryBytes.size + pathBytes.size)
            .order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(queryBytes.size)
        buffer.put(queryBytes)
        buffer.putInt(pathBytes.size)
        buffer.put(pathBytes)
        return buffer.array()
    }

    /**
     * Deserialize a list of file items.
     */
    fun deserializeFileList(data: ByteArray): List<FileItem> {
        if (data.isEmpty()) return emptyList()

        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val count = buffer.getInt()
        val files = mutableListOf<FileItem>()

        repeat(count) {
            files.add(deserializeFileItem(buffer))
        }

        return files
    }

    /**
     * Deserialize a single file item.
     */
    fun deserializeFileItem(data: ByteArray): FileItem {
        return deserializeFileItem(ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN))
    }

    private fun deserializeFileItem(buffer: ByteBuffer): FileItem {
        // Name
        val nameLength = buffer.getInt()
        val nameBytes = ByteArray(nameLength)
        buffer.get(nameBytes)
        val name = String(nameBytes, StandardCharsets.UTF_8)

        // Path
        val pathLength = buffer.getInt()
        val pathBytes = ByteArray(pathLength)
        buffer.get(pathBytes)
        val path = String(pathBytes, StandardCharsets.UTF_8)

        // Flags (1 byte: bit 0 = isDirectory)
        val flags = buffer.get()
        val isDirectory = (flags.toInt() and 0x01) != 0

        // Size
        val size = buffer.getLong()

        // Last modified
        val lastModified = buffer.getLong()

        return FileItem(
            name = name,
            path = path,
            isDirectory = isDirectory,
            size = size,
            lastModified = lastModified
        )
    }

    /**
     * Deserialize storage info.
     */
    fun deserializeStorageInfo(data: ByteArray): StorageInfo {
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

        val totalSpace = buffer.getLong()
        val freeSpace = buffer.getLong()

        // Drive letter
        val driveLength = buffer.getInt()
        val driveBytes = ByteArray(driveLength)
        buffer.get(driveBytes)
        val driveLetter = String(driveBytes, StandardCharsets.UTF_8)

        // Volume name
        val volumeLength = buffer.getInt()
        val volumeBytes = ByteArray(volumeLength)
        buffer.get(volumeBytes)
        val volumeName = String(volumeBytes, StandardCharsets.UTF_8)

        return StorageInfo(
            totalSpace = totalSpace,
            freeSpace = freeSpace,
            driveLetter = driveLetter,
            volumeName = volumeName
        )
    }

    /**
     * Deserialize list of drive paths.
     */
    fun deserializeDriveList(data: ByteArray): List<String> {
        if (data.isEmpty()) return emptyList()

        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val count = buffer.getInt()
        val drives = mutableListOf<String>()

        repeat(count) {
            val length = buffer.getInt()
            val bytes = ByteArray(length)
            buffer.get(bytes)
            drives.add(String(bytes, StandardCharsets.UTF_8))
        }

        return drives
    }

    /**
     * Serialize file write request header.
     */
    fun serializeFileWriteHeader(
        remotePath: String,
        totalSize: Long,
        chunkSize: Int
    ): ByteArray {
        val pathBytes = remotePath.toByteArray(StandardCharsets.UTF_8)
        val buffer = ByteBuffer.allocate(4 + pathBytes.size + 8 + 4)
            .order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(pathBytes.size)
        buffer.put(pathBytes)
        buffer.putLong(totalSize)
        buffer.putInt(chunkSize)
        return buffer.array()
    }

    /**
     * Serialize file read request.
     */
    fun serializeFileReadRequest(
        remotePath: String,
        offset: Long = 0,
        length: Long = -1 // -1 means read all
    ): ByteArray {
        val pathBytes = remotePath.toByteArray(StandardCharsets.UTF_8)
        val buffer = ByteBuffer.allocate(4 + pathBytes.size + 16)
            .order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(pathBytes.size)
        buffer.put(pathBytes)
        buffer.putLong(offset)
        buffer.putLong(length)
        return buffer.array()
    }

    /**
     * Deserialize error response.
     */
    fun deserializeError(data: ByteArray): Pair<Int, String> {
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val errorCode = buffer.getInt()
        val messageLength = buffer.getInt()
        val messageBytes = ByteArray(messageLength)
        buffer.get(messageBytes)
        return errorCode to String(messageBytes, StandardCharsets.UTF_8)
    }
}

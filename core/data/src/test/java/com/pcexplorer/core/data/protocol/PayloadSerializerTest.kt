package com.pcexplorer.core.data.protocol

import com.pcexplorer.core.domain.model.FileItem
import com.pcexplorer.core.domain.model.StorageInfo
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

class PayloadSerializerTest {

    // serializePath tests

    @Test
    fun `serializePath serializes simple path`() {
        val path = "/documents"

        val result = PayloadSerializer.serializePath(path)

        val buffer = ByteBuffer.wrap(result).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(path.length, buffer.getInt())
        val pathBytes = ByteArray(path.length)
        buffer.get(pathBytes)
        assertEquals(path, String(pathBytes, StandardCharsets.UTF_8))
    }

    @Test
    fun `serializePath handles empty path`() {
        val path = ""

        val result = PayloadSerializer.serializePath(path)

        val buffer = ByteBuffer.wrap(result).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(0, buffer.getInt())
    }

    @Test
    fun `serializePath handles unicode characters`() {
        val path = "/документы/файл.txt"

        val result = PayloadSerializer.serializePath(path)
        val deserializedPath = PayloadSerializer.deserializePath(result)

        assertEquals(path, deserializedPath)
    }

    @Test
    fun `serializePath handles path with spaces`() {
        val path = "/my documents/some file.txt"

        val result = PayloadSerializer.serializePath(path)
        val deserializedPath = PayloadSerializer.deserializePath(result)

        assertEquals(path, deserializedPath)
    }

    // deserializePath tests

    @Test
    fun `deserializePath deserializes path correctly`() {
        val path = "/users/test/documents"
        val pathBytes = path.toByteArray(StandardCharsets.UTF_8)
        val buffer = ByteBuffer.allocate(4 + pathBytes.size)
            .order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(pathBytes.size)
        buffer.put(pathBytes)

        val result = PayloadSerializer.deserializePath(buffer.array())

        assertEquals(path, result)
    }

    @Test
    fun `serializePath and deserializePath are reversible`() {
        val paths = listOf(
            "/",
            "/home",
            "/path/to/file.txt",
            "C:\\Windows\\System32",
            "/path with spaces/file name.txt"
        )

        paths.forEach { path ->
            val serialized = PayloadSerializer.serializePath(path)
            val deserialized = PayloadSerializer.deserializePath(serialized)
            assertEquals("Failed for path: $path", path, deserialized)
        }
    }

    // serializeRename tests

    @Test
    fun `serializeRename serializes path and new name`() {
        val path = "/documents/old.txt"
        val newName = "new.txt"

        val result = PayloadSerializer.serializeRename(path, newName)

        val buffer = ByteBuffer.wrap(result).order(ByteOrder.LITTLE_ENDIAN)

        // Read path
        val pathLength = buffer.getInt()
        val pathBytes = ByteArray(pathLength)
        buffer.get(pathBytes)
        assertEquals(path, String(pathBytes, StandardCharsets.UTF_8))

        // Read new name
        val nameLength = buffer.getInt()
        val nameBytes = ByteArray(nameLength)
        buffer.get(nameBytes)
        assertEquals(newName, String(nameBytes, StandardCharsets.UTF_8))
    }

    @Test
    fun `serializeRename handles unicode in new name`() {
        val path = "/documents/file.txt"
        val newName = "новый_файл.txt"

        val result = PayloadSerializer.serializeRename(path, newName)

        val buffer = ByteBuffer.wrap(result).order(ByteOrder.LITTLE_ENDIAN)

        // Read path
        val pathLength = buffer.getInt()
        val pathBytes = ByteArray(pathLength)
        buffer.get(pathBytes)
        assertEquals(path, String(pathBytes, StandardCharsets.UTF_8))

        // Read new name
        val nameLength = buffer.getInt()
        val nameBytes = ByteArray(nameLength)
        buffer.get(nameBytes)
        assertEquals(newName, String(nameBytes, StandardCharsets.UTF_8))
    }

    // serializeSearch tests

    @Test
    fun `serializeSearch serializes query and path`() {
        val query = "test"
        val path = "/documents"

        val result = PayloadSerializer.serializeSearch(query, path)

        val buffer = ByteBuffer.wrap(result).order(ByteOrder.LITTLE_ENDIAN)

        // Read query
        val queryLength = buffer.getInt()
        val queryBytes = ByteArray(queryLength)
        buffer.get(queryBytes)
        assertEquals(query, String(queryBytes, StandardCharsets.UTF_8))

        // Read path
        val pathLength = buffer.getInt()
        val pathBytes = ByteArray(pathLength)
        buffer.get(pathBytes)
        assertEquals(path, String(pathBytes, StandardCharsets.UTF_8))
    }

    @Test
    fun `serializeSearch handles empty path`() {
        val query = "search term"
        val path = ""

        val result = PayloadSerializer.serializeSearch(query, path)

        val buffer = ByteBuffer.wrap(result).order(ByteOrder.LITTLE_ENDIAN)

        // Read query
        val queryLength = buffer.getInt()
        val queryBytes = ByteArray(queryLength)
        buffer.get(queryBytes)
        assertEquals(query, String(queryBytes, StandardCharsets.UTF_8))

        // Read path - should be 0 length
        val pathLength = buffer.getInt()
        assertEquals(0, pathLength)
    }

    // deserializeFileList tests

    @Test
    fun `deserializeFileList returns empty list for empty data`() {
        val result = PayloadSerializer.deserializeFileList(ByteArray(0))

        assertTrue(result.isEmpty())
    }

    @Test
    fun `deserializeFileList deserializes single file`() {
        val data = createFileListBytes(
            listOf(
                TestFileData("test.txt", "/path/test.txt", false, 1024, 1000000)
            )
        )

        val result = PayloadSerializer.deserializeFileList(data)

        assertEquals(1, result.size)
        assertEquals("test.txt", result[0].name)
        assertEquals("/path/test.txt", result[0].path)
        assertFalse(result[0].isDirectory)
        assertEquals(1024L, result[0].size)
        assertEquals(1000000L, result[0].lastModified)
    }

    @Test
    fun `deserializeFileList deserializes multiple files`() {
        val data = createFileListBytes(
            listOf(
                TestFileData("file1.txt", "/path/file1.txt", false, 100, 1000),
                TestFileData("folder", "/path/folder", true, 0, 2000),
                TestFileData("file2.doc", "/path/file2.doc", false, 500, 3000)
            )
        )

        val result = PayloadSerializer.deserializeFileList(data)

        assertEquals(3, result.size)
        assertEquals("file1.txt", result[0].name)
        assertEquals("folder", result[1].name)
        assertTrue(result[1].isDirectory)
        assertEquals("file2.doc", result[2].name)
    }

    // deserializeFileItem tests

    @Test
    fun `deserializeFileItem deserializes file correctly`() {
        val data = createSingleFileBytes("document.pdf", "/docs/document.pdf", false, 2048, 999999)

        val result = PayloadSerializer.deserializeFileItem(data)

        assertEquals("document.pdf", result.name)
        assertEquals("/docs/document.pdf", result.path)
        assertFalse(result.isDirectory)
        assertEquals(2048L, result.size)
        assertEquals(999999L, result.lastModified)
    }

    @Test
    fun `deserializeFileItem deserializes directory correctly`() {
        val data = createSingleFileBytes("my_folder", "/home/my_folder", true, 0, 888888)

        val result = PayloadSerializer.deserializeFileItem(data)

        assertEquals("my_folder", result.name)
        assertTrue(result.isDirectory)
        assertEquals(0L, result.size)
    }

    // deserializeStorageInfo tests

    @Test
    fun `deserializeStorageInfo deserializes correctly`() {
        val data = createStorageInfoBytes(1000000000L, 500000000L, "C:", "Windows")

        val result = PayloadSerializer.deserializeStorageInfo(data)

        assertEquals(1000000000L, result.totalSpace)
        assertEquals(500000000L, result.freeSpace)
        assertEquals("C:", result.driveLetter)
        assertEquals("Windows", result.volumeName)
    }

    @Test
    fun `deserializeStorageInfo handles empty volume name`() {
        val data = createStorageInfoBytes(1000L, 500L, "D:", "")

        val result = PayloadSerializer.deserializeStorageInfo(data)

        assertEquals("D:", result.driveLetter)
        assertEquals("", result.volumeName)
    }

    // deserializeDriveList tests

    @Test
    fun `deserializeDriveList returns empty list for empty data`() {
        val result = PayloadSerializer.deserializeDriveList(ByteArray(0))

        assertTrue(result.isEmpty())
    }

    @Test
    fun `deserializeDriveList deserializes multiple drives`() {
        val drives = listOf("C:", "D:", "E:")
        val data = createDriveListBytes(drives)

        val result = PayloadSerializer.deserializeDriveList(data)

        assertEquals(3, result.size)
        assertEquals("C:", result[0])
        assertEquals("D:", result[1])
        assertEquals("E:", result[2])
    }

    @Test
    fun `deserializeDriveList handles single drive`() {
        val data = createDriveListBytes(listOf("C:"))

        val result = PayloadSerializer.deserializeDriveList(data)

        assertEquals(1, result.size)
        assertEquals("C:", result[0])
    }

    // serializeFileWriteHeader tests

    @Test
    fun `serializeFileWriteHeader serializes correctly`() {
        val remotePath = "/upload/file.txt"
        val totalSize = 1024L
        val chunkSize = 4096

        val result = PayloadSerializer.serializeFileWriteHeader(remotePath, totalSize, chunkSize)

        val buffer = ByteBuffer.wrap(result).order(ByteOrder.LITTLE_ENDIAN)

        // Read path
        val pathLength = buffer.getInt()
        val pathBytes = ByteArray(pathLength)
        buffer.get(pathBytes)
        assertEquals(remotePath, String(pathBytes, StandardCharsets.UTF_8))

        // Read total size
        assertEquals(totalSize, buffer.getLong())

        // Read chunk size
        assertEquals(chunkSize, buffer.getInt())
    }

    // serializeFileReadRequest tests

    @Test
    fun `serializeFileReadRequest serializes with default offset and length`() {
        val remotePath = "/download/file.txt"

        val result = PayloadSerializer.serializeFileReadRequest(remotePath)

        val buffer = ByteBuffer.wrap(result).order(ByteOrder.LITTLE_ENDIAN)

        // Read path
        val pathLength = buffer.getInt()
        val pathBytes = ByteArray(pathLength)
        buffer.get(pathBytes)
        assertEquals(remotePath, String(pathBytes, StandardCharsets.UTF_8))

        // Read offset (default 0)
        assertEquals(0L, buffer.getLong())

        // Read length (default -1)
        assertEquals(-1L, buffer.getLong())
    }

    @Test
    fun `serializeFileReadRequest serializes with custom offset and length`() {
        val remotePath = "/file.txt"
        val offset = 100L
        val length = 500L

        val result = PayloadSerializer.serializeFileReadRequest(remotePath, offset, length)

        val buffer = ByteBuffer.wrap(result).order(ByteOrder.LITTLE_ENDIAN)

        // Skip path
        val pathLength = buffer.getInt()
        buffer.position(buffer.position() + pathLength)

        assertEquals(offset, buffer.getLong())
        assertEquals(length, buffer.getLong())
    }

    // deserializeError tests

    @Test
    fun `deserializeError deserializes error correctly`() {
        val errorCode = 3
        val errorMessage = "File not found"
        val data = createErrorBytes(errorCode, errorMessage)

        val result = PayloadSerializer.deserializeError(data)

        assertEquals(errorCode, result.first)
        assertEquals(errorMessage, result.second)
    }

    @Test
    fun `deserializeError handles empty message`() {
        val data = createErrorBytes(1, "")

        val result = PayloadSerializer.deserializeError(data)

        assertEquals(1, result.first)
        assertEquals("", result.second)
    }

    @Test
    fun `deserializeError handles unicode message`() {
        val errorMessage = "Файл не найден"
        val data = createErrorBytes(2, errorMessage)

        val result = PayloadSerializer.deserializeError(data)

        assertEquals(errorMessage, result.second)
    }

    // Helper data class
    data class TestFileData(
        val name: String,
        val path: String,
        val isDirectory: Boolean,
        val size: Long,
        val lastModified: Long
    )

    // Helper functions to create test byte arrays
    private fun createFileListBytes(files: List<TestFileData>): ByteArray {
        var size = 4 // count
        files.forEach { file ->
            size += 4 + file.name.toByteArray(StandardCharsets.UTF_8).size // name
            size += 4 + file.path.toByteArray(StandardCharsets.UTF_8).size // path
            size += 1 // flags
            size += 8 // size
            size += 8 // lastModified
        }

        val buffer = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(files.size)

        files.forEach { file ->
            val nameBytes = file.name.toByteArray(StandardCharsets.UTF_8)
            buffer.putInt(nameBytes.size)
            buffer.put(nameBytes)

            val pathBytes = file.path.toByteArray(StandardCharsets.UTF_8)
            buffer.putInt(pathBytes.size)
            buffer.put(pathBytes)

            buffer.put(if (file.isDirectory) 0x01.toByte() else 0x00.toByte())
            buffer.putLong(file.size)
            buffer.putLong(file.lastModified)
        }

        return buffer.array()
    }

    private fun createSingleFileBytes(
        name: String,
        path: String,
        isDirectory: Boolean,
        size: Long,
        lastModified: Long
    ): ByteArray {
        val nameBytes = name.toByteArray(StandardCharsets.UTF_8)
        val pathBytes = path.toByteArray(StandardCharsets.UTF_8)

        val bufferSize = 4 + nameBytes.size + 4 + pathBytes.size + 1 + 8 + 8
        val buffer = ByteBuffer.allocate(bufferSize).order(ByteOrder.LITTLE_ENDIAN)

        buffer.putInt(nameBytes.size)
        buffer.put(nameBytes)
        buffer.putInt(pathBytes.size)
        buffer.put(pathBytes)
        buffer.put(if (isDirectory) 0x01.toByte() else 0x00.toByte())
        buffer.putLong(size)
        buffer.putLong(lastModified)

        return buffer.array()
    }

    private fun createStorageInfoBytes(
        totalSpace: Long,
        freeSpace: Long,
        driveLetter: String,
        volumeName: String
    ): ByteArray {
        val driveBytes = driveLetter.toByteArray(StandardCharsets.UTF_8)
        val volumeBytes = volumeName.toByteArray(StandardCharsets.UTF_8)

        val bufferSize = 8 + 8 + 4 + driveBytes.size + 4 + volumeBytes.size
        val buffer = ByteBuffer.allocate(bufferSize).order(ByteOrder.LITTLE_ENDIAN)

        buffer.putLong(totalSpace)
        buffer.putLong(freeSpace)
        buffer.putInt(driveBytes.size)
        buffer.put(driveBytes)
        buffer.putInt(volumeBytes.size)
        buffer.put(volumeBytes)

        return buffer.array()
    }

    private fun createDriveListBytes(drives: List<String>): ByteArray {
        var size = 4 // count
        drives.forEach { drive ->
            size += 4 + drive.toByteArray(StandardCharsets.UTF_8).size
        }

        val buffer = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(drives.size)

        drives.forEach { drive ->
            val driveBytes = drive.toByteArray(StandardCharsets.UTF_8)
            buffer.putInt(driveBytes.size)
            buffer.put(driveBytes)
        }

        return buffer.array()
    }

    private fun createErrorBytes(errorCode: Int, message: String): ByteArray {
        val messageBytes = message.toByteArray(StandardCharsets.UTF_8)
        val buffer = ByteBuffer.allocate(8 + messageBytes.size).order(ByteOrder.LITTLE_ENDIAN)

        buffer.putInt(errorCode)
        buffer.putInt(messageBytes.size)
        buffer.put(messageBytes)

        return buffer.array()
    }
}

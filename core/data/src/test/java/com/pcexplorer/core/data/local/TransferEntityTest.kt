package com.pcexplorer.core.data.local

import com.pcexplorer.core.domain.model.TransferState
import com.pcexplorer.core.domain.model.TransferTask
import com.pcexplorer.core.domain.model.TransferType
import org.junit.Assert.*
import org.junit.Test

class TransferEntityTest {

    // TransferEntity creation tests

    @Test
    fun `TransferEntity contains all properties`() {
        val entity = TransferEntity(
            id = "test-id",
            fileName = "file.txt",
            sourcePath = "/source/file.txt",
            destinationPath = "/dest/file.txt",
            type = "DOWNLOAD",
            totalBytes = 1000,
            transferredBytes = 500,
            state = "InProgress",
            createdAt = 1000000L,
            completedAt = null,
            error = null
        )

        assertEquals("test-id", entity.id)
        assertEquals("file.txt", entity.fileName)
        assertEquals("/source/file.txt", entity.sourcePath)
        assertEquals("/dest/file.txt", entity.destinationPath)
        assertEquals("DOWNLOAD", entity.type)
        assertEquals(1000L, entity.totalBytes)
        assertEquals(500L, entity.transferredBytes)
        assertEquals("InProgress", entity.state)
        assertEquals(1000000L, entity.createdAt)
        assertNull(entity.completedAt)
        assertNull(entity.error)
    }

    @Test
    fun `TransferEntity with completedAt`() {
        val entity = TransferEntity(
            id = "test-id",
            fileName = "file.txt",
            sourcePath = "/source/file.txt",
            destinationPath = "/dest/file.txt",
            type = "DOWNLOAD",
            totalBytes = 1000,
            transferredBytes = 1000,
            state = "Completed",
            createdAt = 1000000L,
            completedAt = 2000000L,
            error = null
        )

        assertEquals(2000000L, entity.completedAt)
    }

    @Test
    fun `TransferEntity with error`() {
        val entity = TransferEntity(
            id = "test-id",
            fileName = "file.txt",
            sourcePath = "/source/file.txt",
            destinationPath = "/dest/file.txt",
            type = "UPLOAD",
            totalBytes = 1000,
            transferredBytes = 300,
            state = "Failed",
            createdAt = 1000000L,
            completedAt = null,
            error = "Connection lost"
        )

        assertEquals("Connection lost", entity.error)
        assertEquals("Failed", entity.state)
    }

    // toTransferTask tests

    @Test
    fun `toTransferTask converts entity to domain model`() {
        val entity = TransferEntity(
            id = "task-123",
            fileName = "document.pdf",
            sourcePath = "/remote/document.pdf",
            destinationPath = "/local/document.pdf",
            type = "DOWNLOAD",
            totalBytes = 5000,
            transferredBytes = 2500,
            state = "InProgress",
            createdAt = 1234567890L,
            completedAt = null,
            error = null
        )

        val task = entity.toTransferTask()

        assertEquals("task-123", task.id)
        assertEquals("document.pdf", task.fileName)
        assertEquals("/remote/document.pdf", task.sourcePath)
        assertEquals("/local/document.pdf", task.destinationPath)
        assertEquals(TransferType.DOWNLOAD, task.type)
        assertEquals(5000L, task.totalBytes)
        assertEquals(2500L, task.transferredBytes)
        assertEquals(TransferState.InProgress, task.state)
        assertEquals(1234567890L, task.createdAt)
        assertNull(task.completedAt)
        assertNull(task.error)
    }

    @Test
    fun `toTransferTask converts UPLOAD type correctly`() {
        val entity = TransferEntity(
            id = "upload-1",
            fileName = "photo.jpg",
            sourcePath = "/local/photo.jpg",
            destinationPath = "/remote/photo.jpg",
            type = "UPLOAD",
            totalBytes = 1000,
            transferredBytes = 0,
            state = "Pending",
            createdAt = 1000000L,
            completedAt = null,
            error = null
        )

        val task = entity.toTransferTask()

        assertEquals(TransferType.UPLOAD, task.type)
    }

    @Test
    fun `toTransferTask converts all states correctly`() {
        val states = listOf("Pending", "InProgress", "Completed", "Failed", "Cancelled")
        val expectedStates = listOf(
            TransferState.Pending,
            TransferState.InProgress,
            TransferState.Completed,
            TransferState.Failed,
            TransferState.Cancelled
        )

        states.forEachIndexed { index, stateString ->
            val entity = createEntity(state = stateString)
            val task = entity.toTransferTask()
            assertEquals(expectedStates[index], task.state)
        }
    }

    @Test
    fun `toTransferTask preserves completedAt`() {
        val entity = TransferEntity(
            id = "completed-1",
            fileName = "file.txt",
            sourcePath = "/source/file.txt",
            destinationPath = "/dest/file.txt",
            type = "DOWNLOAD",
            totalBytes = 1000,
            transferredBytes = 1000,
            state = "Completed",
            createdAt = 1000000L,
            completedAt = 2000000L,
            error = null
        )

        val task = entity.toTransferTask()

        assertEquals(2000000L, task.completedAt)
    }

    @Test
    fun `toTransferTask preserves error`() {
        val entity = TransferEntity(
            id = "failed-1",
            fileName = "file.txt",
            sourcePath = "/source/file.txt",
            destinationPath = "/dest/file.txt",
            type = "DOWNLOAD",
            totalBytes = 1000,
            transferredBytes = 500,
            state = "Failed",
            createdAt = 1000000L,
            completedAt = null,
            error = "Network timeout"
        )

        val task = entity.toTransferTask()

        assertEquals("Network timeout", task.error)
    }

    // fromTransferTask tests

    @Test
    fun `fromTransferTask converts domain model to entity`() {
        val task = TransferTask(
            id = "task-456",
            fileName = "video.mp4",
            sourcePath = "/local/video.mp4",
            destinationPath = "/remote/video.mp4",
            type = TransferType.UPLOAD,
            totalBytes = 10000,
            transferredBytes = 7500,
            state = TransferState.InProgress,
            createdAt = 9876543210L,
            completedAt = null,
            error = null
        )

        val entity = TransferEntity.fromTransferTask(task)

        assertEquals("task-456", entity.id)
        assertEquals("video.mp4", entity.fileName)
        assertEquals("/local/video.mp4", entity.sourcePath)
        assertEquals("/remote/video.mp4", entity.destinationPath)
        assertEquals("UPLOAD", entity.type)
        assertEquals(10000L, entity.totalBytes)
        assertEquals(7500L, entity.transferredBytes)
        assertEquals("InProgress", entity.state)
        assertEquals(9876543210L, entity.createdAt)
        assertNull(entity.completedAt)
        assertNull(entity.error)
    }

    @Test
    fun `fromTransferTask converts DOWNLOAD type correctly`() {
        val task = TransferTask(
            id = "download-1",
            fileName = "file.txt",
            sourcePath = "/remote/file.txt",
            destinationPath = "/local/file.txt",
            type = TransferType.DOWNLOAD,
            totalBytes = 1000,
            transferredBytes = 0,
            state = TransferState.Pending,
            createdAt = 1000000L
        )

        val entity = TransferEntity.fromTransferTask(task)

        assertEquals("DOWNLOAD", entity.type)
    }

    @Test
    fun `fromTransferTask converts all states correctly`() {
        val states = listOf(
            TransferState.Pending,
            TransferState.InProgress,
            TransferState.Completed,
            TransferState.Failed,
            TransferState.Cancelled
        )
        val expectedStateStrings = listOf("Pending", "InProgress", "Completed", "Failed", "Cancelled")

        states.forEachIndexed { index, state ->
            val task = createTask(state = state)
            val entity = TransferEntity.fromTransferTask(task)
            assertEquals(expectedStateStrings[index], entity.state)
        }
    }

    @Test
    fun `fromTransferTask preserves completedAt`() {
        val task = TransferTask(
            id = "completed-1",
            fileName = "file.txt",
            sourcePath = "/source/file.txt",
            destinationPath = "/dest/file.txt",
            type = TransferType.DOWNLOAD,
            totalBytes = 1000,
            transferredBytes = 1000,
            state = TransferState.Completed,
            createdAt = 1000000L,
            completedAt = 2000000L
        )

        val entity = TransferEntity.fromTransferTask(task)

        assertEquals(2000000L, entity.completedAt)
    }

    @Test
    fun `fromTransferTask preserves error`() {
        val task = TransferTask(
            id = "failed-1",
            fileName = "file.txt",
            sourcePath = "/source/file.txt",
            destinationPath = "/dest/file.txt",
            type = TransferType.UPLOAD,
            totalBytes = 1000,
            transferredBytes = 200,
            state = TransferState.Failed,
            createdAt = 1000000L,
            error = "Permission denied"
        )

        val entity = TransferEntity.fromTransferTask(task)

        assertEquals("Permission denied", entity.error)
    }

    // Round-trip tests

    @Test
    fun `round trip entity to task and back preserves all data`() {
        val originalEntity = TransferEntity(
            id = "round-trip-1",
            fileName = "archive.zip",
            sourcePath = "/remote/archive.zip",
            destinationPath = "/local/archive.zip",
            type = "DOWNLOAD",
            totalBytes = 50000,
            transferredBytes = 25000,
            state = "InProgress",
            createdAt = 1111111111L,
            completedAt = null,
            error = null
        )

        val task = originalEntity.toTransferTask()
        val newEntity = TransferEntity.fromTransferTask(task)

        assertEquals(originalEntity.id, newEntity.id)
        assertEquals(originalEntity.fileName, newEntity.fileName)
        assertEquals(originalEntity.sourcePath, newEntity.sourcePath)
        assertEquals(originalEntity.destinationPath, newEntity.destinationPath)
        assertEquals(originalEntity.type, newEntity.type)
        assertEquals(originalEntity.totalBytes, newEntity.totalBytes)
        assertEquals(originalEntity.transferredBytes, newEntity.transferredBytes)
        assertEquals(originalEntity.state, newEntity.state)
        assertEquals(originalEntity.createdAt, newEntity.createdAt)
        assertEquals(originalEntity.completedAt, newEntity.completedAt)
        assertEquals(originalEntity.error, newEntity.error)
    }

    @Test
    fun `round trip task to entity and back preserves all data`() {
        val originalTask = TransferTask(
            id = "round-trip-2",
            fileName = "music.mp3",
            sourcePath = "/local/music.mp3",
            destinationPath = "/remote/music.mp3",
            type = TransferType.UPLOAD,
            totalBytes = 3000,
            transferredBytes = 3000,
            state = TransferState.Completed,
            createdAt = 2222222222L,
            completedAt = 3333333333L,
            error = null
        )

        val entity = TransferEntity.fromTransferTask(originalTask)
        val newTask = entity.toTransferTask()

        assertEquals(originalTask.id, newTask.id)
        assertEquals(originalTask.fileName, newTask.fileName)
        assertEquals(originalTask.sourcePath, newTask.sourcePath)
        assertEquals(originalTask.destinationPath, newTask.destinationPath)
        assertEquals(originalTask.type, newTask.type)
        assertEquals(originalTask.totalBytes, newTask.totalBytes)
        assertEquals(originalTask.transferredBytes, newTask.transferredBytes)
        assertEquals(originalTask.state, newTask.state)
        assertEquals(originalTask.createdAt, newTask.createdAt)
        assertEquals(originalTask.completedAt, newTask.completedAt)
        assertEquals(originalTask.error, newTask.error)
    }

    @Test
    fun `round trip with error preserves error message`() {
        val originalTask = TransferTask(
            id = "error-task",
            fileName = "failed.txt",
            sourcePath = "/source/failed.txt",
            destinationPath = "/dest/failed.txt",
            type = TransferType.DOWNLOAD,
            totalBytes = 1000,
            transferredBytes = 100,
            state = TransferState.Failed,
            createdAt = 1000000L,
            error = "Disk full"
        )

        val entity = TransferEntity.fromTransferTask(originalTask)
        val newTask = entity.toTransferTask()

        assertEquals("Disk full", newTask.error)
    }

    // Data class tests

    @Test
    fun `TransferEntity data class equals works correctly`() {
        val entity1 = createEntity()
        val entity2 = createEntity()

        assertEquals(entity1, entity2)
    }

    @Test
    fun `TransferEntity data class equals with different ids`() {
        val entity1 = createEntity(id = "id-1")
        val entity2 = createEntity(id = "id-2")

        assertNotEquals(entity1, entity2)
    }

    @Test
    fun `TransferEntity data class copy works correctly`() {
        val original = createEntity(state = "Pending")
        val updated = original.copy(
            state = "Completed",
            transferredBytes = 1000,
            completedAt = 2000000L
        )

        assertEquals("Completed", updated.state)
        assertEquals(1000L, updated.transferredBytes)
        assertEquals(2000000L, updated.completedAt)
        assertEquals(original.id, updated.id)
        assertEquals(original.fileName, updated.fileName)
    }

    // Helper functions

    private fun createEntity(
        id: String = "test-id",
        fileName: String = "file.txt",
        sourcePath: String = "/source/file.txt",
        destinationPath: String = "/dest/file.txt",
        type: String = "DOWNLOAD",
        totalBytes: Long = 1000,
        transferredBytes: Long = 0,
        state: String = "Pending",
        createdAt: Long = 1000000L,
        completedAt: Long? = null,
        error: String? = null
    ): TransferEntity {
        return TransferEntity(
            id = id,
            fileName = fileName,
            sourcePath = sourcePath,
            destinationPath = destinationPath,
            type = type,
            totalBytes = totalBytes,
            transferredBytes = transferredBytes,
            state = state,
            createdAt = createdAt,
            completedAt = completedAt,
            error = error
        )
    }

    private fun createTask(
        id: String = "test-id",
        fileName: String = "file.txt",
        sourcePath: String = "/source/file.txt",
        destinationPath: String = "/dest/file.txt",
        type: TransferType = TransferType.DOWNLOAD,
        totalBytes: Long = 1000,
        transferredBytes: Long = 0,
        state: TransferState = TransferState.Pending,
        createdAt: Long = 1000000L,
        completedAt: Long? = null,
        error: String? = null
    ): TransferTask {
        return TransferTask(
            id = id,
            fileName = fileName,
            sourcePath = sourcePath,
            destinationPath = destinationPath,
            type = type,
            totalBytes = totalBytes,
            transferredBytes = transferredBytes,
            state = state,
            createdAt = createdAt,
            completedAt = completedAt,
            error = error
        )
    }
}

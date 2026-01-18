package com.pcexplorer.core.domain.model

import org.junit.Assert.*
import org.junit.Test

class TransferStateTest {

    @Test
    fun `TransferTask creates with generated UUID`() {
        val task1 = TransferTask(
            fileName = "test.txt",
            sourcePath = "/source/test.txt",
            destinationPath = "/dest/test.txt",
            type = TransferType.DOWNLOAD,
            totalBytes = 1000
        )

        val task2 = TransferTask(
            fileName = "test.txt",
            sourcePath = "/source/test.txt",
            destinationPath = "/dest/test.txt",
            type = TransferType.DOWNLOAD,
            totalBytes = 1000
        )

        assertNotEquals(task1.id, task2.id)
    }

    @Test
    fun `TransferTask with custom id`() {
        val task = TransferTask(
            id = "custom-id-123",
            fileName = "test.txt",
            sourcePath = "/source/test.txt",
            destinationPath = "/dest/test.txt",
            type = TransferType.DOWNLOAD,
            totalBytes = 1000
        )

        assertEquals("custom-id-123", task.id)
    }

    @Test
    fun `progress calculates correctly`() {
        val task = TransferTask(
            fileName = "test.txt",
            sourcePath = "/source/test.txt",
            destinationPath = "/dest/test.txt",
            type = TransferType.DOWNLOAD,
            totalBytes = 1000,
            transferredBytes = 500
        )

        assertEquals(0.5f, task.progress, 0.001f)
    }

    @Test
    fun `progress returns 0 for zero totalBytes`() {
        val task = TransferTask(
            fileName = "test.txt",
            sourcePath = "/source/test.txt",
            destinationPath = "/dest/test.txt",
            type = TransferType.DOWNLOAD,
            totalBytes = 0,
            transferredBytes = 0
        )

        assertEquals(0f, task.progress, 0.001f)
    }

    @Test
    fun `progress returns 1 for complete transfer`() {
        val task = TransferTask(
            fileName = "test.txt",
            sourcePath = "/source/test.txt",
            destinationPath = "/dest/test.txt",
            type = TransferType.DOWNLOAD,
            totalBytes = 1000,
            transferredBytes = 1000
        )

        assertEquals(1.0f, task.progress, 0.001f)
    }

    @Test
    fun `progressPercent calculates correctly`() {
        val task = TransferTask(
            fileName = "test.txt",
            sourcePath = "/source/test.txt",
            destinationPath = "/dest/test.txt",
            type = TransferType.DOWNLOAD,
            totalBytes = 1000,
            transferredBytes = 750
        )

        assertEquals(75, task.progressPercent)
    }

    @Test
    fun `isActive returns true for Pending state`() {
        val task = TransferTask(
            fileName = "test.txt",
            sourcePath = "/source/test.txt",
            destinationPath = "/dest/test.txt",
            type = TransferType.DOWNLOAD,
            totalBytes = 1000,
            state = TransferState.Pending
        )

        assertTrue(task.isActive)
    }

    @Test
    fun `isActive returns true for InProgress state`() {
        val task = TransferTask(
            fileName = "test.txt",
            sourcePath = "/source/test.txt",
            destinationPath = "/dest/test.txt",
            type = TransferType.DOWNLOAD,
            totalBytes = 1000,
            state = TransferState.InProgress
        )

        assertTrue(task.isActive)
    }

    @Test
    fun `isActive returns false for Completed state`() {
        val task = TransferTask(
            fileName = "test.txt",
            sourcePath = "/source/test.txt",
            destinationPath = "/dest/test.txt",
            type = TransferType.DOWNLOAD,
            totalBytes = 1000,
            state = TransferState.Completed
        )

        assertFalse(task.isActive)
    }

    @Test
    fun `isActive returns false for Failed state`() {
        val task = TransferTask(
            fileName = "test.txt",
            sourcePath = "/source/test.txt",
            destinationPath = "/dest/test.txt",
            type = TransferType.DOWNLOAD,
            totalBytes = 1000,
            state = TransferState.Failed
        )

        assertFalse(task.isActive)
    }

    @Test
    fun `isActive returns false for Cancelled state`() {
        val task = TransferTask(
            fileName = "test.txt",
            sourcePath = "/source/test.txt",
            destinationPath = "/dest/test.txt",
            type = TransferType.DOWNLOAD,
            totalBytes = 1000,
            state = TransferState.Cancelled
        )

        assertFalse(task.isActive)
    }

    @Test
    fun `isCompleted returns true only for Completed state`() {
        val completedTask = TransferTask(
            fileName = "test.txt",
            sourcePath = "/source/test.txt",
            destinationPath = "/dest/test.txt",
            type = TransferType.DOWNLOAD,
            totalBytes = 1000,
            state = TransferState.Completed
        )

        val pendingTask = completedTask.copy(state = TransferState.Pending)

        assertTrue(completedTask.isCompleted)
        assertFalse(pendingTask.isCompleted)
    }

    @Test
    fun `isFailed returns true only for Failed state`() {
        val failedTask = TransferTask(
            fileName = "test.txt",
            sourcePath = "/source/test.txt",
            destinationPath = "/dest/test.txt",
            type = TransferType.DOWNLOAD,
            totalBytes = 1000,
            state = TransferState.Failed
        )

        val pendingTask = failedTask.copy(state = TransferState.Pending)

        assertTrue(failedTask.isFailed)
        assertFalse(pendingTask.isFailed)
    }

    @Test
    fun `isCancelled returns true only for Cancelled state`() {
        val cancelledTask = TransferTask(
            fileName = "test.txt",
            sourcePath = "/source/test.txt",
            destinationPath = "/dest/test.txt",
            type = TransferType.DOWNLOAD,
            totalBytes = 1000,
            state = TransferState.Cancelled
        )

        val pendingTask = cancelledTask.copy(state = TransferState.Pending)

        assertTrue(cancelledTask.isCancelled)
        assertFalse(pendingTask.isCancelled)
    }

    @Test
    fun `TransferType DOWNLOAD value`() {
        assertEquals("DOWNLOAD", TransferType.DOWNLOAD.name)
    }

    @Test
    fun `TransferType UPLOAD value`() {
        assertEquals("UPLOAD", TransferType.UPLOAD.name)
    }

    @Test
    fun `TransferState enum values`() {
        val states = TransferState.values()

        assertEquals(5, states.size)
        assertTrue(states.contains(TransferState.Pending))
        assertTrue(states.contains(TransferState.InProgress))
        assertTrue(states.contains(TransferState.Completed))
        assertTrue(states.contains(TransferState.Failed))
        assertTrue(states.contains(TransferState.Cancelled))
    }

    @Test
    fun `TransferTask default state is Pending`() {
        val task = TransferTask(
            fileName = "test.txt",
            sourcePath = "/source/test.txt",
            destinationPath = "/dest/test.txt",
            type = TransferType.DOWNLOAD,
            totalBytes = 1000
        )

        assertEquals(TransferState.Pending, task.state)
    }

    @Test
    fun `TransferTask default transferredBytes is 0`() {
        val task = TransferTask(
            fileName = "test.txt",
            sourcePath = "/source/test.txt",
            destinationPath = "/dest/test.txt",
            type = TransferType.DOWNLOAD,
            totalBytes = 1000
        )

        assertEquals(0, task.transferredBytes)
    }

    @Test
    fun `TransferTask default completedAt is null`() {
        val task = TransferTask(
            fileName = "test.txt",
            sourcePath = "/source/test.txt",
            destinationPath = "/dest/test.txt",
            type = TransferType.DOWNLOAD,
            totalBytes = 1000
        )

        assertNull(task.completedAt)
    }

    @Test
    fun `TransferTask default error is null`() {
        val task = TransferTask(
            fileName = "test.txt",
            sourcePath = "/source/test.txt",
            destinationPath = "/dest/test.txt",
            type = TransferType.DOWNLOAD,
            totalBytes = 1000
        )

        assertNull(task.error)
    }

    @Test
    fun `TransferTask with error`() {
        val task = TransferTask(
            fileName = "test.txt",
            sourcePath = "/source/test.txt",
            destinationPath = "/dest/test.txt",
            type = TransferType.DOWNLOAD,
            totalBytes = 1000,
            state = TransferState.Failed,
            error = "Connection lost"
        )

        assertEquals("Connection lost", task.error)
    }

    @Test
    fun `TransferTask with completedAt`() {
        val completedAt = System.currentTimeMillis()
        val task = TransferTask(
            fileName = "test.txt",
            sourcePath = "/source/test.txt",
            destinationPath = "/dest/test.txt",
            type = TransferType.DOWNLOAD,
            totalBytes = 1000,
            state = TransferState.Completed,
            completedAt = completedAt
        )

        assertEquals(completedAt, task.completedAt)
    }

    @Test
    fun `TransferTask createdAt is set automatically`() {
        val before = System.currentTimeMillis()
        val task = TransferTask(
            fileName = "test.txt",
            sourcePath = "/source/test.txt",
            destinationPath = "/dest/test.txt",
            type = TransferType.DOWNLOAD,
            totalBytes = 1000
        )
        val after = System.currentTimeMillis()

        assertTrue(task.createdAt >= before)
        assertTrue(task.createdAt <= after)
    }
}

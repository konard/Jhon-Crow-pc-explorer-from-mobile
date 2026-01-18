package com.pcexplorer.core.domain.usecase

import com.pcexplorer.core.domain.model.TransferState
import com.pcexplorer.core.domain.model.TransferTask
import com.pcexplorer.core.domain.model.TransferType
import com.pcexplorer.core.domain.repository.TransferRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class TransferFileUseCaseTest {

    private lateinit var transferRepository: TransferRepository
    private lateinit var useCase: TransferFileUseCase

    @Before
    fun setUp() {
        transferRepository = mockk(relaxed = true)
        useCase = TransferFileUseCase(transferRepository)
    }

    // transfers flow tests

    @Test
    fun `transfers exposes repository transfers flow`() = runTest {
        val tasks = listOf(
            createTransferTask("1", TransferState.Completed),
            createTransferTask("2", TransferState.InProgress)
        )
        every { transferRepository.transfers } returns flowOf(tasks)

        // Re-create use case to get fresh flow
        useCase = TransferFileUseCase(transferRepository)

        val result = useCase.transfers.first()

        assertEquals(2, result.size)
        assertEquals("1", result[0].id)
        assertEquals("2", result[1].id)
    }

    @Test
    fun `transfers returns empty list when no transfers`() = runTest {
        every { transferRepository.transfers } returns flowOf(emptyList())
        useCase = TransferFileUseCase(transferRepository)

        val result = useCase.transfers.first()

        assertTrue(result.isEmpty())
    }

    // activeTransfers flow tests

    @Test
    fun `activeTransfers exposes repository activeTransfers flow`() = runTest {
        val activeTasks = listOf(
            createTransferTask("1", TransferState.InProgress),
            createTransferTask("2", TransferState.Pending)
        )
        every { transferRepository.activeTransfers } returns flowOf(activeTasks)
        useCase = TransferFileUseCase(transferRepository)

        val result = useCase.activeTransfers.first()

        assertEquals(2, result.size)
        assertTrue(result.all { it.state == TransferState.InProgress || it.state == TransferState.Pending })
    }

    // download tests

    @Test
    fun `download calls repository with remote and local paths`() = runTest {
        val task = createTransferTask("1", TransferState.Pending, TransferType.DOWNLOAD)
        coEvery { transferRepository.downloadFile(any(), any()) } returns Result.success(task)

        useCase.download("/remote/file.txt", "/local/file.txt")

        coVerify { transferRepository.downloadFile("/remote/file.txt", "/local/file.txt") }
    }

    @Test
    fun `download returns transfer task on success`() = runTest {
        val task = createTransferTask("1", TransferState.Pending, TransferType.DOWNLOAD)
        coEvery { transferRepository.downloadFile(any(), any()) } returns Result.success(task)

        val result = useCase.download("/remote/file.txt", "/local/file.txt")

        assertTrue(result.isSuccess)
        assertEquals(task, result.getOrNull())
        assertEquals(TransferType.DOWNLOAD, result.getOrNull()?.type)
    }

    @Test
    fun `download returns failure when repository fails`() = runTest {
        val exception = Exception("Download failed")
        coEvery { transferRepository.downloadFile(any(), any()) } returns Result.failure(exception)

        val result = useCase.download("/remote/file.txt", "/local/file.txt")

        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
    }

    // upload tests

    @Test
    fun `upload calls repository with local and remote paths`() = runTest {
        val task = createTransferTask("1", TransferState.Pending, TransferType.UPLOAD)
        coEvery { transferRepository.uploadFile(any(), any()) } returns Result.success(task)

        useCase.upload("/local/file.txt", "/remote/file.txt")

        coVerify { transferRepository.uploadFile("/local/file.txt", "/remote/file.txt") }
    }

    @Test
    fun `upload returns transfer task on success`() = runTest {
        val task = createTransferTask("1", TransferState.Pending, TransferType.UPLOAD)
        coEvery { transferRepository.uploadFile(any(), any()) } returns Result.success(task)

        val result = useCase.upload("/local/file.txt", "/remote/file.txt")

        assertTrue(result.isSuccess)
        assertEquals(task, result.getOrNull())
        assertEquals(TransferType.UPLOAD, result.getOrNull()?.type)
    }

    @Test
    fun `upload returns failure when repository fails`() = runTest {
        val exception = Exception("Upload failed")
        coEvery { transferRepository.uploadFile(any(), any()) } returns Result.failure(exception)

        val result = useCase.upload("/local/file.txt", "/remote/file.txt")

        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
    }

    // cancel tests

    @Test
    fun `cancel calls repository with task id`() = runTest {
        coEvery { transferRepository.cancelTransfer(any()) } returns Result.success(Unit)

        useCase.cancel("task-123")

        coVerify { transferRepository.cancelTransfer("task-123") }
    }

    @Test
    fun `cancel returns success when repository succeeds`() = runTest {
        coEvery { transferRepository.cancelTransfer(any()) } returns Result.success(Unit)

        val result = useCase.cancel("task-123")

        assertTrue(result.isSuccess)
    }

    @Test
    fun `cancel returns failure when repository fails`() = runTest {
        val exception = Exception("Transfer not found")
        coEvery { transferRepository.cancelTransfer(any()) } returns Result.failure(exception)

        val result = useCase.cancel("unknown-task")

        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
    }

    // retry tests

    @Test
    fun `retry calls repository with task id`() = runTest {
        val task = createTransferTask("1", TransferState.Pending)
        coEvery { transferRepository.retryTransfer(any()) } returns Result.success(task)

        useCase.retry("task-123")

        coVerify { transferRepository.retryTransfer("task-123") }
    }

    @Test
    fun `retry returns new transfer task on success`() = runTest {
        val task = createTransferTask("1", TransferState.Pending)
        coEvery { transferRepository.retryTransfer(any()) } returns Result.success(task)

        val result = useCase.retry("task-123")

        assertTrue(result.isSuccess)
        assertEquals(task, result.getOrNull())
    }

    @Test
    fun `retry returns failure when repository fails`() = runTest {
        val exception = Exception("Cannot retry completed transfer")
        coEvery { transferRepository.retryTransfer(any()) } returns Result.failure(exception)

        val result = useCase.retry("completed-task")

        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
    }

    // getProgress tests

    @Test
    fun `getProgress returns flow from repository`() = runTest {
        val task = createTransferTask("1", TransferState.InProgress, transferredBytes = 500)
        every { transferRepository.getTransferProgress(any()) } returns flowOf(task)

        val result = useCase.getProgress("task-1").first()

        assertEquals(task, result)
        assertEquals(500L, result?.transferredBytes)
        verify { transferRepository.getTransferProgress("task-1") }
    }

    @Test
    fun `getProgress returns null for unknown task`() = runTest {
        every { transferRepository.getTransferProgress(any()) } returns flowOf(null)

        val result = useCase.getProgress("unknown-task").first()

        assertNull(result)
    }

    @Test
    fun `getProgress emits updated progress`() = runTest {
        val progressFlow = MutableStateFlow<TransferTask?>(
            createTransferTask("1", TransferState.InProgress, transferredBytes = 0)
        )
        every { transferRepository.getTransferProgress("task-1") } returns progressFlow

        val flow = useCase.getProgress("task-1")

        assertEquals(0L, flow.first()?.transferredBytes)

        progressFlow.value = createTransferTask("1", TransferState.InProgress, transferredBytes = 500)
        assertEquals(500L, flow.first()?.transferredBytes)

        progressFlow.value = createTransferTask("1", TransferState.Completed, transferredBytes = 1000)
        assertEquals(1000L, flow.first()?.transferredBytes)
        assertEquals(TransferState.Completed, flow.first()?.state)
    }

    // clearHistory tests

    @Test
    fun `clearHistory calls repository clearHistory`() = runTest {
        coEvery { transferRepository.clearHistory() } returns Unit

        useCase.clearHistory()

        coVerify { transferRepository.clearHistory() }
    }

    // Helper function
    private fun createTransferTask(
        id: String,
        state: TransferState,
        type: TransferType = TransferType.DOWNLOAD,
        transferredBytes: Long = 0
    ): TransferTask {
        return TransferTask(
            id = id,
            fileName = "file.txt",
            sourcePath = "/source/file.txt",
            destinationPath = "/dest/file.txt",
            totalBytes = 1000,
            transferredBytes = transferredBytes,
            state = state,
            type = type,
            createdAt = System.currentTimeMillis(),
            error = null
        )
    }
}

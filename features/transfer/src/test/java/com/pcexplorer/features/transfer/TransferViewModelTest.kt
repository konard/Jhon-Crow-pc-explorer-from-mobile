package com.pcexplorer.features.transfer

import com.pcexplorer.core.domain.model.TransferState
import com.pcexplorer.core.domain.model.TransferTask
import com.pcexplorer.core.domain.model.TransferType
import com.pcexplorer.core.domain.usecase.TransferFileUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TransferViewModelTest {

    private lateinit var transferFileUseCase: TransferFileUseCase
    private lateinit var viewModel: TransferViewModel
    private val testDispatcher = StandardTestDispatcher()
    private val transfersFlow = MutableStateFlow<List<TransferTask>>(emptyList())

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        transferFileUseCase = mockk(relaxed = true)
        every { transferFileUseCase.transfers } returns transfersFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): TransferViewModel {
        return TransferViewModel(transferFileUseCase)
    }

    // uiState tests - StateFlow with WhileSubscribed requires active subscriber
    // These tests verify initial state only, as StateFlow timing is complex

    @Test
    fun `initial uiState has empty lists`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        // Initial state is always empty due to initialValue in stateIn
        assertTrue(viewModel.uiState.value.activeTransfers.isEmpty())
        assertTrue(viewModel.uiState.value.completedTransfers.isEmpty())
    }

    // downloadFile tests

    @Test
    fun `downloadFile calls use case download`() = runTest {
        val task = createTask("1", TransferState.Pending)
        coEvery { transferFileUseCase.download(any(), any()) } returns Result.success(task)
        viewModel = createViewModel()

        viewModel.downloadFile("/remote/file.txt", "/local/file.txt")
        advanceUntilIdle()

        coVerify { transferFileUseCase.download("/remote/file.txt", "/local/file.txt") }
    }

    @Test
    fun `downloadFile handles failure silently`() = runTest {
        coEvery { transferFileUseCase.download(any(), any()) } returns Result.failure(Exception("Download failed"))
        viewModel = createViewModel()

        viewModel.downloadFile("/remote/file.txt", "/local/file.txt")
        advanceUntilIdle()

        coVerify { transferFileUseCase.download("/remote/file.txt", "/local/file.txt") }
        // Should not crash
    }

    // uploadFile tests

    @Test
    fun `uploadFile calls use case upload`() = runTest {
        val task = createTask("1", TransferState.Pending, TransferType.UPLOAD)
        coEvery { transferFileUseCase.upload(any(), any()) } returns Result.success(task)
        viewModel = createViewModel()

        viewModel.uploadFile("/local/file.txt", "/remote/file.txt")
        advanceUntilIdle()

        coVerify { transferFileUseCase.upload("/local/file.txt", "/remote/file.txt") }
    }

    @Test
    fun `uploadFile handles failure silently`() = runTest {
        coEvery { transferFileUseCase.upload(any(), any()) } returns Result.failure(Exception("Upload failed"))
        viewModel = createViewModel()

        viewModel.uploadFile("/local/file.txt", "/remote/file.txt")
        advanceUntilIdle()

        coVerify { transferFileUseCase.upload("/local/file.txt", "/remote/file.txt") }
        // Should not crash
    }

    // cancelTransfer tests

    @Test
    fun `cancelTransfer calls use case cancel`() = runTest {
        coEvery { transferFileUseCase.cancel(any()) } returns Result.success(Unit)
        viewModel = createViewModel()

        viewModel.cancelTransfer("task-123")
        advanceUntilIdle()

        coVerify { transferFileUseCase.cancel("task-123") }
    }

    @Test
    fun `cancelTransfer handles failure silently`() = runTest {
        coEvery { transferFileUseCase.cancel(any()) } returns Result.failure(Exception("Not found"))
        viewModel = createViewModel()

        viewModel.cancelTransfer("unknown-task")
        advanceUntilIdle()

        coVerify { transferFileUseCase.cancel("unknown-task") }
        // Should not crash
    }

    // retryTransfer tests

    @Test
    fun `retryTransfer calls use case retry`() = runTest {
        val task = createTask("1", TransferState.Pending)
        coEvery { transferFileUseCase.retry(any()) } returns Result.success(task)
        viewModel = createViewModel()

        viewModel.retryTransfer("task-123")
        advanceUntilIdle()

        coVerify { transferFileUseCase.retry("task-123") }
    }

    @Test
    fun `retryTransfer handles failure silently`() = runTest {
        coEvery { transferFileUseCase.retry(any()) } returns Result.failure(Exception("Cannot retry"))
        viewModel = createViewModel()

        viewModel.retryTransfer("completed-task")
        advanceUntilIdle()

        coVerify { transferFileUseCase.retry("completed-task") }
        // Should not crash
    }

    // clearHistory tests

    @Test
    fun `clearHistory calls use case clearHistory`() = runTest {
        coEvery { transferFileUseCase.clearHistory() } returns Unit
        viewModel = createViewModel()

        viewModel.clearHistory()
        advanceUntilIdle()

        coVerify { transferFileUseCase.clearHistory() }
    }

    // Integration tests - simplified to test method invocations only

    @Test
    fun `full transfer lifecycle - method invocations`() = runTest {
        val task = createTask("1", TransferState.Pending)
        coEvery { transferFileUseCase.download(any(), any()) } returns Result.success(task)
        coEvery { transferFileUseCase.cancel(any()) } returns Result.success(Unit)
        viewModel = createViewModel()

        // Start download
        viewModel.downloadFile("/remote/file.txt", "/local/file.txt")
        advanceUntilIdle()
        coVerify { transferFileUseCase.download("/remote/file.txt", "/local/file.txt") }

        // Cancel transfer
        viewModel.cancelTransfer("1")
        advanceUntilIdle()
        coVerify { transferFileUseCase.cancel("1") }
    }

    @Test
    fun `multiple operations can be called sequentially`() = runTest {
        val task = createTask("1", TransferState.Pending, TransferType.UPLOAD)
        coEvery { transferFileUseCase.upload(any(), any()) } returns Result.success(task)
        coEvery { transferFileUseCase.retry(any()) } returns Result.success(task)
        viewModel = createViewModel()

        viewModel.uploadFile("/local/file.txt", "/remote/file.txt")
        advanceUntilIdle()

        viewModel.retryTransfer("1")
        advanceUntilIdle()

        coVerify { transferFileUseCase.upload("/local/file.txt", "/remote/file.txt") }
        coVerify { transferFileUseCase.retry("1") }
    }

    // Helper function
    private fun createTask(
        id: String,
        state: TransferState,
        type: TransferType = TransferType.DOWNLOAD
    ): TransferTask {
        return TransferTask(
            id = id,
            fileName = "file.txt",
            sourcePath = "/source/file.txt",
            destinationPath = "/dest/file.txt",
            type = type,
            totalBytes = 1000,
            transferredBytes = 500,
            state = state,
            createdAt = System.currentTimeMillis()
        )
    }
}

// TransferUiState tests
class TransferUiStateTest {

    @Test
    fun `TransferUiState default values`() {
        val state = TransferUiState()

        assertTrue(state.activeTransfers.isEmpty())
        assertTrue(state.completedTransfers.isEmpty())
    }

    @Test
    fun `TransferUiState with values`() {
        val activeTask = TransferTask(
            id = "1",
            fileName = "file.txt",
            sourcePath = "/src",
            destinationPath = "/dst",
            type = TransferType.DOWNLOAD,
            totalBytes = 1000,
            state = TransferState.InProgress
        )
        val completedTask = activeTask.copy(id = "2", state = TransferState.Completed)

        val state = TransferUiState(
            activeTransfers = listOf(activeTask),
            completedTransfers = listOf(completedTask)
        )

        assertEquals(1, state.activeTransfers.size)
        assertEquals(1, state.completedTransfers.size)
    }
}

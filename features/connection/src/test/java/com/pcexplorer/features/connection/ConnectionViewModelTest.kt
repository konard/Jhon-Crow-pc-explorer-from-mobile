package com.pcexplorer.features.connection

import com.pcexplorer.core.domain.model.ConnectionState
import com.pcexplorer.core.domain.usecase.ConnectToDeviceUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
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
class ConnectionViewModelTest {

    private lateinit var connectToDeviceUseCase: ConnectToDeviceUseCase
    private lateinit var viewModel: ConnectionViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        connectToDeviceUseCase = mockk(relaxed = true)
        every { connectToDeviceUseCase.connectionState } returns flowOf(ConnectionState.Disconnected)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): ConnectionViewModel {
        return ConnectionViewModel(connectToDeviceUseCase)
    }

    // connect tests

    @Test
    fun `connect calls use case`() = runTest {
        coEvery { connectToDeviceUseCase() } returns Result.success(Unit)
        viewModel = createViewModel()

        viewModel.connect()
        advanceUntilIdle()

        coVerify { connectToDeviceUseCase() }
    }

    @Test
    fun `connect handles failure silently`() = runTest {
        coEvery { connectToDeviceUseCase() } returns Result.failure(Exception("Connection failed"))
        viewModel = createViewModel()

        viewModel.connect()
        advanceUntilIdle()

        coVerify { connectToDeviceUseCase() }
        // ViewModel should not crash on failure
    }

    // disconnect tests

    @Test
    fun `disconnect calls use case disconnect`() = runTest {
        coEvery { connectToDeviceUseCase.disconnect() } returns Unit
        viewModel = createViewModel()

        viewModel.disconnect()
        advanceUntilIdle()

        coVerify { connectToDeviceUseCase.disconnect() }
    }

    // requestPermission tests

    @Test
    fun `requestPermission calls use case requestPermission`() = runTest {
        coEvery { connectToDeviceUseCase.requestPermission() } returns Unit
        viewModel = createViewModel()

        viewModel.requestPermission()
        advanceUntilIdle()

        coVerify { connectToDeviceUseCase.requestPermission() }
    }

    // hasPermission tests

    @Test
    fun `hasPermission returns use case result - true`() {
        every { connectToDeviceUseCase.hasPermission() } returns true
        viewModel = createViewModel()

        val result = viewModel.hasPermission()

        assertTrue(result)
    }

    @Test
    fun `hasPermission returns use case result - false`() {
        every { connectToDeviceUseCase.hasPermission() } returns false
        viewModel = createViewModel()

        val result = viewModel.hasPermission()

        assertFalse(result)
    }

    // Integration tests

    @Test
    fun `typical usage flow - request permission then connect`() = runTest {
        coEvery { connectToDeviceUseCase.requestPermission() } returns Unit
        coEvery { connectToDeviceUseCase() } returns Result.success(Unit)
        every { connectToDeviceUseCase.hasPermission() } returns false
        viewModel = createViewModel()

        // Check permission first
        assertFalse(viewModel.hasPermission())

        // Request permission
        viewModel.requestPermission()
        advanceUntilIdle()
        coVerify { connectToDeviceUseCase.requestPermission() }

        // Permission granted
        every { connectToDeviceUseCase.hasPermission() } returns true
        assertTrue(viewModel.hasPermission())

        // Now connect
        viewModel.connect()
        advanceUntilIdle()
        coVerify { connectToDeviceUseCase() }
    }

    @Test
    fun `can disconnect even after error`() = runTest {
        coEvery { connectToDeviceUseCase() } returns Result.failure(Exception("Connection failed"))
        coEvery { connectToDeviceUseCase.disconnect() } returns Unit
        viewModel = createViewModel()

        viewModel.connect()
        advanceUntilIdle()

        // Should still be able to call disconnect
        viewModel.disconnect()
        advanceUntilIdle()

        coVerify { connectToDeviceUseCase.disconnect() }
    }
}

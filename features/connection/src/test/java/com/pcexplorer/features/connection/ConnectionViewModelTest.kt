package com.pcexplorer.features.connection

import com.pcexplorer.core.domain.model.ConnectionState
import com.pcexplorer.core.domain.model.DeviceInfo
import com.pcexplorer.core.domain.usecase.ConnectToDeviceUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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
    private val connectionStateFlow = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        connectToDeviceUseCase = mockk(relaxed = true)
        every { connectToDeviceUseCase.connectionState } returns connectionStateFlow
        viewModel = ConnectionViewModel(connectToDeviceUseCase)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // connectionState tests - using first() to actually subscribe to the StateFlow

    @Test
    fun `initial connectionState is Disconnected`() = runTest {
        val state = viewModel.connectionState.first()
        assertEquals(ConnectionState.Disconnected, state)
    }

    @Test
    fun `connectionState reflects use case state changes`() = runTest {
        // First, collect once to trigger the stateIn subscription
        assertEquals(ConnectionState.Disconnected, viewModel.connectionState.first())

        connectionStateFlow.value = ConnectionState.Connecting
        advanceUntilIdle()
        assertEquals(ConnectionState.Connecting, viewModel.connectionState.first())

        val deviceInfo = DeviceInfo(1, 2, "dev", null, null, null)
        connectionStateFlow.value = ConnectionState.Connected(deviceInfo)
        advanceUntilIdle()
        assertTrue(viewModel.connectionState.first() is ConnectionState.Connected)
    }

    @Test
    fun `connectionState shows PermissionRequired`() = runTest {
        assertEquals(ConnectionState.Disconnected, viewModel.connectionState.first())

        connectionStateFlow.value = ConnectionState.PermissionRequired
        advanceUntilIdle()

        assertEquals(ConnectionState.PermissionRequired, viewModel.connectionState.first())
    }

    @Test
    fun `connectionState shows Error state`() = runTest {
        assertEquals(ConnectionState.Disconnected, viewModel.connectionState.first())

        connectionStateFlow.value = ConnectionState.Error("Test error")
        advanceUntilIdle()

        val state = viewModel.connectionState.first()
        assertTrue(state is ConnectionState.Error)
        assertEquals("Test error", (state as ConnectionState.Error).message)
    }

    // connect tests

    @Test
    fun `connect calls use case`() = runTest {
        coEvery { connectToDeviceUseCase() } returns Result.success(Unit)

        viewModel.connect()
        advanceUntilIdle()

        coVerify { connectToDeviceUseCase() }
    }

    @Test
    fun `connect handles failure silently`() = runTest {
        coEvery { connectToDeviceUseCase() } returns Result.failure(Exception("Connection failed"))

        viewModel.connect()
        advanceUntilIdle()

        coVerify { connectToDeviceUseCase() }
        // ViewModel should not crash on failure
    }

    // disconnect tests

    @Test
    fun `disconnect calls use case disconnect`() = runTest {
        coEvery { connectToDeviceUseCase.disconnect() } returns Unit

        viewModel.disconnect()
        advanceUntilIdle()

        coVerify { connectToDeviceUseCase.disconnect() }
    }

    // requestPermission tests

    @Test
    fun `requestPermission calls use case requestPermission`() = runTest {
        coEvery { connectToDeviceUseCase.requestPermission() } returns Unit

        viewModel.requestPermission()
        advanceUntilIdle()

        coVerify { connectToDeviceUseCase.requestPermission() }
    }

    // hasPermission tests

    @Test
    fun `hasPermission returns use case result - true`() {
        every { connectToDeviceUseCase.hasPermission() } returns true

        val result = viewModel.hasPermission()

        assertTrue(result)
    }

    @Test
    fun `hasPermission returns use case result - false`() {
        every { connectToDeviceUseCase.hasPermission() } returns false

        val result = viewModel.hasPermission()

        assertFalse(result)
    }

    // Integration tests

    @Test
    fun `full connection flow`() = runTest {
        coEvery { connectToDeviceUseCase.requestPermission() } returns Unit
        coEvery { connectToDeviceUseCase() } returns Result.success(Unit)
        every { connectToDeviceUseCase.hasPermission() } returns false

        // Initial state - use first() to actually subscribe
        assertEquals(ConnectionState.Disconnected, viewModel.connectionState.first())

        // Request permission
        viewModel.requestPermission()
        advanceUntilIdle()
        coVerify { connectToDeviceUseCase.requestPermission() }

        // Permission granted, now connect
        every { connectToDeviceUseCase.hasPermission() } returns true
        connectionStateFlow.value = ConnectionState.Connecting
        viewModel.connect()
        advanceUntilIdle()

        assertEquals(ConnectionState.Connecting, viewModel.connectionState.first())

        // Connected
        val deviceInfo = DeviceInfo(1, 2, "dev", "Manufacturer", "Product", "123")
        connectionStateFlow.value = ConnectionState.Connected(deviceInfo)
        advanceUntilIdle()

        assertTrue(viewModel.connectionState.first() is ConnectionState.Connected)
    }

    @Test
    fun `disconnect after error`() = runTest {
        coEvery { connectToDeviceUseCase.disconnect() } returns Unit

        // Subscribe to get initial state
        assertEquals(ConnectionState.Disconnected, viewModel.connectionState.first())

        // Error state
        connectionStateFlow.value = ConnectionState.Error("Connection lost")
        advanceUntilIdle()

        // Try to disconnect
        viewModel.disconnect()
        advanceUntilIdle()

        coVerify { connectToDeviceUseCase.disconnect() }
    }
}

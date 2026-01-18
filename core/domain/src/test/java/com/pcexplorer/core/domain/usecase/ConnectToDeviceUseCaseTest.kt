package com.pcexplorer.core.domain.usecase

import com.pcexplorer.core.domain.model.ConnectionState
import com.pcexplorer.core.domain.model.DeviceInfo
import com.pcexplorer.core.domain.repository.UsbConnectionRepository
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

class ConnectToDeviceUseCaseTest {

    private lateinit var usbConnectionRepository: UsbConnectionRepository
    private lateinit var useCase: ConnectToDeviceUseCase

    @Before
    fun setUp() {
        usbConnectionRepository = mockk()
        useCase = ConnectToDeviceUseCase(usbConnectionRepository)
    }

    // connectionState flow tests

    @Test
    fun `connectionState exposes repository connectionState flow`() = runTest {
        val state = ConnectionState.Disconnected
        every { usbConnectionRepository.connectionState } returns flowOf(state)
        useCase = ConnectToDeviceUseCase(usbConnectionRepository)

        val result = useCase.connectionState.first()

        assertEquals(ConnectionState.Disconnected, result)
    }

    @Test
    fun `connectionState emits Connecting state`() = runTest {
        every { usbConnectionRepository.connectionState } returns flowOf(ConnectionState.Connecting)
        useCase = ConnectToDeviceUseCase(usbConnectionRepository)

        val result = useCase.connectionState.first()

        assertEquals(ConnectionState.Connecting, result)
    }

    @Test
    fun `connectionState emits Connected state with DeviceInfo`() = runTest {
        val deviceInfo = DeviceInfo(
            vendorId = 1234,
            productId = 5678,
            deviceName = "/dev/usb0",
            manufacturerName = "Test Manufacturer",
            productName = "Test Device",
            serialNumber = "SN123"
        )
        val connectedState = ConnectionState.Connected(deviceInfo)
        every { usbConnectionRepository.connectionState } returns flowOf(connectedState)
        useCase = ConnectToDeviceUseCase(usbConnectionRepository)

        val result = useCase.connectionState.first()

        assertTrue(result is ConnectionState.Connected)
        assertEquals(deviceInfo, (result as ConnectionState.Connected).deviceInfo)
    }

    @Test
    fun `connectionState emits PermissionRequired state`() = runTest {
        every { usbConnectionRepository.connectionState } returns flowOf(ConnectionState.PermissionRequired)
        useCase = ConnectToDeviceUseCase(usbConnectionRepository)

        val result = useCase.connectionState.first()

        assertEquals(ConnectionState.PermissionRequired, result)
    }

    @Test
    fun `connectionState emits Error state`() = runTest {
        val errorState = ConnectionState.Error("Connection timeout")
        every { usbConnectionRepository.connectionState } returns flowOf(errorState)
        useCase = ConnectToDeviceUseCase(usbConnectionRepository)

        val result = useCase.connectionState.first()

        assertTrue(result is ConnectionState.Error)
        assertEquals("Connection timeout", (result as ConnectionState.Error).message)
    }

    @Test
    fun `connectionState tracks state changes`() = runTest {
        val stateFlow = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
        every { usbConnectionRepository.connectionState } returns stateFlow
        useCase = ConnectToDeviceUseCase(usbConnectionRepository)

        assertEquals(ConnectionState.Disconnected, useCase.connectionState.first())

        stateFlow.value = ConnectionState.Connecting
        assertEquals(ConnectionState.Connecting, useCase.connectionState.first())

        val deviceInfo = DeviceInfo(1, 2, "dev", null, null, null)
        stateFlow.value = ConnectionState.Connected(deviceInfo)
        assertTrue(useCase.connectionState.first() is ConnectionState.Connected)
    }

    // invoke (connect) tests

    @Test
    fun `invoke calls repository connect`() = runTest {
        coEvery { usbConnectionRepository.connect() } returns Result.success(Unit)

        useCase()

        coVerify { usbConnectionRepository.connect() }
    }

    @Test
    fun `invoke returns success when connection succeeds`() = runTest {
        coEvery { usbConnectionRepository.connect() } returns Result.success(Unit)

        val result = useCase()

        assertTrue(result.isSuccess)
    }

    @Test
    fun `invoke returns failure when connection fails`() = runTest {
        val exception = Exception("No USB device found")
        coEvery { usbConnectionRepository.connect() } returns Result.failure(exception)

        val result = useCase()

        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
    }

    @Test
    fun `invoke returns failure for permission denied`() = runTest {
        val exception = SecurityException("USB permission denied")
        coEvery { usbConnectionRepository.connect() } returns Result.failure(exception)

        val result = useCase()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SecurityException)
    }

    // disconnect tests

    @Test
    fun `disconnect calls repository disconnect`() = runTest {
        coEvery { usbConnectionRepository.disconnect() } returns Unit

        useCase.disconnect()

        coVerify { usbConnectionRepository.disconnect() }
    }

    @Test
    fun `disconnect can be called multiple times`() = runTest {
        coEvery { usbConnectionRepository.disconnect() } returns Unit

        useCase.disconnect()
        useCase.disconnect()

        coVerify(exactly = 2) { usbConnectionRepository.disconnect() }
    }

    // requestPermission tests

    @Test
    fun `requestPermission calls repository requestPermission`() = runTest {
        coEvery { usbConnectionRepository.requestPermission() } returns Unit

        useCase.requestPermission()

        coVerify { usbConnectionRepository.requestPermission() }
    }

    // hasPermission tests

    @Test
    fun `hasPermission returns true when permission granted`() {
        every { usbConnectionRepository.hasPermission() } returns true

        val result = useCase.hasPermission()

        assertTrue(result)
        verify { usbConnectionRepository.hasPermission() }
    }

    @Test
    fun `hasPermission returns false when permission not granted`() {
        every { usbConnectionRepository.hasPermission() } returns false

        val result = useCase.hasPermission()

        assertFalse(result)
    }

    @Test
    fun `hasPermission can be called without suspend`() {
        every { usbConnectionRepository.hasPermission() } returns true

        // This should compile and run without runTest
        val result = useCase.hasPermission()

        assertTrue(result)
    }

    // Integration-like tests

    @Test
    fun `typical connection flow - request permission then connect`() = runTest {
        every { usbConnectionRepository.hasPermission() } returns false
        coEvery { usbConnectionRepository.requestPermission() } returns Unit
        coEvery { usbConnectionRepository.connect() } returns Result.success(Unit)

        // First check permission
        assertFalse(useCase.hasPermission())

        // Request permission
        useCase.requestPermission()
        coVerify { usbConnectionRepository.requestPermission() }

        // Then connect
        every { usbConnectionRepository.hasPermission() } returns true
        assertTrue(useCase.hasPermission())

        val result = useCase()
        assertTrue(result.isSuccess)
    }

    @Test
    fun `disconnect after connection error`() = runTest {
        val exception = Exception("Connection lost")
        coEvery { usbConnectionRepository.connect() } returns Result.failure(exception)
        coEvery { usbConnectionRepository.disconnect() } returns Unit

        val connectResult = useCase()
        assertTrue(connectResult.isFailure)

        // Should still be able to disconnect
        useCase.disconnect()
        coVerify { usbConnectionRepository.disconnect() }
    }
}

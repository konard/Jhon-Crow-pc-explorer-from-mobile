package com.pcexplorer.core.domain.model

import org.junit.Assert.*
import org.junit.Test

class ConnectionStateTest {

    @Test
    fun `Disconnected is a singleton object`() {
        val state1 = ConnectionState.Disconnected
        val state2 = ConnectionState.Disconnected

        assertSame(state1, state2)
    }

    @Test
    fun `Connecting is a singleton object`() {
        val state1 = ConnectionState.Connecting
        val state2 = ConnectionState.Connecting

        assertSame(state1, state2)
    }

    @Test
    fun `PermissionRequired is a singleton object`() {
        val state1 = ConnectionState.PermissionRequired
        val state2 = ConnectionState.PermissionRequired

        assertSame(state1, state2)
    }

    @Test
    fun `Connected contains DeviceInfo`() {
        val deviceInfo = DeviceInfo(
            vendorId = 1234,
            productId = 5678,
            deviceName = "/dev/usb0",
            manufacturerName = "Test Manufacturer",
            productName = "Test Product",
            serialNumber = "ABC123"
        )

        val state = ConnectionState.Connected(deviceInfo)

        assertEquals(deviceInfo, state.deviceInfo)
    }

    @Test
    fun `Error contains message`() {
        val state = ConnectionState.Error("Connection failed")

        assertEquals("Connection failed", state.message)
        assertNull(state.cause)
    }

    @Test
    fun `Error contains message and cause`() {
        val cause = RuntimeException("Network error")
        val state = ConnectionState.Error("Connection failed", cause)

        assertEquals("Connection failed", state.message)
        assertEquals(cause, state.cause)
    }

    @Test
    fun `Error default cause is null`() {
        val state = ConnectionState.Error("Error occurred")

        assertNull(state.cause)
    }

    @Test
    fun `DeviceInfo contains all properties`() {
        val deviceInfo = DeviceInfo(
            vendorId = 1234,
            productId = 5678,
            deviceName = "/dev/usb0",
            manufacturerName = "Test Manufacturer",
            productName = "Test Product",
            serialNumber = "ABC123"
        )

        assertEquals(1234, deviceInfo.vendorId)
        assertEquals(5678, deviceInfo.productId)
        assertEquals("/dev/usb0", deviceInfo.deviceName)
        assertEquals("Test Manufacturer", deviceInfo.manufacturerName)
        assertEquals("Test Product", deviceInfo.productName)
        assertEquals("ABC123", deviceInfo.serialNumber)
    }

    @Test
    fun `DeviceInfo nullable fields can be null`() {
        val deviceInfo = DeviceInfo(
            vendorId = 1234,
            productId = 5678,
            deviceName = "/dev/usb0",
            manufacturerName = null,
            productName = null,
            serialNumber = null
        )

        assertNull(deviceInfo.manufacturerName)
        assertNull(deviceInfo.productName)
        assertNull(deviceInfo.serialNumber)
    }

    @Test
    fun `DeviceInfo data class equality works correctly`() {
        val deviceInfo1 = DeviceInfo(
            vendorId = 1234,
            productId = 5678,
            deviceName = "/dev/usb0",
            manufacturerName = "Test",
            productName = "Product",
            serialNumber = "123"
        )

        val deviceInfo2 = DeviceInfo(
            vendorId = 1234,
            productId = 5678,
            deviceName = "/dev/usb0",
            manufacturerName = "Test",
            productName = "Product",
            serialNumber = "123"
        )

        assertEquals(deviceInfo1, deviceInfo2)
        assertEquals(deviceInfo1.hashCode(), deviceInfo2.hashCode())
    }

    @Test
    fun `Connected states with same DeviceInfo are equal`() {
        val deviceInfo = DeviceInfo(
            vendorId = 1234,
            productId = 5678,
            deviceName = "/dev/usb0",
            manufacturerName = "Test",
            productName = "Product",
            serialNumber = "123"
        )

        val state1 = ConnectionState.Connected(deviceInfo)
        val state2 = ConnectionState.Connected(deviceInfo.copy())

        assertEquals(state1, state2)
    }

    @Test
    fun `Error states with same message are equal`() {
        val state1 = ConnectionState.Error("Same error")
        val state2 = ConnectionState.Error("Same error")

        assertEquals(state1, state2)
    }

    @Test
    fun `Error states with different messages are not equal`() {
        val state1 = ConnectionState.Error("Error 1")
        val state2 = ConnectionState.Error("Error 2")

        assertNotEquals(state1, state2)
    }

    @Test
    fun `ConnectionState can be used in when expression`() {
        val states = listOf(
            ConnectionState.Disconnected,
            ConnectionState.Connecting,
            ConnectionState.PermissionRequired,
            ConnectionState.Connected(
                DeviceInfo(1, 2, "dev", null, null, null)
            ),
            ConnectionState.Error("error")
        )

        val results = states.map { state ->
            when (state) {
                is ConnectionState.Disconnected -> "disconnected"
                is ConnectionState.Connecting -> "connecting"
                is ConnectionState.PermissionRequired -> "permission"
                is ConnectionState.Connected -> "connected"
                is ConnectionState.Error -> "error"
            }
        }

        assertEquals(listOf("disconnected", "connecting", "permission", "connected", "error"), results)
    }

    @Test
    fun `ConnectionState sealed class hierarchy is correct`() {
        assertTrue(ConnectionState.Disconnected is ConnectionState)
        assertTrue(ConnectionState.Connecting is ConnectionState)
        assertTrue(ConnectionState.PermissionRequired is ConnectionState)
        assertTrue(ConnectionState.Connected(DeviceInfo(1, 2, "dev", null, null, null)) is ConnectionState)
        assertTrue(ConnectionState.Error("error") is ConnectionState)
    }

    @Test
    fun `DeviceInfo copy works correctly`() {
        val original = DeviceInfo(
            vendorId = 1234,
            productId = 5678,
            deviceName = "/dev/usb0",
            manufacturerName = "Test",
            productName = "Product",
            serialNumber = "123"
        )

        val copy = original.copy(vendorId = 9999)

        assertEquals(9999, copy.vendorId)
        assertEquals(5678, copy.productId)
        assertEquals("/dev/usb0", copy.deviceName)
    }
}

package com.pcexplorer.core.domain.model

/**
 * Represents the current state of USB connection to PC.
 */
sealed class ConnectionState {
    data object Disconnected : ConnectionState()
    data object Connecting : ConnectionState()
    data class Connected(val deviceInfo: DeviceInfo) : ConnectionState()
    data class Error(val message: String, val cause: Throwable? = null) : ConnectionState()
    data object PermissionRequired : ConnectionState()
}

/**
 * Information about the connected USB device.
 */
data class DeviceInfo(
    val vendorId: Int,
    val productId: Int,
    val deviceName: String,
    val manufacturerName: String?,
    val productName: String?,
    val serialNumber: String?
)

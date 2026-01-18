package com.pcexplorer.core.domain.usecase

import com.pcexplorer.core.domain.model.ConnectionState
import com.pcexplorer.core.domain.repository.UsbConnectionRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case for connecting to PC through USB.
 */
class ConnectToDeviceUseCase @Inject constructor(
    private val usbConnectionRepository: UsbConnectionRepository
) {
    /**
     * Get the current connection state.
     */
    val connectionState: Flow<ConnectionState> = usbConnectionRepository.connectionState

    /**
     * Attempt to connect to the PC.
     */
    suspend operator fun invoke(): Result<Unit> {
        return usbConnectionRepository.connect()
    }

    /**
     * Disconnect from the PC.
     */
    suspend fun disconnect() {
        usbConnectionRepository.disconnect()
    }

    /**
     * Request USB permission.
     */
    suspend fun requestPermission() {
        usbConnectionRepository.requestPermission()
    }

    /**
     * Check if permission is granted.
     */
    fun hasPermission(): Boolean = usbConnectionRepository.hasPermission()
}

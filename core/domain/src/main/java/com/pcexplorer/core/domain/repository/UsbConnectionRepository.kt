package com.pcexplorer.core.domain.repository

import com.pcexplorer.core.domain.model.ConnectionState
import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing USB connection to PC.
 */
interface UsbConnectionRepository {
    /**
     * Flow of current connection state.
     */
    val connectionState: Flow<ConnectionState>

    /**
     * Attempt to connect to the PC through USB.
     */
    suspend fun connect(): Result<Unit>

    /**
     * Disconnect from the PC.
     */
    suspend fun disconnect()

    /**
     * Request USB permission from the user.
     */
    suspend fun requestPermission()

    /**
     * Check if USB permission is granted.
     */
    fun hasPermission(): Boolean

    /**
     * Send raw data over USB connection.
     */
    suspend fun sendData(data: ByteArray): Result<Unit>

    /**
     * Receive raw data from USB connection.
     */
    suspend fun receiveData(bufferSize: Int): Result<ByteArray>
}

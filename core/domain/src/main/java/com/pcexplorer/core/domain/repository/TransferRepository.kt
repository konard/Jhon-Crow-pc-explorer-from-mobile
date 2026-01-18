package com.pcexplorer.core.domain.repository

import com.pcexplorer.core.domain.model.TransferTask
import kotlinx.coroutines.flow.Flow

/**
 * Repository for file transfer operations.
 */
interface TransferRepository {
    /**
     * Flow of all transfer tasks.
     */
    val transfers: Flow<List<TransferTask>>

    /**
     * Flow of active transfers (pending or in progress).
     */
    val activeTransfers: Flow<List<TransferTask>>

    /**
     * Download a file from PC to Android device.
     * @param remotePath Path on the PC
     * @param localPath Destination path on Android
     * @return The created transfer task
     */
    suspend fun downloadFile(remotePath: String, localPath: String): Result<TransferTask>

    /**
     * Upload a file from Android device to PC.
     * @param localPath Path on Android
     * @param remotePath Destination path on PC
     * @return The created transfer task
     */
    suspend fun uploadFile(localPath: String, remotePath: String): Result<TransferTask>

    /**
     * Cancel a transfer.
     */
    suspend fun cancelTransfer(taskId: String): Result<Unit>

    /**
     * Retry a failed transfer.
     */
    suspend fun retryTransfer(taskId: String): Result<TransferTask>

    /**
     * Get transfer by ID.
     */
    suspend fun getTransfer(taskId: String): TransferTask?

    /**
     * Clear completed transfers from history.
     */
    suspend fun clearHistory()

    /**
     * Get transfer progress as a flow.
     */
    fun getTransferProgress(taskId: String): Flow<TransferTask?>
}

package com.pcexplorer.core.domain.usecase

import com.pcexplorer.core.domain.model.TransferTask
import com.pcexplorer.core.domain.repository.TransferRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case for file transfer operations.
 */
class TransferFileUseCase @Inject constructor(
    private val transferRepository: TransferRepository
) {
    /**
     * Flow of all transfers.
     */
    val transfers: Flow<List<TransferTask>> = transferRepository.transfers

    /**
     * Flow of active transfers.
     */
    val activeTransfers: Flow<List<TransferTask>> = transferRepository.activeTransfers

    /**
     * Download a file from PC to Android.
     */
    suspend fun download(remotePath: String, localPath: String): Result<TransferTask> {
        return transferRepository.downloadFile(remotePath, localPath)
    }

    /**
     * Upload a file from Android to PC.
     */
    suspend fun upload(localPath: String, remotePath: String): Result<TransferTask> {
        return transferRepository.uploadFile(localPath, remotePath)
    }

    /**
     * Cancel a transfer.
     */
    suspend fun cancel(taskId: String): Result<Unit> {
        return transferRepository.cancelTransfer(taskId)
    }

    /**
     * Retry a failed transfer.
     */
    suspend fun retry(taskId: String): Result<TransferTask> {
        return transferRepository.retryTransfer(taskId)
    }

    /**
     * Get transfer progress.
     */
    fun getProgress(taskId: String): Flow<TransferTask?> {
        return transferRepository.getTransferProgress(taskId)
    }

    /**
     * Clear transfer history.
     */
    suspend fun clearHistory() {
        transferRepository.clearHistory()
    }
}

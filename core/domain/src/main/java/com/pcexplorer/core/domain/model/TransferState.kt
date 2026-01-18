package com.pcexplorer.core.domain.model

import java.util.UUID

/**
 * Represents a file transfer operation.
 */
data class TransferTask(
    val id: String = UUID.randomUUID().toString(),
    val fileName: String,
    val sourcePath: String,
    val destinationPath: String,
    val type: TransferType,
    val totalBytes: Long,
    val transferredBytes: Long = 0,
    val state: TransferState = TransferState.Pending,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val error: String? = null
) {
    val progress: Float
        get() = if (totalBytes > 0) transferredBytes.toFloat() / totalBytes else 0f

    val progressPercent: Int
        get() = (progress * 100).toInt()

    val isActive: Boolean
        get() = state == TransferState.Pending || state == TransferState.InProgress

    val isCompleted: Boolean
        get() = state == TransferState.Completed

    val isFailed: Boolean
        get() = state == TransferState.Failed

    val isCancelled: Boolean
        get() = state == TransferState.Cancelled
}

/**
 * Direction of the file transfer.
 */
enum class TransferType {
    DOWNLOAD, // From PC to Android
    UPLOAD    // From Android to PC
}

/**
 * State of a file transfer operation.
 */
enum class TransferState {
    Pending,
    InProgress,
    Completed,
    Failed,
    Cancelled
}

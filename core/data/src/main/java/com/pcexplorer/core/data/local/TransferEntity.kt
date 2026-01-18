package com.pcexplorer.core.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.pcexplorer.core.domain.model.TransferState
import com.pcexplorer.core.domain.model.TransferTask
import com.pcexplorer.core.domain.model.TransferType

/**
 * Room entity for persisting transfer tasks.
 */
@Entity(tableName = "transfers")
data class TransferEntity(
    @PrimaryKey
    val id: String,
    val fileName: String,
    val sourcePath: String,
    val destinationPath: String,
    val type: String, // DOWNLOAD or UPLOAD
    val totalBytes: Long,
    val transferredBytes: Long,
    val state: String,
    val createdAt: Long,
    val completedAt: Long?,
    val error: String?
) {
    fun toTransferTask(): TransferTask = TransferTask(
        id = id,
        fileName = fileName,
        sourcePath = sourcePath,
        destinationPath = destinationPath,
        type = TransferType.valueOf(type),
        totalBytes = totalBytes,
        transferredBytes = transferredBytes,
        state = TransferState.valueOf(state),
        createdAt = createdAt,
        completedAt = completedAt,
        error = error
    )

    companion object {
        fun fromTransferTask(task: TransferTask): TransferEntity = TransferEntity(
            id = task.id,
            fileName = task.fileName,
            sourcePath = task.sourcePath,
            destinationPath = task.destinationPath,
            type = task.type.name,
            totalBytes = task.totalBytes,
            transferredBytes = task.transferredBytes,
            state = task.state.name,
            createdAt = task.createdAt,
            completedAt = task.completedAt,
            error = task.error
        )
    }
}

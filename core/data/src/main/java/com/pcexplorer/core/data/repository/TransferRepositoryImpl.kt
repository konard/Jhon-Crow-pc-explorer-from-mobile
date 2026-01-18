package com.pcexplorer.core.data.repository

import android.content.Context
import com.pcexplorer.core.common.Logger
import com.pcexplorer.core.data.local.TransferDao
import com.pcexplorer.core.data.local.TransferEntity
import com.pcexplorer.core.data.protocol.PayloadSerializer
import com.pcexplorer.core.data.protocol.UsbPacket
import com.pcexplorer.core.data.protocol.UsbProtocol
import com.pcexplorer.core.domain.model.TransferState
import com.pcexplorer.core.domain.model.TransferTask
import com.pcexplorer.core.domain.model.TransferType
import com.pcexplorer.core.domain.repository.TransferRepository
import com.pcexplorer.core.domain.repository.UsbConnectionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "TransferRepository"
private const val CHUNK_SIZE = 32 * 1024 // 32KB chunks

@Singleton
class TransferRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val transferDao: TransferDao,
    private val usbConnectionRepository: UsbConnectionRepository
) : TransferRepository {

    override val transfers: Flow<List<TransferTask>> =
        transferDao.getAllTransfers().map { entities ->
            entities.map { it.toTransferTask() }
        }

    override val activeTransfers: Flow<List<TransferTask>> =
        transferDao.getActiveTransfers().map { entities ->
            entities.map { it.toTransferTask() }
        }

    override suspend fun downloadFile(remotePath: String, localPath: String): Result<TransferTask> {
        return withContext(Dispatchers.IO) {
            try {
                // Get file info first to know the size
                val infoPacket = UsbPacket(
                    command = UsbProtocol.Commands.GET_FILE_INFO,
                    payload = PayloadSerializer.serializePath(remotePath)
                )
                usbConnectionRepository.sendData(infoPacket.toBytes()).getOrThrow()

                val infoResponse = usbConnectionRepository.receiveData(UsbProtocol.MAX_PACKET_SIZE).getOrThrow()
                val infoPacketResponse = UsbPacket.fromBytes(infoResponse).getOrThrow()

                if (infoPacketResponse.command == UsbProtocol.Commands.RESPONSE_ERROR) {
                    val (code, message) = PayloadSerializer.deserializeError(infoPacketResponse.payload)
                    return@withContext Result.failure(Exception("Error $code: $message"))
                }

                val fileInfo = PayloadSerializer.deserializeFileItem(infoPacketResponse.payload)

                // Create transfer task
                val task = TransferTask(
                    fileName = fileInfo.name,
                    sourcePath = remotePath,
                    destinationPath = localPath,
                    type = TransferType.DOWNLOAD,
                    totalBytes = fileInfo.size,
                    state = TransferState.Pending
                )
                transferDao.insertTransfer(TransferEntity.fromTransferTask(task))

                // Start download
                executeDownload(task)
            } catch (e: Exception) {
                Logger.e(TAG, "Download failed", e)
                Result.failure(e)
            }
        }
    }

    private suspend fun executeDownload(task: TransferTask): Result<TransferTask> {
        var currentTask = task.copy(state = TransferState.InProgress)
        transferDao.updateTransfer(TransferEntity.fromTransferTask(currentTask))

        return try {
            // Send read file request
            val requestPacket = UsbPacket(
                command = UsbProtocol.Commands.READ_FILE,
                payload = PayloadSerializer.serializeFileReadRequest(task.sourcePath)
            )
            usbConnectionRepository.sendData(requestPacket.toBytes()).getOrThrow()

            // Create local file
            val localFile = File(task.destinationPath)
            localFile.parentFile?.mkdirs()

            FileOutputStream(localFile).use { output ->
                var totalReceived = 0L

                while (totalReceived < task.totalBytes) {
                    val chunkResponse = usbConnectionRepository.receiveData(UsbProtocol.MAX_PACKET_SIZE).getOrThrow()
                    val chunkPacket = UsbPacket.fromBytes(chunkResponse).getOrThrow()

                    when (chunkPacket.command) {
                        UsbProtocol.Commands.RESPONSE_FILE_CHUNK -> {
                            output.write(chunkPacket.payload)
                            totalReceived += chunkPacket.payload.size

                            // Update progress
                            currentTask = currentTask.copy(transferredBytes = totalReceived)
                            transferDao.updateProgress(task.id, totalReceived, TransferState.InProgress.name)
                        }
                        UsbProtocol.Commands.RESPONSE_END -> {
                            break
                        }
                        UsbProtocol.Commands.RESPONSE_ERROR -> {
                            val (code, message) = PayloadSerializer.deserializeError(chunkPacket.payload)
                            throw Exception("Transfer error $code: $message")
                        }
                        else -> {
                            throw Exception("Unexpected response: ${chunkPacket.command}")
                        }
                    }
                }
            }

            // Mark as completed
            currentTask = currentTask.copy(
                state = TransferState.Completed,
                completedAt = System.currentTimeMillis()
            )
            transferDao.updateTransfer(TransferEntity.fromTransferTask(currentTask))
            Logger.i(TAG, "Download completed: ${task.fileName}")
            Result.success(currentTask)
        } catch (e: Exception) {
            Logger.e(TAG, "Download error", e)
            currentTask = currentTask.copy(
                state = TransferState.Failed,
                error = e.message
            )
            transferDao.updateTransfer(TransferEntity.fromTransferTask(currentTask))
            Result.failure(e)
        }
    }

    override suspend fun uploadFile(localPath: String, remotePath: String): Result<TransferTask> {
        return withContext(Dispatchers.IO) {
            try {
                val localFile = File(localPath)
                if (!localFile.exists()) {
                    return@withContext Result.failure(Exception("Local file not found"))
                }

                // Create transfer task
                val task = TransferTask(
                    fileName = localFile.name,
                    sourcePath = localPath,
                    destinationPath = remotePath,
                    type = TransferType.UPLOAD,
                    totalBytes = localFile.length(),
                    state = TransferState.Pending
                )
                transferDao.insertTransfer(TransferEntity.fromTransferTask(task))

                // Start upload
                executeUpload(task)
            } catch (e: Exception) {
                Logger.e(TAG, "Upload failed", e)
                Result.failure(e)
            }
        }
    }

    private suspend fun executeUpload(task: TransferTask): Result<TransferTask> {
        var currentTask = task.copy(state = TransferState.InProgress)
        transferDao.updateTransfer(TransferEntity.fromTransferTask(currentTask))

        return try {
            val localFile = File(task.sourcePath)

            // Send write file header
            val headerPacket = UsbPacket(
                command = UsbProtocol.Commands.WRITE_FILE,
                payload = PayloadSerializer.serializeFileWriteHeader(
                    task.destinationPath,
                    task.totalBytes,
                    CHUNK_SIZE
                )
            )
            usbConnectionRepository.sendData(headerPacket.toBytes()).getOrThrow()

            // Wait for acknowledgment
            val ackResponse = usbConnectionRepository.receiveData(UsbProtocol.DEFAULT_BUFFER_SIZE).getOrThrow()
            val ackPacket = UsbPacket.fromBytes(ackResponse).getOrThrow()
            if (ackPacket.command != UsbProtocol.Commands.RESPONSE_OK) {
                throw Exception("Server rejected upload")
            }

            // Send file chunks
            FileInputStream(localFile).use { input ->
                val buffer = ByteArray(CHUNK_SIZE)
                var totalSent = 0L
                var bytesRead: Int

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    val chunk = if (bytesRead < buffer.size) buffer.copyOf(bytesRead) else buffer
                    val isLast = totalSent + bytesRead >= task.totalBytes

                    val chunkPacket = UsbPacket(
                        command = UsbProtocol.Commands.RESPONSE_FILE_CHUNK,
                        flags = if (isLast) UsbProtocol.Flags.FINAL else UsbProtocol.Flags.CONTINUATION,
                        payload = chunk
                    )
                    usbConnectionRepository.sendData(chunkPacket.toBytes()).getOrThrow()

                    totalSent += bytesRead

                    // Update progress
                    currentTask = currentTask.copy(transferredBytes = totalSent)
                    transferDao.updateProgress(task.id, totalSent, TransferState.InProgress.name)
                }
            }

            // Wait for completion acknowledgment
            val completeResponse = usbConnectionRepository.receiveData(UsbProtocol.DEFAULT_BUFFER_SIZE).getOrThrow()
            val completePacket = UsbPacket.fromBytes(completeResponse).getOrThrow()

            if (completePacket.command != UsbProtocol.Commands.RESPONSE_OK) {
                throw Exception("Upload not acknowledged by server")
            }

            // Mark as completed
            currentTask = currentTask.copy(
                state = TransferState.Completed,
                completedAt = System.currentTimeMillis()
            )
            transferDao.updateTransfer(TransferEntity.fromTransferTask(currentTask))
            Logger.i(TAG, "Upload completed: ${task.fileName}")
            Result.success(currentTask)
        } catch (e: Exception) {
            Logger.e(TAG, "Upload error", e)
            currentTask = currentTask.copy(
                state = TransferState.Failed,
                error = e.message
            )
            transferDao.updateTransfer(TransferEntity.fromTransferTask(currentTask))
            Result.failure(e)
        }
    }

    override suspend fun cancelTransfer(taskId: String): Result<Unit> {
        return try {
            val entity = transferDao.getTransferById(taskId)
                ?: return Result.failure(Exception("Transfer not found"))

            val updatedEntity = entity.copy(state = TransferState.Cancelled.name)
            transferDao.updateTransfer(updatedEntity)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun retryTransfer(taskId: String): Result<TransferTask> {
        return try {
            val entity = transferDao.getTransferById(taskId)
                ?: return Result.failure(Exception("Transfer not found"))

            val task = entity.toTransferTask()

            if (task.type == TransferType.DOWNLOAD) {
                downloadFile(task.sourcePath, task.destinationPath)
            } else {
                uploadFile(task.sourcePath, task.destinationPath)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getTransfer(taskId: String): TransferTask? {
        return transferDao.getTransferById(taskId)?.toTransferTask()
    }

    override suspend fun clearHistory() {
        transferDao.clearHistory()
    }

    override fun getTransferProgress(taskId: String): Flow<TransferTask?> {
        return transferDao.getTransferByIdFlow(taskId).map { it?.toTransferTask() }
    }
}

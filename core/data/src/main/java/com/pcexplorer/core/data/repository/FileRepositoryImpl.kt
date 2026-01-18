package com.pcexplorer.core.data.repository

import com.pcexplorer.core.common.Logger
import com.pcexplorer.core.data.protocol.PayloadSerializer
import com.pcexplorer.core.data.protocol.ProtocolException
import com.pcexplorer.core.data.protocol.UsbPacket
import com.pcexplorer.core.data.protocol.UsbProtocol
import com.pcexplorer.core.domain.model.FileItem
import com.pcexplorer.core.domain.model.SortOrder
import com.pcexplorer.core.domain.model.StorageInfo
import com.pcexplorer.core.domain.repository.FileRepository
import com.pcexplorer.core.domain.repository.UsbConnectionRepository
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "FileRepository"

@Singleton
class FileRepositoryImpl @Inject constructor(
    private val usbConnectionRepository: UsbConnectionRepository
) : FileRepository {

    override suspend fun listFiles(path: String, sortOrder: SortOrder): Result<List<FileItem>> {
        return executeCommand(
            command = UsbProtocol.Commands.LIST_DIR,
            payload = PayloadSerializer.serializePath(path)
        ) { responseData ->
            PayloadSerializer.deserializeFileList(responseData)
        }
    }

    override suspend fun searchFiles(query: String, path: String): Result<List<FileItem>> {
        return executeCommand(
            command = UsbProtocol.Commands.SEARCH,
            payload = PayloadSerializer.serializeSearch(query, path)
        ) { responseData ->
            PayloadSerializer.deserializeFileList(responseData)
        }
    }

    override suspend fun getFileInfo(path: String): Result<FileItem> {
        return executeCommand(
            command = UsbProtocol.Commands.GET_FILE_INFO,
            payload = PayloadSerializer.serializePath(path)
        ) { responseData ->
            PayloadSerializer.deserializeFileItem(responseData)
        }
    }

    override suspend fun createFolder(path: String, name: String): Result<FileItem> {
        val fullPath = if (path.endsWith("/") || path.endsWith("\\")) {
            "$path$name"
        } else {
            "$path/$name"
        }
        return executeCommand(
            command = UsbProtocol.Commands.CREATE_DIR,
            payload = PayloadSerializer.serializePath(fullPath)
        ) { responseData ->
            PayloadSerializer.deserializeFileItem(responseData)
        }
    }

    override suspend fun rename(path: String, newName: String): Result<FileItem> {
        return executeCommand(
            command = UsbProtocol.Commands.RENAME,
            payload = PayloadSerializer.serializeRename(path, newName)
        ) { responseData ->
            PayloadSerializer.deserializeFileItem(responseData)
        }
    }

    override suspend fun delete(paths: List<String>): Result<Unit> {
        // Delete each path one by one
        for (path in paths) {
            val result = executeCommand<Unit>(
                command = UsbProtocol.Commands.DELETE,
                payload = PayloadSerializer.serializePath(path)
            ) { }

            if (result.isFailure) {
                return result
            }
        }
        return Result.success(Unit)
    }

    override suspend fun getStorageInfo(drivePath: String): Result<StorageInfo> {
        return executeCommand(
            command = UsbProtocol.Commands.GET_STORAGE_INFO,
            payload = PayloadSerializer.serializePath(drivePath)
        ) { responseData ->
            PayloadSerializer.deserializeStorageInfo(responseData)
        }
    }

    override suspend fun getDrives(): Result<List<String>> {
        return executeCommand(
            command = UsbProtocol.Commands.GET_DRIVES,
            payload = ByteArray(0)
        ) { responseData ->
            PayloadSerializer.deserializeDriveList(responseData)
        }
    }

    private suspend fun <T> executeCommand(
        command: Byte,
        payload: ByteArray,
        parseResponse: (ByteArray) -> T
    ): Result<T> {
        return try {
            // Create and send packet
            val packet = UsbPacket(command = command, payload = payload)
            val sendResult = usbConnectionRepository.sendData(packet.toBytes())
            if (sendResult.isFailure) {
                return Result.failure(sendResult.exceptionOrNull() ?: Exception("Failed to send command"))
            }

            // Receive response
            val responseBytes = usbConnectionRepository.receiveData(UsbProtocol.MAX_PACKET_SIZE)
                .getOrElse { return Result.failure(it) }

            // Parse response packet
            val responsePacket = UsbPacket.fromBytes(responseBytes)
                .getOrElse { return Result.failure(it) }

            // Handle response
            when (responsePacket.command) {
                UsbProtocol.Commands.RESPONSE_OK,
                UsbProtocol.Commands.RESPONSE_DATA -> {
                    Result.success(parseResponse(responsePacket.payload))
                }
                UsbProtocol.Commands.RESPONSE_ERROR -> {
                    val (errorCode, message) = PayloadSerializer.deserializeError(responsePacket.payload)
                    Result.failure(ProtocolException("Error $errorCode: $message"))
                }
                else -> {
                    Result.failure(ProtocolException("Unexpected response command: ${responsePacket.command}"))
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Command execution failed", e)
            Result.failure(e)
        }
    }
}

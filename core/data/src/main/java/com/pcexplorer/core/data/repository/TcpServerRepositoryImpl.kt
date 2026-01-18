package com.pcexplorer.core.data.repository

import com.pcexplorer.core.common.Logger
import com.pcexplorer.core.data.protocol.UsbPacket
import com.pcexplorer.core.data.protocol.UsbProtocol
import com.pcexplorer.core.domain.model.ConnectionState
import com.pcexplorer.core.domain.model.DeviceInfo
import com.pcexplorer.core.domain.repository.UsbConnectionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val TAG = "TcpServerRepo"

/**
 * TCP server-based connection repository for ADB forward mode.
 *
 * This is a fallback mode for devices where 'adb reverse' doesn't work properly
 * (e.g., older Huawei devices like Honor 7S/DUA-L22 where the reverse tunnel
 * is unidirectional - Android can send to PC but PC responses don't reach Android).
 *
 * In this mode, the roles are reversed compared to TCP_ADB:
 * - Android app acts as the SERVER (listens on a port)
 * - PC server acts as the CLIENT (connects through adb forward tunnel)
 *
 * Connection flow:
 * 1. Android app binds to localhost:5556 and starts listening
 * 2. PC executes: adb forward tcp:5555 tcp:5556
 * 3. PC server connects to localhost:5555 (its side of the tunnel)
 * 4. ADB forwards the connection to Android's localhost:5556
 * 5. Android app accepts the connection
 * 6. Handshake is performed (PC sends first in this mode)
 *
 * This approach works on more devices because 'adb forward' is more reliable
 * than 'adb reverse' on older devices. The key difference is that ADB acts
 * as a full bidirectional proxy in forward mode.
 */
class TcpServerRepositoryImpl : UsbConnectionRepository {

    companion object {
        // Port to listen on (on the Android device)
        private const val SERVER_PORT = 5556
        private const val ACCEPT_TIMEOUT_MS = 30000  // 30 seconds to wait for connection
        private const val READ_TIMEOUT_MS = 10000    // 10 seconds read timeout
    }

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: Flow<ConnectionState> = _connectionState.asStateFlow()

    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    override suspend fun connect(): Result<Unit> = withContext(Dispatchers.IO) {
        _connectionState.value = ConnectionState.Connecting

        Logger.i(TAG, "Starting TCP server mode (ADB forward fallback)")
        Logger.i(TAG, "Listening on localhost:$SERVER_PORT...")
        Logger.i(TAG, "Waiting for PC to connect via 'adb forward tcp:5555 tcp:$SERVER_PORT'...")

        try {
            // Create server socket - bind to localhost only for security
            serverSocket = ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress("127.0.0.1", SERVER_PORT))
                soTimeout = ACCEPT_TIMEOUT_MS
            }

            Logger.i(TAG, "Server socket listening on port $SERVER_PORT")
            Logger.d(TAG, "Accept timeout: ${ACCEPT_TIMEOUT_MS}ms, Read timeout: ${READ_TIMEOUT_MS}ms")

            // Wait for PC to connect
            clientSocket = serverSocket?.accept()?.apply {
                soTimeout = READ_TIMEOUT_MS
            }

            if (clientSocket == null) {
                throw IOException("Server socket accept returned null")
            }

            inputStream = clientSocket?.getInputStream()
            outputStream = clientSocket?.getOutputStream()

            Logger.i(TAG, "PC connected from ${clientSocket?.remoteSocketAddress}")

            // In forward mode, the PC sends the handshake first
            val handshakeResult = receiveHandshake()
            if (handshakeResult.isFailure) {
                throw handshakeResult.exceptionOrNull() ?: Exception("Handshake failed")
            }

            // Create device info for this connection
            val deviceInfo = DeviceInfo(
                vendorId = 0,
                productId = 0,
                deviceName = "TCP:Forward:$SERVER_PORT",
                manufacturerName = "PC Server (Forward Mode)",
                productName = "PC Explorer",
                serialNumber = null
            )

            _connectionState.value = ConnectionState.Connected(deviceInfo)
            Logger.i(TAG, "Connected to PC via ADB forward tunnel")
            Result.success(Unit)
        } catch (e: SocketTimeoutException) {
            val message = "Connection timed out. Make sure the PC server is running with --forward flag."
            Logger.e(TAG, message, e)
            disconnectInternal()
            _connectionState.value = ConnectionState.Error(message, e)
            Result.failure(Exception(message, e))
        } catch (e: IOException) {
            val message = "Server socket error: ${e.message}. Check if another app is using port $SERVER_PORT."
            Logger.e(TAG, message, e)
            disconnectInternal()
            _connectionState.value = ConnectionState.Error(message, e)
            Result.failure(Exception(message, e))
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to start server", e)
            disconnectInternal()
            _connectionState.value = ConnectionState.Error(e.message ?: "Server failed", e)
            Result.failure(e)
        }
    }

    /**
     * Receive handshake from PC (PC initiates in forward mode).
     *
     * In forward mode, the connection direction is reversed, so the PC
     * sends the handshake first and we respond.
     */
    private suspend fun receiveHandshake(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Logger.d(TAG, "Waiting for handshake from PC...")

            // Receive handshake from PC
            val response = receiveData(UsbProtocol.DEFAULT_BUFFER_SIZE).getOrThrow()
            val packet = UsbPacket.fromBytes(response).getOrThrow()

            if (packet.command != UsbProtocol.Commands.HANDSHAKE) {
                throw Exception("Expected HANDSHAKE command, got ${packet.command}")
            }

            val serverInfo = String(packet.payload)
            Logger.i(TAG, "Handshake from PC: $serverInfo")

            // Send our handshake response
            val responsePacket = UsbPacket(
                command = UsbProtocol.Commands.RESPONSE_OK,
                payload = "PCEX-Android-1.0-Forward".toByteArray()
            )
            sendData(responsePacket.toBytes()).getOrThrow()

            Logger.d(TAG, "Handshake completed successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Logger.e(TAG, "Handshake failed", e)
            Result.failure(e)
        }
    }

    override suspend fun disconnect() {
        withContext(Dispatchers.IO) {
            // No disconnect command needed in server mode - just close connections
            Logger.i(TAG, "Disconnecting server mode connection...")
            disconnectInternal()
            _connectionState.value = ConnectionState.Disconnected
        }
    }

    private fun disconnectInternal() {
        try {
            inputStream?.close()
            outputStream?.close()
            clientSocket?.close()
            serverSocket?.close()
        } catch (e: Exception) {
            Logger.w(TAG, "Error during disconnect", e)
        } finally {
            inputStream = null
            outputStream = null
            clientSocket = null
            serverSocket = null
        }
    }

    override suspend fun requestPermission() {
        // TCP doesn't require Android permissions like USB does
        // Just try to start the server
        connect()
    }

    override fun hasPermission(): Boolean {
        // TCP connections don't require special permissions
        return true
    }

    override suspend fun sendData(data: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        val out = outputStream ?: return@withContext Result.failure(Exception("Not connected"))

        try {
            out.write(data)
            out.flush()
            Logger.d(TAG, "Sent ${data.size} bytes")
            Result.success(Unit)
        } catch (e: Exception) {
            Logger.e(TAG, "Error sending data", e)
            Result.failure(e)
        }
    }

    override suspend fun receiveData(bufferSize: Int): Result<ByteArray> = withContext(Dispatchers.IO) {
        val input = inputStream ?: return@withContext Result.failure(Exception("Not connected"))

        try {
            // First, read the header to get the payload length
            // Header: Magic(4) + Command(1) + Flags(1) + Length(4) = 10 bytes
            // Then payload, then Checksum(4)
            val headerSize = 10
            val header = ByteArray(headerSize)
            var headerBytesRead = 0

            while (headerBytesRead < headerSize) {
                val read = input.read(header, headerBytesRead, headerSize - headerBytesRead)
                if (read == -1) {
                    return@withContext Result.failure(Exception("Connection closed"))
                }
                headerBytesRead += read
            }

            // Parse payload length from header (bytes 6-9, little endian)
            val payloadLength = ByteBuffer.wrap(header, 6, 4)
                .order(ByteOrder.LITTLE_ENDIAN)
                .getInt()

            // Read payload + checksum (4 bytes)
            val remaining = payloadLength + 4
            val fullPacket = ByteArray(headerSize + remaining)
            System.arraycopy(header, 0, fullPacket, 0, headerSize)

            var totalRead = 0
            while (totalRead < remaining) {
                val read = input.read(fullPacket, headerSize + totalRead, remaining - totalRead)
                if (read == -1) {
                    return@withContext Result.failure(Exception("Connection closed during read"))
                }
                totalRead += read
            }

            Logger.d(TAG, "Received ${fullPacket.size} bytes")
            Result.success(fullPacket)
        } catch (e: SocketTimeoutException) {
            Logger.w(TAG, "Read timeout", e)
            Result.failure(Exception("Read timeout", e))
        } catch (e: Exception) {
            Logger.e(TAG, "Error receiving data", e)
            Result.failure(e)
        }
    }
}

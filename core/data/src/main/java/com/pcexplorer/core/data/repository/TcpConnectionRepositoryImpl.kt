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
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val TAG = "TcpConnectionRepo"

/**
 * TCP-based connection repository for ADB reverse mode.
 *
 * When using ADB reverse forwarding (adb reverse tcp:5555 tcp:5555),
 * the Android app connects to localhost:5555 which is tunneled to the PC server.
 *
 * Connection flow:
 * 1. PC Server binds to localhost:5555
 * 2. adb reverse tcp:5555 tcp:5555 is executed
 * 3. Android app connects to localhost:5555 (this class)
 * 4. Traffic is tunneled through ADB to the PC server
 */
class TcpConnectionRepositoryImpl : UsbConnectionRepository {

    companion object {
        // Use IP address directly to avoid DNS resolution issues on some Android devices
        // where "localhost" may not resolve correctly
        private const val DEFAULT_HOST = "127.0.0.1"
        private const val DEFAULT_PORT = 5555
        private const val CONNECT_TIMEOUT_MS = 5000
        private const val READ_TIMEOUT_MS = 10000
    }

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: Flow<ConnectionState> = _connectionState.asStateFlow()

    private var socket: Socket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    private var host: String = DEFAULT_HOST
    private var port: Int = DEFAULT_PORT

    /**
     * Configure the connection parameters.
     *
     * @param host The host to connect to (default: 127.0.0.1 for ADB reverse)
     * @param port The port to connect to (default: 5555)
     */
    fun configure(host: String = DEFAULT_HOST, port: Int = DEFAULT_PORT) {
        // Validate port range
        val validPort = if (port in 1..65535) port else DEFAULT_PORT
        if (port != validPort) {
            Logger.w(TAG, "Invalid port $port, using default $DEFAULT_PORT")
        }

        // Normalize localhost to 127.0.0.1 to avoid DNS resolution issues
        val normalizedHost = if (host.equals("localhost", ignoreCase = true)) {
            Logger.d(TAG, "Normalizing 'localhost' to '127.0.0.1'")
            "127.0.0.1"
        } else {
            host
        }

        this.host = normalizedHost
        this.port = validPort
        Logger.d(TAG, "Configured TCP connection: $normalizedHost:$validPort")
    }

    override suspend fun connect(): Result<Unit> = withContext(Dispatchers.IO) {
        _connectionState.value = ConnectionState.Connecting

        // Validate configuration before connecting
        if (port !in 1..65535) {
            val message = "Invalid port configuration: $port. Please check settings."
            Logger.e(TAG, message)
            _connectionState.value = ConnectionState.Error(message)
            return@withContext Result.failure(Exception(message))
        }

        Logger.i(TAG, "Connecting to $host:$port via TCP...")
        Logger.d(TAG, "Creating socket with timeout: connect=${CONNECT_TIMEOUT_MS}ms, read=${READ_TIMEOUT_MS}ms")

        try {
            // Create socket address - log for debugging
            val socketAddress = InetSocketAddress(host, port)
            Logger.d(TAG, "Socket address created: ${socketAddress.hostString}:${socketAddress.port}, unresolved=${socketAddress.isUnresolved}")

            // Create socket and connect
            socket = Socket().apply {
                soTimeout = READ_TIMEOUT_MS
                connect(socketAddress, CONNECT_TIMEOUT_MS)
            }

            inputStream = socket?.getInputStream()
            outputStream = socket?.getOutputStream()

            Logger.i(TAG, "TCP socket connected to $host:$port")

            // Send handshake
            val handshakeResult = sendHandshake()
            if (handshakeResult.isFailure) {
                throw handshakeResult.exceptionOrNull() ?: Exception("Handshake failed")
            }

            // Create device info for TCP connection
            val deviceInfo = DeviceInfo(
                vendorId = 0,
                productId = 0,
                deviceName = "TCP:$host:$port",
                manufacturerName = "PC Server",
                productName = "PC Explorer",
                serialNumber = null
            )

            _connectionState.value = ConnectionState.Connected(deviceInfo)
            Logger.i(TAG, "Connected to PC via TCP at $host:$port")
            Result.success(Unit)
        } catch (e: SocketTimeoutException) {
            val message = "Connection timed out. Make sure the PC server is running."
            Logger.e(TAG, message, e)
            disconnectInternal()
            _connectionState.value = ConnectionState.Error(message, e)
            Result.failure(Exception(message, e))
        } catch (e: IOException) {
            val message = "Connection failed: ${e.message}. Make sure the PC server is running and ADB is connected."
            Logger.e(TAG, message, e)
            disconnectInternal()
            _connectionState.value = ConnectionState.Error(message, e)
            Result.failure(Exception(message, e))
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to connect via TCP", e)
            disconnectInternal()
            _connectionState.value = ConnectionState.Error(e.message ?: "Connection failed", e)
            Result.failure(e)
        }
    }

    private suspend fun sendHandshake(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val packet = UsbPacket(
                command = UsbProtocol.Commands.HANDSHAKE,
                payload = "PCEX-Android-1.0".toByteArray()
            )

            sendData(packet.toBytes()).getOrThrow()

            val response = receiveData(UsbProtocol.DEFAULT_BUFFER_SIZE).getOrThrow()
            val responsePacket = UsbPacket.fromBytes(response).getOrThrow()

            if (responsePacket.command != UsbProtocol.Commands.RESPONSE_OK) {
                throw Exception("Handshake rejected by server")
            }

            Logger.d(TAG, "Handshake successful")
            Result.success(Unit)
        } catch (e: Exception) {
            Logger.e(TAG, "Handshake failed", e)
            Result.failure(e)
        }
    }

    override suspend fun disconnect() {
        withContext(Dispatchers.IO) {
            // Send disconnect command
            try {
                val packet = UsbPacket(command = UsbProtocol.Commands.DISCONNECT)
                sendData(packet.toBytes())
            } catch (e: Exception) {
                Logger.w(TAG, "Error sending disconnect command", e)
            }

            disconnectInternal()
            _connectionState.value = ConnectionState.Disconnected
        }
    }

    private fun disconnectInternal() {
        try {
            inputStream?.close()
            outputStream?.close()
            socket?.close()
        } catch (e: Exception) {
            Logger.w(TAG, "Error during disconnect", e)
        } finally {
            inputStream = null
            outputStream = null
            socket = null
        }
    }

    override suspend fun requestPermission() {
        // TCP doesn't require Android permissions like USB does
        // Just try to connect
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
            val headerSize = 10 // Bytes before payload
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

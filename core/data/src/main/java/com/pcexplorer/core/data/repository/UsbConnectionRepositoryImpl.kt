package com.pcexplorer.core.data.repository

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import com.pcexplorer.core.common.Logger
import com.pcexplorer.core.data.protocol.UsbPacket
import com.pcexplorer.core.data.protocol.UsbProtocol
import com.pcexplorer.core.domain.model.ConnectionState
import com.pcexplorer.core.domain.model.DeviceInfo
import com.pcexplorer.core.domain.repository.UsbConnectionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

private const val TAG = "UsbConnectionRepo"
private const val ACTION_USB_PERMISSION = "com.pcexplorer.USB_PERMISSION"

@Singleton
class UsbConnectionRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : UsbConnectionRepository {

    private val usbManager: UsbManager by lazy {
        context.getSystemService(Context.USB_SERVICE) as UsbManager
    }

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: Flow<ConnectionState> = _connectionState.asStateFlow()

    private var currentDevice: UsbDevice? = null
    private var connection: UsbDeviceConnection? = null
    private var usbInterface: UsbInterface? = null
    private var endpointIn: UsbEndpoint? = null
    private var endpointOut: UsbEndpoint? = null

    private var permissionCallback: ((Boolean) -> Unit)? = null

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_USB_PERMISSION -> {
                    synchronized(this@UsbConnectionRepositoryImpl) {
                        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                        }
                        val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                        Logger.d(TAG, "USB permission result: granted=$granted, device=${device?.deviceName}")
                        permissionCallback?.invoke(granted)
                        permissionCallback = null
                    }
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    Logger.d(TAG, "USB device attached")
                    // Device attached - connection will be handled by Activity
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }
                    if (device == currentDevice) {
                        Logger.d(TAG, "Current USB device detached")
                        disconnectInternal()
                        _connectionState.value = ConnectionState.Disconnected
                    }
                }
            }
        }
    }

    init {
        registerReceiver()
    }

    private fun registerReceiver() {
        val filter = IntentFilter().apply {
            addAction(ACTION_USB_PERMISSION)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(usbReceiver, filter)
        }
    }

    override suspend fun connect(): Result<Unit> {
        _connectionState.value = ConnectionState.Connecting

        // Find connected USB devices
        val deviceList = usbManager.deviceList
        if (deviceList.isEmpty()) {
            _connectionState.value = ConnectionState.Disconnected
            return Result.failure(Exception("No USB devices found"))
        }

        // For now, try to connect to the first available device
        // In production, you'd filter for specific device types
        val device = deviceList.values.firstOrNull()
        if (device == null) {
            _connectionState.value = ConnectionState.Disconnected
            return Result.failure(Exception("No compatible USB device found"))
        }

        // Check permission
        if (!usbManager.hasPermission(device)) {
            _connectionState.value = ConnectionState.PermissionRequired
            return Result.failure(Exception("USB permission required"))
        }

        return connectToDevice(device)
    }

    private suspend fun connectToDevice(device: UsbDevice): Result<Unit> {
        return try {
            currentDevice = device

            // Open connection
            connection = usbManager.openDevice(device)
            if (connection == null) {
                throw Exception("Failed to open USB connection")
            }

            // Find bulk transfer endpoints
            for (i in 0 until device.interfaceCount) {
                val iface = device.getInterface(i)
                for (j in 0 until iface.endpointCount) {
                    val endpoint = iface.getEndpoint(j)
                    if (endpoint.type == android.hardware.usb.UsbConstants.USB_ENDPOINT_XFER_BULK) {
                        if (endpoint.direction == android.hardware.usb.UsbConstants.USB_DIR_IN) {
                            endpointIn = endpoint
                        } else {
                            endpointOut = endpoint
                        }
                    }
                }
                if (endpointIn != null && endpointOut != null) {
                    usbInterface = iface
                    break
                }
            }

            if (usbInterface == null || endpointIn == null || endpointOut == null) {
                throw Exception("No suitable USB endpoints found")
            }

            // Claim interface
            if (!connection!!.claimInterface(usbInterface, true)) {
                throw Exception("Failed to claim USB interface")
            }

            // Send handshake
            val handshakeResult = sendHandshake()
            if (handshakeResult.isFailure) {
                throw handshakeResult.exceptionOrNull() ?: Exception("Handshake failed")
            }

            val deviceInfo = DeviceInfo(
                vendorId = device.vendorId,
                productId = device.productId,
                deviceName = device.deviceName,
                manufacturerName = device.manufacturerName,
                productName = device.productName,
                serialNumber = device.serialNumber
            )

            _connectionState.value = ConnectionState.Connected(deviceInfo)
            Logger.i(TAG, "Connected to USB device: ${device.deviceName}")
            Result.success(Unit)
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to connect to USB device", e)
            disconnectInternal()
            _connectionState.value = ConnectionState.Error(e.message ?: "Connection failed", e)
            Result.failure(e)
        }
    }

    private suspend fun sendHandshake(): Result<Unit> {
        val packet = UsbPacket(
            command = UsbProtocol.Commands.HANDSHAKE,
            payload = "PCEX-Android-1.0".toByteArray()
        )
        return sendData(packet.toBytes()).mapCatching {
            val response = receiveData(UsbProtocol.DEFAULT_BUFFER_SIZE).getOrThrow()
            val responsePacket = UsbPacket.fromBytes(response).getOrThrow()
            if (responsePacket.command != UsbProtocol.Commands.RESPONSE_OK) {
                throw Exception("Handshake rejected by server")
            }
        }
    }

    override suspend fun disconnect() {
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

    private fun disconnectInternal() {
        try {
            usbInterface?.let { iface ->
                connection?.releaseInterface(iface)
            }
            connection?.close()
        } catch (e: Exception) {
            Logger.w(TAG, "Error during disconnect", e)
        } finally {
            connection = null
            usbInterface = null
            endpointIn = null
            endpointOut = null
            currentDevice = null
        }
    }

    override suspend fun requestPermission() {
        val device = usbManager.deviceList.values.firstOrNull() ?: return

        if (usbManager.hasPermission(device)) {
            connectToDevice(device)
            return
        }

        suspendCancellableCoroutine { continuation ->
            permissionCallback = { granted ->
                if (granted) {
                    continuation.resume(Unit)
                } else {
                    continuation.resume(Unit)
                }
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                Intent(ACTION_USB_PERMISSION),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            usbManager.requestPermission(device, pendingIntent)
        }

        // After permission is granted, try to connect
        if (usbManager.hasPermission(device)) {
            connectToDevice(device)
        } else {
            _connectionState.value = ConnectionState.PermissionRequired
        }
    }

    override fun hasPermission(): Boolean {
        val device = usbManager.deviceList.values.firstOrNull() ?: return false
        return usbManager.hasPermission(device)
    }

    override suspend fun sendData(data: ByteArray): Result<Unit> {
        val conn = connection ?: return Result.failure(Exception("Not connected"))
        val endpoint = endpointOut ?: return Result.failure(Exception("No output endpoint"))

        return try {
            val sent = conn.bulkTransfer(endpoint, data, data.size, UsbProtocol.TIMEOUT_MS)
            if (sent < 0) {
                Result.failure(Exception("Failed to send data"))
            } else {
                Logger.d(TAG, "Sent $sent bytes")
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Error sending data", e)
            Result.failure(e)
        }
    }

    override suspend fun receiveData(bufferSize: Int): Result<ByteArray> {
        val conn = connection ?: return Result.failure(Exception("Not connected"))
        val endpoint = endpointIn ?: return Result.failure(Exception("No input endpoint"))

        return try {
            val buffer = ByteArray(bufferSize)
            val received = conn.bulkTransfer(endpoint, buffer, buffer.size, UsbProtocol.TIMEOUT_MS)
            if (received < 0) {
                Result.failure(Exception("Failed to receive data"))
            } else {
                Logger.d(TAG, "Received $received bytes")
                Result.success(buffer.copyOf(received))
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Error receiving data", e)
            Result.failure(e)
        }
    }
}

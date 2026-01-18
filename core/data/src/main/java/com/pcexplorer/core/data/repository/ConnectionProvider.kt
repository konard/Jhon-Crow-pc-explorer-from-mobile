package com.pcexplorer.core.data.repository

import android.content.Context
import com.pcexplorer.core.common.Logger
import com.pcexplorer.core.domain.model.ConnectionMode
import com.pcexplorer.core.domain.model.ConnectionState
import com.pcexplorer.core.domain.repository.UsbConnectionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ConnectionProvider"
// Use the same SharedPreferences file as SettingsViewModel to ensure settings sync
private const val PREFS_NAME = "settings"
private const val KEY_CONNECTION_MODE = "connection_mode"
private const val KEY_WIFI_HOST = "wifi_host"
private const val KEY_WIFI_PORT = "wifi_port"

/**
 * Provides the appropriate connection repository based on the selected mode.
 *
 * This class manages the connection mode selection and delegates to either
 * USB or TCP connection implementations.
 */
@Singleton
class ConnectionProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val usbRepository: UsbConnectionRepositoryImpl,
    private val tcpRepository: TcpConnectionRepositoryImpl,
    private val tcpServerRepository: TcpServerRepositoryImpl
) : UsbConnectionRepository {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _connectionModeFlow = MutableStateFlow(loadConnectionMode())
    val connectionModeFlow: Flow<ConnectionMode> = _connectionModeFlow.asStateFlow()

    private var currentRepository: UsbConnectionRepository = selectRepository(_connectionModeFlow.value)

    override val connectionState: Flow<ConnectionState>
        get() = currentRepository.connectionState

    /**
     * Get the current connection mode.
     */
    fun getConnectionMode(): ConnectionMode = _connectionModeFlow.value

    /**
     * Set the connection mode.
     * This will disconnect any existing connection and switch to the new mode.
     */
    suspend fun setConnectionMode(mode: ConnectionMode) {
        if (_connectionModeFlow.value != mode) {
            Logger.i(TAG, "Switching connection mode from ${_connectionModeFlow.value} to $mode")

            // Disconnect current connection
            currentRepository.disconnect()

            // Save and update mode
            prefs.edit().putString(KEY_CONNECTION_MODE, mode.name).apply()
            _connectionModeFlow.value = mode

            // Switch repository
            currentRepository = selectRepository(mode)
        }
    }

    /**
     * Get the Wi-Fi host address for TCP_WIFI mode.
     */
    fun getWifiHost(): String = prefs.getString(KEY_WIFI_HOST, "") ?: ""

    /**
     * Set the Wi-Fi host address for TCP_WIFI mode.
     */
    fun setWifiHost(host: String) {
        prefs.edit().putString(KEY_WIFI_HOST, host).apply()
    }

    /**
     * Get the Wi-Fi port for TCP_WIFI mode.
     */
    fun getWifiPort(): Int = prefs.getInt(KEY_WIFI_PORT, 5555)

    /**
     * Set the Wi-Fi port for TCP_WIFI mode.
     */
    fun setWifiPort(port: Int) {
        prefs.edit().putInt(KEY_WIFI_PORT, port).apply()
    }

    private fun loadConnectionMode(): ConnectionMode {
        val modeName = prefs.getString(KEY_CONNECTION_MODE, ConnectionMode.TCP_ADB.name)
        return try {
            ConnectionMode.valueOf(modeName ?: ConnectionMode.TCP_ADB.name)
        } catch (e: IllegalArgumentException) {
            Logger.w(TAG, "Invalid connection mode: $modeName, defaulting to TCP_ADB")
            ConnectionMode.TCP_ADB
        }
    }

    private fun selectRepository(mode: ConnectionMode): UsbConnectionRepository {
        return when (mode) {
            ConnectionMode.USB -> {
                Logger.d(TAG, "Using USB connection repository")
                usbRepository
            }
            ConnectionMode.TCP_ADB -> {
                Logger.d(TAG, "Using TCP connection repository (ADB mode)")
                // Use 127.0.0.1 instead of "localhost" to avoid DNS resolution issues
                tcpRepository.configure(host = "127.0.0.1", port = 5555)
                tcpRepository
            }
            ConnectionMode.TCP_FORWARD -> {
                Logger.d(TAG, "Using TCP server repository (ADB forward fallback mode)")
                // In forward mode, the Android app acts as a server and PC connects to us
                // This works on devices where adb reverse tunnel is unidirectional
                tcpServerRepository
            }
            ConnectionMode.TCP_WIFI -> {
                val host = getWifiHost()
                val port = getWifiPort()
                Logger.d(TAG, "Using TCP connection repository (Wi-Fi mode: $host:$port)")
                tcpRepository.configure(host = host, port = port)
                tcpRepository
            }
            ConnectionMode.AUTO -> {
                // Default to TCP_ADB for auto mode
                Logger.d(TAG, "AUTO mode: defaulting to TCP connection (ADB)")
                // Use 127.0.0.1 instead of "localhost" to avoid DNS resolution issues
                tcpRepository.configure(host = "127.0.0.1", port = 5555)
                tcpRepository
            }
        }
    }

    override suspend fun connect(): Result<Unit> {
        Logger.i(TAG, "Connecting using mode: ${_connectionModeFlow.value}")
        return currentRepository.connect()
    }

    override suspend fun disconnect() {
        currentRepository.disconnect()
    }

    override suspend fun requestPermission() {
        currentRepository.requestPermission()
    }

    override fun hasPermission(): Boolean {
        return currentRepository.hasPermission()
    }

    override suspend fun sendData(data: ByteArray): Result<Unit> {
        return currentRepository.sendData(data)
    }

    override suspend fun receiveData(bufferSize: Int): Result<ByteArray> {
        return currentRepository.receiveData(bufferSize)
    }
}

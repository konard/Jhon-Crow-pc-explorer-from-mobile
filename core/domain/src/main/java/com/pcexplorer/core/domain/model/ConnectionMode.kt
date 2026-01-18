package com.pcexplorer.core.domain.model

/**
 * Connection mode for communicating with the PC server.
 */
enum class ConnectionMode {
    /**
     * Direct USB connection.
     * Uses Android USB Host API for direct communication.
     * Requires USB permission from the user.
     *
     * Note: This mode has known limitations on Windows due to driver issues.
     */
    USB,

    /**
     * TCP connection via ADB reverse forwarding.
     * Connects to localhost:5555 which is tunneled to PC via ADB.
     *
     * This is the recommended mode when:
     * - USB cable is connected
     * - ADB (USB debugging) is enabled on the phone
     * - PC server is running in ADB mode
     *
     * The PC server sets up "adb reverse tcp:5555 tcp:5555" which makes
     * the phone's localhost:5555 forward to the PC's localhost:5555.
     */
    TCP_ADB,

    /**
     * TCP connection via Wi-Fi.
     * Connects directly to the PC's IP address on port 5555.
     *
     * This mode works when:
     * - Phone and PC are on the same network
     * - PC server is running in simulation mode (--simulate)
     * - User knows the PC's IP address
     *
     * No USB cable or ADB is required.
     */
    TCP_WIFI,

    /**
     * Automatic mode selection.
     * Tries TCP_ADB first (if USB is connected), then falls back to USB.
     */
    AUTO
}

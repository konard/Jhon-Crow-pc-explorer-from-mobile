package com.pcexplorer.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
import com.pcexplorer.core.common.Logger

private const val TAG = "UsbPermissionReceiver"

/**
 * Receives USB permission results.
 * Note: The actual permission handling is done in UsbConnectionRepositoryImpl
 * This receiver is mainly for logging and debugging purposes.
 */
class UsbPermissionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.pcexplorer.USB_PERMISSION") {
            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
            Logger.d(TAG, "USB permission received: granted=$granted")
        }
    }
}

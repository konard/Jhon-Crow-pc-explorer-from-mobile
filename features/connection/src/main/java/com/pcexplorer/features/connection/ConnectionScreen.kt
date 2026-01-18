package com.pcexplorer.features.connection

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.UsbOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pcexplorer.core.domain.model.ConnectionState

@Composable
fun ConnectionScreen(
    onConnected: () -> Unit,
    viewModel: ConnectionViewModel = hiltViewModel()
) {
    val connectionState by viewModel.connectionState.collectAsState()

    // Navigate when connected
    if (connectionState is ConnectionState.Connected) {
        onConnected()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        ConnectionStatusCard(
            state = connectionState,
            onConnect = { viewModel.connect() },
            onDisconnect = { viewModel.disconnect() },
            onRequestPermission = { viewModel.requestPermission() }
        )
    }
}

@Composable
private fun ConnectionStatusCard(
    state: ConnectionState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onRequestPermission: () -> Unit
) {
    Card(
        modifier = Modifier.padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Icon based on state
            ConnectionIcon(state = state)

            Spacer(modifier = Modifier.height(24.dp))

            // Status text
            ConnectionStatusText(state = state)

            Spacer(modifier = Modifier.height(24.dp))

            // Action button
            ConnectionActionButton(
                state = state,
                onConnect = onConnect,
                onDisconnect = onDisconnect,
                onRequestPermission = onRequestPermission
            )
        }
    }
}

@Composable
private fun ConnectionIcon(state: ConnectionState) {
    val (icon, tint) = when (state) {
        is ConnectionState.Connected -> Icons.Filled.Check to Color(0xFF4CAF50)
        is ConnectionState.Connecting -> Icons.Filled.Usb to MaterialTheme.colorScheme.primary
        is ConnectionState.Error -> Icons.Filled.Error to MaterialTheme.colorScheme.error
        is ConnectionState.PermissionRequired -> Icons.Filled.Usb to Color(0xFFFFA000)
        is ConnectionState.Disconnected -> Icons.Filled.UsbOff to MaterialTheme.colorScheme.onSurfaceVariant
    }

    if (state is ConnectionState.Connecting) {
        CircularProgressIndicator(
            modifier = Modifier.size(80.dp),
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 4.dp
        )
    } else {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = tint
        )
    }
}

@Composable
private fun ConnectionStatusText(state: ConnectionState) {
    val (title, subtitle) = when (state) {
        is ConnectionState.Connected -> {
            "Connected" to "Device: ${state.deviceInfo.productName ?: state.deviceInfo.deviceName}"
        }
        is ConnectionState.Connecting -> "Connecting..." to "Please wait"
        is ConnectionState.Error -> "Connection Error" to state.message
        is ConnectionState.PermissionRequired -> "Permission Required" to "USB permission needed to continue"
        is ConnectionState.Disconnected -> "Not Connected" to "Connect your Android device to PC via USB"
    }

    Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun ConnectionActionButton(
    state: ConnectionState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onRequestPermission: () -> Unit
) {
    when (state) {
        is ConnectionState.Disconnected -> {
            Button(onClick = onConnect) {
                Text("Connect")
            }
        }
        is ConnectionState.Connected -> {
            OutlinedButton(onClick = onDisconnect) {
                Text("Disconnect")
            }
        }
        is ConnectionState.PermissionRequired -> {
            Button(onClick = onRequestPermission) {
                Text("Grant Permission")
            }
        }
        is ConnectionState.Error -> {
            Button(onClick = onConnect) {
                Text("Retry")
            }
        }
        is ConnectionState.Connecting -> {
            // No button while connecting
        }
    }
}

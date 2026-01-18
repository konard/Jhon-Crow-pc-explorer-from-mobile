package com.pcexplorer.features.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pcexplorer.core.domain.model.ConnectionMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showThemeDialog by remember { mutableStateOf(false) }
    var showConnectionModeDialog by remember { mutableStateOf(false) }
    var showWifiSettingsDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // Appearance section
            SettingsSectionHeader(title = "Appearance")

            ListItem(
                modifier = Modifier.clickable { showThemeDialog = true },
                headlineContent = { Text("Theme") },
                supportingContent = {
                    Text(
                        when (uiState.themeMode) {
                            ThemeMode.LIGHT -> "Light"
                            ThemeMode.DARK -> "Dark"
                            ThemeMode.SYSTEM -> "Follow system"
                        }
                    )
                },
                leadingContent = {
                    Icon(
                        imageVector = when (uiState.themeMode) {
                            ThemeMode.LIGHT -> Icons.Filled.LightMode
                            ThemeMode.DARK -> Icons.Filled.DarkMode
                            ThemeMode.SYSTEM -> Icons.Filled.DarkMode
                        },
                        contentDescription = null
                    )
                }
            )

            HorizontalDivider()

            // Connection section
            SettingsSectionHeader(title = "Connection")

            ListItem(
                modifier = Modifier.clickable { showConnectionModeDialog = true },
                headlineContent = { Text("Connection mode") },
                supportingContent = {
                    Text(
                        when (uiState.connectionMode) {
                            ConnectionMode.USB -> "Direct USB (requires driver setup)"
                            ConnectionMode.TCP_ADB -> "USB with ADB (recommended)"
                            ConnectionMode.TCP_FORWARD -> "ADB Forward (for older devices)"
                            ConnectionMode.TCP_WIFI -> "Wi-Fi (${uiState.wifiHost}:${uiState.wifiPort})"
                            ConnectionMode.AUTO -> "Automatic"
                        }
                    )
                },
                leadingContent = {
                    Icon(
                        imageVector = when (uiState.connectionMode) {
                            ConnectionMode.USB -> Icons.Filled.Usb
                            ConnectionMode.TCP_ADB -> Icons.Filled.Cable
                            ConnectionMode.TCP_FORWARD -> Icons.Filled.SwapHoriz
                            ConnectionMode.TCP_WIFI -> Icons.Filled.Wifi
                            ConnectionMode.AUTO -> Icons.Filled.Cable
                        },
                        contentDescription = null
                    )
                }
            )

            if (uiState.connectionMode == ConnectionMode.TCP_WIFI) {
                ListItem(
                    modifier = Modifier.clickable { showWifiSettingsDialog = true },
                    headlineContent = { Text("Wi-Fi server address") },
                    supportingContent = { Text("${uiState.wifiHost}:${uiState.wifiPort}") },
                    leadingContent = {
                        Icon(Icons.Filled.Wifi, contentDescription = null)
                    }
                )
            }

            ListItem(
                headlineContent = { Text("Auto-connect on USB attachment") },
                supportingContent = { Text("Automatically connect when a USB device is attached") },
                leadingContent = {
                    Icon(Icons.Filled.Usb, contentDescription = null)
                },
                trailingContent = {
                    Switch(
                        checked = uiState.autoConnect,
                        onCheckedChange = { viewModel.setAutoConnect(it) }
                    )
                }
            )

            HorizontalDivider()

            // Transfer section
            SettingsSectionHeader(title = "Transfer")

            ListItem(
                headlineContent = { Text("Buffer size: ${uiState.bufferSizeKb} KB") },
                supportingContent = {
                    Column {
                        Text("Larger buffer = faster transfers, more memory")
                        Spacer(modifier = Modifier.height(8.dp))
                        Slider(
                            value = uiState.bufferSizeKb.toFloat(),
                            onValueChange = { viewModel.setBufferSize(it.toInt()) },
                            valueRange = 8f..128f,
                            steps = 7
                        )
                    }
                },
                leadingContent = {
                    Icon(Icons.Filled.Speed, contentDescription = null)
                }
            )

            ListItem(
                headlineContent = { Text("Parallel transfers: ${uiState.parallelTransfers}") },
                supportingContent = {
                    Column {
                        Text("Number of simultaneous file transfers")
                        Spacer(modifier = Modifier.height(8.dp))
                        Slider(
                            value = uiState.parallelTransfers.toFloat(),
                            onValueChange = { viewModel.setParallelTransfers(it.toInt()) },
                            valueRange = 1f..4f,
                            steps = 2
                        )
                    }
                },
                leadingContent = {
                    Icon(Icons.Filled.Speed, contentDescription = null)
                }
            )

            HorizontalDivider()

            // About section
            SettingsSectionHeader(title = "About")

            ListItem(
                headlineContent = { Text("Version") },
                supportingContent = { Text(uiState.appVersion) },
                leadingContent = {
                    Icon(Icons.Filled.Info, contentDescription = null)
                }
            )

            ListItem(
                headlineContent = { Text("PC Explorer") },
                supportingContent = { Text("Access your PC files from Android via USB") },
                leadingContent = {
                    Icon(Icons.Filled.Info, contentDescription = null)
                }
            )
        }
    }

    // Theme selection dialog
    if (showThemeDialog) {
        ThemeSelectionDialog(
            currentTheme = uiState.themeMode,
            onThemeSelected = {
                viewModel.setThemeMode(it)
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false }
        )
    }

    // Connection mode selection dialog
    if (showConnectionModeDialog) {
        ConnectionModeDialog(
            currentMode = uiState.connectionMode,
            onModeSelected = {
                viewModel.setConnectionMode(it)
                showConnectionModeDialog = false
            },
            onDismiss = { showConnectionModeDialog = false }
        )
    }

    // Wi-Fi settings dialog
    if (showWifiSettingsDialog) {
        WifiSettingsDialog(
            currentHost = uiState.wifiHost,
            currentPort = uiState.wifiPort,
            onSave = { host, port ->
                viewModel.setWifiHost(host)
                viewModel.setWifiPort(port)
                showWifiSettingsDialog = false
            },
            onDismiss = { showWifiSettingsDialog = false }
        )
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
    )
}

@Composable
private fun ThemeSelectionDialog(
    currentTheme: ThemeMode,
    onThemeSelected: (ThemeMode) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose theme") },
        text = {
            Column(Modifier.selectableGroup()) {
                ThemeMode.entries.forEach { theme ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .selectable(
                                selected = theme == currentTheme,
                                onClick = { onThemeSelected(theme) },
                                role = Role.RadioButton
                            )
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = theme == currentTheme,
                            onClick = null
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(
                            text = when (theme) {
                                ThemeMode.LIGHT -> "Light"
                                ThemeMode.DARK -> "Dark"
                                ThemeMode.SYSTEM -> "Follow system"
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ConnectionModeDialog(
    currentMode: ConnectionMode,
    onModeSelected: (ConnectionMode) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Connection mode") },
        text = {
            Column(Modifier.selectableGroup()) {
                ConnectionMode.entries.forEach { mode ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(72.dp)
                            .selectable(
                                selected = mode == currentMode,
                                onClick = { onModeSelected(mode) },
                                role = Role.RadioButton
                            )
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = mode == currentMode,
                            onClick = null
                        )
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(
                                text = when (mode) {
                                    ConnectionMode.USB -> "Direct USB"
                                    ConnectionMode.TCP_ADB -> "USB with ADB (Recommended)"
                                    ConnectionMode.TCP_FORWARD -> "ADB Forward Mode"
                                    ConnectionMode.TCP_WIFI -> "Wi-Fi"
                                    ConnectionMode.AUTO -> "Automatic"
                                },
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = when (mode) {
                                    ConnectionMode.USB -> "Requires driver setup on Windows"
                                    ConnectionMode.TCP_ADB -> "Works with USB debugging enabled"
                                    ConnectionMode.TCP_FORWARD -> "For older Huawei/Honor devices"
                                    ConnectionMode.TCP_WIFI -> "Connect over Wi-Fi network"
                                    ConnectionMode.AUTO -> "Try ADB first, then USB"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun WifiSettingsDialog(
    currentHost: String,
    currentPort: Int,
    onSave: (String, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var host by remember { mutableStateOf(currentHost) }
    var port by remember { mutableStateOf(currentPort.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Wi-Fi server address") },
        text = {
            Column {
                Text(
                    text = "Enter the IP address and port of the PC server",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("IP Address") },
                    placeholder = { Text("192.168.1.100") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it.filter { c -> c.isDigit() } },
                    label = { Text("Port") },
                    placeholder = { Text("5555") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val portInt = port.toIntOrNull() ?: 5555
                    onSave(host, portInt)
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

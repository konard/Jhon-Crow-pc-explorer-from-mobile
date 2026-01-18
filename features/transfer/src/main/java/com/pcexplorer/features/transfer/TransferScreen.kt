package com.pcexplorer.features.transfer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pcexplorer.core.domain.model.TransferState
import com.pcexplorer.core.domain.model.TransferTask
import com.pcexplorer.core.domain.model.TransferType
import com.pcexplorer.shared.components.EmptyState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferScreen(
    viewModel: TransferViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transfers") },
                actions = {
                    if (uiState.completedTransfers.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearHistory() }) {
                            Icon(
                                imageVector = Icons.Filled.DeleteSweep,
                                contentDescription = "Clear history"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { paddingValues ->
        if (uiState.activeTransfers.isEmpty() && uiState.completedTransfers.isEmpty()) {
            EmptyState(
                modifier = Modifier.padding(paddingValues),
                title = "No transfers",
                message = "Download or upload files to see them here"
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (uiState.activeTransfers.isNotEmpty()) {
                    item {
                        Text(
                            text = "Active",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    items(uiState.activeTransfers, key = { it.id }) { transfer ->
                        TransferCard(
                            transfer = transfer,
                            onCancel = { viewModel.cancelTransfer(transfer.id) },
                            onRetry = { viewModel.retryTransfer(transfer.id) }
                        )
                    }
                }

                if (uiState.completedTransfers.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "History",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    items(uiState.completedTransfers, key = { it.id }) { transfer ->
                        TransferCard(
                            transfer = transfer,
                            onCancel = { },
                            onRetry = { viewModel.retryTransfer(transfer.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TransferCard(
    transfer: TransferTask,
    onCancel: () -> Unit,
    onRetry: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Type icon
                Icon(
                    imageVector = if (transfer.type == TransferType.DOWNLOAD)
                        Icons.Filled.CloudDownload
                    else
                        Icons.Filled.CloudUpload,
                    contentDescription = transfer.type.name,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                // File name
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = transfer.fileName,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${transfer.formattedSize} of ${formatBytes(transfer.totalBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Status icon
                TransferStatusIcon(state = transfer.state)
            }

            // Progress bar for active transfers
            if (transfer.isActive) {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { transfer.progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${transfer.progressPercent}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Actions
            if (transfer.isActive || transfer.isFailed) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (transfer.isActive) {
                        TextButton(onClick = onCancel) {
                            Icon(
                                imageVector = Icons.Filled.Cancel,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Cancel")
                        }
                    }
                    if (transfer.isFailed) {
                        TextButton(onClick = onRetry) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Retry")
                        }
                    }
                }
            }

            // Error message
            if (transfer.error != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = transfer.error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun TransferStatusIcon(state: TransferState) {
    val (icon, tint) = when (state) {
        TransferState.Pending -> Icons.Filled.CloudDownload to MaterialTheme.colorScheme.onSurfaceVariant
        TransferState.InProgress -> Icons.Filled.CloudDownload to MaterialTheme.colorScheme.primary
        TransferState.Completed -> Icons.Filled.CheckCircle to Color(0xFF4CAF50)
        TransferState.Failed -> Icons.Filled.Error to MaterialTheme.colorScheme.error
        TransferState.Cancelled -> Icons.Filled.Cancel to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Icon(
        imageVector = icon,
        contentDescription = state.name,
        tint = tint,
        modifier = Modifier.size(24.dp)
    )
}

private val TransferTask.formattedSize: String
    get() = formatBytes(transferredBytes)

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
    else -> String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024))
}

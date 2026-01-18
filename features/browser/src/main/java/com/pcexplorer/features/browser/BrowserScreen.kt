package com.pcexplorer.features.browser

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pcexplorer.core.domain.model.FileItem
import com.pcexplorer.core.domain.model.SortBy
import com.pcexplorer.shared.components.EmptyState
import com.pcexplorer.shared.components.FileIcon
import com.pcexplorer.shared.components.LoadingIndicator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    onDownloadFile: (FileItem) -> Unit,
    viewModel: BrowserViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var showSortMenu by remember { mutableStateOf(false) }
    var showDriveMenu by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    // Handle back navigation
    BackHandler(enabled = true) {
        if (uiState.isSelectionMode) {
            viewModel.clearSelection()
        } else if (isSearchActive) {
            isSearchActive = false
            searchQuery = ""
            viewModel.loadFiles()
        } else {
            viewModel.navigateUp()
        }
    }

    // Show error in snackbar
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (uiState.isSelectionMode) {
                SelectionTopBar(
                    selectedCount = uiState.selectedFiles.size,
                    onClearSelection = { viewModel.clearSelection() },
                    onSelectAll = { viewModel.selectAll() },
                    onDelete = { showDeleteConfirmDialog = true },
                    onDownload = {
                        uiState.files
                            .filter { it.path in uiState.selectedFiles && it.isFile }
                            .forEach { onDownloadFile(it) }
                        viewModel.clearSelection()
                    }
                )
            } else {
                BrowserTopBar(
                    currentPath = uiState.currentPath,
                    drives = uiState.drives,
                    showDriveMenu = showDriveMenu,
                    onDriveMenuToggle = { showDriveMenu = !showDriveMenu },
                    onDriveSelected = { drive ->
                        viewModel.changeDrive(drive)
                        showDriveMenu = false
                    },
                    isSearchActive = isSearchActive,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    onSearchToggle = {
                        isSearchActive = !isSearchActive
                        if (!isSearchActive) {
                            searchQuery = ""
                            viewModel.loadFiles()
                        }
                    },
                    onSearch = { viewModel.search(searchQuery) },
                    showSortMenu = showSortMenu,
                    onSortMenuToggle = { showSortMenu = !showSortMenu },
                    currentSortBy = uiState.sortOrder.sortBy,
                    onSortSelected = { sortBy ->
                        viewModel.setSortOrder(sortBy)
                        showSortMenu = false
                    },
                    onNewFolder = { showNewFolderDialog = true }
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Storage info bar
            uiState.storageInfo?.let { info ->
                StorageInfoBar(
                    usedSpace = info.formattedUsed,
                    freeSpace = info.formattedFree,
                    usagePercent = info.usagePercent
                )
            }

            // Content
            when {
                uiState.isLoading -> LoadingIndicator(message = "Loading files...")
                uiState.files.isEmpty() -> EmptyState(
                    title = if (uiState.isSearching) "No results" else "Empty folder",
                    message = if (uiState.isSearching) "No files match \"${uiState.searchQuery}\"" else null
                )
                else -> FileList(
                    files = uiState.files,
                    selectedFiles = uiState.selectedFiles,
                    isSelectionMode = uiState.isSelectionMode,
                    onItemClick = { item ->
                        if (uiState.isSelectionMode) {
                            viewModel.toggleSelection(item.path)
                        } else {
                            viewModel.openItem(item)
                        }
                    },
                    onItemLongClick = { item ->
                        viewModel.toggleSelection(item.path)
                    },
                    onDownload = onDownloadFile
                )
            }
        }
    }

    // Dialogs
    if (showNewFolderDialog) {
        NewFolderDialog(
            onDismiss = { showNewFolderDialog = false },
            onCreate = { name ->
                viewModel.createFolder(name)
                showNewFolderDialog = false
            }
        )
    }

    if (showDeleteConfirmDialog) {
        DeleteConfirmDialog(
            count = uiState.selectedFiles.size,
            onDismiss = { showDeleteConfirmDialog = false },
            onConfirm = {
                viewModel.deleteSelected()
                showDeleteConfirmDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowserTopBar(
    currentPath: String,
    drives: List<String>,
    showDriveMenu: Boolean,
    onDriveMenuToggle: () -> Unit,
    onDriveSelected: (String) -> Unit,
    isSearchActive: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearchToggle: () -> Unit,
    onSearch: () -> Unit,
    showSortMenu: Boolean,
    onSortMenuToggle: () -> Unit,
    currentSortBy: SortBy,
    onSortSelected: (SortBy) -> Unit,
    onNewFolder: () -> Unit
) {
    TopAppBar(
        title = {
            if (isSearchActive) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text("Search files...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.combinedClickable(
                        onClick = onDriveMenuToggle
                    )
                ) {
                    Text(
                        text = currentPath.ifEmpty { "PC Explorer" },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (drives.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.Filled.ArrowDropDown,
                            contentDescription = "Select drive"
                        )
                        DropdownMenu(
                            expanded = showDriveMenu,
                            onDismissRequest = { onDriveMenuToggle() }
                        ) {
                            drives.forEach { drive ->
                                DropdownMenuItem(
                                    text = { Text(drive) },
                                    onClick = { onDriveSelected(drive) }
                                )
                            }
                        }
                    }
                }
            }
        },
        actions = {
            IconButton(onClick = onSearchToggle) {
                Icon(
                    imageVector = if (isSearchActive) Icons.Filled.Close else Icons.Filled.Search,
                    contentDescription = if (isSearchActive) "Close search" else "Search"
                )
            }

            if (!isSearchActive) {
                Box {
                    IconButton(onClick = onSortMenuToggle) {
                        Icon(Icons.Filled.Sort, contentDescription = "Sort")
                    }
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { onSortMenuToggle() }
                    ) {
                        SortBy.entries.forEach { sortBy ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = sortBy.name.lowercase().replaceFirstChar { it.uppercase() },
                                        color = if (sortBy == currentSortBy)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            Color.Unspecified
                                    )
                                },
                                onClick = { onSortSelected(sortBy) }
                            )
                        }
                    }
                }

                IconButton(onClick = onNewFolder) {
                    Icon(Icons.Filled.CreateNewFolder, contentDescription = "New folder")
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(
    selectedCount: Int,
    onClearSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onDelete: () -> Unit,
    onDownload: () -> Unit
) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClearSelection) {
                Icon(Icons.Filled.Close, contentDescription = "Clear selection")
            }
        },
        title = { Text("$selectedCount selected") },
        actions = {
            IconButton(onClick = onSelectAll) {
                Icon(Icons.Filled.SelectAll, contentDescription = "Select all")
            }
            IconButton(onClick = onDownload) {
                Icon(Icons.Filled.Download, contentDescription = "Download")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    )
}

@Composable
private fun StorageInfoBar(
    usedSpace: String,
    freeSpace: String,
    usagePercent: Float
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        LinearProgressIndicator(
            progress = { usagePercent },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            color = if (usagePercent > 0.9f) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Used: $usedSpace",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Free: $freeSpace",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileList(
    files: List<FileItem>,
    selectedFiles: Set<String>,
    isSelectionMode: Boolean,
    onItemClick: (FileItem) -> Unit,
    onItemLongClick: (FileItem) -> Unit,
    onDownload: (FileItem) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(files, key = { it.path }) { file ->
            FileListItem(
                file = file,
                isSelected = file.path in selectedFiles,
                isSelectionMode = isSelectionMode,
                onClick = { onItemClick(file) },
                onLongClick = { onItemLongClick(file) },
                onDownload = { onDownload(file) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileListItem(
    file: FileItem,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDownload: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()) }

    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        colors = ListItemDefaults.colors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else
                Color.Transparent
        ),
        leadingContent = {
            if (isSelectionMode) {
                Icon(
                    imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                    contentDescription = if (isSelected) "Selected" else "Not selected",
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                FileIcon(
                    extension = file.extension,
                    isDirectory = file.isDirectory,
                    modifier = Modifier.size(40.dp)
                )
            }
        },
        headlineContent = {
            Text(
                text = file.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Row {
                if (!file.isDirectory) {
                    Text(
                        text = file.formattedSize,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                }
                if (file.lastModified > 0) {
                    Text(
                        text = dateFormat.format(Date(file.lastModified)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        trailingContent = {
            if (!isSelectionMode && file.isFile) {
                var showMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "More options"
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Download") },
                            leadingIcon = {
                                Icon(Icons.Filled.Download, contentDescription = null)
                            },
                            onClick = {
                                onDownload()
                                showMenu = false
                            }
                        )
                    }
                }
            }
        }
    )
    HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
}

@Composable
private fun NewFolderDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var folderName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Folder") },
        text = {
            OutlinedTextField(
                value = folderName,
                onValueChange = { folderName = it },
                label = { Text("Folder name") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(folderName) },
                enabled = folderName.isNotBlank()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun DeleteConfirmDialog(
    count: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete files?") },
        text = { Text("Are you sure you want to delete $count item(s)? This action cannot be undone.") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

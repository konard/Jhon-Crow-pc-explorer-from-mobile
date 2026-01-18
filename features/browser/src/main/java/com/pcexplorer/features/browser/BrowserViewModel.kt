package com.pcexplorer.features.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pcexplorer.core.domain.model.FileItem
import com.pcexplorer.core.domain.model.SortBy
import com.pcexplorer.core.domain.model.SortOrder
import com.pcexplorer.core.domain.model.StorageInfo
import com.pcexplorer.core.domain.usecase.FileOperationsUseCase
import com.pcexplorer.core.domain.usecase.ListFilesUseCase
import com.pcexplorer.core.domain.usecase.SearchFilesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BrowserUiState(
    val files: List<FileItem> = emptyList(),
    val currentPath: String = "",
    val pathHistory: List<String> = emptyList(),
    val selectedFiles: Set<String> = emptySet(),
    val sortOrder: SortOrder = SortOrder(),
    val isLoading: Boolean = false,
    val isSearching: Boolean = false,
    val searchQuery: String = "",
    val error: String? = null,
    val storageInfo: StorageInfo? = null,
    val drives: List<String> = emptyList(),
    val isSelectionMode: Boolean = false
)

@HiltViewModel
class BrowserViewModel @Inject constructor(
    private val listFilesUseCase: ListFilesUseCase,
    private val searchFilesUseCase: SearchFilesUseCase,
    private val fileOperationsUseCase: FileOperationsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(BrowserUiState())
    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()

    init {
        loadDrives()
    }

    private fun loadDrives() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            fileOperationsUseCase.getDrives()
                .onSuccess { drives ->
                    _uiState.update { state ->
                        state.copy(
                            drives = drives,
                            currentPath = drives.firstOrNull() ?: "",
                            isLoading = false
                        )
                    }
                    // Load files from first drive
                    if (_uiState.value.currentPath.isNotEmpty()) {
                        loadFiles(_uiState.value.currentPath)
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(error = error.message, isLoading = false) }
                }
        }
    }

    fun loadFiles(path: String = _uiState.value.currentPath) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, isSearching = false, searchQuery = "") }

            listFilesUseCase(path, _uiState.value.sortOrder)
                .onSuccess { files ->
                    _uiState.update { state ->
                        val newHistory = if (path != state.currentPath && state.currentPath.isNotEmpty()) {
                            state.pathHistory + state.currentPath
                        } else {
                            state.pathHistory
                        }
                        state.copy(
                            files = files,
                            currentPath = path,
                            pathHistory = newHistory,
                            isLoading = false,
                            selectedFiles = emptySet(),
                            isSelectionMode = false
                        )
                    }
                    // Load storage info
                    loadStorageInfo(path)
                }
                .onFailure { error ->
                    _uiState.update { it.copy(error = error.message, isLoading = false) }
                }
        }
    }

    private fun loadStorageInfo(path: String) {
        viewModelScope.launch {
            fileOperationsUseCase.getStorageInfo(path)
                .onSuccess { info ->
                    _uiState.update { it.copy(storageInfo = info) }
                }
        }
    }

    fun navigateUp(): Boolean {
        val currentState = _uiState.value
        val parentPath = getParentPath(currentState.currentPath)

        return if (parentPath != null && parentPath != currentState.currentPath) {
            loadFiles(parentPath)
            true
        } else if (currentState.pathHistory.isNotEmpty()) {
            val previousPath = currentState.pathHistory.last()
            _uiState.update { it.copy(pathHistory = it.pathHistory.dropLast(1)) }
            loadFiles(previousPath)
            true
        } else {
            false
        }
    }

    private fun getParentPath(path: String): String? {
        val separatorIndex = path.trimEnd('/', '\\').lastIndexOfAny(charArrayOf('/', '\\'))
        return if (separatorIndex > 0) {
            path.substring(0, separatorIndex)
        } else if (separatorIndex == 0) {
            "/"
        } else {
            null
        }
    }

    fun openItem(item: FileItem) {
        if (item.isDirectory) {
            loadFiles(item.path)
        }
    }

    fun search(query: String) {
        if (query.isBlank()) {
            loadFiles()
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, isSearching = true, searchQuery = query) }

            searchFilesUseCase(query, _uiState.value.currentPath)
                .onSuccess { files ->
                    _uiState.update { it.copy(files = files, isLoading = false) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(error = error.message, isLoading = false) }
                }
        }
    }

    fun setSortOrder(sortBy: SortBy) {
        val currentSort = _uiState.value.sortOrder
        val newOrder = if (currentSort.sortBy == sortBy) {
            currentSort.toggle()
        } else {
            SortOrder(sortBy = sortBy)
        }
        _uiState.update { it.copy(sortOrder = newOrder) }
        loadFiles()
    }

    fun toggleSelection(path: String) {
        _uiState.update { state ->
            val newSelection = if (state.selectedFiles.contains(path)) {
                state.selectedFiles - path
            } else {
                state.selectedFiles + path
            }
            state.copy(
                selectedFiles = newSelection,
                isSelectionMode = newSelection.isNotEmpty()
            )
        }
    }

    fun selectAll() {
        _uiState.update { state ->
            state.copy(
                selectedFiles = state.files.map { it.path }.toSet(),
                isSelectionMode = true
            )
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedFiles = emptySet(), isSelectionMode = false) }
    }

    fun deleteSelected() {
        val paths = _uiState.value.selectedFiles.toList()
        if (paths.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            fileOperationsUseCase.delete(paths)
                .onSuccess {
                    clearSelection()
                    loadFiles()
                }
                .onFailure { error ->
                    _uiState.update { it.copy(error = error.message, isLoading = false) }
                }
        }
    }

    fun createFolder(name: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            fileOperationsUseCase.createFolder(_uiState.value.currentPath, name)
                .onSuccess {
                    loadFiles()
                }
                .onFailure { error ->
                    _uiState.update { it.copy(error = error.message, isLoading = false) }
                }
        }
    }

    fun rename(path: String, newName: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            fileOperationsUseCase.rename(path, newName)
                .onSuccess {
                    loadFiles()
                }
                .onFailure { error ->
                    _uiState.update { it.copy(error = error.message, isLoading = false) }
                }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun changeDrive(drive: String) {
        _uiState.update { it.copy(pathHistory = emptyList()) }
        loadFiles(drive)
    }
}

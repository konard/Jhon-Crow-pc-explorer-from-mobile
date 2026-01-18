package com.pcexplorer.features.transfer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pcexplorer.core.domain.model.TransferState
import com.pcexplorer.core.domain.model.TransferTask
import com.pcexplorer.core.domain.usecase.TransferFileUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TransferUiState(
    val activeTransfers: List<TransferTask> = emptyList(),
    val completedTransfers: List<TransferTask> = emptyList()
)

@HiltViewModel
class TransferViewModel @Inject constructor(
    private val transferFileUseCase: TransferFileUseCase
) : ViewModel() {

    val uiState: StateFlow<TransferUiState> = transferFileUseCase.transfers
        .map { transfers ->
            TransferUiState(
                activeTransfers = transfers.filter { it.isActive },
                completedTransfers = transfers.filter { !it.isActive }
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = TransferUiState()
        )

    fun downloadFile(remotePath: String, localPath: String) {
        viewModelScope.launch {
            transferFileUseCase.download(remotePath, localPath)
        }
    }

    fun uploadFile(localPath: String, remotePath: String) {
        viewModelScope.launch {
            transferFileUseCase.upload(localPath, remotePath)
        }
    }

    fun cancelTransfer(taskId: String) {
        viewModelScope.launch {
            transferFileUseCase.cancel(taskId)
        }
    }

    fun retryTransfer(taskId: String) {
        viewModelScope.launch {
            transferFileUseCase.retry(taskId)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            transferFileUseCase.clearHistory()
        }
    }
}

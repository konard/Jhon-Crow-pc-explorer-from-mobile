package com.pcexplorer.features.connection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pcexplorer.core.domain.model.ConnectionState
import com.pcexplorer.core.domain.usecase.ConnectToDeviceUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ConnectionViewModel @Inject constructor(
    private val connectToDeviceUseCase: ConnectToDeviceUseCase
) : ViewModel() {

    val connectionState: StateFlow<ConnectionState> = connectToDeviceUseCase.connectionState
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ConnectionState.Disconnected
        )

    fun connect() {
        viewModelScope.launch {
            connectToDeviceUseCase()
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            connectToDeviceUseCase.disconnect()
        }
    }

    fun requestPermission() {
        viewModelScope.launch {
            connectToDeviceUseCase.requestPermission()
        }
    }

    fun hasPermission(): Boolean = connectToDeviceUseCase.hasPermission()
}

package com.zappix.store

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class StoreUiState(
    val loading: Boolean = true,
    val apps: List<StoreApp> = emptyList(),
    val error: String? = null
)

class StoreViewModel(
    private val api: StoreApi = StoreApi()
) : ViewModel() {
    private val _state = MutableStateFlow(StoreUiState())
    val state: StateFlow<StoreUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching { api.loadApps() }
                .onSuccess { _state.value = StoreUiState(loading = false, apps = it) }
                .onFailure { _state.value = StoreUiState(loading = false, error = it.message ?: "Unable to load apps") }
        }
    }
}

package com.zappix.store

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class StoreUiState(
    val loading: Boolean = true,
    val loaded: Boolean = false,
    val apps: List<StoreApp> = emptyList(),
    val error: String? = null
)

class StoreViewModel(
    private val api: StoreApi = StoreApi(),
    private val updateChecker: ZappixUpdateChecker = ZappixUpdateChecker()
) : ViewModel() {
    private val _state = MutableStateFlow(StoreUiState())
    val state: StateFlow<StoreUiState> = _state.asStateFlow()

    private val _update = MutableStateFlow<ZappixUpdateInfo?>(null)
    val update: StateFlow<ZappixUpdateInfo?> = _update.asStateFlow()

    /** Session-scoped UI decisions that must survive Activity recreation. */
    var updateDismissed = false
    var adultConfirmed = false

    private var refreshJob: Job? = null

    init {
        refresh()
        checkForUpdate()
    }

    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                val apps = api.loadApps()
                _state.value = StoreUiState(loading = false, loaded = true, apps = apps)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // Keep the last good catalog on screen when a refresh fails.
                _state.value = _state.value.copy(loading = false, error = e.userMessage("Unable to load apps."))
            }
        }
    }

    private fun checkForUpdate() {
        viewModelScope.launch {
            try {
                _update.value = updateChecker.check()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // Update checks are best effort.
            }
        }
    }
}

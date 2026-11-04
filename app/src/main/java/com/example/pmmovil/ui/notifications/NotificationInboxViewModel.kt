package com.example.pmmovil.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pmmovil.data.model.NotificationInboxItem
import com.example.pmmovil.data.preferences.ModePreferences
import com.example.pmmovil.data.repository.NotificationInboxRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NotificationInboxUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val items: List<NotificationInboxItem> = emptyList(),
    val unreadCount: Int = 0,
    val errorMessage: String? = null
)

@HiltViewModel
class NotificationInboxViewModel @Inject constructor(
    private val repository: NotificationInboxRepository,
    private val modePreferences: ModePreferences
) : ViewModel() {

    private val _state = MutableStateFlow(NotificationInboxUiState())
    val state: StateFlow<NotificationInboxUiState> = _state.asStateFlow()

    fun refresh() {
        load(isRefresh = _state.value.items.isNotEmpty())
    }

    fun markRead(id: Long) {
        viewModelScope.launch {
            repository.markRead(id).onSuccess {
                load()
            }
        }
    }

    fun markAllRead() {
        viewModelScope.launch {
            repository.markAllRead().onSuccess {
                load()
            }
        }
    }

    fun lastModeFor(crefAgrupada: String): ModePreferences.Mode =
        modePreferences.get(crefAgrupada)

    fun rememberMode(crefAgrupada: String, mode: ModePreferences.Mode) {
        modePreferences.set(crefAgrupada, mode)
    }

    private fun load(isRefresh: Boolean = false) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                isLoading = !isRefresh && _state.value.items.isEmpty(),
                isRefreshing = isRefresh,
                errorMessage = null
            )

            repository.getInbox(limit = 50)
                .onSuccess { response ->
                    _state.value = NotificationInboxUiState(
                        isLoading = false,
                        isRefreshing = false,
                        items = response.items,
                        unreadCount = response.unreadCount
                    )
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = error.message ?: "No se pudo cargar la bandeja."
                    )
                }
        }
    }
}

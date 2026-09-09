package com.silverchat.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.silverchat.core.common.result.ScResult
import com.silverchat.core.domain.repository.AuthRepository
import com.silverchat.core.domain.repository.DeviceSession
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Активные сессии пользователя.
 *
 * Список загружается suspend-запросом, а не потоком: сессии меняются редко
 * (вход/выход на устройствах), и держать на них WebSocket-подписку означало
 * бы лишний трафик ради данных, которые устаревают раз в неделю.
 *
 * `revokeOtherSessions` — отдельный серверный метод, а не цикл по
 * `revokeSession`: при десятке устройств цикл породил бы десять запросов
 * и оставил окно, в котором часть сессий ещё жива.
 */
@HiltViewModel
class DevicesViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DevicesUiState(isLoading = true))
    val uiState: StateFlow<DevicesUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    /** Загрузка списка сессий. Повторный вызов — это pull-to-refresh. */
    fun refresh() = viewModelScope.launch {
        _uiState.update { it.copy(isLoading = true, errorText = null) }
        when (val result = authRepository.activeSessions()) {
            is ScResult.Success -> _uiState.update {
                it.copy(isLoading = false, sessions = result.data.sortedByDescending { s -> s.lastActiveAt })
            }

            is ScResult.Failure -> _uiState.update {
                it.copy(isLoading = false, errorText = result.error.message)
            }

            ScResult.Loading -> Unit
        }
    }

    /** Завершение конкретной сессии. Текущую завершить нельзя — это `logout`. */
    fun revoke(sessionId: String) = viewModelScope.launch {
        _uiState.update { it.copy(isProcessingId = sessionId) }
        when (val result = authRepository.revokeSession(sessionId)) {
            is ScResult.Success -> _uiState.update { state ->
                state.copy(
                    // Удаляем локально: повторный запрос списка не нужен,
                    // сервер уже отозвал токен
                    sessions = state.sessions.filterNot { it.id == sessionId },
                    isProcessingId = null,
                )
            }

            is ScResult.Failure -> _uiState.update {
                it.copy(isProcessingId = null, errorText = result.error.message)
            }

            ScResult.Loading -> Unit
        }
    }

    /** Завершение всех сессий, кроме текущей. */
    fun revokeOthers() = viewModelScope.launch {
        _uiState.update { it.copy(isProcessingAll = true) }
        when (val result = authRepository.revokeOtherSessions()) {
            is ScResult.Success -> _uiState.update { state ->
                state.copy(
                    sessions = state.sessions.filter { it.isCurrent },
                    isProcessingAll = false,
                )
            }

            is ScResult.Failure -> _uiState.update {
                it.copy(isProcessingAll = false, errorText = result.error.message)
            }

            ScResult.Loading -> Unit
        }
    }

    fun dismissError() { _uiState.update { it.copy(errorText = null) } }
}

data class DevicesUiState(
    val sessions: List<DeviceSession> = emptyList(),
    val isLoading: Boolean = false,
    val isProcessingId: String? = null,
    val isProcessingAll: Boolean = false,
    val errorText: String? = null,
) {
    val current: DeviceSession? get() = sessions.firstOrNull { it.isCurrent }
    val others: List<DeviceSession> get() = sessions.filterNot { it.isCurrent }
    val isBusy: Boolean get() = isProcessingAll || isProcessingId != null
}

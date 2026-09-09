package com.silverchat.core.security

import com.silverchat.core.common.log.LogTag
import com.silverchat.core.common.log.ScLogger
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Хранилище пары токенов + состояние авторизации.
 *
 * Токены живут ТОЛЬКО в EncryptedSharedPreferences. В память выносится
 * лишь access-токен (нужен OkHttp-интерцептору на каждый запрос) и то
 * в [StateFlow], который обнуляется при блокировке приложения.
 */
@Singleton
class TokenStore @Inject constructor(
    private val secureStorage: SecureStorage,
) {

    private val _authState = MutableStateFlow(detectAuthState())
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    val accessToken: String?
        get() = _authState.value.token

    val isAuthenticated: Boolean
        get() = _authState.value is AuthState.Authenticated

    fun saveTokens(access: String, refresh: String, userId: String) {
        secureStorage.accessToken = access
        secureStorage.refreshToken = refresh
        secureStorage.putString(SecureKeys.USER_ID, userId)
        _authState.value = AuthState.Authenticated(access, refresh, userId)
        ScLogger.i(LogTag.AUTH, "Токены сохранены (access=${ScLogger.mask(access)})")
    }

    fun updateAccessToken(access: String) {
        secureStorage.accessToken = access
        val current = _authState.value
        if (current is AuthState.Authenticated) {
            _authState.value = current.copy(token = access)
        }
    }

    fun updateRefreshToken(refresh: String) {
        secureStorage.refreshToken = refresh
        val current = _authState.value
        if (current is AuthState.Authenticated) {
            _authState.value = current.copy(refreshToken = refresh)
        }
    }

    /** Выход из аккаунта: чистим всё защищённое хранилище. */
    fun logout() {
        secureStorage.clearAll()
        _authState.value = AuthState.LoggedOut
        ScLogger.i(LogTag.AUTH, "Logout: защищённое хранилище очищено")
    }

    /** Отзыв сессии сервером (401 / kick по WebSocket). */
    fun onSessionInvalidated(reason: String) {
        ScLogger.w(LogTag.AUTH, "Сессия недействительна: $reason")
        secureStorage.accessToken = null
        secureStorage.refreshToken = null
        secureStorage.sessionId = null
        _authState.value = AuthState.Expired(reason)
    }

    private fun detectAuthState(): AuthState {
        val access = secureStorage.accessToken
        val refresh = secureStorage.refreshToken
        return if (!access.isNullOrBlank() && !refresh.isNullOrBlank()) {
            AuthState.Authenticated(access, refresh, userId = secureStorage.getString(SecureKeys.USER_ID).orEmpty())
        } else {
            AuthState.LoggedOut
        }
    }
}

sealed interface AuthState {
    data object Loading : AuthState
    data object LoggedOut : AuthState
    data class Authenticated(
        val token: String,
        val refreshToken: String,
        val userId: String,
    ) : AuthState

    data class Expired(val reason: String) : AuthState

    val isLoggedIn: Boolean get() = this is Authenticated
}

package com.silverchat.core.data.repository

import android.os.Build
import com.silverchat.core.common.result.ScResult
import com.silverchat.core.data.device.DeviceIdentity
import com.silverchat.core.data.mapper.toDomainSession
import com.silverchat.core.domain.repository.AuthRepository
import com.silverchat.core.domain.repository.DeviceSession
import com.silverchat.core.domain.repository.OtpTicket
import com.silverchat.core.model.User
import com.silverchat.core.model.UserId
import com.silverchat.core.network.api.SilverChatApi
import com.silverchat.core.network.dto.RefreshRequest
import com.silverchat.core.network.dto.SendOtpRequest
import com.silverchat.core.network.dto.VerifyOtpRequest
import com.silverchat.core.network.mapper.apiCall
import com.silverchat.core.network.mapper.toDomain
import com.silverchat.core.network.ws.RealtimeSocket
import com.silverchat.core.security.TokenStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Авторизация по OTP (Telegram-style: телефон + код, без пароля).
 *
 * Токены живут в [TokenStore] поверх `EncryptedSharedPreferences`
 * (AES-256 GCM, ключ в Android Keystore) — в обычном `SharedPreferences`
 * их держать нельзя: root-устройство или backup вытащат их целиком.
 */
@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val api: SilverChatApi,
    private val tokenStore: TokenStore,
    private val socket: RealtimeSocket,
    private val deviceIdentity: DeviceIdentity,
) : AuthRepository {

    /**
     * `currentUserId` — единственный источник «кто я» для всего приложения.
     * Админ-панель, кошелёк и маркет строят от него `stateIn(Eagerly)`,
     * поэтому поток обязан быть горячим и никогда не падать.
     */
    override val currentUserId: Flow<UserId?> =
        tokenStore.authState.map { state ->
            (state as? com.silverchat.core.security.AuthState.Authenticated)?.userId
        }

    override suspend fun sendOtp(phone: String): ScResult<OtpTicket> = apiCall {
        val normalized = phone.filter { it.isDigit() || it == '+' }
        val response = api.sendOtp(
            SendOtpRequest(phone = normalized, deviceId = deviceIdentity.deviceId),
        )
        OtpTicket(
            phone = response.phone,
            expiresInSec = response.expiresIn,
            nextRetryInSec = response.retryAfter,
            isNewUser = response.isNewUser,
        )
    }

    override suspend fun verifyOtp(phone: String, code: String): ScResult<User> = apiCall {
        val response = api.verifyOtp(
            VerifyOtpRequest(
                phone = phone.filter { it.isDigit() || it == '+' },
                code = code.trim(),
                deviceName = deviceName(),
            ),
        )
        // Порядок важен: сначала токены, потом сокет. Иначе сокет подключится
        // без access-токена и сервер отклонит рукопожатие.
        tokenStore.saveTokens(
            access = response.accessToken,
            refresh = response.refreshToken,
            userId = response.user.id,
        )
        socket.updateToken(response.accessToken)
        response.user.toDomain()
    }

    override suspend fun register(
        firstName: String,
        lastName: String?,
        username: String?,
    ): ScResult<User> = apiCall {
        api.updateMe(
            com.silverchat.core.network.dto.UpdateProfileRequest(
                firstName = firstName.trim().ifBlank { null },
                lastName = lastName?.trim()?.ifBlank { null },
                username = username?.trim()?.removePrefix("@")?.ifBlank { null },
            ),
        ).toDomain()
    }

    override suspend fun logout(): ScResult<Unit> = apiCall {
        // Серверный вызов — best effort: даже если сеть недоступна,
        // локальную сессию уничтожить обязан.
        runCatching { api.logout() }
        socket.disconnect()
        tokenStore.logout()
    }

    override suspend fun refreshTokens(): ScResult<Unit> = apiCall {
        val current = tokenStore.authState.value
        val refresh = (current as? com.silverchat.core.security.AuthState.Authenticated)
            ?.refreshToken
            ?: error("Нет refresh-токена")
        val response = api.refreshTokens(
            RefreshRequest(refreshToken = refresh, deviceId = deviceIdentity.deviceId),
        )
        tokenStore.saveTokens(response.accessToken, response.refreshToken, response.user.id)
        socket.updateToken(response.accessToken)
    }

    override suspend fun activeSessions(): ScResult<List<DeviceSession>> = apiCall {
        api.sessions().map { it.toDomainSession() }
    }

    override suspend fun revokeSession(sessionId: String): ScResult<Unit> = apiCall {
        api.revokeSession(sessionId)
        Unit
    }

    override suspend fun revokeOtherSessions(): ScResult<Unit> = apiCall {
        // Отдельного эндпоинта нет: снимаем все, кроме текущей.
        // Это осознанно N+1 запросов — операций в настройках, не в горячем пути.
        val sessions = api.sessions()
        sessions.filterNot { it.isCurrent }.forEach { runCatching { api.revokeSession(it.id) } }
        Unit
    }

    private fun deviceName(): String = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
}

package com.silverchat.core.domain.repository

import com.silverchat.core.common.result.ScResult
import com.silverchat.core.model.User
import com.silverchat.core.model.UserId
import kotlinx.coroutines.flow.Flow

/** Авторизация, сессии, мультilogin-устройства. */
interface AuthRepository {

    /** ID текущего пользователя; null — не авторизован. */
    val currentUserId: Flow<UserId?>

    suspend fun sendOtp(phone: String): ScResult<OtpTicket>

    suspend fun verifyOtp(phone: String, code: String): ScResult<User>

    suspend fun register(
        firstName: String,
        lastName: String?,
        username: String?,
    ): ScResult<User>

    suspend fun logout(): ScResult<Unit>

    /** Ротация refresh-токена. Вызывается интерцептором при 401. */
    suspend fun refreshTokens(): ScResult<Unit>

    /** Активные сессии на всех устройствах (экран «Настройки → Устройства»). */
    suspend fun activeSessions(): ScResult<List<DeviceSession>>

    suspend fun revokeSession(sessionId: String): ScResult<Unit>

    suspend fun revokeOtherSessions(): ScResult<Unit>
}

data class OtpTicket(
    val phone: String,
    val expiresInSec: Int,
    val nextRetryInSec: Int,
    /** true, если номер ещё не зарегистрирован — показываем экран регистрации. */
    val isNewUser: Boolean,
)

data class DeviceSession(
    val id: String,
    val deviceName: String,
    val platform: String,
    val appVersion: String,
    val lastActiveAt: Long,
    val ip: String?,
    val locationHint: String?,
    val isCurrent: Boolean,
)

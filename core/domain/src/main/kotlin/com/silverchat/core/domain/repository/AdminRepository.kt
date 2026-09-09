package com.silverchat.core.domain.repository

import com.silverchat.core.common.result.ScResult
import com.silverchat.core.model.AdminAction
import com.silverchat.core.model.AdminBroadcastAudience
import com.silverchat.core.model.AdminGrantRequest
import com.silverchat.core.model.AdminLogEntry
import com.silverchat.core.model.AdminRole
import com.silverchat.core.model.AdminStats
import com.silverchat.core.model.ChatId
import com.silverchat.core.model.ListingId
import com.silverchat.core.model.MessageId
import com.silverchat.core.model.ModerationCase
import com.silverchat.core.model.ModerationStatus
import com.silverchat.core.model.Streak
import com.silverchat.core.model.User
import com.silverchat.core.model.UserId
import com.silverchat.core.model.Wallet
import kotlinx.coroutines.flow.Flow

/**
 * Админ-панель и супер-права.
 *
 * ВАЖНО ПРО БЕЗОПАСНОСТЬ: клиентская проверка прав — только UX.
 * Каждый метод соответствует эндпоинту, закрытому middleware AdminGuard
 * на бэкенде. Даже если злоумышленник подменит ответ [observeAccess],
 * сервер отклонит запрос с 403.
 */
interface AdminRepository {

    /** Права текущего пользователя. @silver -> OWNER + полный набор действий. */
    fun observeAccess(): Flow<AdminAccess>

    suspend fun stats(): ScResult<AdminStats>

    suspend fun searchUsers(query: String, onlyFlagged: Boolean = false): ScResult<List<User>>

    suspend fun userDetails(userId: UserId): ScResult<AdminUserDetails>

    /** Универсальное действие (кнопки в карточке пользователя админки). */
    suspend fun grant(request: AdminGrantRequest): ScResult<User>

    /* ── Выдача статусов ────────────────────────────────────────────────── */

    /** Верификационная галочка. */
    suspend fun setVerified(userId: UserId, verified: Boolean, reason: String): ScResult<User>

    /** Премиум-подписка: days = null означает Lifetime. */
    suspend fun setPremium(userId: UserId, days: Int?, reason: String): ScResult<User>

    /** Статус разработчика. */
    suspend fun setDeveloper(userId: UserId, developer: Boolean, reason: String): ScResult<User>

    /** Административная роль (включая понижение). */
    suspend fun setAdminRole(userId: UserId, role: AdminRole, reason: String): ScResult<User>

    /* ── Модерация ──────────────────────────────────────────────────────── */

    suspend fun ban(userId: UserId, reason: String, permanent: Boolean): ScResult<Unit>

    suspend fun unban(userId: UserId, reason: String): ScResult<Unit>

    suspend fun restrict(userId: UserId, reason: String, untilTs: Long): ScResult<Unit>

    fun observeReports(status: ModerationStatus? = ModerationStatus.OPEN): Flow<List<ModerationCase>>

    suspend fun resolveReport(caseId: String, status: ModerationStatus, comment: String): ScResult<Unit>

    suspend fun deleteMessageAnywhere(chatId: ChatId, messageId: MessageId, reason: String): ScResult<Unit>

    suspend fun deleteChatAnywhere(chatId: ChatId, reason: String): ScResult<Unit>

    /* ── Экономика из админки ───────────────────────────────────────────── */

    /** Начисление сильверов любому пользователю БЕЗ ограничения суммы. */
    suspend fun creditSilver(userId: UserId, amount: Long, reason: String): ScResult<Wallet>

    suspend fun debitSilver(userId: UserId, amount: Long, reason: String): ScResult<Wallet>

    suspend fun resetWallet(userId: UserId, reason: String): ScResult<Wallet>

    suspend fun refundTransaction(transactionId: String, reason: String): ScResult<Unit>

    /* ── Маркет из админки ──────────────────────────────────────────────── */

    suspend fun blockUsername(username: String, reason: String): ScResult<Unit>

    suspend fun adjustListingPrice(
        listingId: ListingId,
        priceSilver: Long,
        reason: String,
    ): ScResult<com.silverchat.core.model.UsernameListing>

    /* ── Рассылки и аудит ───────────────────────────────────────────────── */

    suspend fun broadcast(text: String, audience: AdminBroadcastAudience): ScResult<Unit>

    /**
     * Входящие рассылки от администрации.
     *
     * Приходят событием `admin.broadcast` по WebSocket. Подписчик один —
     * оболочка приложения (:app), которая показывает их уведомлением:
     * рассылка адресована всем и не привязана ни к чату, ни к экрану,
     * поэтому рисовать её внутри фичи негде.
     */
    fun observeAdminBroadcasts(): Flow<String>

    fun observeAuditLog(action: AdminAction? = null, limit: Int = 100): Flow<List<AdminLogEntry>>
}

data class AdminAccess(
    val allowed: Boolean,
    val role: AdminRole,
    val isMasterAccount: Boolean,
    val capabilities: Set<AdminAction> = emptySet(),
) {
    fun can(action: AdminAction): Boolean = allowed && action in capabilities

    companion object {
        val DENIED = AdminAccess(allowed = false, role = AdminRole.NONE, isMasterAccount = false)
    }
}

/** Расширенная карточка пользователя для админки. */
data class AdminUserDetails(
    val user: User,
    val wallet: Wallet,
    val streak: Streak,
    val premium: com.silverchat.core.model.PremiumStatus,
    val ownedUsernames: List<String>,
    val activeListings: Int,
    val reportsAgainst: Int,
    val sessions: List<DeviceSession>,
    val recentAudit: List<AdminLogEntry>,
)

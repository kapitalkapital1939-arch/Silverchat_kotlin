package com.silverchat.core.domain.repository

import com.silverchat.core.common.result.ScResult
import com.silverchat.core.model.LedgerEntry
import com.silverchat.core.model.PremiumStatus
import com.silverchat.core.model.PremiumTier
import com.silverchat.core.model.PremiumTierId
import com.silverchat.core.model.Streak
import com.silverchat.core.model.UserId
import com.silverchat.core.model.Wallet
import kotlinx.coroutines.flow.Flow

/**
 * Экономика: сильверы, стрики активности, SilverChat Premium.
 *
 * Инвариант: баланс — всегда серверная истина. Локально кэшируем только
 * последнее значение для мгновенной отрисовки, а любое изменение приходит
 * событием `wallet.updated` через WebSocket.
 */
interface WalletRepository {

    fun observeWallet(): Flow<Wallet?>

    /**
     * История движений (двойная запись).
     *
     * Отдаёт всё, что накоплено в локальном кэше: первая страница подтягивается
     * при подписке, остальные — по [loadMoreLedger]. Лимита на выдачу нет,
     * потому что размер списка ограничен тем, сколько страниц запросил
     * пользователь.
     */
    fun observeLedger(): Flow<List<LedgerEntry>>

    /**
     * Догружает страницу истории старше самой старой кэшированной записи.
     *
     * @return `true`, если сервер вернул полную страницу и история, вероятно,
     *   продолжается; `false` — если записей больше нет. Ошибкой считается
     *   только сбой запроса, а не конец истории.
     */
    suspend fun loadMoreLedger(): ScResult<Boolean>

    fun observeStreak(): Flow<Streak?>

    /** Дневное начисление за стрик. Повторный claim в тот же день -> Conflict. */
    suspend fun claimDailyStreak(): ScResult<Streak>

    suspend fun transfer(toUserId: UserId, amount: Long, comment: String?): ScResult<LedgerEntry>

    /* ── Premium ────────────────────────────────────────────────────────── */

    fun observePremiumStatus(): Flow<PremiumStatus>

    fun observePremiumTiers(): Flow<List<PremiumTier>>

    suspend fun purchasePremium(tierId: PremiumTierId): ScResult<PremiumStatus>

    suspend fun giftPremium(toUserId: UserId, tierId: PremiumTierId): ScResult<PremiumStatus>

    suspend fun cancelAutoRenew(): ScResult<Unit>

    /**
     * Проверка доступности премиум-функции.
     * Используется UI для paywall: если вернулось false — показываем
     * [com.silverchat.core.designsystem.component.PremiumPaywallSheet].
     */
    fun observePerkAvailable(perk: com.silverchat.core.model.PremiumPerkCode): Flow<Boolean>
}

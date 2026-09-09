package com.silverchat.core.domain.usecase.wallet

import com.silverchat.core.common.dispatcher.DispatcherProvider
import com.silverchat.core.common.result.ScError
import com.silverchat.core.common.result.ScResult
import com.silverchat.core.domain.base.SuspendUseCase
import com.silverchat.core.domain.repository.WalletRepository
import com.silverchat.core.model.PremiumTierId
import com.silverchat.core.model.Streak
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Ежедневное начисление за стрик активности.
 *
 * Клиент НЕ решает, положено ли начисление: он лишь отправляет claim,
 * а сервер сверяет lastClaimDate по часовому поясу пользователя.
 * Локальная проверка нужна только чтобы не дёргать сеть дважды подряд.
 */
class ClaimStreakUseCase @Inject constructor(
    private val wallet: WalletRepository,
    dispatchers: DispatcherProvider,
) : SuspendUseCase<Unit, Streak>(dispatchers) {

    override suspend fun execute(params: Unit): ScResult<Streak> {
        val current = wallet.observeStreak().first()

        if (current != null && !current.claimable) {
            return ScResult.Failure(
                ScError.Conflict("Награда за сегодня уже получена"),
            )
        }

        return wallet.claimDailyStreak()
    }
}

/** Покупка SilverChat Premium за сильверы. */
class PurchasePremiumUseCase @Inject constructor(
    private val wallet: WalletRepository,
    dispatchers: DispatcherProvider,
) : SuspendUseCase<PremiumTierId, com.silverchat.core.model.PremiumStatus>(dispatchers) {

    override suspend fun execute(params: PremiumTierId): ScResult<com.silverchat.core.model.PremiumStatus> {
        val tiers = wallet.observePremiumTiers().first()
        val tier = tiers.firstOrNull { it.id == params }
            ?: return ScResult.Failure(ScError.NotFound("Тариф не найден"))

        val balance = wallet.observeWallet().first()
            ?: return ScResult.Failure(ScError.Network("Кошелёк ещё не загружен"))

        if (tier.priceSilver > balance.available) {
            return ScResult.Failure(
                ScError.InsufficientFunds(
                    message = "Не хватает ${tier.priceSilver - balance.available} сильверов",
                    required = tier.priceSilver,
                    available = balance.available,
                ),
            )
        }

        return wallet.purchasePremium(params)
    }
}

/**
 * Расчёт прогресса стрика для UI (кольцо «7/30 дней», милестоны).
 * Чистая логика — тестируется без корутин.
 */
object StreakCalculator {

    data class Progress(
        val days: Int,
        val nextMilestoneDay: Int?,
        val progressToNext: Float,
        val rewardAtNext: Long,
        val isMaxReached: Boolean,
    )

    fun progress(streak: Streak): Progress {
        val next = streak.milestones.firstOrNull { !it.achieved && it.day > streak.streakDays }
        return if (next == null) {
            Progress(
                days = streak.streakDays,
                nextMilestoneDay = null,
                progressToNext = 1f,
                rewardAtNext = 0L,
                isMaxReached = true,
            )
        } else {
            val previous = streak.milestones.lastOrNull { it.day <= streak.streakDays }?.day ?: 0
            val span = (next.day - previous).coerceAtLeast(1)
            val done = (streak.streakDays - previous).coerceIn(0, span)
            Progress(
                days = streak.streakDays,
                nextMilestoneDay = next.day,
                progressToNext = done.toFloat() / span,
                rewardAtNext = next.rewardSilver,
                isMaxReached = false,
            )
        }
    }
}

package com.silverchat.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/* =========================================================================
   ВНУТРЕННЯЯ ВАЛЮТА — «СИЛЬВЕРЫ»
   ------------------------------------------------------------------------
   Все движения идут через двойную запись (ledger): сумма [amount] со знаком.
   Клиент НИКОГДА не хранит баланс как источник истины — только кэш из ответа
   сервера. Это исключает накрутку локальным изменением БД.
   ========================================================================= */

@Serializable
data class Wallet(
    @SerialName("user_id") val userId: UserId,
    @SerialName("balance") val balance: Long,
    @SerialName("frozen") val frozen: Long = 0L,          // в escrow на время сделки
    @SerialName("earned_total") val earnedTotal: Long = 0L,
    @SerialName("spent_total") val spentTotal: Long = 0L,
    @SerialName("updated_at") val updatedAt: Long = 0L,
) {
    val available: Long get() = balance - frozen
}

@Serializable
data class LedgerEntry(
    @SerialName("id") val id: String,
    @SerialName("amount") val amount: Long,               // > 0 начисление, < 0 списание
    @SerialName("reason") val reason: LedgerReason,
    @SerialName("reference") val reference: String? = null,
    @SerialName("counterparty") val counterpartyName: String? = null,
    @SerialName("balance_after") val balanceAfter: Long,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("reversible") val reversible: Boolean = false,
)

@Serializable
enum class LedgerReason {
    @SerialName("signup_bonus") SIGNUP_BONUS,
    @SerialName("daily_bonus") DAILY_BONUS,
    @SerialName("streak_reward") STREAK_REWARD,
    @SerialName("gift_received") GIFT_RECEIVED,
    @SerialName("gift_converted") GIFT_CONVERTED,
    @SerialName("username_sale") USERNAME_SALE,
    @SerialName("username_purchase") USERNAME_PURCHASE,
    @SerialName("username_offer") USERNAME_OFFER,
    @SerialName("gift_purchase") GIFT_PURCHASE,
    @SerialName("premium_purchase") PREMIUM_PURCHASE,
    @SerialName("admin_grant") ADMIN_GRANT,               // начисление из админки @silver
    @SerialName("admin_revoke") ADMIN_REVOKE,
    @SerialName("refund") REFUND,
    @SerialName("transfer") TRANSFER,
    @SerialName("referral") REFERRAL,
}

/* =========================================================================
   СТРИКИ АКТИВНОСТИ
   ========================================================================= */

/**
 * @property streakDays   дни подряд
 * @property frozen       «заморозки» — сохраняют стрик при пропуске дня
 * @property claimable    доступно ли дневное начисление прямо сейчас
 *
 * Начисление происходит ТОЛЬКО на сервере: клиент шлёт `streak.claim`,
 * сервер сверяет `lastClaimDate` по часовому поясу пользователя.
 */
@Serializable
data class Streak(
    @SerialName("streak_days") val streakDays: Int,
    @SerialName("best_streak") val bestStreak: Int = 0,
    @SerialName("frozen") val frozenStreaks: Int = 0,
    @SerialName("last_claim_date") val lastClaimDate: String? = null,
    @SerialName("next_reward_silver") val nextRewardSilver: Long = 50L,
    @SerialName("milestones") val milestones: List<StreakMilestone> = emptyList(),
    @SerialName("claimable") val claimable: Boolean = false,
)

@Serializable
data class StreakMilestone(
    @SerialName("day") val day: Int,
    @SerialName("reward_silver") val rewardSilver: Long,
    @SerialName("achieved") val achieved: Boolean = false,
)

/* =========================================================================
   SILVERCHAT PREMIUM
   ========================================================================= */

@Serializable
sealed interface PremiumStatus {

    /** Подписка действует прямо сейчас. Lifetime считается активным всегда. */
    val isActive: Boolean
        get() = when (this) {
            None -> false
            is Expired -> false
            Lifetime -> true
            is Active -> expiresAt > System.currentTimeMillis()
        }

    /** Дата окончания для подписки «до …»; null для Lifetime и отсутствующей. */
    val expiresAtOrNull: Long?
        get() = when (this) {
            is Active -> expiresAt
            is Expired -> expiredAt
            Lifetime -> null
            None -> null
        }

    @Serializable
    @SerialName("none")
    data object None : PremiumStatus

    @Serializable
    @SerialName("active")
    data class Active(
        @SerialName("tier") val tier: PremiumTierId,
        @SerialName("expires_at") val expiresAt: Long,
        @SerialName("auto_renew") val autoRenew: Boolean = false,
        @SerialName("gifted_by") val giftedBy: UserId? = null,
    ) : PremiumStatus

    @Serializable
    @SerialName("expired")
    data class Expired(@SerialName("expired_at") val expiredAt: Long) : PremiumStatus

    /** Пожизненный премиум — выдаётся только из админ-панели. */
    @Serializable
    @SerialName("lifetime")
    data object Lifetime : PremiumStatus
}

@Serializable
data class PremiumTier(
    @SerialName("id") val id: PremiumTierId,
    @SerialName("title") val title: String,
    @SerialName("period_days") val periodDays: Int,
    @SerialName("price_silver") val priceSilver: Long,
    @SerialName("price_fiat") val priceFiat: String? = null,
    @SerialName("perks") val perks: List<PremiumPerk> = emptyList(),
    @SerialName("best_value") val bestValue: Boolean = false,
    @SerialName("discount_percent") val discountPercent: Int = 0,
)

@Serializable
@JvmInline
value class PremiumTierId(val raw: String) {
    override fun toString(): String = raw
    companion object {
        val MONTH = PremiumTierId("premium_month")
        val HALF_YEAR = PremiumTierId("premium_6m")
        val YEAR = PremiumTierId("premium_year")
        val LIFETIME = PremiumTierId("premium_lifetime")
    }
}

/**
 * Что именно разблокирует Premium. Перечислено явно, чтобы клиент мог
 * показать paywall с конкретикой, а не «купите премиум».
 */
@Serializable
data class PremiumPerk(
    @SerialName("code") val code: PremiumPerkCode,
    @SerialName("title") val title: String,
    @SerialName("description") val description: String,
)

@Serializable
enum class PremiumPerkCode {
    @SerialName("exclusive_stickers") EXCLUSIVE_STICKERS,   // уникальные стикеры
    @SerialName("exclusive_gifs") EXCLUSIVE_GIFS,           // уникальные GIF
    @SerialName("animated_avatar") ANIMATED_AVATAR,         // анимированная аватарка (видео)
    @SerialName("animated_banner") ANIMATED_BANNER,         // анимированный баннер
    @SerialName("custom_themes") CUSTOM_THEMES,             // расширенная кастомизация тем
    @SerialName("free_reactions") FREE_REACTIONS,           // любые эмодзи-реакции
    @SerialName("no_ads") NO_ADS,
    @SerialName("larger_uploads") LARGER_UPLOADS,
    @SerialName("voice_to_text") VOICE_TO_TEXT,
    @SerialName("story_stealth") STORY_STEALTH,             // скрытный просмотр сторис
    @SerialName("username_discount") USERNAME_DISCOUNT,     // скидка в маркете
    @SerialName("more_folders") MORE_FOLDERS,
    @SerialName("hd_calls") HD_CALLS,                       // HD-качество звонков
}

/** Ответ на попытку использовать премиум-функцию без подписки -> показ paywall. */
@Serializable
data class PremiumGate(
    @SerialName("perks") val requiredPerks: List<PremiumPerkCode>,
    @SerialName("suggested_tier") val suggestedTier: PremiumTierId,
    @SerialName("message") val message: String? = null,
)

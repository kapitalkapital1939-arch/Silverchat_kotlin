package com.silverchat.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/* =========================================================================
   АДМИН-ПАНЕЛЬ И СУПЕР-ПРАВА
   ------------------------------------------------------------------------
   Доступ имеет только мастер-аккаунт @silver (UserFlags.isMasterAccount)
   и роли ниже. Проверка делается ТРИЖДЫ:
     1. на бэкенде в middleware AdminGuard (обязательно);
     2. в доменном слое клиента (AdminAccessPolicy) — чтобы не показывать UI;
     3. в навигации — граф :feature:admin не регистрируется без прав.
   Клиентская проверка — только UX. Безопасность обеспечивает бэкенд.
   ========================================================================= */

/** Логин мастер-аккаунта, зашитый в требования продукта. */
object MasterAccount {
    const val USERNAME: String = "silver"
    const val HANDLE: String = "@$USERNAME"
}

@Serializable
enum class AdminRole {
    @SerialName("none") NONE,
    @SerialName("moderator") MODERATOR,     // жалобы, бан, удаление контента
    @SerialName("support") SUPPORT,         // просмотр карточек, помощь
    @SerialName("market_manager") MARKET_MANAGER, // маркет: листинги, цены
    @SerialName("finance") FINANCE,         // начисление/списание сильверов
    @SerialName("admin") ADMIN,             // выдача статусов, верификация
    @SerialName("owner") OWNER,             // @silver: всё, включая роли
}

/**
 * Действия админки. Каждый пункт соответствует отдельной кнопке/экрану
 * и отдельному REST-эндпоинту + записи в аудит-лог.
 */
@Serializable
enum class AdminAction {
    @SerialName("grant_verified") GRANT_VERIFIED,          // верификационная галочка
    @SerialName("revoke_verified") REVOKE_VERIFIED,
    @SerialName("grant_premium") GRANT_PREMIUM,            // премиум-подписка
    @SerialName("revoke_premium") REVOKE_PREMIUM,
    @SerialName("grant_developer") GRANT_DEVELOPER,        // статус разработчика
    @SerialName("revoke_developer") REVOKE_DEVELOPER,
    @SerialName("grant_admin_role") GRANT_ADMIN_ROLE,
    @SerialName("revoke_admin_role") REVOKE_ADMIN_ROLE,
    @SerialName("credit_silver") CREDIT_SILVER,            // начисление сильверов (без лимита)
    @SerialName("debit_silver") DEBIT_SILVER,
    @SerialName("reset_wallet") RESET_WALLET,
    @SerialName("ban_user") BAN_USER,
    @SerialName("unban_user") UNBAN_USER,
    @SerialName("restrict_user") RESTRICT_USER,
    @SerialName("delete_message") DELETE_MESSAGE,
    @SerialName("delete_chat") DELETE_CHAT,
    @SerialName("block_username") BLOCK_USERNAME,          // изъятие юзернейма из маркета
    @SerialName("adjust_listing_price") ADJUST_LISTING_PRICE,
    @SerialName("refund_transaction") REFUND_TRANSACTION,
    @SerialName("broadcast") BROADCAST,                    // рассылка всем
    @SerialName("resolve_report") RESOLVE_REPORT,
}

/**
 * Аудитория рассылки из админ-панели.
 *
 * Значения соответствуют сегментам на бэкенде: рассылка «всем» —
 * необратимое действие, поэтому аудитория задаётся явно, а не по умолчанию.
 */
@Serializable
enum class AdminBroadcastAudience {
    /** Все зарегистрированные пользователи. */
    @SerialName("all") ALL,

    /** Только активные за последние 30 дней. */
    @SerialName("active") ACTIVE,

    /** Только подписчики Premium. */
    @SerialName("premium") PREMIUM,

    /** Только верифицированные аккаунты. */
    @SerialName("verified") VERIFIED,

    /** Пользователи, никогда не покупавшие Premium. */
    @SerialName("non_premium") NON_PREMIUM,
}

/** Запрос на изменение прав/баланса пользователя. */
@Serializable
data class AdminGrantRequest(
    @SerialName("target_user_id") val targetUserId: UserId,
    @SerialName("action") val action: AdminAction,
    @SerialName("amount_silver") val amountSilver: Long? = null,
    @SerialName("duration_days") val durationDays: Int? = null,
    @SerialName("role") val role: AdminRole? = null,
    @SerialName("reason") val reason: String,             // обязательное обоснование для аудита
)

/** Запись аудит-лога. Неизменяема, хранится на бэке, клиент только читает. */
@Serializable
data class AdminLogEntry(
    @SerialName("id") val id: String,
    @SerialName("actor_id") val actorId: UserId,
    @SerialName("actor_name") val actorName: String,
    @SerialName("action") val action: AdminAction,
    @SerialName("target_user_id") val targetUserId: UserId? = null,
    @SerialName("target_name") val targetName: String? = null,
    @SerialName("payload") val payload: String? = null,
    @SerialName("reason") val reason: String? = null,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("ip") val ip: String? = null,
)

/** Жалоба / модерационный кейс. */
@Serializable
data class ModerationCase(
    @SerialName("id") val id: String,
    @SerialName("reporter_id") val reporterId: UserId,
    @SerialName("target_type") val targetType: ModerationTargetType,
    @SerialName("target_id") val targetId: String,
    @SerialName("reason") val reason: ReportReason,
    @SerialName("comment") val comment: String? = null,
    @SerialName("status") val status: ModerationStatus = ModerationStatus.OPEN,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("resolved_by") val resolvedBy: UserId? = null,
)

@Serializable
enum class ModerationTargetType {
    @SerialName("user") USER,
    @SerialName("message") MESSAGE,
    @SerialName("chat") CHAT,
    @SerialName("story") STORY,
    @SerialName("username") USERNAME,
    @SerialName("gift") GIFT,
}

@Serializable
enum class ReportReason {
    @SerialName("spam") SPAM,
    @SerialName("scam") SCAM,
    @SerialName("abuse") ABUSE,
    @SerialName("nudity") NUDITY,
    @SerialName("violence") VIOLENCE,
    @SerialName("impersonation") IMPERSONATION,
    @SerialName("illegal") ILLEGAL,
    @SerialName("other") OTHER,
}

@Serializable
enum class ModerationStatus {
    @SerialName("open") OPEN,
    @SerialName("in_review") IN_REVIEW,
    @SerialName("resolved") RESOLVED,
    @SerialName("rejected") REJECTED,
}

/** Сводка для дашборда админки. */
@Serializable
data class AdminStats(
    @SerialName("total_users") val totalUsers: Long,
    @SerialName("active_users_24h") val activeUsers24h: Long,
    @SerialName("premium_users") val premiumUsers: Long,
    @SerialName("total_messages_24h") val totalMessages24h: Long,
    @SerialName("silver_emitted_total") val silverEmittedTotal: Long,
    @SerialName("silver_burned_total") val silverBurnedTotal: Long,
    @SerialName("market_volume_24h") val marketVolume24h: Long,
    @SerialName("open_reports") val openReports: Int,
    @SerialName("active_calls") val activeCalls: Int,
    @SerialName("stories_published_24h") val storiesPublished24h: Long,
    @SerialName("sparkline_users") val sparklineUsers: List<Int> = emptyList(),
    @SerialName("sparkline_messages") val sparklineMessages: List<Int> = emptyList(),
)

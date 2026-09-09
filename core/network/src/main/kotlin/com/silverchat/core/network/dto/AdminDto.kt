package com.silverchat.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/* =========================================================================
   DTO АДМИН-ПАНЕЛИ (@silver)
   ========================================================================= */

@Serializable
data class AdminAccessDto(
    @SerialName("allowed") val allowed: Boolean,
    @SerialName("role") val role: String = "none",
    @SerialName("is_master_account") val isMasterAccount: Boolean = false,
    @SerialName("capabilities") val capabilities: List<String> = emptyList(),
)

@Serializable
data class AdminStatsDto(
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

@Serializable
data class AdminUserDetailsDto(
    @SerialName("user") val user: UserDto,
    @SerialName("wallet") val wallet: WalletDto,
    @SerialName("streak") val streak: StreakDto,
    @SerialName("premium") val premium: PremiumStatusDto,
    @SerialName("owned_usernames") val ownedUsernames: List<String> = emptyList(),
    @SerialName("active_listings") val activeListings: Int = 0,
    @SerialName("reports_against") val reportsAgainst: Int = 0,
    @SerialName("sessions") val sessions: List<SessionDto> = emptyList(),
    @SerialName("recent_audit") val recentAudit: List<AdminLogDto> = emptyList(),
)

@Serializable
data class ReportDto(
    @SerialName("id") val id: String,
    @SerialName("reporter_id") val reporterId: String,
    @SerialName("target_type") val targetType: String,
    @SerialName("target_id") val targetId: String,
    @SerialName("reason") val reason: String,
    @SerialName("comment") val comment: String? = null,
    @SerialName("status") val status: String = "open",
    @SerialName("created_at") val createdAt: Long,
    @SerialName("resolved_by") val resolvedBy: String? = null,
)

@Serializable
data class AdminLogDto(
    @SerialName("id") val id: String,
    @SerialName("actor_id") val actorId: String,
    @SerialName("actor_name") val actorName: String,
    @SerialName("action") val action: String,
    @SerialName("target_user_id") val targetUserId: String? = null,
    @SerialName("target_name") val targetName: String? = null,
    @SerialName("payload") val payload: String? = null,
    @SerialName("reason") val reason: String? = null,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("ip") val ip: String? = null,
)

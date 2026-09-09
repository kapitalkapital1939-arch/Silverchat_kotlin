package com.silverchat.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/* ── Сторис ──────────────────────────────────────────────────────────────── */

@Serializable
data class StoryDto(
    @SerialName("id") val id: String,
    @SerialName("author_id") val authorId: String,
    @SerialName("author") val author: UserDto? = null,
    @SerialName("media") val media: StoryMediaDto,
    @SerialName("caption") val caption: String? = null,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("expires_at") val expiresAt: Long,
    @SerialName("privacy") val privacy: String = "contacts",
    @SerialName("viewers_count") val viewersCount: Int = 0,
    @SerialName("reactions") val reactions: List<ReactionDto> = emptyList(),
    @SerialName("seen_by_me") val seenByMe: Boolean = false,
    @SerialName("pinned_to_profile") val pinnedToProfile: Boolean = false,
    @SerialName("replies_enabled") val repliesEnabled: Boolean = true,
    @SerialName("location") val location: LocationRequest? = null,
    @SerialName("mention_ids") val mentionIds: List<String> = emptyList(),
)

@Serializable
data class StoryMediaDto(
    @SerialName("type") val type: String,
    @SerialName("url") val url: String,
    @SerialName("thumb_url") val thumbUrl: String? = null,
    @SerialName("duration_ms") val durationMs: Long = 5_000L,
    @SerialName("width") val width: Int = 1080,
    @SerialName("height") val height: Int = 1920,
    @SerialName("background_gradient") val backgroundGradient: List<Long> = emptyList(),
    @SerialName("overlay_text") val overlayText: String? = null,
)

@Serializable
data class StoryClusterDto(
    @SerialName("author_id") val authorId: String,
    @SerialName("author") val author: UserDto,
    @SerialName("stories") val stories: List<StoryDto>,
)

/* ── Кошелёк / экономика ─────────────────────────────────────────────────── */

@Serializable
data class WalletDto(
    @SerialName("user_id") val userId: String,
    @SerialName("balance") val balance: Long,
    @SerialName("frozen") val frozen: Long = 0L,
    @SerialName("earned_total") val earnedTotal: Long = 0L,
    @SerialName("spent_total") val spentTotal: Long = 0L,
    @SerialName("updated_at") val updatedAt: Long = 0L,
)

@Serializable
data class LedgerDto(
    @SerialName("id") val id: String,
    @SerialName("amount") val amount: Long,
    @SerialName("reason") val reason: String,
    @SerialName("reference") val reference: String? = null,
    @SerialName("counterparty") val counterparty: String? = null,
    @SerialName("balance_after") val balanceAfter: Long,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("reversible") val reversible: Boolean = false,
)

@Serializable
data class StreakDto(
    @SerialName("streak_days") val streakDays: Int,
    @SerialName("best_streak") val bestStreak: Int = 0,
    @SerialName("frozen") val frozen: Int = 0,
    @SerialName("last_claim_date") val lastClaimDate: String? = null,
    @SerialName("next_reward_silver") val nextRewardSilver: Long = 50L,
    @SerialName("milestones") val milestones: List<MilestoneDto> = emptyList(),
    @SerialName("claimable") val claimable: Boolean = false,
)

@Serializable
data class MilestoneDto(
    @SerialName("day") val day: Int,
    @SerialName("reward_silver") val rewardSilver: Long,
    @SerialName("achieved") val achieved: Boolean = false,
)

@Serializable
data class PremiumStatusDto(
    @SerialName("kind") val kind: String = "none", // none | active | expired | lifetime
    @SerialName("tier") val tier: String? = null,
    @SerialName("expires_at") val expiresAt: Long? = null,
    @SerialName("auto_renew") val autoRenew: Boolean = false,
    @SerialName("gifted_by") val giftedBy: String? = null,
    @SerialName("expired_at") val expiredAt: Long? = null,
)

@Serializable
data class PremiumTierDto(
    @SerialName("id") val id: String,
    @SerialName("title") val title: String,
    @SerialName("period_days") val periodDays: Int,
    @SerialName("price_silver") val priceSilver: Long,
    @SerialName("price_fiat") val priceFiat: String? = null,
    @SerialName("perks") val perks: List<PremiumPerkDto> = emptyList(),
    @SerialName("best_value") val bestValue: Boolean = false,
    @SerialName("discount_percent") val discountPercent: Int = 0,
)

@Serializable
data class PremiumPerkDto(
    @SerialName("code") val code: String,
    @SerialName("title") val title: String,
    @SerialName("description") val description: String,
)

/* ── Маркет ──────────────────────────────────────────────────────────────── */

@Serializable
data class MarketListingDto(
    @SerialName("id") val id: String,
    @SerialName("username") val username: String,
    @SerialName("price_silver") val priceSilver: Long,
    @SerialName("rarity") val rarity: String = "common",
    @SerialName("categories") val categories: List<String> = emptyList(),
    @SerialName("status") val status: String = "available",
    @SerialName("seller_id") val sellerId: String? = null,
    @SerialName("seller_name") val sellerName: String? = null,
    @SerialName("description") val description: String? = null,
    @SerialName("views") val views: Int = 0,
    @SerialName("offers_count") val offersCount: Int = 0,
    @SerialName("min_offer_silver") val minOfferSilver: Long? = null,
    @SerialName("history") val priceHistory: List<PricePointDto> = emptyList(),
    @SerialName("created_at") val createdAt: Long = 0L,
    @SerialName("expires_at") val expiresAt: Long? = null,
)

@Serializable
data class PricePointDto(
    @SerialName("at") val at: Long,
    @SerialName("price_silver") val priceSilver: Long,
)

@Serializable
data class OfferDto(
    @SerialName("id") val id: String,
    @SerialName("listing_id") val listingId: String,
    @SerialName("buyer_id") val buyerId: String,
    @SerialName("buyer_name") val buyerName: String,
    @SerialName("amount_silver") val amountSilver: Long,
    @SerialName("status") val status: String,
    @SerialName("created_at") val createdAt: Long,
)

@Serializable
data class GiftDto(
    @SerialName("id") val id: String,
    @SerialName("title") val title: String,
    @SerialName("emoji") val emoji: String,
    @SerialName("asset_url") val assetUrl: String? = null,
    @SerialName("lottie_url") val lottieUrl: String? = null,
    @SerialName("price_silver") val priceSilver: Long,
    @SerialName("category") val category: String = "classic",
    @SerialName("premium_only") val premiumOnly: Boolean = false,
    @SerialName("limited_total") val limitedTotal: Int? = null,
    @SerialName("limited_left") val limitedLeft: Int? = null,
    @SerialName("convertible_silver") val convertibleSilver: Long? = null,
)

@Serializable
data class OwnedGiftDto(
    @SerialName("id") val id: String,
    @SerialName("gift") val gift: GiftDto,
    @SerialName("received_from") val receivedFrom: String? = null,
    @SerialName("received_at") val receivedAt: Long,
    @SerialName("converted") val converted: Boolean = false,
    @SerialName("upgrade_level") val upgradeLevel: Int = 1,
)

package com.silverchat.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/* =========================================================================
   МАРКЕТ ЮЗЕРНЕЙМОВ
   ------------------------------------------------------------------------
   Покупка уникального @username за внутреннюю валюту («сильверы»).
   Сделка атомарна на стороне бэкенда: списание сильверов и передача
   username проходят в одной транзакции (см. backend/services/market).
   ========================================================================= */

@Serializable
data class UsernameListing(
    @SerialName("id") val id: ListingId,
    @SerialName("username") val username: String,
    @SerialName("price_silver") val priceSilver: Long,
    @SerialName("rarity") val rarity: UsernameRarity,
    @SerialName("categories") val categories: List<UsernameCategory> = emptyList(),
    @SerialName("status") val status: ListingStatus = ListingStatus.AVAILABLE,
    @SerialName("seller_id") val sellerId: UserId? = null,
    @SerialName("seller_name") val sellerName: String? = null,
    @SerialName("description") val description: String? = null,
    @SerialName("views") val views: Int = 0,
    @SerialName("offers_count") val offersCount: Int = 0,
    @SerialName("min_offer_silver") val minOfferSilver: Long? = null,
    @SerialName("history") val priceHistory: List<PricePoint> = emptyList(),
    @SerialName("created_at") val createdAt: Long = 0L,
    @SerialName("expires_at") val expiresAt: Long? = null,
) {
    val length: Int get() = username.length
    val isShort: Boolean get() = length <= 4
    val isReserved: Boolean get() = status == ListingStatus.RESERVED || status == ListingStatus.SOLD
}

@Serializable
@JvmInline
value class ListingId(val raw: String) {
    override fun toString(): String = raw
}

/**
 * Редкость влияет на стартовую цену и на бейдж в карточке маркета.
 * SCORE вычисляется на бэке: длина + словарность + симметрия + история продаж.
 */
@Serializable
enum class UsernameRarity(val scoreWeight: Double) {
    @SerialName("common") COMMON(1.0),
    @SerialName("rare") RARE(2.5),
    @SerialName("epic") EPIC(6.0),
    @SerialName("legendary") LEGENDARY(15.0),
    @SerialName("grail") GRAIL(40.0),
}

@Serializable
enum class UsernameCategory {
    @SerialName("short") SHORT,          // 1–4 символа
    @SerialName("word") WORD,            // словарное слово
    @SerialName("name") NAME,            // имя
    @SerialName("crypto") CRYPTO,        // btc, eth, token…
    @SerialName("gaming") GAMING,
    @SerialName("business") BUSINESS,
    @SerialName("numeric") NUMERIC,
    @SerialName("palindrome") PALINDROME,
    @SerialName("repeated") REPEATED,     // aaa, xxx
    @SerialName("premium") PREMIUM_ONLY,
}

@Serializable
enum class ListingStatus {
    @SerialName("available") AVAILABLE,
    @SerialName("reserved") RESERVED,     // забронировано на время оплаты
    @SerialName("sold") SOLD,
    @SerialName("auction") AUCTION,
    @SerialName("blocked") BLOCKED,       // зарезервировано администрацией
    @SerialName("owned_by_me") OWNED_BY_ME,
}

@Serializable
data class PricePoint(
    @SerialName("at") val at: Long,
    @SerialName("price_silver") val priceSilver: Long,
)

/** Заявка на покупку. Проходит через escrow: бронь 15 минут. */
@Serializable
data class PurchaseRequest(
    @SerialName("listing_id") val listingId: ListingId,
    @SerialName("buyer_id") val buyerId: UserId,
    @SerialName("offer_silver") val offerSilver: Long? = null,
    @SerialName("use_balance") val useBalance: Boolean = true,
)

@Serializable
sealed interface PurchaseResult {
    @Serializable
    @SerialName("success")
    data class Success(
        @SerialName("listing") val listing: UsernameListing,
        @SerialName("new_balance") val newBalance: Long,
        @SerialName("tx_id") val transactionId: String,
    ) : PurchaseResult

    @Serializable
    @SerialName("insufficient_funds")
    data class InsufficientFunds(
        @SerialName("required") val required: Long,
        @SerialName("available") val available: Long,
    ) : PurchaseResult

    @Serializable
    @SerialName("taken")
    data object AlreadyTaken : PurchaseResult

    @Serializable
    @SerialName("reserved")
    data class Reserved(@SerialName("until") val reservedUntil: Long) : PurchaseResult

    @Serializable
    @SerialName("forbidden")
    data object Forbidden : PurchaseResult
}

/** Выставление собственного username на продажу. */
@Serializable
data class SaleRequest(
    @SerialName("username") val username: String,
    @SerialName("price_silver") val priceSilver: Long,
    @SerialName("accept_offers") val acceptOffers: Boolean = true,
    @SerialName("min_offer_silver") val minOfferSilver: Long? = null,
    @SerialName("auction") val auction: Boolean = false,
    @SerialName("duration_hours") val durationHours: Int? = null,
)

/** Встречное предложение по цене. */
@Serializable
data class UsernameOffer(
    @SerialName("id") val id: String,
    @SerialName("listing_id") val listingId: ListingId,
    @SerialName("buyer_id") val buyerId: UserId,
    @SerialName("buyer_name") val buyerName: String,
    @SerialName("amount_silver") val amountSilver: Long,
    @SerialName("status") val status: OfferStatus,
    @SerialName("created_at") val createdAt: Long,
)

@Serializable
enum class OfferStatus {
    @SerialName("pending") PENDING,
    @SerialName("accepted") ACCEPTED,
    @SerialName("declined") DECLINED,
    @SerialName("expired") EXPIRED,
}

/* =========================================================================
   ПОДАРКИ ЗА СИЛЬВЕРЫ
   ========================================================================= */

@Serializable
data class Gift(
    @SerialName("id") val id: String,
    @SerialName("title") val title: String,
    @SerialName("emoji") val emoji: String,
    @SerialName("asset_url") val assetUrl: String? = null,
    @SerialName("lottie_url") val lottieUrl: String? = null,
    @SerialName("price_silver") val priceSilver: Long,
    @SerialName("category") val category: GiftCategory = GiftCategory.CLASSIC,
    @SerialName("premium_only") val premiumOnly: Boolean = false,
    @SerialName("limited_total") val limitedTotal: Int? = null,
    @SerialName("limited_left") val limitedLeft: Int? = null,
    @SerialName("convertible_silver") val convertibleSilver: Long? = null,
) {
    val isLimited: Boolean get() = limitedTotal != null
    val soldOut: Boolean get() = limitedLeft == 0
}

@Serializable
enum class GiftCategory {
    @SerialName("classic") CLASSIC,
    @SerialName("animated") ANIMATED,
    @SerialName("luxury") LUXURY,
    @SerialName("seasonal") SEASONAL,
    @SerialName("collectible") COLLECTIBLE,
}

/** Подарок в инвентаре пользователя. Можно конвертировать в сильверы. */
@Serializable
data class OwnedGift(
    @SerialName("id") val id: String,
    @SerialName("gift") val gift: Gift,
    @SerialName("received_from") val receivedFrom: UserId? = null,
    @SerialName("received_at") val receivedAt: Long,
    @SerialName("converted") val converted: Boolean = false,
    @SerialName("upgrade_level") val upgradeLevel: Int = 1,
)

@Serializable
data class GiftTransaction(
    @SerialName("gift_id") val giftId: String,
    @SerialName("receiver_id") val receiverId: UserId,
    @SerialName("message") val message: String? = null,
    @SerialName("anonymous") val anonymous: Boolean = false,
)

/* =========================================================================
   МАРКЕТ: ОБЩИЕ ВИТРИНЫ
   ========================================================================= */

@Serializable
data class MarketFeed(
    @SerialName("listings") val listings: List<UsernameListing>,
    @SerialName("gifts") val gifts: List<Gift>,
    @SerialName("premium_tiers") val premiumTiers: List<PremiumTier>,
    @SerialName("trending") val trending: List<String> = emptyList(),
    @SerialName("total_volume_24h") val volume24h: Long = 0L,
)

@Serializable
enum class MarketSort {
    @SerialName("price_asc") PRICE_ASC,
    @SerialName("price_desc") PRICE_DESC,
    @SerialName("rarity") RARITY,
    @SerialName("newest") NEWEST,
    @SerialName("length") LENGTH,
    @SerialName("popular") POPULAR,
}

@Serializable
data class MarketQuery(
    @SerialName("search") val search: String = "",
    @SerialName("categories") val categories: List<UsernameCategory> = emptyList(),
    @SerialName("rarity") val rarity: List<UsernameRarity> = emptyList(),
    @SerialName("max_price") val maxPriceSilver: Long? = null,
    @SerialName("min_length") val minLength: Int? = null,
    @SerialName("max_length") val maxLength: Int? = null,
    @SerialName("sort") val sort: MarketSort = MarketSort.POPULAR,
    @SerialName("page") val page: Int = 0,
)

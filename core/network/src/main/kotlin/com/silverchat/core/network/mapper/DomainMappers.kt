package com.silverchat.core.network.mapper

import com.silverchat.core.model.AdminAction
import com.silverchat.core.model.AdminGrantRequest
import com.silverchat.core.model.AdminLogEntry
import com.silverchat.core.model.AdminRole
import com.silverchat.core.model.AdminStats
import com.silverchat.core.model.CallEndReason
import com.silverchat.core.model.CallHistoryEntry
import com.silverchat.core.model.CallId
import com.silverchat.core.model.CallParticipant
import com.silverchat.core.model.CallSession
import com.silverchat.core.model.CallState
import com.silverchat.core.model.CallType
import com.silverchat.core.model.ChatId
import com.silverchat.core.model.ConnectionQuality
import com.silverchat.core.model.Gift
import com.silverchat.core.model.GiftCategory
import com.silverchat.core.model.IceServer
import com.silverchat.core.model.LedgerEntry
import com.silverchat.core.model.LedgerReason
import com.silverchat.core.model.ListingId
import com.silverchat.core.model.ListingStatus
import com.silverchat.core.model.MarketFeed
import com.silverchat.core.model.MessageId
import com.silverchat.core.model.ModerationCase
import com.silverchat.core.model.ModerationStatus
import com.silverchat.core.model.ModerationTargetType
import com.silverchat.core.model.OfferStatus
import com.silverchat.core.model.OwnedGift
import com.silverchat.core.model.PremiumPerk
import com.silverchat.core.model.PremiumPerkCode
import com.silverchat.core.model.PremiumTier
import com.silverchat.core.model.PremiumTierId
import com.silverchat.core.model.PricePoint
import com.silverchat.core.model.PurchaseRequest
import com.silverchat.core.model.PurchaseResult
import com.silverchat.core.model.ReportReason
import com.silverchat.core.model.Story
import com.silverchat.core.model.StoryCluster
import com.silverchat.core.model.StoryId
import com.silverchat.core.model.StoryMedia
import com.silverchat.core.model.StoryMediaType
import com.silverchat.core.model.StoryPrivacy
import com.silverchat.core.model.Streak
import com.silverchat.core.model.StreakMilestone
import com.silverchat.core.model.User
import com.silverchat.core.model.UserId
import com.silverchat.core.model.UsernameCategory
import com.silverchat.core.model.UsernameListing
import com.silverchat.core.model.UsernameOffer
import com.silverchat.core.model.UsernameRarity
import com.silverchat.core.model.Wallet
import com.silverchat.core.network.dto.AdminAccessDto
import com.silverchat.core.network.dto.AdminLogDto
import com.silverchat.core.network.dto.AdminStatsDto
import com.silverchat.core.network.dto.BuyResponse
import com.silverchat.core.network.dto.CallHistoryDto
import com.silverchat.core.network.dto.CallParticipantDto
import com.silverchat.core.network.dto.CallSessionDto
import com.silverchat.core.network.dto.GiftDto
import com.silverchat.core.network.dto.IceServerDto
import com.silverchat.core.network.dto.LedgerDto
import com.silverchat.core.network.dto.MarketFeedDto
import com.silverchat.core.network.dto.MarketListingDto
import com.silverchat.core.network.dto.MilestoneDto
import com.silverchat.core.network.dto.OfferDto
import com.silverchat.core.network.dto.OwnedGiftDto
import com.silverchat.core.network.dto.PremiumPerkDto
import com.silverchat.core.network.dto.PremiumTierDto
import com.silverchat.core.network.dto.PricePointDto
import com.silverchat.core.network.dto.ReportDto
import com.silverchat.core.network.dto.StoryClusterDto
import com.silverchat.core.network.dto.StoryDto
import com.silverchat.core.network.dto.StoryMediaDto
import com.silverchat.core.network.dto.StreakDto
import com.silverchat.core.network.dto.WalletDto

/* ── Сторис ──────────────────────────────────────────────────────────────── */

fun StoryDto.toDomain(): Story = Story(
    id = StoryId(id),
    authorId = UserId(authorId),
    author = author?.toDomain(),
    media = media.toDomain(),
    caption = caption,
    createdAt = createdAt,
    expiresAt = expiresAt,
    privacy = when (privacy) {
        "everyone" -> StoryPrivacy.EVERYONE
        "close_friends" -> StoryPrivacy.CLOSE_FRIENDS
        "selected" -> StoryPrivacy.SELECTED
        "private" -> StoryPrivacy.PRIVATE
        else -> StoryPrivacy.CONTACTS
    },
    viewersCount = viewersCount,
    reactions = reactions.map { it.toDomain() },
    seenByMe = seenByMe,
    pinnedToProfile = pinnedToProfile,
    repliesEnabled = repliesEnabled,
    location = location?.toDomain(),
    mentionIds = mentionIds.map { UserId(it) },
)

fun StoryMediaDto.toDomain(): StoryMedia = StoryMedia(
    type = when (type) {
        "video" -> StoryMediaType.VIDEO
        "text" -> StoryMediaType.TEXT
        "gif" -> StoryMediaType.GIF
        else -> StoryMediaType.PHOTO
    },
    url = url,
    thumbUrl = thumbUrl,
    durationMs = durationMs,
    width = width,
    height = height,
    backgroundGradient = backgroundGradient,
    overlayText = overlayText,
)

fun StoryClusterDto.toDomain(): StoryCluster = StoryCluster(
    authorId = UserId(authorId),
    author = author.toDomain(),
    stories = stories.map { it.toDomain() },
)

/* ── Кошелёк и экономика ─────────────────────────────────────────────────── */

fun WalletDto.toDomain(): Wallet = Wallet(
    userId = UserId(userId),
    balance = balance,
    frozen = frozen,
    earnedTotal = earnedTotal,
    spentTotal = spentTotal,
    updatedAt = updatedAt,
)

fun LedgerDto.toDomain(): LedgerEntry = LedgerEntry(
    id = id,
    amount = amount,
    reason = LedgerReason.entries.firstOrNull { it.name.equals(reason, true) } ?: LedgerReason.TRANSFER,
    reference = reference,
    counterpartyName = counterparty,
    balanceAfter = balanceAfter,
    createdAt = createdAt,
    reversible = reversible,
)

fun StreakDto.toDomain(): Streak = Streak(
    streakDays = streakDays,
    bestStreak = bestStreak,
    frozenStreaks = frozen,
    lastClaimDate = lastClaimDate,
    nextRewardSilver = nextRewardSilver,
    milestones = milestones.map { StreakMilestone(it.day, it.rewardSilver, it.achieved) },
    claimable = claimable,
)

fun MilestoneDto.toDomain(): StreakMilestone = StreakMilestone(day, rewardSilver, achieved)

fun PremiumTierDto.toDomain(): PremiumTier = PremiumTier(
    id = PremiumTierId(id),
    title = title,
    periodDays = periodDays,
    priceSilver = priceSilver,
    priceFiat = priceFiat,
    perks = perks.map { PremiumPerk(perkCode(it.code), it.title, it.description) },
    bestValue = bestValue,
    discountPercent = discountPercent,
)

private fun perkCode(raw: String): PremiumPerkCode =
    PremiumPerkCode.entries.firstOrNull { it.name.equals(raw, true) } ?: PremiumPerkCode.NO_ADS

fun PremiumPerkDto.toDomain(): PremiumPerk = PremiumPerk(perkCode(code), title, description)

/* ── Маркет ──────────────────────────────────────────────────────────────── */

fun MarketListingDto.toDomain(): UsernameListing = UsernameListing(
    id = ListingId(id),
    username = username,
    priceSilver = priceSilver,
    rarity = UsernameRarity.entries.firstOrNull { it.name.equals(rarity, true) } ?: UsernameRarity.COMMON,
    categories = categories.mapNotNull { raw ->
        UsernameCategory.entries.firstOrNull { it.name.equals(raw, true) }
    },
    status = ListingStatus.entries.firstOrNull { it.name.equals(status, true) } ?: ListingStatus.AVAILABLE,
    sellerId = sellerId?.let { UserId(it) },
    sellerName = sellerName,
    description = description,
    views = views,
    offersCount = offersCount,
    minOfferSilver = minOfferSilver,
    priceHistory = priceHistory.map { PricePoint(it.at, it.priceSilver) },
    createdAt = createdAt,
    expiresAt = expiresAt,
)

fun PricePointDto.toDomain(): PricePoint = PricePoint(at, priceSilver)

fun OfferDto.toDomain(): UsernameOffer = UsernameOffer(
    id = id,
    listingId = ListingId(listingId),
    buyerId = UserId(buyerId),
    buyerName = buyerName,
    amountSilver = amountSilver,
    status = OfferStatus.entries.firstOrNull { it.name.equals(status, true) } ?: OfferStatus.PENDING,
    createdAt = createdAt,
)

fun GiftDto.toDomain(): Gift = Gift(
    id = id,
    title = title,
    emoji = emoji,
    assetUrl = assetUrl,
    lottieUrl = lottieUrl,
    priceSilver = priceSilver,
    category = GiftCategory.entries.firstOrNull { it.name.equals(category, true) } ?: GiftCategory.CLASSIC,
    premiumOnly = premiumOnly,
    limitedTotal = limitedTotal,
    limitedLeft = limitedLeft,
    convertibleSilver = convertibleSilver,
)

fun OwnedGiftDto.toDomain(): OwnedGift = OwnedGift(
    id = id,
    gift = gift.toDomain(),
    receivedFrom = receivedFrom?.let { UserId(it) },
    receivedAt = receivedAt,
    converted = converted,
    upgradeLevel = upgradeLevel,
)

fun MarketFeedDto.toDomain(): MarketFeed = MarketFeed(
    listings = listings.map { it.toDomain() },
    gifts = gifts.map { it.toDomain() },
    premiumTiers = premiumTiers.map { it.toDomain() },
    trending = trending,
    volume24h = volume24h,
)

/**
 * Результат покупки.
 * Статус приходит строкой, потому что сервер может добавить новый исход сделки
 * (например, «аукцион продлён») — старый клиент при этом не падает.
 */
fun BuyResponse.toDomain(): PurchaseResult = when (status) {
    "success" -> PurchaseResult.Success(
        listing = listing?.toDomain() ?: error("success без listing — ошибка сервера"),
        newBalance = wallet?.balance ?: 0L,
        transactionId = transactionId.orEmpty(),
    )

    "insufficient_funds" -> PurchaseResult.InsufficientFunds(
        required = requiredSilver ?: 0L,
        available = availableSilver ?: 0L,
    )

    "taken" -> PurchaseResult.AlreadyTaken
    "reserved" -> PurchaseResult.Reserved(reservedUntil ?: 0L)
    else -> PurchaseResult.Forbidden
}

/* ── Звонки ──────────────────────────────────────────────────────────────── */

fun CallSessionDto.toDomain(): CallSession = CallSession(
    id = CallId(id),
    type = CallType.entries.firstOrNull { it.name.equals(type, true) } ?: CallType.AUDIO,
    chatId = ChatId(chatId),
    initiatorId = UserId(initiatorId),
    participants = participants.map {
        CallParticipant(
            userId = UserId(it.userId),
            state = it.state.toCallState(),
            muted = it.muted,
            cameraOff = it.cameraOff,
            quality = ConnectionQuality.UNKNOWN,
        )
    },
    state = state.toCallState(),
    startedAt = startedAt,
    endedAt = endedAt,
    endReason = endReason?.let { raw ->
        CallEndReason.entries.firstOrNull { it.name.equals(raw, true) }
    },
    isEncrypted = isEncrypted,
)

private fun String.toCallState(): CallState =
    CallState.entries.firstOrNull { it.name.equals(this, true) } ?: CallState.IDLE

fun CallParticipantDto.toDomain(): CallParticipant = CallParticipant(
    userId = UserId(userId),
    state = state.toCallState(),
    muted = muted,
    cameraOff = cameraOff,
)

fun CallHistoryDto.toDomain(): CallHistoryEntry = CallHistoryEntry(
    callId = CallId(callId),
    chatId = ChatId(chatId),
    peer = peer.toDomain(),
    type = CallType.entries.firstOrNull { it.name.equals(type, true) } ?: CallType.AUDIO,
    missed = missed,
    startedAt = startedAt,
    durationMs = durationMs,
)

fun IceServerDto.toDomain(): IceServer = IceServer(urls, username, credential)

/* ── Админка ─────────────────────────────────────────────────────────────── */

fun AdminAccessDto.toDomainRole(): AdminRole =
    AdminRole.entries.firstOrNull { it.name.equals(role, true) } ?: AdminRole.NONE

fun AdminStatsDto.toDomain(): AdminStats = AdminStats(
    totalUsers = totalUsers,
    activeUsers24h = activeUsers24h,
    premiumUsers = premiumUsers,
    totalMessages24h = totalMessages24h,
    silverEmittedTotal = silverEmittedTotal,
    silverBurnedTotal = silverBurnedTotal,
    marketVolume24h = marketVolume24h,
    openReports = openReports,
    activeCalls = activeCalls,
    storiesPublished24h = storiesPublished24h,
    sparklineUsers = sparklineUsers,
    sparklineMessages = sparklineMessages,
)

fun AdminLogDto.toDomain(): AdminLogEntry = AdminLogEntry(
    id = id,
    actorId = UserId(actorId),
    actorName = actorName,
    action = AdminAction.entries.firstOrNull { it.name.equals(action, true) } ?: AdminAction.RESOLVE_REPORT,
    targetUserId = targetUserId?.let { UserId(it) },
    targetName = targetName,
    payload = payload,
    reason = reason,
    createdAt = createdAt,
    ip = ip,
)

fun ReportDto.toDomain(): ModerationCase = ModerationCase(
    id = id,
    reporterId = UserId(reporterId),
    targetType = ModerationTargetType.entries.firstOrNull { it.name.equals(targetType, true) }
        ?: ModerationTargetType.USER,
    targetId = targetId,
    reason = ReportReason.entries.firstOrNull { it.name.equals(reason, true) } ?: ReportReason.OTHER,
    comment = comment,
    status = ModerationStatus.entries.firstOrNull { it.name.equals(status, true) } ?: ModerationStatus.OPEN,
    createdAt = createdAt,
    resolvedBy = resolvedBy?.let { UserId(it) },
)

fun AdminGrantRequest.toDto() = com.silverchat.core.network.dto.AdminGrantDto(
    targetUserId = targetUserId.raw,
    action = action.name.lowercase(),
    durationDays = durationDays,
    role = role?.name?.lowercase(),
    amountSilver = amountSilver,
    reason = reason,
)

@Suppress("unused")
private fun unusedModelRefs(user: User, messageId: MessageId, purchase: PurchaseRequest): String =
    "${user.id}$messageId${purchase.listingId}"

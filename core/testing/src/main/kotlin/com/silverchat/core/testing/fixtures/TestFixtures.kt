package com.silverchat.core.testing.fixtures

import com.silverchat.core.model.AvatarMedia
import com.silverchat.core.model.Chat
import com.silverchat.core.model.ChatId
import com.silverchat.core.model.ChatType
import com.silverchat.core.model.Gift
import com.silverchat.core.model.GiftCategory
import com.silverchat.core.model.LedgerEntry
import com.silverchat.core.model.LedgerReason
import com.silverchat.core.model.ListingId
import com.silverchat.core.model.ListingStatus
import com.silverchat.core.model.MemberRole
import com.silverchat.core.model.Message
import com.silverchat.core.model.MessageContent
import com.silverchat.core.model.MessageId
import com.silverchat.core.model.MessageStatus
import com.silverchat.core.model.PremiumPerk
import com.silverchat.core.model.PremiumPerkCode
import com.silverchat.core.model.PremiumStatus
import com.silverchat.core.model.PremiumTier
import com.silverchat.core.model.PremiumTierId
import com.silverchat.core.model.Reaction
import com.silverchat.core.model.ReactionKind
import com.silverchat.core.model.Story
import com.silverchat.core.model.StoryCluster
import com.silverchat.core.model.StoryId
import com.silverchat.core.model.StoryMedia
import com.silverchat.core.model.StoryMediaType
import com.silverchat.core.model.Streak
import com.silverchat.core.model.StreakMilestone
import com.silverchat.core.model.User
import com.silverchat.core.model.UserBadges
import com.silverchat.core.model.UserFlags
import com.silverchat.core.model.UserId
import com.silverchat.core.model.UserPresence
import com.silverchat.core.model.UsernameListing
import com.silverchat.core.model.UsernameRarity
import com.silverchat.core.model.Wallet

/**
 * Фабрики тестовых данных.
 *
 * Все параметры имеют значения по умолчанию, поэтому в тесте видно только то,
 * что действительно проверяется:
 * `aMessage(content = MessageContent.Text("привет"))`
 */
object TestFixtures {

    const val MASTER_USERNAME = "silver"

    fun userId(value: String = "user-1") = UserId(value)
    fun chatId(value: String = "chat-1") = ChatId(value)
    fun messageId(value: String = "msg-1") = MessageId(value)
    fun storyId(value: String = "story-1") = StoryId(value)
    fun listingId(value: String = "listing-1") = ListingId(value)

    /** Обычный пользователь. */
    fun aUser(
        id: String = "user-1",
        firstName: String = "Алина",
        lastName: String? = "Коваль",
        username: String? = "alina",
        verified: Boolean = false,
        premium: Boolean = false,
        online: Boolean = true,
    ): User = User(
        id = UserId(id),
        firstName = firstName,
        lastName = lastName,
        username = username,
        bio = "Дизайнер интерфейсов",
        avatar = AvatarMedia(staticUrl = "https://cdn.silver.chat/a/$id.jpg"),
        badges = UserBadges(verified = verified, premium = premium),
        presence = if (online) UserPresence.Online else UserPresence.Offline(System.currentTimeMillis() - 3_600_000),
        createdAt = 1_700_000_000_000L,
    )

    /**
     * Мастер-аккаунт @silver — владелец продукта с полными админ-правами.
     * Используется во всех тестах админ-панели.
     */
    fun masterAccount(): User = User(
        id = UserId("master-1"),
        firstName = "Silver",
        lastName = null,
        username = MASTER_USERNAME,
        bio = "Владелец SilverChat",
        badges = UserBadges(verified = true, premium = true, admin = true, owner = true),
        flags = UserFlags(isMasterAccount = true),
        presence = UserPresence.Online,
        premium = PremiumStatus.Lifetime,
    )

    fun aPersonalChat(
        id: String = "chat-1",
        peer: User = aUser(),
        unread: Int = 0,
        lastMessage: Message? = null,
    ): Chat = Chat(
        id = ChatId(id),
        type = ChatType.PERSONAL,
        title = peer.fullName,
        peer = peer,
        membersCount = 2,
        unreadCount = unread,
        lastMessage = lastMessage ?: aMessage(chatId = id),
        createdAt = 1_700_000_000_000L,
    )

    fun aGroupChat(
        id: String = "chat-group",
        title: String = "Команда SilverChat",
        myRole: MemberRole = MemberRole.ADMIN,
        members: Int = 12,
    ): Chat = Chat(
        id = ChatId(id),
        type = ChatType.SUPERGROUP,
        title = title,
        membersCount = members,
        myRole = myRole,
        lastMessage = aMessage(chatId = id),
        createdAt = 1_700_000_000_000L,
    )

    fun aChannel(
        id: String = "chat-channel",
        title: String = "SilverChat News",
        verified: Boolean = true,
        myRole: MemberRole = MemberRole.MEMBER,
    ): Chat = Chat(
        id = ChatId(id),
        type = ChatType.CHANNEL,
        title = title,
        username = "silverchat",
        membersCount = 48_200,
        verified = verified,
        myRole = myRole,
        lastMessage = aMessage(chatId = id, senderId = "channel-admin"),
        createdAt = 1_700_000_000_000L,
    )

    fun aMessage(
        id: String = "msg-1",
        chatId: String = "chat-1",
        senderId: String = "user-1",
        text: String = "Привет! Как продвигается миграция на Kotlin?",
        status: MessageStatus = MessageStatus.READ,
        sentAt: Long = 1_730_000_000_000L,
        reactions: List<Reaction> = emptyList(),
        edited: Boolean = false,
    ): Message = Message(
        id = MessageId(id),
        chatId = ChatId(chatId),
        senderId = UserId(senderId),
        sender = aUser(id = senderId),
        content = MessageContent.Text(text),
        status = status,
        reactions = reactions,
        sentAt = sentAt,
        editedAt = if (edited) sentAt + 60_000 else null,
    )

    fun aVoiceMessage(id: String = "msg-voice", chatId: String = "chat-1"): Message = Message(
        id = MessageId(id),
        chatId = ChatId(chatId),
        senderId = UserId("user-2"),
        content = MessageContent.Voice(
            url = "https://cdn.silver.chat/v/$id.opus",
            durationMs = 14_000L,
            waveform = List(60) { (it % 7 + 3) / 10f },
        ),
        sentAt = 1_730_000_000_000L,
    )

    fun aCircleMessage(id: String = "msg-circle", chatId: String = "chat-1"): Message = Message(
        id = MessageId(id),
        chatId = ChatId(chatId),
        senderId = UserId("user-2"),
        content = MessageContent.VideoCircle(
            url = "https://cdn.silver.chat/c/$id.mp4",
            durationMs = 22_000L,
        ),
        sentAt = 1_730_000_000_000L,
    )

    fun aReaction(kind: ReactionKind = ReactionKind.HEART, count: Int = 3, mine: Boolean = false): Reaction =
        Reaction(kind, count, reactedByMe = mine)

    fun aStory(
        id: String = "story-1",
        authorId: String = "user-1",
        seen: Boolean = false,
        createdAt: Long = 1_730_000_000_000L,
    ): Story = Story(
        id = StoryId(id),
        authorId = UserId(authorId),
        author = aUser(id = authorId),
        media = StoryMedia(
            type = StoryMediaType.PHOTO,
            url = "https://cdn.silver.chat/s/$id.jpg",
            backgroundGradient = listOf(0xFF3E82F7, 0xFF8B5CF6),
        ),
        caption = "Запустили бета-тест маркета юзернеймов 🚀",
        createdAt = createdAt,
        expiresAt = createdAt + 86_400_000L,
        viewersCount = 42,
        seenByMe = seen,
    )

    fun aStoryCluster(authorId: String = "user-1", count: Int = 2, unseen: Boolean = true): StoryCluster =
        StoryCluster(
            authorId = UserId(authorId),
            author = aUser(id = authorId),
            stories = List(count) { index ->
                aStory(id = "$authorId-story-$index", authorId = authorId, seen = !unseen)
            },
        )

    fun aWallet(balance: Long = 12_500L, frozen: Long = 0L): Wallet = Wallet(
        userId = UserId("user-1"),
        balance = balance,
        frozen = frozen,
        earnedTotal = balance + 5_000,
        spentTotal = 5_000,
        updatedAt = 1_730_000_000_000L,
    )

    fun aLedgerEntry(
        amount: Long = 500L,
        reason: LedgerReason = LedgerReason.DAILY_BONUS,
    ): LedgerEntry = LedgerEntry(
        id = "tx-$amount-$reason",
        amount = amount,
        reason = reason,
        balanceAfter = 12_500L,
        createdAt = 1_730_000_000_000L,
    )

    fun aStreak(days: Int = 7, claimable: Boolean = true): Streak = Streak(
        streakDays = days,
        bestStreak = maxOf(days, 21),
        frozenStreaks = 1,
        nextRewardSilver = 100L,
        milestones = listOf(
            StreakMilestone(7, 100L, achieved = days >= 7),
            StreakMilestone(30, 1_000L, achieved = days >= 30),
            StreakMilestone(100, 10_000L, achieved = days >= 100),
        ),
        claimable = claimable,
    )

    fun aListing(
        username: String = "neo",
        price: Long = 25_000L,
        rarity: UsernameRarity = UsernameRarity.EPIC,
        status: ListingStatus = ListingStatus.AVAILABLE,
    ): UsernameListing = UsernameListing(
        id = ListingId("listing-$username"),
        username = username,
        priceSilver = price,
        rarity = rarity,
        status = status,
        sellerName = "crypto_whale",
        views = 340,
        offersCount = 3,
        createdAt = 1_730_000_000_000L,
    )

    fun aGift(
        id: String = "gift-rose",
        title: String = "Роза",
        emoji: String = "🌹",
        price: Long = 250L,
        premiumOnly: Boolean = false,
    ): Gift = Gift(
        id = id,
        title = title,
        emoji = emoji,
        priceSilver = price,
        category = GiftCategory.CLASSIC,
        premiumOnly = premiumOnly,
        convertibleSilver = price / 2,
    )

    fun aPremiumTier(
        id: PremiumTierId = PremiumTierId.YEAR,
        title: String = "12 месяцев",
        days: Int = 365,
        price: Long = 45_000L,
        bestValue: Boolean = true,
    ): PremiumTier = PremiumTier(
        id = id,
        title = title,
        periodDays = days,
        priceSilver = price,
        perks = PremiumPerkCode.entries.take(5).map {
            PremiumPerk(it, it.name, "Описание ${it.name}")
        },
        bestValue = bestValue,
        discountPercent = 30,
    )
}

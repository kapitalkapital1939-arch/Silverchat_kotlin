package com.silverchat.core.network.mapper

import com.silverchat.core.model.Chat
import com.silverchat.core.model.ChatFolder
import com.silverchat.core.model.ChatId
import com.silverchat.core.model.ChatInviteLink
import com.silverchat.core.model.ChatMember
import com.silverchat.core.model.ChatPermissions
import com.silverchat.core.model.ChatType
import com.silverchat.core.model.Draft
import com.silverchat.core.model.ForwardInfo
import com.silverchat.core.model.LinkPreview
import com.silverchat.core.model.MemberPermissions
import com.silverchat.core.model.MemberRole
import com.silverchat.core.model.Message
import com.silverchat.core.model.MessageContent
import com.silverchat.core.model.MessageId
import com.silverchat.core.model.MessagePreview
import com.silverchat.core.model.MessageStatus
import com.silverchat.core.model.PollOption
import com.silverchat.core.model.Reaction
import com.silverchat.core.model.ReactionKind
import com.silverchat.core.model.ServiceAction
import com.silverchat.core.model.TextEntity
import com.silverchat.core.model.TextEntityType
import com.silverchat.core.model.UserId
import com.silverchat.core.network.dto.ChatDto
import com.silverchat.core.network.dto.ChatFolderDto
import com.silverchat.core.network.dto.ChatMemberDto
import com.silverchat.core.network.dto.ChatPermissionsDto
import com.silverchat.core.network.dto.DraftDto
import com.silverchat.core.network.dto.ForwardDto
import com.silverchat.core.network.dto.InviteLinkDto
import com.silverchat.core.network.dto.LinkPreviewDto
import com.silverchat.core.network.dto.MemberPermissionsDto
import com.silverchat.core.network.dto.MessageContentDto
import com.silverchat.core.network.dto.MessageDto
import com.silverchat.core.network.dto.MessagePreviewDto
import com.silverchat.core.network.dto.PollOptionDto
import com.silverchat.core.network.dto.ReactionDto
import com.silverchat.core.network.dto.TextEntityDto

/* ── Чаты ────────────────────────────────────────────────────────────────── */

fun ChatDto.toDomain(): Chat = Chat(
    id = ChatId(id),
    type = type.toChatType(),
    title = title,
    about = about,
    avatar = avatar?.toDomain(),
    username = username,
    peer = peer?.toDomain(),
    membersCount = membersCount,
    onlineCount = onlineCount,
    lastMessage = lastMessage?.toDomain(),
    draft = draft?.toDomain(),
    unreadCount = unreadCount,
    mentionsCount = mentionsCount,
    pinned = pinned.map { MessageId(it) },
    muted = muted,
    mutedUntil = mutedUntil,
    archived = archived,
    folderIds = folderIds,
    verified = verified,
    restricted = restricted,
    permissions = permissions.toDomain(),
    myRole = myRole.toRole(),
    inviteLinks = inviteLinks.map { it.toDomain() },
    joinRequestsCount = joinRequests,
    location = location?.toDomain(),
    workingHours = workingHours?.toDomain(),
    slowModeSeconds = slowModeSeconds,
    createdAt = createdAt,
)

private fun String.toChatType(): ChatType = when (this) {
    "secret" -> ChatType.SECRET
    "group" -> ChatType.GROUP
    "supergroup" -> ChatType.SUPERGROUP
    "channel" -> ChatType.CHANNEL
    "broadcast" -> ChatType.BROADCAST
    "bot" -> ChatType.BOT
    "saved" -> ChatType.SAVED
    else -> ChatType.PERSONAL
}

private fun String.toRole(): MemberRole = when (this) {
    "owner" -> MemberRole.OWNER
    "admin" -> MemberRole.ADMIN
    "restricted" -> MemberRole.RESTRICTED
    "kicked" -> MemberRole.KICKED
    else -> MemberRole.MEMBER
}

fun ChatPermissionsDto.toDomain(): ChatPermissions = ChatPermissions(
    canSendMessages = canSendMessages,
    canSendMedia = canSendMedia,
    canSendVoice = canSendVoice,
    canSendStickers = canSendStickers,
    canAddMembers = canAddMembers,
    canReact = canReact,
    canPinMessages = canPin,
    joinByInviteOnly = joinByInviteOnly,
    approveNewMembers = approveNewMembers,
    slowModeSeconds = slowModeSeconds,
)

fun MemberPermissionsDto.toDomain(): MemberPermissions = MemberPermissions(
    canSendMessages = canSendMessages,
    canSendMedia = canSendMedia,
    canSendVoice = canSendVoice,
    canSendStickers = canSendStickers,
    canReact = canReact,
    canInvite = canInvite,
    canPin = canPin,
    restrictedUntil = restrictedUntil,
)

fun ChatMemberDto.toDomain(): ChatMember = ChatMember(
    user = user.toDomain(),
    role = when (role) {
        "owner" -> MemberRole.OWNER
        "admin" -> MemberRole.ADMIN
        "restricted" -> MemberRole.RESTRICTED
        "kicked" -> MemberRole.KICKED
        else -> MemberRole.MEMBER
    },
    customTitle = customTitle,
    joinedAt = joinedAt,
    permissions = permissions.toDomain(),
)

fun ChatFolderDto.toDomain(): ChatFolder = ChatFolder(
    id = id,
    title = title,
    icon = icon,
    includeTypes = includeTypes.map { it.let(::runCatching).mapNotNull { r -> r.getOrNull() } }
        .mapNotNull { raw -> ChatType.entries.firstOrNull { it.name.equals(raw.toString(), true) } },
    excludeMuted = excludeMuted,
    excludeRead = excludeRead,
    pinnedChatIds = pinnedChatIds.map { ChatId(it) },
    isDefault = isDefault,
)

fun DraftDto.toDomain(): Draft = Draft(
    text = text,
    replyTo = replyTo?.let { MessageId(it) },
    pendingAttachments = attachments,
    updatedAt = updatedAt,
)

fun InviteLinkDto.toDomain(): ChatInviteLink = ChatInviteLink(
    token = token,
    chatId = ChatId(chatId),
    createdBy = UserId(createdBy),
    name = name,
    maxUses = maxUses,
    usedCount = usedCount,
    expiresAt = expiresAt,
    requiresApproval = requiresApproval,
    revoked = revoked,
)

/* ── Сообщения ───────────────────────────────────────────────────────────── */

fun MessageDto.toDomain(): Message = Message(
    id = MessageId(id),
    chatId = ChatId(chatId),
    senderId = UserId(senderId),
    sender = sender?.toDomain(),
    content = content.toDomain(),
    status = status.toStatus(),
    replyTo = replyTo?.toDomain(),
    forwardInfo = forward?.toDomain(),
    editedAt = editedAt,
    reactions = reactions.map { it.toDomain() },
    myReaction = myReaction?.toReactionKind(),
    sentAt = sentAt,
    scheduledAt = scheduledAt,
    pinned = pinned,
    silent = silent,
    contentProtected = contentProtected,
    viewsCount = viewsCount,
    deleted = deleted,
)

private fun String.toStatus(): MessageStatus = when (this) {
    "draft" -> MessageStatus.DRAFT
    "pending" -> MessageStatus.SENDING
    "delivered" -> MessageStatus.DELIVERED
    "read" -> MessageStatus.READ
    "failed" -> MessageStatus.FAILED
    else -> MessageStatus.SENT
}

fun ReactionDto.toDomain(): Reaction = Reaction(
    kind = kind.toReactionKind() ?: ReactionKind.LIKE,
    count = count,
    reactedByMe = reactedByMe,
    recentUsers = recentUsers.map { UserId(it) },
)

/** Неизвестная реакция сервера не роняет клиент — уходим в null. */
fun String.toReactionKind(): ReactionKind? = ReactionKind.entries.firstOrNull { it.name.equals(this, true) }

fun MessagePreviewDto.toDomain(): MessagePreview = MessagePreview(
    messageId = MessageId(messageId),
    chatId = ChatId(chatId),
    senderId = UserId(senderId),
    senderName = senderName,
    snippet = snippet,
    contentKind = contentKind,
)

fun ForwardDto.toDomain(): ForwardInfo = ForwardInfo(
    fromUserId = fromUserId?.let { UserId(it) },
    fromName = fromName,
    fromChatId = fromChatId?.let { ChatId(it) },
    originalMessageId = originalMessageId?.let { MessageId(it) },
    forwardedAt = forwardedAt,
)

fun PollOptionDto.toDomain(): PollOption = PollOption(
    text = text,
    votes = votes,
    votedByMe = votedByMe,
)

fun TextEntityDto.toDomain(): TextEntity? {
    val type = when (type) {
        "bold" -> TextEntityType.BOLD
        "italic" -> TextEntityType.ITALIC
        "underline" -> TextEntityType.UNDERLINE
        "strikethrough" -> TextEntityType.STRIKETHROUGH
        "code" -> TextEntityType.CODE
        "pre" -> TextEntityType.PRE
        "link" -> TextEntityType.LINK
        "mention" -> TextEntityType.MENTION
        "hashtag" -> TextEntityType.HASHTAG
        "spoiler" -> TextEntityType.SPOILER
        "blockquote" -> TextEntityType.BLOCKQUOTE
        else -> return null
    }
    return TextEntity(type, offset, length, url, language)
}

fun LinkPreviewDto.toDomain(): LinkPreview = LinkPreview(
    url = url,
    title = title,
    description = description,
    imageUrl = imageUrl,
)

/**
 * Маппинг контента. Unknown -> MessageContent.Text с пометкой, что версия
 * клиента устарела: пользователь видит внятный текст вместо пустого пузыря.
 */
fun MessageContentDto.toDomain(): MessageContent = when (this) {
    is MessageContentDto.Text -> MessageContent.Text(
        text = text,
        entities = entities.mapNotNull { it.toDomain() },
        linkPreview = linkPreview?.toDomain(),
    )

    is MessageContentDto.Photo -> MessageContent.Photo(
        url = url, thumbUrl = thumbUrl, width = width, height = height,
        sizeBytes = sizeBytes, caption = caption, albumId = albumId, spoiler = spoiler,
    )

    is MessageContentDto.Video -> MessageContent.Video(
        url = url, posterUrl = posterUrl, durationMs = durationMs,
        width = width, height = height, sizeBytes = sizeBytes,
        caption = caption, albumId = albumId,
    )

    is MessageContentDto.Voice -> MessageContent.Voice(
        url = url, durationMs = durationMs, waveform = waveform, sizeBytes = sizeBytes,
    )

    is MessageContentDto.VideoCircle -> MessageContent.VideoCircle(
        url = url, posterUrl = posterUrl, durationMs = durationMs, sizeBytes = sizeBytes,
    )

    is MessageContentDto.File -> MessageContent.File(
        url = url, fileName = fileName, mimeType = mimeType, sizeBytes = sizeBytes,
    )

    is MessageContentDto.Sticker -> MessageContent.Sticker(
        packId = packId, emoji = emoji, url = url, animated = animated, isPremium = isPremium,
    )

    is MessageContentDto.Gif -> MessageContent.Gif(
        url = url, thumbUrl = thumbUrl, width = width, height = height, isPremium = isPremium,
    )

    is MessageContentDto.Location -> MessageContent.Location(
        latitude = latitude, longitude = longitude, livePeriodSeconds = livePeriodSeconds, title = title,
    )

    is MessageContentDto.Contact -> MessageContent.Contact(
        userId = userId?.let { UserId(it) }, firstName = firstName, lastName = lastName, phone = phone,
    )

    is MessageContentDto.Poll -> MessageContent.Poll(
        question = question,
        options = options.map { it.toDomain() },
        anonymous = anonymous,
        multipleChoice = multiple,
        correctOptionIndex = correctOptionIndex,
        closed = closed,
    )

    is MessageContentDto.Service -> MessageContent.Service(
        action = when (action) {
            "chat_created" -> ServiceAction.CHAT_CREATED
            "member_joined" -> ServiceAction.MEMBER_JOINED
            "member_left" -> ServiceAction.MEMBER_LEFT
            "member_invited" -> ServiceAction.MEMBER_INVITED
            "member_kicked" -> ServiceAction.MEMBER_KICKED
            "title_changed" -> ServiceAction.TITLE_CHANGED
            "avatar_changed" -> ServiceAction.AVATAR_CHANGED
            "pinned_message" -> ServiceAction.PINNED_MESSAGE
            "call_missed" -> ServiceAction.CALL_MISSED
            "call_ended" -> ServiceAction.CALL_ENDED
            "gift_sent" -> ServiceAction.GIFT_SENT
            "premium_gifted" -> ServiceAction.PREMIUM_GIFTED
            "username_purchased" -> ServiceAction.USERNAME_PURCHASESED
            "story_published" -> ServiceAction.STORY_PUBLISHED
            else -> ServiceAction.CHAT_CREATED
        },
        title = title,
    )

    is MessageContentDto.Gift -> MessageContent.Gift(
        giftId = giftId, title = title, emoji = emoji,
        priceSilver = priceSilver, message = message,
    )

    is MessageContentDto.Unknown -> MessageContent.Text(
        text = "Сообщение не поддерживается вашей версией SilverChat. Обновите приложение.",
    )
}

/** Обратный маппинг: доменный контент -> DTO для отправки. */
fun MessageContent.toDto(): MessageContentDto = when (this) {
    is MessageContent.Text -> MessageContentDto.Text(
        text = text,
        entities = entities.map {
            TextEntityDto(it.type.name.lowercase(), it.offset, it.length, it.url, it.language)
        },
    )

    is MessageContent.Photo -> MessageContentDto.Photo(url, thumbUrl, width, height, sizeBytes, caption, albumId, spoiler)
    is MessageContent.Video -> MessageContentDto.Video(url, posterUrl, durationMs, width, height, sizeBytes, caption, albumId)
    is MessageContent.Voice -> MessageContentDto.Voice(url, durationMs, waveform, sizeBytes)
    is MessageContent.VideoCircle -> MessageContentDto.VideoCircle(url, posterUrl, durationMs, sizeBytes)
    is MessageContent.File -> MessageContentDto.File(url, fileName, mimeType, sizeBytes)
    is MessageContent.Sticker -> MessageContentDto.Sticker(packId, emoji, url, animated, isPremium)
    is MessageContent.Gif -> MessageContentDto.Gif(url, thumbUrl, width, height, isPremium)
    is MessageContent.Location -> MessageContentDto.Location(latitude, longitude, livePeriodSeconds, title)
    is MessageContent.Contact -> MessageContentDto.Contact(userId?.raw, firstName, lastName, phone)
    is MessageContent.Poll -> MessageContentDto.Poll(
        question = question,
        options = options.map { PollOptionDto(it.text, it.votes, it.votedByMe) },
        anonymous = anonymous,
        multiple = multipleChoice,
        correctOptionIndex = correctOptionIndex,
        closed = closed,
    )

    is MessageContent.Service -> MessageContentDto.Service(action.name.lowercase(), title)
    is MessageContent.Invite -> MessageContentDto.Text("Приглашение: ${invite.url}")
    is MessageContent.Gift -> MessageContentDto.Gift(giftId, title, emoji, priceSilver, message)
}

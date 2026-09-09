package com.silverchat.core.data.mapper

import com.silverchat.core.database.entity.CallHistoryEntity
import com.silverchat.core.database.entity.ChatEntity
import com.silverchat.core.database.entity.DraftEntity
import com.silverchat.core.database.entity.LedgerEntity
import com.silverchat.core.database.entity.MemberEntity
import com.silverchat.core.database.entity.MessageEntity
import com.silverchat.core.database.entity.PendingOutgoingEntity
import com.silverchat.core.database.entity.SearchHistoryEntity
import com.silverchat.core.database.entity.StoryEntity
import com.silverchat.core.model.AvatarAnimationType
import com.silverchat.core.model.AvatarMedia
import com.silverchat.core.model.CallHistoryEntry
import com.silverchat.core.model.CallType
import com.silverchat.core.model.Chat
import com.silverchat.core.model.ChatMember
import com.silverchat.core.model.ChatPermissions
import com.silverchat.core.model.ChatType
import com.silverchat.core.model.Draft
import com.silverchat.core.model.LedgerEntry
import com.silverchat.core.model.LedgerReason
import com.silverchat.core.model.LocationInfo
import com.silverchat.core.model.MemberPermissions
import com.silverchat.core.model.MemberRole
import com.silverchat.core.model.Message
import com.silverchat.core.model.MessageContent
import com.silverchat.core.model.Reaction
import com.silverchat.core.model.Story
import com.silverchat.core.model.StoryMedia
import com.silverchat.core.model.StoryMediaType
import com.silverchat.core.model.StoryPrivacy
import com.silverchat.core.model.User
import com.silverchat.core.model.WorkingHours
import kotlinx.serialization.json.Json

/* =========================================================================
   МАППЕРЫ: Room-сущность <-> доменная модель
   ------------------------------------------------------------------------
   Room хранит плоские поля, а сложные структуры — двумя способами:
     • через @TypeConverter (Converters.kt) — status, reactions, myReaction,
       content: Room сам сериализует их в TEXT, поэтому в Kotlin это
       полноценные типы, а не строки;
     • через явные JSON-колонки (*_json) — sender, replyTo, forward, peer,
       permissions, location, workingHours: ониnullable и читаются лениво.

   Правило: ни одна сущность не «протекает» наружу :core:data. Фичи видят
   только модели из :core:model, поэтому замену Room можно сделать, не
   трогая UI.
   ========================================================================= */

/**
 * JSON для колонок-«мешков».
 *
 * `ignoreUnknownKeys` обязателен: сервер может добавить поле раньше, чем
 * выйдет обновление клиента, и падать на старом кэше нельзя.
 * `explicitNulls = false` держит кэш компактным: строк в Room много, а
 * каждое `"поле":null` — лишние байты на диске без всякой пользы.
 */
internal val DataJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}

private inline fun <reified T> String?.decodeOrNull(): T? =
    if (isNullOrBlank()) null else runCatching { DataJson.decodeFromString<T>(this) }.getOrNull()

private inline fun <reified T> String?.decodeOr(default: T): T = decodeOrNull<T>() ?: default

private inline fun <reified T> T?.encodeOrNull(): String? =
    if (this == null) null else runCatching { DataJson.encodeToString(this) }.getOrNull()

/** Безопасный парсер enum: неизвестное значение сервера не должно ронять кэш. */
private inline fun <reified T : Enum<T>> parseEnum(
    raw: String?,
    values: Array<T>,
    fallback: T,
): T = values.firstOrNull { it.name.equals(raw, true) } ?: fallback

/* ── Chat ──────────────────────────────────────────────────────────────── */

fun ChatEntity.toDomain(draft: Draft? = null): Chat = Chat(
    id = id,
    type = parseEnum(type, ChatType.entries.toTypedArray(), ChatType.PERSONAL),
    title = title,
    about = about,
    avatar = if (avatarStaticUrl == null && avatarAnimatedUrl == null) {
        null
    } else {
        AvatarMedia(
            staticUrl = avatarStaticUrl,
            animatedUrl = avatarAnimatedUrl,
            animationType = parseEnum(
                avatarAnimationType,
                AvatarAnimationType.entries.toTypedArray(),
                AvatarAnimationType.NONE,
            ),
        )
    },
    username = username,
    peer = peerJson.decodeOrNull<User>(),
    membersCount = membersCount,
    onlineCount = onlineCount,
    lastMessage = lastMessageJson.decodeOrNull<Message>(),
    draft = draft,
    unreadCount = unreadCount,
    mentionsCount = mentionsCount,
    pinned = pinnedJson.decodeOr<List<String>>(emptyList()),
    // Закреплённая сторис и папки живут на сервере и в кэш чата не входят:
    // они читаются отдельными запросами, чтобы не раздувать строку chats.
    pinnedStoryId = null,
    muted = muted,
    mutedUntil = mutedUntil,
    archived = archived,
    folderIds = emptyList(),
    verified = verified,
    restricted = restricted,
    permissions = permissionsJson.decodeOr(ChatPermissions()),
    myRole = parseEnum(myRole, MemberRole.entries.toTypedArray(), MemberRole.MEMBER),
    inviteLinks = emptyList(),
    joinRequestsCount = joinRequests,
    location = locationJson.decodeOrNull<LocationInfo>(),
    workingHours = workingHoursJson.decodeOrNull<WorkingHours>(),
    slowModeSeconds = slowModeSeconds,
    createdAt = createdAt,
)

/**
 * @param existingUpdatedAt сохраняет прежнюю метку при частичном обновлении,
 *        чтобы `updatedAt` не «прыгала» на каждом WebSocket-событии.
 */
fun Chat.toEntity(existingUpdatedAt: Long? = null): ChatEntity = ChatEntity(
    id = id,
    type = type.name.lowercase(),
    title = title,
    about = about,
    avatarStaticUrl = avatar?.staticUrl,
    avatarAnimatedUrl = avatar?.animatedUrl,
    avatarAnimationType = (avatar?.animationType ?: AvatarAnimationType.NONE).name.lowercase(),
    username = username,
    peerId = peer?.id,
    peerJson = peer.encodeOrNull(),
    membersCount = membersCount,
    onlineCount = onlineCount,
    lastMessageJson = lastMessage.encodeOrNull(),
    unreadCount = unreadCount,
    mentionsCount = mentionsCount,
    pinnedJson = pinned.encodeOrNull(),
    muted = muted,
    mutedUntil = mutedUntil,
    archived = archived,
    verified = verified,
    restricted = restricted,
    myRole = myRole.name.lowercase(),
    permissionsJson = permissions.encodeOrNull(),
    locationJson = location.encodeOrNull(),
    workingHoursJson = workingHours.encodeOrNull(),
    slowModeSeconds = slowModeSeconds,
    joinRequests = joinRequestsCount,
    lastMessageAt = lastMessage?.sentAt ?: createdAt,
    createdAt = createdAt,
    updatedAt = existingUpdatedAt ?: System.currentTimeMillis(),
)

/* ── Message ───────────────────────────────────────────────────────────── */

/**
 * Контент, которого не удалось распарсить, превращается в сервисное сообщение
 * «неподдерживаемый тип». Это осознанный выбор: потерять одну строку в кэше
 * лучше, чем уронить весь список сообщений из-за нового типа на сервере.
 */
private val FALLBACK_CONTENT: MessageContent = MessageContent.Text(text = "")

fun MessageEntity.toDomain(): Message = Message(
    id = id,
    chatId = chatId,
    senderId = senderId,
    sender = senderJson.decodeOrNull<User>(),
    content = contentJson.decodeOr<MessageContent>(FALLBACK_CONTENT),
    status = status,
    replyTo = replyToJson.decodeOrNull(),
    forwardInfo = forwardJson.decodeOrNull(),
    editedAt = editedAt,
    reactions = reactions,
    myReaction = myReaction,
    sentAt = sentAt,
    scheduledAt = scheduledAt,
    pinned = pinned,
    silent = silent,
    // contentProtected / deletedForAll / authorSignature не кэшируются:
    // это серверные флаги, которые важны только в живой сессии.
    contentProtected = false,
    viewsCount = viewsCount,
    deleted = deleted,
    deletedForAll = false,
    authorSignature = null,
)

/**
 * @param clientMessageId заполняется только для оптимистично отправленных
 *        сообщений: по нему `MessageDao.acknowledge` сопоставит локальную
 *        строку с серверным id, когда придёт `message.sent.ack`.
 */
fun Message.toEntity(clientMessageId: String? = null): MessageEntity = MessageEntity(
    id = id,
    chatId = chatId,
    senderId = senderId,
    senderJson = sender.encodeOrNull(),
    contentJson = DataJson.encodeToString(MessageContent.serializer(), content),
    status = status,
    replyToJson = replyTo.encodeOrNull(),
    forwardJson = forwardInfo.encodeOrNull(),
    editedAt = editedAt,
    reactions = reactions,
    myReaction = myReaction,
    sentAt = sentAt,
    scheduledAt = scheduledAt,
    pinned = pinned,
    silent = silent,
    viewsCount = viewsCount,
    deleted = deleted,
    clientMessageId = clientMessageId,
    acknowledged = clientMessageId == null,
)

/* ── ChatMember ────────────────────────────────────────────────────────── */

/** Возвращает null, если в кэше лежит битый JSON пользователя. */
fun MemberEntity.toDomain(): ChatMember? {
    val parsedUser = userJson.decodeOrNull<User>() ?: return null
    return ChatMember(
        user = parsedUser,
        role = parseEnum(role, MemberRole.entries.toTypedArray(), MemberRole.MEMBER),
        customTitle = customTitle,
        joinedAt = joinedAt,
        permissions = permissionsJson.decodeOr(MemberPermissions()),
    )
}

fun ChatMember.toEntity(chatId: String): MemberEntity = MemberEntity(
    chatId = chatId,
    userId = user.id,
    userJson = user.encodeOrNull() ?: "{}",
    role = role.name.lowercase(),
    customTitle = customTitle,
    joinedAt = joinedAt,
    permissionsJson = permissions.encodeOrNull(),
)

/* ── Draft ─────────────────────────────────────────────────────────────── */

fun DraftEntity.toDomain(): Draft = Draft(
    text = text,
    replyTo = replyTo,
    pendingAttachments = attachments.decodeOr<List<String>>(emptyList()),
    updatedAt = updatedAt,
)

fun Draft.toEntity(chatId: String): DraftEntity = DraftEntity(
    chatId = chatId,
    text = text,
    replyTo = replyTo,
    attachments = pendingAttachments.encodeOrNull(),
    updatedAt = updatedAt,
)

/* ── PendingOutgoing ───────────────────────────────────────────────────── */

/**
 * Элемент очереди отправки.
 *
 * Живёт в :core:data, а не в :core:model, потому что это деталь реализации
 * синхронизации: продуктовому слою не нужно знать про op и payload_json.
 */
data class PendingOutgoing(
    val clientMessageId: String,
    val chatId: String,
    val op: String,
    val payloadJson: String,
    val attempts: Int,
    val createdAt: Long,
    val lastAttemptAt: Long?,
    val lastError: String?,
)

fun PendingOutgoingEntity.toDomain(): PendingOutgoing = PendingOutgoing(
    clientMessageId = clientMessageId,
    chatId = chatId,
    op = op,
    payloadJson = payloadJson,
    attempts = attempts,
    createdAt = createdAt,
    lastAttemptAt = lastAttemptAt,
    lastError = lastError,
)

fun PendingOutgoing.toEntity(): PendingOutgoingEntity = PendingOutgoingEntity(
    clientMessageId = clientMessageId,
    chatId = chatId,
    op = op,
    payloadJson = payloadJson,
    attempts = attempts,
    createdAt = createdAt,
    lastAttemptAt = lastAttemptAt,
    lastError = lastError,
)

/* ── Story ─────────────────────────────────────────────────────────────── */

private val FALLBACK_STORY_MEDIA = StoryMedia(type = StoryMediaType.PHOTO, url = "")

fun StoryEntity.toDomain(): Story = Story(
    id = id,
    authorId = authorId,
    author = authorJson.decodeOrNull<User>(),
    media = mediaJson.decodeOr(FALLBACK_STORY_MEDIA),
    caption = caption,
    createdAt = createdAt,
    expiresAt = expiresAt,
    privacy = parseEnum(privacy, StoryPrivacy.entries.toTypedArray(), StoryPrivacy.CONTACTS),
    viewersCount = viewersCount,
    reactions = reactionsJson.decodeOr<List<Reaction>>(emptyList()),
    seenByMe = seenByMe,
    pinnedToProfile = pinnedToProfile,
    // repliesEnabled / location / mentionIds не кэшируются: сторис живёт 24 часа,
    // и ради таких мелочей держать ещё три JSON-колонки невыгодно.
    repliesEnabled = true,
    location = null,
    mentionIds = emptyList(),
)

fun Story.toEntity(): StoryEntity = StoryEntity(
    id = id,
    authorId = authorId,
    authorJson = author.encodeOrNull(),
    mediaJson = DataJson.encodeToString(StoryMedia.serializer(), media),
    caption = caption,
    createdAt = createdAt,
    expiresAt = expiresAt,
    privacy = privacy.name.lowercase(),
    viewersCount = viewersCount,
    reactionsJson = reactions.encodeOrNull(),
    seenByMe = seenByMe,
    pinnedToProfile = pinnedToProfile,
)

/* ── LedgerEntry ───────────────────────────────────────────────────────── */

fun LedgerEntity.toDomain(): LedgerEntry = LedgerEntry(
    id = id,
    amount = amount,
    reason = parseEnum(reason, LedgerReason.entries.toTypedArray(), LedgerReason.DAILY_BONUS),
    reference = reference,
    counterpartyName = counterparty,
    balanceAfter = balanceAfter,
    createdAt = createdAt,
    reversible = false,
)

fun LedgerEntry.toEntity(): LedgerEntity = LedgerEntity(
    id = id,
    amount = amount,
    reason = reason.name.lowercase(),
    reference = reference,
    counterparty = counterpartyName,
    balanceAfter = balanceAfter,
    createdAt = createdAt,
)

/* ── CallHistoryEntry ──────────────────────────────────────────────────── */

fun CallHistoryEntity.toDomain(): CallHistoryEntry? {
    val parsedPeer = peerJson.decodeOrNull<User>() ?: return null
    return CallHistoryEntry(
        callId = callId,
        chatId = chatId,
        peer = parsedPeer,
        type = parseEnum(type, CallType.entries.toTypedArray(), CallType.AUDIO),
        missed = missed,
        startedAt = startedAt,
        durationMs = durationMs,
    )
}

fun CallHistoryEntry.toEntity(): CallHistoryEntity = CallHistoryEntity(
    callId = callId,
    chatId = chatId,
    peerJson = User.serializer().let { DataJson.encodeToString(it, peer) },
    type = type.name.lowercase(),
    missed = missed,
    startedAt = startedAt,
    durationMs = durationMs,
)

/* ── SearchHistory ─────────────────────────────────────────────────────── */

/** Запись истории поиска. Доменный тип — деталь :core:data. */
data class RecentSearch(
    val query: String,
    val lastUsedAt: Long,
    val useCount: Int,
)

fun SearchHistoryEntity.toDomain(): RecentSearch = RecentSearch(
    query = query,
    lastUsedAt = lastUsedAt,
    useCount = useCount,
)

package com.silverchat.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Сообщение.
 *
 * Контент вынесен в sealed-иерархию [MessageContent]: так добавление нового
 * типа (например, «кружки» или опросы) не ломает существующий UI — достаточно
 * добавить ветку в MessageBubble и ветку в маппер сетевого слоя.
 *
 * Группировка в UI (хвосты пузырей, аватар только у последнего) считается из
 * [senderId] + [sentAt] на экране, в модель не persist'ится.
 */
@Serializable
data class Message(
    @SerialName("id") val id: MessageId,
    @SerialName("chat_id") val chatId: ChatId,
    @SerialName("sender_id") val senderId: UserId,
    @SerialName("sender") val sender: User? = null,

    @SerialName("content") val content: MessageContent,
    @SerialName("status") val status: MessageStatus = MessageStatus.SENDING,

    @SerialName("reply_to") val replyTo: MessagePreview? = null,
    @SerialName("forward") val forwardInfo: ForwardInfo? = null,
    @SerialName("edited_at") val editedAt: Long? = null,

    @SerialName("reactions") val reactions: List<Reaction> = emptyList(),
    @SerialName("my_reaction") val myReaction: ReactionKind? = null,

    @SerialName("sent_at") val sentAt: Long,
    @SerialName("scheduled_at") val scheduledAt: Long? = null,

    @SerialName("pinned") val pinned: Boolean = false,
    @SerialName("silent") val silent: Boolean = false,
    @SerialName("protected") val contentProtected: Boolean = false,

    @SerialName("views_count") val viewsCount: Int = 0,
    @SerialName("deleted") val deleted: Boolean = false,
    @SerialName("deleted_for_all") val deletedForAll: Boolean = false,

    @SerialName("author_signature") val authorSignature: String? = null,
) {
    val isOutgoing: Boolean get() = false // заполняется в репозитории относительно currentUserId
    val isEdited: Boolean get() = editedAt != null
    val isStickerOnly: Boolean get() = content is MessageContent.Sticker
    val canBeEdited: Boolean get() = !deleted && status != MessageStatus.FAILED
    val canBeDeleted: Boolean get() = !deleted

    fun reactionCount(kind: ReactionKind): Int =
        reactions.firstOrNull { it.kind == kind }?.count ?: 0
}

/* ── Статусы доставки (галочки в UI) ─────────────────────────────────────── */

@Serializable
enum class MessageStatus {
    @SerialName("draft") DRAFT,
    @SerialName("pending") SENDING,
    @SerialName("sent") SENT,
    @SerialName("delivered") DELIVERED,
    @SerialName("read") READ,
    @SerialName("failed") FAILED,
}

/* ── Контент сообщения ───────────────────────────────────────────────────── */

@Serializable
sealed interface MessageContent {

    /** Обычный текст. [entities] — жирный/курсив/код/ссылки/спойлер. */
    @Serializable
    @SerialName("text")
    data class Text(
        @SerialName("text") val text: String,
        @SerialName("entities") val entities: List<TextEntity> = emptyList(),
        @SerialName("link_preview") val linkPreview: LinkPreview? = null,
    ) : MessageContent

    /** Фото (одиночное или альбом через [albumId]). */
    @Serializable
    @SerialName("photo")
    data class Photo(
        @SerialName("url") val url: String,
        @SerialName("thumb_url") val thumbUrl: String? = null,
        @SerialName("width") val width: Int,
        @SerialName("height") val height: Int,
        @SerialName("size_bytes") val sizeBytes: Long = 0L,
        @SerialName("caption") val caption: String? = null,
        @SerialName("album_id") val albumId: String? = null,
        @SerialName("spoiler") val spoiler: Boolean = false,
        @SerialName("upload_progress") val uploadProgress: Float = 1f,
    ) : MessageContent

    /** Видео. */
    @Serializable
    @SerialName("video")
    data class Video(
        @SerialName("url") val url: String,
        @SerialName("poster_url") val posterUrl: String? = null,
        @SerialName("duration_ms") val durationMs: Long,
        @SerialName("width") val width: Int,
        @SerialName("height") val height: Int,
        @SerialName("size_bytes") val sizeBytes: Long = 0L,
        @SerialName("caption") val caption: String? = null,
        @SerialName("album_id") val albumId: String? = null,
        @SerialName("upload_progress") val uploadProgress: Float = 1f,
    ) : MessageContent

    /** Голосовое сообщение. [waveform] — нормированные амплитуды 0..1 для отрисовки. */
    @Serializable
    @SerialName("voice")
    data class Voice(
        @SerialName("url") val url: String,
        @SerialName("duration_ms") val durationMs: Long,
        @SerialName("waveform") val waveform: List<Float> = emptyList(),
        @SerialName("size_bytes") val sizeBytes: Long = 0L,
        @SerialName("upload_progress") val uploadProgress: Float = 1f,
    ) : MessageContent

    /** Видеосообщение «кружок». */
    @Serializable
    @SerialName("circle")
    data class VideoCircle(
        @SerialName("url") val url: String,
        @SerialName("poster_url") val posterUrl: String? = null,
        @SerialName("duration_ms") val durationMs: Long,
        @SerialName("size_bytes") val sizeBytes: Long = 0L,
        @SerialName("upload_progress") val uploadProgress: Float = 1f,
    ) : MessageContent

    /** Документ/файл. */
    @Serializable
    @SerialName("file")
    data class File(
        @SerialName("url") val url: String,
        @SerialName("name") val fileName: String,
        @SerialName("mime") val mimeType: String,
        @SerialName("size_bytes") val sizeBytes: Long,
        @SerialName("upload_progress") val uploadProgress: Float = 1f,
    ) : MessageContent

    /** Стикер. [isPremium] — только для SilverChat Premium (эксклюзивные паки). */
    @Serializable
    @SerialName("sticker")
    data class Sticker(
        @SerialName("pack_id") val packId: String,
        @SerialName("emoji") val emoji: String,
        @SerialName("url") val url: String,
        @SerialName("animated") val animated: Boolean = false,
        @SerialName("is_premium") val isPremium: Boolean = false,
    ) : MessageContent

    /** GIF-анимация. Эксклюзивные GIF — привилегия Premium. */
    @Serializable
    @SerialName("gif")
    data class Gif(
        @SerialName("url") val url: String,
        @SerialName("thumb_url") val thumbUrl: String? = null,
        @SerialName("width") val width: Int,
        @SerialName("height") val height: Int,
        @SerialName("is_premium") val isPremium: Boolean = false,
    ) : MessageContent

    /** Геолокация / live-локация. */
    @Serializable
    @SerialName("location")
    data class Location(
        @SerialName("lat") val latitude: Double,
        @SerialName("lng") val longitude: Double,
        @SerialName("live_period_seconds") val livePeriodSeconds: Int? = null,
        @SerialName("title") val title: String? = null,
    ) : MessageContent

    /** Контакт. */
    @Serializable
    @SerialName("contact")
    data class Contact(
        @SerialName("user_id") val userId: UserId? = null,
        @SerialName("first_name") val firstName: String,
        @SerialName("last_name") val lastName: String? = null,
        @SerialName("phone") val phone: String,
    ) : MessageContent

    /** Опрос (викторина/обычный). */
    @Serializable
    @SerialName("poll")
    data class Poll(
        @SerialName("question") val question: String,
        @SerialName("options") val options: List<PollOption>,
        @SerialName("anonymous") val anonymous: Boolean = true,
        @SerialName("multiple") val multipleChoice: Boolean = false,
        @SerialName("quiz") val correctOptionIndex: Int? = null,
        @SerialName("closed") val closed: Boolean = false,
    ) : MessageContent

    /** Служебное сообщение (вступил в группу, создан канал, звонок завершён…). */
    @Serializable
    @SerialName("service")
    data class Service(
        @SerialName("action") val action: ServiceAction,
        @SerialName("title") val title: String,
    ) : MessageContent

    /** Приглашение в чат. */
    @Serializable
    @SerialName("invite")
    data class Invite(
        @SerialName("invite") val invite: ChatInviteLink,
        @SerialName("chat_title") val chatTitle: String,
    ) : MessageContent

    /** Подарок за сильверы. */
    @Serializable
    @SerialName("gift")
    data class Gift(
        @SerialName("gift_id") val giftId: String,
        @SerialName("title") val title: String,
        @SerialName("emoji") val emoji: String,
        @SerialName("price_silver") val priceSilver: Long,
        @SerialName("message") val message: String? = null,
    ) : MessageContent
}

@Serializable
data class PollOption(
    @SerialName("text") val text: String,
    @SerialName("votes") val votes: Int = 0,
    @SerialName("voted") val votedByMe: Boolean = false,
)

@Serializable
enum class ServiceAction {
    @SerialName("chat_created") CHAT_CREATED,
    @SerialName("member_joined") MEMBER_JOINED,
    @SerialName("member_left") MEMBER_LEFT,
    @SerialName("member_invited") MEMBER_INVITED,
    @SerialName("member_kicked") MEMBER_KICKED,
    @SerialName("title_changed") TITLE_CHANGED,
    @SerialName("avatar_changed") AVATAR_CHANGED,
    @SerialName("pinned_message") PINNED_MESSAGE,
    @SerialName("call_missed") CALL_MISSED,
    @SerialName("call_ended") CALL_ENDED,
    @SerialName("gift_sent") GIFT_SENT,
    @SerialName("premium_gifted") PREMIUM_GIFTED,
    @SerialName("username_purchased") USERNAME_PURCHASESED,
    @SerialName("story_published") STORY_PUBLISHED,
}

/* ── Форматирование текста ───────────────────────────────────────────────── */

@Serializable
data class TextEntity(
    @SerialName("type") val type: TextEntityType,
    @SerialName("offset") val offset: Int,
    @SerialName("length") val length: Int,
    @SerialName("url") val url: String? = null,
    @SerialName("language") val language: String? = null,
)

@Serializable
enum class TextEntityType {
    @SerialName("bold") BOLD,
    @SerialName("italic") ITALIC,
    @SerialName("underline") UNDERLINE,
    @SerialName("strikethrough") STRIKETHROUGH,
    @SerialName("code") CODE,
    @SerialName("pre") PRE,
    @SerialName("link") LINK,
    @SerialName("mention") MENTION,
    @SerialName("hashtag") HASHTAG,
    @SerialName("spoiler") SPOILER,
    @SerialName("blockquote") BLOCKQUOTE,
}

@Serializable
data class LinkPreview(
    @SerialName("url") val url: String,
    @SerialName("title") val title: String,
    @SerialName("description") val description: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
)

/* ── Reply / Forward ─────────────────────────────────────────────────────── */

/**
 * Лёгкое представление сообщения, на которое идёт ответ.
 * Хранится денормализованно, чтобы reply-превью не требовало второго запроса.
 */
@Serializable
data class MessagePreview(
    @SerialName("message_id") val messageId: MessageId,
    @SerialName("chat_id") val chatId: ChatId,
    @SerialName("sender_id") val senderId: UserId,
    @SerialName("sender_name") val senderName: String,
    @SerialName("snippet") val snippet: String,
    @SerialName("content_kind") val contentKind: String = "text",
)

@Serializable
data class ForwardInfo(
    @SerialName("from_user_id") val fromUserId: UserId? = null,
    @SerialName("from_name") val fromName: String,
    @SerialName("from_chat_id") val fromChatId: ChatId? = null,
    @SerialName("original_message_id") val originalMessageId: MessageId? = null,
    @SerialName("forwarded_at") val forwardedAt: Long,
)

/* ── Реакции ─────────────────────────────────────────────────────────────── */

/**
 * Реакции на сообщения.
 *
 * Free-реакции (любое эмодзи + кастомные стикеры) — привилегия Premium;
 * базовый набор [ReactionKind] доступен всем.
 */
@Serializable
data class Reaction(
    @SerialName("kind") val kind: ReactionKind,
    @SerialName("count") val count: Int,
    @SerialName("reacted_by_me") val reactedByMe: Boolean = false,
    @SerialName("recent_users") val recentUsers: List<UserId> = emptyList(),
)

@Serializable
enum class ReactionKind(val emoji: String) {
    @SerialName("like") LIKE("👍"),
    @SerialName("heart") HEART("❤️"),
    @SerialName("fire") FIRE("🔥"),
    @SerialName("party") PARTY("🎉"),
    @SerialName("laugh") LAUGH("😂"),
    @SerialName("surprised") SURPRISED("😮"),
    @SerialName("cry") CRY("😢"),
    @SerialName("dislike") DISLIKE("👎"),
    @SerialName("clap") CLAP("👏"),
    @SerialName("think") THINK("🤔"),
    @SerialName("silver") SILVER("🥈"),
}

/** Быстрая панель реакций: двойной тап ставит [DEFAULT_DOUBLE_TAP]. */
object QuickReactions {
    val DEFAULT_DOUBLE_TAP: ReactionKind = ReactionKind.HEART
    val PANEL: List<ReactionKind> = listOf(
        ReactionKind.LIKE, ReactionKind.HEART, ReactionKind.FIRE,
        ReactionKind.PARTY, ReactionKind.LAUGH, ReactionKind.SURPRISED,
    )
}

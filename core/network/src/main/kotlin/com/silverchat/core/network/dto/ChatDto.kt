package com.silverchat.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/* =========================================================================
   DTO ЧАТА / СООБЩЕНИЯ / КОНТЕНТА
   ========================================================================= */

@Serializable
data class ChatDto(
    @SerialName("id") val id: String,
    @SerialName("type") val type: String,
    @SerialName("title") val title: String,
    @SerialName("about") val about: String? = null,
    @SerialName("avatar") val avatar: AvatarDto? = null,
    @SerialName("username") val username: String? = null,
    @SerialName("peer") val peer: UserDto? = null,
    @SerialName("members_count") val membersCount: Int = 0,
    @SerialName("online_count") val onlineCount: Int = 0,
    @SerialName("last_message") val lastMessage: MessageDto? = null,
    @SerialName("draft") val draft: DraftDto? = null,
    @SerialName("unread_count") val unreadCount: Int = 0,
    @SerialName("mentions_count") val mentionsCount: Int = 0,
    @SerialName("pinned") val pinned: List<String> = emptyList(),
    @SerialName("pinned_story_id") val pinnedStoryId: String? = null,
    @SerialName("muted") val muted: Boolean = false,
    @SerialName("muted_until") val mutedUntil: Long? = null,
    @SerialName("archived") val archived: Boolean = false,
    @SerialName("folder_ids") val folderIds: List<String> = emptyList(),
    @SerialName("verified") val verified: Boolean = false,
    @SerialName("restricted") val restricted: Boolean = false,
    @SerialName("permissions") val permissions: ChatPermissionsDto = ChatPermissionsDto(),
    @SerialName("my_role") val myRole: String = "member",
    @SerialName("invite_links") val inviteLinks: List<InviteLinkDto> = emptyList(),
    @SerialName("join_requests") val joinRequests: Int = 0,
    @SerialName("location") val location: LocationRequest? = null,
    @SerialName("working_hours") val workingHours: WorkingHoursRequest? = null,
    @SerialName("slow_mode_seconds") val slowModeSeconds: Int = 0,
    @SerialName("created_at") val createdAt: Long = 0L,
)

@Serializable
data class ChatPermissionsDto(
    @SerialName("can_send_messages") val canSendMessages: Boolean = true,
    @SerialName("can_send_media") val canSendMedia: Boolean = true,
    @SerialName("can_send_voice") val canSendVoice: Boolean = true,
    @SerialName("can_send_stickers") val canSendStickers: Boolean = true,
    @SerialName("can_add_members") val canAddMembers: Boolean = true,
    @SerialName("can_react") val canReact: Boolean = true,
    @SerialName("can_pin") val canPin: Boolean = false,
    @SerialName("join_by_invite_only") val joinByInviteOnly: Boolean = true,
    @SerialName("approve_new_members") val approveNewMembers: Boolean = false,
    @SerialName("slow_mode_seconds") val slowModeSeconds: Int = 0,
)

@Serializable
data class ChatMemberDto(
    @SerialName("user") val user: UserDto,
    @SerialName("role") val role: String = "member",
    @SerialName("custom_title") val customTitle: String? = null,
    @SerialName("joined_at") val joinedAt: Long = 0L,
    @SerialName("permissions") val permissions: MemberPermissionsDto = MemberPermissionsDto(),
)

@Serializable
data class MemberPermissionsDto(
    @SerialName("can_send_messages") val canSendMessages: Boolean = true,
    @SerialName("can_send_media") val canSendMedia: Boolean = true,
    @SerialName("can_send_voice") val canSendVoice: Boolean = true,
    @SerialName("can_send_stickers") val canSendStickers: Boolean = true,
    @SerialName("can_react") val canReact: Boolean = true,
    @SerialName("can_invite") val canInvite: Boolean = true,
    @SerialName("can_pin") val canPin: Boolean = false,
    @SerialName("restricted_until") val restrictedUntil: Long? = null,
)

@Serializable
data class ChatFolderDto(
    @SerialName("id") val id: String,
    @SerialName("title") val title: String,
    @SerialName("icon") val icon: String,
    @SerialName("include_types") val includeTypes: List<String> = emptyList(),
    @SerialName("exclude_muted") val excludeMuted: Boolean = false,
    @SerialName("exclude_read") val excludeRead: Boolean = false,
    @SerialName("pinned_chat_ids") val pinnedChatIds: List<String> = emptyList(),
    @SerialName("is_default") val isDefault: Boolean = false,
)

@Serializable
data class DraftDto(
    @SerialName("text") val text: String = "",
    @SerialName("reply_to") val replyTo: String? = null,
    @SerialName("attachments") val attachments: List<String> = emptyList(),
    @SerialName("updated_at") val updatedAt: Long = 0L,
)

/* ── Сообщения ───────────────────────────────────────────────────────────── */

@Serializable
data class MessageDto(
    @SerialName("id") val id: String,
    @SerialName("chat_id") val chatId: String,
    @SerialName("sender_id") val senderId: String,
    @SerialName("sender") val sender: UserDto? = null,
    @SerialName("content") val content: MessageContentDto,
    @SerialName("status") val status: String = "sent",
    @SerialName("reply_to") val replyTo: MessagePreviewDto? = null,
    @SerialName("forward") val forward: ForwardDto? = null,
    @SerialName("edited_at") val editedAt: Long? = null,
    @SerialName("reactions") val reactions: List<ReactionDto> = emptyList(),
    @SerialName("my_reaction") val myReaction: String? = null,
    @SerialName("sent_at") val sentAt: Long,
    @SerialName("scheduled_at") val scheduledAt: Long? = null,
    @SerialName("pinned") val pinned: Boolean = false,
    @SerialName("silent") val silent: Boolean = false,
    @SerialName("protected") val contentProtected: Boolean = false,
    @SerialName("views_count") val viewsCount: Int = 0,
    @SerialName("deleted") val deleted: Boolean = false,
    @SerialName("client_message_id") val clientMessageId: String? = null,
)

/**
 * Контент сообщения — discriminated union по полю `kind`.
 *
 * Polymorphic-сериализация kotlinx.serialization с классом-дискриминатором:
 * сервер шлёт {"kind":"photo", ...}, клиент декодирует в нужный подтип.
 * Новые виды контента добавляются без breaking change для старых клиентов —
 * неизвестный `kind` уходит в [MessageContentDto.Unknown] и рисуется как
 * «Сообщение не поддерживается вашей версией».
 */
@Serializable
sealed interface MessageContentDto {

    @Serializable
    @SerialName("text")
    data class Text(
        @SerialName("text") val text: String,
        @SerialName("entities") val entities: List<TextEntityDto> = emptyList(),
        @SerialName("link_preview") val linkPreview: LinkPreviewDto? = null,
    ) : MessageContentDto

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
    ) : MessageContentDto

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
    ) : MessageContentDto

    @Serializable
    @SerialName("voice")
    data class Voice(
        @SerialName("url") val url: String,
        @SerialName("duration_ms") val durationMs: Long,
        @SerialName("waveform") val waveform: List<Float> = emptyList(),
        @SerialName("size_bytes") val sizeBytes: Long = 0L,
    ) : MessageContentDto

    @Serializable
    @SerialName("circle")
    data class VideoCircle(
        @SerialName("url") val url: String,
        @SerialName("poster_url") val posterUrl: String? = null,
        @SerialName("duration_ms") val durationMs: Long,
        @SerialName("size_bytes") val sizeBytes: Long = 0L,
    ) : MessageContentDto

    @Serializable
    @SerialName("file")
    data class File(
        @SerialName("url") val url: String,
        @SerialName("name") val fileName: String,
        @SerialName("mime") val mimeType: String,
        @SerialName("size_bytes") val sizeBytes: Long,
    ) : MessageContentDto

    @Serializable
    @SerialName("sticker")
    data class Sticker(
        @SerialName("pack_id") val packId: String,
        @SerialName("emoji") val emoji: String,
        @SerialName("url") val url: String,
        @SerialName("animated") val animated: Boolean = false,
        @SerialName("is_premium") val isPremium: Boolean = false,
    ) : MessageContentDto

    @Serializable
    @SerialName("gif")
    data class Gif(
        @SerialName("url") val url: String,
        @SerialName("thumb_url") val thumbUrl: String? = null,
        @SerialName("width") val width: Int,
        @SerialName("height") val height: Int,
        @SerialName("is_premium") val isPremium: Boolean = false,
    ) : MessageContentDto

    @Serializable
    @SerialName("location")
    data class Location(
        @SerialName("lat") val latitude: Double,
        @SerialName("lng") val longitude: Double,
        @SerialName("live_period_seconds") val livePeriodSeconds: Int? = null,
        @SerialName("title") val title: String? = null,
    ) : MessageContentDto

    @Serializable
    @SerialName("contact")
    data class Contact(
        @SerialName("user_id") val userId: String? = null,
        @SerialName("first_name") val firstName: String,
        @SerialName("last_name") val lastName: String? = null,
        @SerialName("phone") val phone: String,
    ) : MessageContentDto

    @Serializable
    @SerialName("poll")
    data class Poll(
        @SerialName("question") val question: String,
        @SerialName("options") val options: List<PollOptionDto>,
        @SerialName("anonymous") val anonymous: Boolean = true,
        @SerialName("multiple") val multiple: Boolean = false,
        @SerialName("quiz") val correctOptionIndex: Int? = null,
        @SerialName("closed") val closed: Boolean = false,
    ) : MessageContentDto

    @Serializable
    @SerialName("service")
    data class Service(
        @SerialName("action") val action: String,
        @SerialName("title") val title: String,
    ) : MessageContentDto

    @Serializable
    @SerialName("gift")
    data class Gift(
        @SerialName("gift_id") val giftId: String,
        @SerialName("title") val title: String,
        @SerialName("emoji") val emoji: String,
        @SerialName("price_silver") val priceSilver: Long,
        @SerialName("message") val message: String? = null,
    ) : MessageContentDto

    /** Неизвестный серверу/клиенту тип — не роняем приложение. */
    @Serializable
    @SerialName("unknown")
    data class Unknown(
        @SerialName("raw_kind") val rawKind: String = "unknown",
    ) : MessageContentDto
}

@Serializable
data class TextEntityDto(
    @SerialName("type") val type: String,
    @SerialName("offset") val offset: Int,
    @SerialName("length") val length: Int,
    @SerialName("url") val url: String? = null,
    @SerialName("language") val language: String? = null,
)

@Serializable
data class LinkPreviewDto(
    @SerialName("url") val url: String,
    @SerialName("title") val title: String,
    @SerialName("description") val description: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
)

@Serializable
data class PollOptionDto(
    @SerialName("text") val text: String,
    @SerialName("votes") val votes: Int = 0,
    @SerialName("voted_by_me") val votedByMe: Boolean = false,
)

@Serializable
data class ReactionDto(
    @SerialName("kind") val kind: String,
    @SerialName("count") val count: Int,
    @SerialName("reacted_by_me") val reactedByMe: Boolean = false,
    @SerialName("recent_users") val recentUsers: List<String> = emptyList(),
)

@Serializable
data class MessagePreviewDto(
    @SerialName("message_id") val messageId: String,
    @SerialName("chat_id") val chatId: String,
    @SerialName("sender_id") val senderId: String,
    @SerialName("sender_name") val senderName: String,
    @SerialName("snippet") val snippet: String,
    @SerialName("content_kind") val contentKind: String = "text",
)

@Serializable
data class ForwardDto(
    @SerialName("from_user_id") val fromUserId: String? = null,
    @SerialName("from_name") val fromName: String,
    @SerialName("from_chat_id") val fromChatId: String? = null,
    @SerialName("original_message_id") val originalMessageId: String? = null,
    @SerialName("forwarded_at") val forwardedAt: Long,
)

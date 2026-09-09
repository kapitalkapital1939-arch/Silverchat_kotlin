package com.silverchat.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Чат — единая сущность для личных диалогов, групп, каналов и ботов.
 *
 * Различаются только [type] и набором прав [permissions]. UI (список чатов,
 * экран диалога, инфо-панель) работает с одним типом — никаких отдельных
 * сущностей Channel/Group/Peer, иначе дублируется половина логики.
 */
@Serializable
data class Chat(
    @SerialName("id") val id: ChatId,
    @SerialName("type") val type: ChatType,
    @SerialName("title") val title: String,
    @SerialName("about") val about: String? = null,
    @SerialName("avatar") val avatar: AvatarMedia? = null,
    @SerialName("username") val username: String? = null,

    // Персональный чат: данные собеседника (для группы/канала = null)
    @SerialName("peer") val peer: User? = null,

    @SerialName("members_count") val membersCount: Int = 0,
    @SerialName("online_count") val onlineCount: Int = 0,
    @SerialName("last_message") val lastMessage: Message? = null,
    @SerialName("draft") val draft: Draft? = null,

    @SerialName("unread_count") val unreadCount: Int = 0,
    @SerialName("mentions_count") val mentionsCount: Int = 0,
    @SerialName("pinned") val pinned: List<MessageId> = emptyList(),
    @SerialName("pinned_story") val pinnedStoryId: StoryId? = null,

    @SerialName("muted") val muted: Boolean = false,
    @SerialName("muted_until") val mutedUntil: Long? = null,
    @SerialName("archived") val archived: Boolean = false,
    @SerialName("folder_ids") val folderIds: List<String> = emptyList(),

    @SerialName("verified") val verified: Boolean = false,
    @SerialName("restricted") val restricted: Boolean = false,

    // Только для каналов/групп
    @SerialName("permissions") val permissions: ChatPermissions = ChatPermissions(),
    @SerialName("my_role") val myRole: MemberRole = MemberRole.MEMBER,
    @SerialName("invite_links") val inviteLinks: List<ChatInviteLink> = emptyList(),
    @SerialName("join_requests") val joinRequestsCount: Int = 0,

    // Кастомные блоки профиля канала
    @SerialName("location") val location: LocationInfo? = null,
    @SerialName("working_hours") val workingHours: WorkingHours? = null,

    @SerialName("slow_mode_seconds") val slowModeSeconds: Int = 0,
    @SerialName("created_at") val createdAt: Long = 0L,
) {
    val isPersonal: Boolean get() = type == ChatType.PERSONAL
    val isGroup: Boolean get() = type == ChatType.GROUP || type == ChatType.SUPERGROUP
    val isChannel: Boolean get() = type == ChatType.CHANNEL || type == ChatType.BROADCAST
    val isSecret: Boolean get() = type == ChatType.SECRET
    val isBot: Boolean get() = type == ChatType.BOT

    val canWrite: Boolean
        get() = when {
            isChannel && myRole == MemberRole.MEMBER -> false // канал: пишут только админы
            restricted -> false
            else -> true
        }

    val canEditInfo: Boolean
        get() = myRole == MemberRole.OWNER || myRole == MemberRole.ADMIN

    val isMuted: Boolean
        get() = muted && (mutedUntil == null || mutedUntil > System.currentTimeMillis())

    val displayName: String get() = peer?.fullName ?: title
}

@Serializable
@JvmInline
value class ChatId(val raw: String) {
    override fun toString(): String = raw
}

@Serializable
enum class ChatType {
    @SerialName("personal") PERSONAL,
    @SerialName("secret") SECRET,
    @SerialName("group") GROUP,
    @SerialName("supergroup") SUPERGROUP,
    @SerialName("channel") CHANNEL,
    @SerialName("broadcast") BROADCAST,
    @SerialName("bot") BOT,
    @SerialName("saved") SAVED,
}

@Serializable
enum class MemberRole {
    @SerialName("owner") OWNER,
    @SerialName("admin") ADMIN,
    @SerialName("member") MEMBER,
    @SerialName("restricted") RESTRICTED,
    @SerialName("kicked") KICKED,
}

@Serializable
data class ChatMember(
    @SerialName("user") val user: User,
    @SerialName("role") val role: MemberRole = MemberRole.MEMBER,
    @SerialName("custom_title") val customTitle: String? = null,
    @SerialName("joined_at") val joinedAt: Long = 0L,
    @SerialName("permissions") val permissions: MemberPermissions = MemberPermissions(),
)

@Serializable
data class MemberPermissions(
    @SerialName("can_send_messages") val canSendMessages: Boolean = true,
    @SerialName("can_send_media") val canSendMedia: Boolean = true,
    @SerialName("can_send_voice") val canSendVoice: Boolean = true,
    @SerialName("can_send_stickers") val canSendStickers: Boolean = true,
    @SerialName("can_react") val canReact: Boolean = true,
    @SerialName("can_invite") val canInvite: Boolean = true,
    @SerialName("can_pin") val canPin: Boolean = false,
    @SerialName("restricted_until") val restrictedUntil: Long? = null,
)

/** Права участников по умолчанию (для группы/канала). */
@Serializable
data class ChatPermissions(
    @SerialName("can_send_messages") val canSendMessages: Boolean = true,
    @SerialName("can_send_media") val canSendMedia: Boolean = true,
    @SerialName("can_send_voice") val canSendVoice: Boolean = true,
    @SerialName("can_send_stickers") val canSendStickers: Boolean = true,
    @SerialName("can_add_members") val canAddMembers: Boolean = true,
    @SerialName("can_react") val canReact: Boolean = true,
    @SerialName("can_pin") val canPinMessages: Boolean = false,
    @SerialName("join_by_invite_only") val joinByInviteOnly: Boolean = true,
    @SerialName("approve_new_members") val approveNewMembers: Boolean = false,
    @SerialName("slow_mode_seconds") val slowModeSeconds: Int = 0,
)

/** Черновик ввода — синхронизируется между устройствами через WebSocket. */
@Serializable
data class Draft(
    @SerialName("text") val text: String = "",
    @SerialName("reply_to") val replyTo: MessageId? = null,
    @SerialName("attachments") val pendingAttachments: List<String> = emptyList(),
    @SerialName("updated_at") val updatedAt: Long = 0L,
) {
    val isEmpty: Boolean get() = text.isBlank() && pendingAttachments.isEmpty()
}

/** Папки чатов (как в Telegram: All / Unread / Personal / Groups / Channels). */
@Serializable
data class ChatFolder(
    @SerialName("id") val id: String,
    @SerialName("title") val title: String,
    @SerialName("icon") val icon: String,
    @SerialName("include_types") val includeTypes: List<ChatType> = emptyList(),
    @SerialName("exclude_muted") val excludeMuted: Boolean = false,
    @SerialName("exclude_read") val excludeRead: Boolean = false,
    @SerialName("pinned_chat_ids") val pinnedChatIds: List<ChatId> = emptyList(),
    @SerialName("is_default") val isDefault: Boolean = false,
)

/**
 * Защищённая пригласительная ссылка (вместо открытого доступа).
 * Одноразовость/лимит/срок действия/заявки на вступление — как в Telegram.
 */
@Serializable
data class ChatInviteLink(
    @SerialName("token") val token: String,
    @SerialName("chat_id") val chatId: ChatId,
    @SerialName("created_by") val createdBy: UserId,
    @SerialName("name") val name: String? = null,
    @SerialName("max_uses") val maxUses: Int? = null,
    @SerialName("used_count") val usedCount: Int = 0,
    @SerialName("expires_at") val expiresAt: Long? = null,
    @SerialName("requires_approval") val requiresApproval: Boolean = false,
    @SerialName("revoked") val revoked: Boolean = false,
) {
    val url: String get() = "https://silver.chat/join/$token"

    val isActive: Boolean
        get() = !revoked &&
            (expiresAt == null || expiresAt > System.currentTimeMillis()) &&
            (maxUses == null || usedCount < maxUses)
}

@Serializable
@JvmInline
value class MessageId(val raw: String) {
    override fun toString(): String = raw
}

@Serializable
@JvmInline
value class StoryId(val raw: String) {
    override fun toString(): String = raw
}

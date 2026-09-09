package com.silverchat.core.domain.repository

import com.silverchat.core.common.result.ScResult
import com.silverchat.core.model.Chat
import com.silverchat.core.model.ChatFolder
import com.silverchat.core.model.ChatId
import com.silverchat.core.model.ChatInviteLink
import com.silverchat.core.model.ChatMember
import com.silverchat.core.model.ChatType
import com.silverchat.core.model.Draft
import com.silverchat.core.model.LocationInfo
import com.silverchat.core.model.MemberRole
import com.silverchat.core.model.UserId
import com.silverchat.core.model.WorkingHours
import kotlinx.coroutines.flow.Flow

/** Чаты, группы, каналы, участники, приглашения, папки. */
interface ChatRepository {

    /* ── Наблюдение ─────────────────────────────────────────────────────── */

    fun observeChats(folderId: String? = null, archived: Boolean = false): Flow<List<Chat>>

    fun observeChat(chatId: ChatId): Flow<Chat?>

    fun observeFolders(): Flow<List<ChatFolder>>

    fun observeTotalUnread(): Flow<Int>

    fun observeTyping(chatId: ChatId): Flow<List<UserId>>

    fun observeMembers(chatId: ChatId, query: String = ""): Flow<List<ChatMember>>

    /* ── Создание ───────────────────────────────────────────────────────── */

    suspend fun openPersonalChat(userId: UserId): ScResult<Chat>

    suspend fun createGroup(
        title: String,
        memberIds: List<UserId>,
        avatarUri: String?,
    ): ScResult<Chat>

    suspend fun createChannel(
        title: String,
        about: String?,
        isBroadcast: Boolean,
    ): ScResult<Chat>

    suspend fun createFolder(title: String, types: List<ChatType>): ScResult<ChatFolder>

    /* ── Управление профилем чата/канала ────────────────────────────────── */

    suspend fun updateChatInfo(
        chatId: ChatId,
        title: String?,
        about: String?,
        avatarUri: String?,
    ): ScResult<Chat>

    suspend fun setChatType(chatId: ChatId, type: ChatType): ScResult<Chat>

    /** Кастомные блоки: местоположение и часы работы канала/группы. */
    suspend fun setChatLocation(chatId: ChatId, location: LocationInfo?): ScResult<Unit>

    suspend fun setChatWorkingHours(chatId: ChatId, hours: WorkingHours?): ScResult<Unit>

    suspend fun setSlowMode(chatId: ChatId, seconds: Int): ScResult<Unit>

    /* ── Участники и роли ───────────────────────────────────────────────── */

    suspend fun inviteMembers(chatId: ChatId, userIds: List<UserId>): ScResult<Unit>

    suspend fun changeMemberRole(chatId: ChatId, userId: UserId, role: MemberRole): ScResult<Unit>

    suspend fun kickMember(chatId: ChatId, userId: UserId): ScResult<Unit>

    suspend fun restrictMember(chatId: ChatId, userId: UserId, untilTs: Long): ScResult<Unit>

    suspend fun leaveChat(chatId: ChatId): ScResult<Unit>

    /* ── Защищённые пригласительные ссылки (вместо открытого доступа) ───── */

    suspend fun createInviteLink(
        chatId: ChatId,
        maxUses: Int?,
        expiresAt: Long?,
        requiresApproval: Boolean,
        name: String?,
    ): ScResult<ChatInviteLink>

    suspend fun revokeInviteLink(chatId: ChatId, token: String): ScResult<Unit>

    suspend fun joinByInvite(token: String): ScResult<Chat>

    suspend fun approveJoinRequest(chatId: ChatId, userId: UserId, approve: Boolean): ScResult<Unit>

    /* ── Состояние списка чатов ─────────────────────────────────────────── */

    suspend fun pinChat(chatId: ChatId, pinned: Boolean): ScResult<Unit>

    suspend fun muteChat(chatId: ChatId, mutedUntil: Long?): ScResult<Unit>

    suspend fun archiveChat(chatId: ChatId, archived: Boolean): ScResult<Unit>

    suspend fun markRead(chatId: ChatId): ScResult<Unit>

    suspend fun saveDraft(chatId: ChatId, draft: Draft): ScResult<Unit>

    suspend fun sendTyping(chatId: ChatId, typing: Boolean): ScResult<Unit>

    suspend fun deleteChat(chatId: ChatId, forEveryone: Boolean): ScResult<Unit>
}

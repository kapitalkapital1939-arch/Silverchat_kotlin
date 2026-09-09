package com.silverchat.core.data.repository

import com.silverchat.core.common.result.ScResult
import com.silverchat.core.data.mapper.toDomain as entityToDomain
import com.silverchat.core.data.mapper.toEntity
import com.silverchat.core.database.dao.ChatDao
import com.silverchat.core.database.dao.DraftDao
import com.silverchat.core.database.dao.MessageDao
import com.silverchat.core.domain.repository.ChatRepository
import com.silverchat.core.model.Chat
import com.silverchat.core.model.ChatFolder
import com.silverchat.core.model.ChatId
import com.silverchat.core.model.ChatInviteLink
import com.silverchat.core.model.ChatMember
import com.silverchat.core.model.ChatType
import com.silverchat.core.model.Draft
import com.silverchat.core.model.LocationInfo
import com.silverchat.core.model.MemberRole
import com.silverchat.core.model.SocketOps
import com.silverchat.core.model.UserId
import com.silverchat.core.model.WorkingHours
import com.silverchat.core.network.api.SilverChatApi
import com.silverchat.core.network.dto.AddMembersRequest
import com.silverchat.core.network.dto.ApproveRequest
import com.silverchat.core.network.dto.ArchiveChatRequest
import com.silverchat.core.network.dto.ChatTypeRequest
import com.silverchat.core.network.dto.CreateChannelRequest
import com.silverchat.core.network.dto.CreateGroupRequest
import com.silverchat.core.network.dto.DraftRequest
import com.silverchat.core.network.dto.FolderRequest
import com.silverchat.core.network.dto.InviteLinkRequest
import com.silverchat.core.network.dto.LocationRequest
import com.silverchat.core.network.dto.MuteChatRequest
import com.silverchat.core.network.dto.OpenPersonalChatRequest
import com.silverchat.core.network.dto.PinChatRequest
import com.silverchat.core.network.dto.UpdateChatRequest
import com.silverchat.core.network.dto.UpdateMemberRequest
import com.silverchat.core.network.dto.WorkingHoursRequest
import com.silverchat.core.network.mapper.apiCall
import com.silverchat.core.network.mapper.toDomain as dtoToDomain
import com.silverchat.core.network.mapper.toDto
import com.silverchat.core.network.ws.RealtimeSocket
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Чаты: список, папки, участники, приглашения, локальные флаги.
 *
 * Offline-first: все `observe*` читают Room, поэтому список чатов рисуется
 * мгновенно даже в самолёте. Сеть лишь обновляет кэш — UI перерисовывается
 * сам, потому что Room отдаёт `Flow`.
 *
 * Локальные флаги (`muted`, `archived`) пишутся в БД ДО ответа сервера:
 * именно этого пользователь ждёт мгновенно. Если сервер откажет, состояние
 * откатится следующим `chat.updated` по WebSocket.
 */
@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val api: SilverChatApi,
    private val chatDao: ChatDao,
    private val messageDao: MessageDao,
    private val draftDao: DraftDao,
    private val socket: RealtimeSocket,
) : ChatRepository {

    /**
     * Папки приходят только внутри `ChatListResponse` и отдельной таблицы не
     * имеют — их единицы, поэтому держим их в памяти и обновляем при синке.
     */
    private val foldersCache = MutableStateFlow<List<ChatFolder>>(emptyList())

    /** Кто печатает прямо сейчас: chatId -> userId. Транзиентное состояние. */
    private val typingCache = MutableStateFlow<Map<ChatId, Set<UserId>>>(emptyMap())

    override fun observeChats(folderId: String?, archived: Boolean): Flow<List<Chat>> =
        combine(chatDao.observeChats(archived), draftDao.observeAll()) { chats, drafts ->
            val draftsByChat = drafts.associateBy { it.chatId }
            chats
                .map { entity -> entity.entityToDomain(draftsByChat[entity.id]?.entityToDomain()) }
                .let { list -> if (folderId == null) list else list.filter { matchesFolder(it, folderId) } }
                .sortedWith(CHATS_ORDER)
        }

    override fun observeChat(chatId: ChatId): Flow<Chat?> =
        combine(chatDao.observeChat(chatId), draftDao.observeDraft(chatId)) { entity, draft ->
            entity?.entityToDomain(draft?.entityToDomain())
        }

    override fun observeFolders(): Flow<List<ChatFolder>> = foldersCache

    override fun observeTotalUnread(): Flow<Int> = chatDao.observeTotalUnread().map { it ?: 0 }

    override fun observeTyping(chatId: ChatId): Flow<List<UserId>> =
        typingCache.map { it[chatId].orEmpty().toList() }

    override fun observeMembers(chatId: ChatId, query: String): Flow<List<ChatMember>> =
        messageDao.observeMembers(chatId).map { entities ->
            entities.mapNotNull { it.entityToDomain() }
                .filter { member ->
                    val needle = query.trim().removePrefix("@")
                    needle.isEmpty() ||
                        member.user.fullName.contains(needle, ignoreCase = true) ||
                        member.user.username.orEmpty().contains(needle, ignoreCase = true)
                }
                .sortedWith(MEMBERS_ORDER)
        }

    /* ── Создание чатов ────────────────────────────────────────────────── */

    override suspend fun openPersonalChat(userId: UserId): ScResult<Chat> = apiCall {
        api.openPersonalChat(OpenPersonalChatRequest(userId)).toDomainAndCache()
    }

    override suspend fun createGroup(
        title: String,
        memberIds: List<UserId>,
        avatarUri: String?,
    ): ScResult<Chat> = apiCall {
        api.createGroup(
            CreateGroupRequest(title = title.trim(), memberIds = memberIds, avatarUrl = avatarUri),
        ).toDomainAndCache()
    }

    override suspend fun createChannel(
        title: String,
        about: String?,
        isBroadcast: Boolean,
    ): ScResult<Chat> = apiCall {
        api.createChannel(
            CreateChannelRequest(
                title = title.trim(),
                about = about?.trim(),
                broadcast = isBroadcast,
                // Канал по умолчанию закрытый: публичный юзернейм выдаётся отдельно.
                joinByInviteOnly = true,
            ),
        ).toDomainAndCache()
    }

    override suspend fun createFolder(title: String, types: List<ChatType>): ScResult<ChatFolder> =
        apiCall {
            val created = api.createFolder(
                FolderRequest(title.trim(), types.map { it.name.lowercase() }),
            ).dtoToDomain()
            foldersCache.value = foldersCache.value.filterNot { it.id == created.id } + created
            created
        }

    /* ── Настройки чата ────────────────────────────────────────────────── */

    override suspend fun updateChatInfo(
        chatId: ChatId,
        title: String?,
        about: String?,
        avatarUri: String?,
    ): ScResult<Chat> = apiCall {
        api.updateChat(
            chatId,
            UpdateChatRequest(
                title = title?.trim()?.ifBlank { null },
                about = about?.trim(),
                avatarUrl = avatarUri,
            ),
        ).toDomainAndCache()
    }

    override suspend fun setChatType(chatId: ChatId, type: ChatType): ScResult<Chat> = apiCall {
        api.setChatType(chatId, ChatTypeRequest(type.name.lowercase())).toDomainAndCache()
    }

    override suspend fun setChatLocation(
        chatId: ChatId,
        location: LocationInfo?,
    ): ScResult<Unit> = apiCall {
        // visible=false + нулевые координаты — способ снять геолокацию:
        // отдельного DELETE в контракте нет, PATCH с таким телом её очищает.
        val body = location?.toDto()
            ?: LocationRequest(latitude = null, longitude = null, visible = false)
        api.setChatLocation(chatId, body).toDomainAndCache()
    }

    override suspend fun setChatWorkingHours(
        chatId: ChatId,
        hours: WorkingHours?,
    ): ScResult<Unit> = apiCall {
        val body = hours?.toDto()
            ?: WorkingHoursRequest(timezone = "UTC", alwaysOpen = false, visible = false)
        api.setChatWorkingHours(chatId, body).toDomainAndCache()
    }

    override suspend fun setSlowMode(chatId: ChatId, seconds: Int): ScResult<Unit> = apiCall {
        api.updateChat(chatId, UpdateChatRequest(slowModeSeconds = seconds)).toDomainAndCache()
    }

    /* ── Участники ─────────────────────────────────────────────────────── */

    override suspend fun inviteMembers(chatId: ChatId, userIds: List<UserId>): ScResult<Unit> =
        apiCall {
            api.addMembers(chatId, AddMembersRequest(userIds))
            Unit
        }

    override suspend fun changeMemberRole(
        chatId: ChatId,
        userId: UserId,
        role: MemberRole,
    ): ScResult<Unit> = apiCall {
        api.updateMember(chatId, userId, UpdateMemberRequest(role = role.name.lowercase()))
        Unit
    }

    override suspend fun kickMember(chatId: ChatId, userId: UserId): ScResult<Unit> = apiCall {
        api.kickMember(chatId, userId)
        messageDao.deleteMember(chatId, userId)
    }

    override suspend fun restrictMember(
        chatId: ChatId,
        userId: UserId,
        untilTs: Long,
    ): ScResult<Unit> = apiCall {
        api.updateMember(
            chatId,
            userId,
            UpdateMemberRequest(
                role = MemberRole.RESTRICTED.name.lowercase(),
                restrictedUntil = untilTs,
            ),
        )
        Unit
    }

    override suspend fun leaveChat(chatId: ChatId): ScResult<Unit> = apiCall {
        api.leaveChat(chatId)
        // Ушёл — локально чат больше не нужен: сообщения, участники, черновик.
        messageDao.deleteChatMessages(chatId)
        draftDao.delete(chatId)
        chatDao.deleteChat(chatId)
    }

    override suspend fun approveJoinRequest(
        chatId: ChatId,
        userId: UserId,
        approve: Boolean,
    ): ScResult<Unit> = apiCall {
        api.approveJoinRequest(chatId, userId, ApproveRequest(approve))
        Unit
    }

    /* ── Ссылки-приглашения ────────────────────────────────────────────── */

    override suspend fun createInviteLink(
        chatId: ChatId,
        maxUses: Int?,
        expiresAt: Long?,
        requiresApproval: Boolean,
        name: String?,
    ): ScResult<ChatInviteLink> = apiCall {
        api.createInviteLink(
            chatId,
            InviteLinkRequest(
                maxUses = maxUses,
                expiresAt = expiresAt,
                requiresApproval = requiresApproval,
                name = name?.trim()?.ifBlank { null },
            ),
        ).dtoToDomain()
    }

    override suspend fun revokeInviteLink(chatId: ChatId, token: String): ScResult<Unit> = apiCall {
        api.revokeInviteLink(chatId, token)
        Unit
    }

    override suspend fun joinByInvite(token: String): ScResult<Chat> = apiCall {
        api.joinByInvite(token.normalizeInviteToken()).toDomainAndCache()
    }

    /* ── Локальные флаги: оптимистично ─────────────────────────────────── */

    override suspend fun pinChat(chatId: ChatId, pinned: Boolean): ScResult<Unit> = apiCall {
        api.pinChat(chatId, PinChatRequest(pinned))
        Unit
    }

    /**
     * `mutedUntil == null` означает «снять мьют», иначе — глушим до метки.
     * Пишем в Room сразу, чтобы бейджи и звук замолчали до ответа сервера.
     */
    override suspend fun muteChat(chatId: ChatId, mutedUntil: Long?): ScResult<Unit> = apiCall {
        val muted = mutedUntil != null
        chatDao.setMuted(chatId, muted, mutedUntil)
        api.muteChat(chatId, MuteChatRequest(muted = muted, mutedUntil = mutedUntil))
        Unit
    }

    /**
     * Архив — оптимистично: строка должна уйти из списка в тот же кадр,
     * иначе пользователь увидит, как чат «прыгает» обратно на место.
     */
    override suspend fun archiveChat(chatId: ChatId, archived: Boolean): ScResult<Unit> = apiCall {
        chatDao.setArchived(chatId, archived)
        api.archiveChat(chatId, ArchiveChatRequest(archived))
        Unit
    }

    override suspend fun markRead(chatId: ChatId): ScResult<Unit> = apiCall {
        chatDao.markRead(chatId)
        api.markChatRead(chatId)
        Unit
    }

    override suspend fun saveDraft(chatId: ChatId, draft: Draft): ScResult<Unit> = apiCall {
        if (draft.text.isBlank() && draft.pendingAttachments.isEmpty()) {
            draftDao.delete(chatId)
        } else {
            draftDao.upsert(draft.toEntity(chatId))
        }
        // Черновик синхронизируется best-effort: потерять его в офлайне — не ошибка,
        // локальная копия уже сохранена и переживёт перезапуск.
        runCatching { api.saveDraft(chatId, DraftRequest(draft.text, draft.replyTo)) }
    }

    override suspend fun sendTyping(chatId: ChatId, typing: Boolean): ScResult<Unit> = apiCall {
        // Только WebSocket: «печатает…» не имеет смысла через REST и не хранится.
        socket.send(
            SocketOps.MESSAGE_TYPING,
            buildJsonObject {
                put("chat_id", chatId)
                put("typing", typing)
            },
        )
        Unit
    }

    override suspend fun deleteChat(chatId: ChatId, forEveryone: Boolean): ScResult<Unit> =
        apiCall {
            api.deleteChat(chatId, forEveryone)
            messageDao.deleteChatMessages(chatId)
            draftDao.delete(chatId)
            chatDao.deleteChat(chatId)
        }

    /* ── Внутреннее: используется диспетчером WebSocket и синхронизацией ── */

    /** Кэш участников подтягивается лениво — при первом открытии списка. */
    suspend fun refreshMembers(chatId: ChatId) {
        val response = api.members(chatId)
        messageDao.upsertMembers(
            response.members.map { it.dtoToDomain().toEntity(chatId) },
        )
    }

    /** Полный список чатов с сервера. Вызывается синхронизацией при старте. */
    suspend fun syncChatList(archived: Boolean = false) {
        val response = api.chats(archived = archived)
        chatDao.upsertAll(response.chats.map { it.dtoToDomain().toEntity() })
        foldersCache.value = response.folders.map { it.dtoToDomain() }
    }

    /** Применил событие `chat.updated` / `chat.created` из WebSocket. */
    suspend fun applyIncoming(chat: Chat) {
        chatDao.upsert(chat.toEntity(existingUpdatedAt = chatDao.getChat(chat.id)?.updatedAt))
    }

    /** Обновил «печатает…» из WebSocket-события `message.typing`. */
    fun applyTyping(chatId: ChatId, userId: UserId, typing: Boolean) {
        typingCache.value = typingCache.value.toMutableMap().apply {
            val current = get(chatId).orEmpty().toMutableSet()
            if (typing) current += userId else current -= userId
            put(chatId, current)
        }
    }

    private suspend fun com.silverchat.core.network.dto.ChatDto.toDomainAndCache(): Chat =
        dtoToDomain().also { applyIncoming(it) }

    private fun matchesFolder(chat: Chat, folderId: String): Boolean {
        val folder = foldersCache.value.firstOrNull { it.id == folderId } ?: return false
        if (folder.includeTypes.isNotEmpty() && chat.type !in folder.includeTypes) return false
        if (folder.excludeMuted && chat.muted) return false
        if (folder.excludeRead && chat.unreadCount == 0) return false
        return true
    }

    private fun String.normalizeInviteToken(): String =
        removePrefix("https://silver.chat/join/")
            .removePrefix("https://t.me/+")
            .removePrefix("@")
            .trim()

    private companion object {
        /** Сначала непрочитанные, дальше по времени последнего сообщения. */
        val CHATS_ORDER = compareByDescending<Chat> { it.unreadCount > 0 }
            .thenByDescending { it.lastMessage?.sentAt ?: it.createdAt }

        /** Владелец, затем админы, затем по имени. */
        val MEMBERS_ORDER = compareBy<ChatMember> { it.role.ordinal }
            .thenBy { it.user.fullName.lowercase() }
    }
}

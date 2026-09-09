package com.silverchat.feature.chats

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.silverchat.core.common.result.ScResult
import com.silverchat.core.designsystem.navigation.Routes
import com.silverchat.core.domain.repository.ChatRepository
import com.silverchat.core.domain.repository.ProfileRepository
import com.silverchat.core.domain.repository.SharedMediaKind
import com.silverchat.core.model.Chat
import com.silverchat.core.model.ChatId
import com.silverchat.core.model.ChatInviteLink
import com.silverchat.core.model.ChatMember
import com.silverchat.core.model.Message
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Информационная панель чата.
 *
 * Собирает четыре независимых потока (чат, участники, ссылки, общие медиа)
 * в один `StateFlow`. Каждый поток переключается через [flatMapLatest] по
 * изменению chatId или категории медиа, чтобы не держать лишние подписки.
 *
 * Права проверяются на модели ([Chat.canEditInfo], [Chat.canManageMembers]):
 * UI просто не рендерит секцию управления для рядового участника, а сервер
 * дополнительно валидирует каждую операцию.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChatInfoViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val profileRepository: ProfileRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val chatId: String = savedStateHandle[Routes.Args.CHAT_ID] ?: ""
    private val id = ChatId(chatId)

    /** Выбранная категория общих медиа. */
    private val mediaKind = MutableStateFlow(SharedMediaKind.PHOTO)

    /** Состояние модального диалога удаления/выхода. */
    private val confirmAction = MutableStateFlow<ConfirmAction?>(null)

    val uiState: StateFlow<ChatInfoUiState> = combine(
        chatRepository.observeChat(id),
        chatRepository.observeMembers(id),
        mediaKind.flatMapLatest { kind ->
            if (chatId.isEmpty()) flowOf(emptyList()) else profileRepository.observeSharedMedia(id, kind)
        },
    ) { chat, members, media ->
        ChatInfoUiState(
            chat = chat,
            members = members,
            // Ссылки приходят внутри Chat: отдельного потока в репозитории нет,
            // а дублировать запрос значило бы рассинхрон двух источников
            inviteLinks = chat?.inviteLinks?.sortedByDescending { !it.revoked } ?: emptyList(),
            sharedMedia = media,
            mediaCount = media.size,
            selectedMediaKind = mediaKind.value,
            onMediaKindSelected = ::selectMediaKind,
            confirmAction = confirmAction.value,
            // Анимации аватара/баннера уважают системную настройку
            animationsEnabled = true,
        )
    }.combine(confirmAction) { state, action -> state.copy(confirmAction = action) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = ChatInfoUiState(),
        )

    fun selectMediaKind(kind: SharedMediaKind) {
        mediaKind.value = kind
    }

    /* ── Уведомления ──────────────────────────────────────────────────── */

    /**
     * Переключение mute.
     *
     * Отключение — немедленное (`mutedUntil = null`), включение — на 8 часов:
     * «навсегда» в один тап слишком агрессивно, а 8 часов покрывают ночь.
     */
    fun toggleMute() = viewModelScope.launch {
        val current = uiState.value.chat ?: return@launch
        val until = if (current.isMuted) null else System.currentTimeMillis() + MUTE_8_HOURS_MS
        when (chatRepository.muteChat(current.id, until)) {
            is ScResult.Failure -> confirmAction.value = ConfirmAction.Error("Не удалось изменить уведомления")
            else -> Unit
        }
    }

    /* ── Пригласительные ссылки ───────────────────────────────────────── */

    /**
     * Новая ссылка с дефолтами безопасности.
     *
     * `requiresApproval = true` — заявки подтверждает админ: это и есть
     * «защищённые ссылки вместо открытого доступа». Лимит в 50 входов
     * ограничивает ущерб при утечке ссылки.
     */
    fun createInviteLink() = viewModelScope.launch {
        if (chatId.isEmpty()) return@launch
        chatRepository.createInviteLink(
            chatId = id,
            maxUses = DEFAULT_LINK_USES,
            expiresAt = System.currentTimeMillis() + DEFAULT_LINK_TTL_MS,
            requiresApproval = true,
            name = null,
        )
    }

    fun revokeLink(link: ChatInviteLink) = viewModelScope.launch {
        chatRepository.revokeInviteLink(id, link.token)
    }

    /** Копирование ссылки в буфер обмена выполняет слой UI (нет доступа к Clipboard в VM). */
    fun copyLink(link: ChatInviteLink) {
        confirmAction.value = ConfirmAction.CopyLink(link.token)
    }

    /* ── Управление чатом ─────────────────────────────────────────────── */

    fun startEditInfo() {
        confirmAction.value = ConfirmAction.EditInfo
    }

    fun disableSlowMode() = viewModelScope.launch {
        chatRepository.setSlowMode(id, seconds = 0)
    }

    fun requestLeave() {
        confirmAction.value = ConfirmAction.Leave
    }

    fun requestDelete() {
        confirmAction.value = ConfirmAction.Delete
    }

    /** Подтверждение деструктивного действия. */
    fun confirmDestructive() = viewModelScope.launch {
        val action = confirmAction.value ?: return@launch
        confirmAction.value = null
        when (action) {
            ConfirmAction.Leave -> chatRepository.leaveChat(id)
            ConfirmAction.Delete -> chatRepository.deleteChat(id, forEveryone = false)
            else -> return@launch
        }
    }

    fun dismissDialog() {
        confirmAction.value = null
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val DEFAULT_LINK_USES = 50
        const val DEFAULT_LINK_TTL_MS = 7L * 24 * 60 * 60 * 1000

        /** 8 часов: «навсегда» в один тап слишком агрессивно, а ночь покрывает. */
        const val MUTE_8_HOURS_MS = 8 * 60 * 60 * 1000L
    }
}

/** Действие, требующее подтверждения или реакции UI. */
sealed interface ConfirmAction {
    data object Leave : ConfirmAction
    data object Delete : ConfirmAction
    data object EditInfo : ConfirmAction
    data class CopyLink(val token: String) : ConfirmAction
    data class Error(val message: String) : ConfirmAction
}

data class ChatInfoUiState(
    val chat: Chat? = null,
    val members: List<ChatMember> = emptyList(),
    val inviteLinks: List<ChatInviteLink> = emptyList(),
    val sharedMedia: List<Message> = emptyList(),
    val mediaCount: Int = 0,
    val selectedMediaKind: SharedMediaKind = SharedMediaKind.PHOTO,
    val onMediaKindSelected: ((SharedMediaKind) -> Unit)? = null,
    val confirmAction: ConfirmAction? = null,
    val animationsEnabled: Boolean = true,
)

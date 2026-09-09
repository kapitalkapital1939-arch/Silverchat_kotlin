package com.silverchat.feature.profile

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.silverchat.core.common.format.TimeFormatter
import com.silverchat.core.common.result.ScResult
import com.silverchat.core.designsystem.navigation.Routes
import com.silverchat.core.domain.repository.AuthRepository
import com.silverchat.core.domain.repository.ChatRepository
import com.silverchat.core.domain.repository.ProfileRepository
import com.silverchat.core.domain.repository.SharedMediaKind
import com.silverchat.core.domain.usecase.profile.UploadAvatarParams
import com.silverchat.core.domain.usecase.profile.UploadAvatarUseCase
import com.silverchat.core.domain.usecase.profile.UploadBannerParams
import com.silverchat.core.domain.usecase.profile.UploadBannerUseCase
import com.silverchat.core.model.AvatarAnimationType
import com.silverchat.core.model.ChatId
import com.silverchat.core.model.Message
import com.silverchat.core.model.User
import com.silverchat.core.model.UserId
import com.silverchat.core.model.UserPresence
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Карточка пользователя — своя и чужая.
 *
 * Один ViewModel на оба случая: источник данных выбирается по аргументу
 * маршрута ([Routes.Args.USER_ID]). Если аргумента нет — показываем
 * `observeMe()`. Две отдельные ViewModel дублировали бы логику баннера,
 * бейджей и общих медиа.
 *
 * Различие в поведении выражено флагом `isMe`: он определяет набор действий
 * (редактировать против написать/позвонить) и приходит из
 * [BuildUserProfileCardUseCase], а не вычисляется сравнением ID на экране.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val chatRepository: ChatRepository,
    private val authRepository: AuthRepository,
    private val uploadAvatarUseCase: UploadAvatarUseCase,
    private val uploadBannerUseCase: UploadBannerUseCase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /** ID пользователя из маршрута; null — это собственный профиль. */
    private val userIdArg: String? = savedStateHandle[Routes.Args.USER_ID]

    /**
     * ID текущего пользователя.
     *
     * `AuthRepository.currentUserId` — обычный `Flow`, у него нет `.value`,
     * поэтому материализуем в `StateFlow` с `Eagerly`: значение нужно уже при
     * первой сборке состояния, чтобы сразу выставить `isMe`.
     */
    private val currentUserId: StateFlow<String?> = authRepository.currentUserId
        .map { it?.raw }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _events = MutableStateFlow<ProfileEvent?>(null)
    val events: StateFlow<ProfileEvent?> = _events.asStateFlow()

    /** Активная вкладка общих медиа. */
    private val mediaTab = MutableStateFlow(SharedMediaKind.PHOTO)

    /** Идёт ли загрузка аватара/баннера. */
    private val isUploading = MutableStateFlow(false)

    /**
     * Наблюдаемый пользователь.
     *
     * `flatMapLatest` по аргументу не нужен: ID не меняется в течение жизни
     * ViewModel (экран пересоздаётся при смене пользователя), поэтому выбор
     * источника делается один раз при создании потока.
     */
    private val observedUser = userIdArg
        ?.let { profileRepository.observeUser(UserId(it)) }
        ?: profileRepository.observeMe()

    val uiState: StateFlow<ProfileUiState> = combine(
        observedUser,
        currentUserId,
        mediaTab,
        isUploading,
    ) { user, myId, tab, uploading ->
        Draft(user, myId, tab, uploading)
    }
        // Общие медиа — отдельный поток: он зависит от chatId, которого
        // может не быть (свой профиль без диалога с собой)
        .combine(sharedMediaFlow()) { draft, media -> draft to media }
        .map { (draft, media) -> build(draft, media) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = ProfileUiState(),
        )

    /**
     * Общие медиа с этим пользователем.
     *
     * Для собственного профиля диалога «с собой» может не существовать,
     * поэтому при отсутствии chatId возвращается пустой список, а не ошибка.
     */
    private fun sharedMediaFlow() = mediaTab.flatMapLatest { tab ->
        val chatId = sharedChatId.value ?: return@flatMapLatest flowOf(emptyList())
        profileRepository.observeSharedMedia(ChatId(chatId), tab)
    }

    /** ID личного диалога с этим пользователем (для вкладки общих медиа). */
    private val sharedChatId = MutableStateFlow<String?>(null)

    init {
        userIdArg?.let { targetId ->
            viewModelScope.launch {
                // openPersonalChat идемпотентен: возвращает существующий диалог
                // или создаёт новый. Для вкладки общих медиа нужен именно chatId.
                when (val result = chatRepository.openPersonalChat(UserId(targetId))) {
                    is ScResult.Success -> sharedChatId.value = result.data.id.raw
                    is ScResult.Failure -> sharedChatId.value = null
                    ScResult.Loading -> Unit
                }
            }
        }
    }

    private fun build(draft: Draft, media: List<Message>): ProfileUiState {
        val user = draft.user
        return ProfileUiState(
            user = user,
            isMe = user?.id?.raw == draft.myId,
            isPremium = user?.premium?.isActive == true,
            presenceText = user?.presence?.toRu(),
            mediaTab = draft.tab,
            sharedMedia = media,
            isUploading = draft.uploading,
            isLoading = user == null,
            sharedChatId = sharedChatId.value,
        )
    }

    /* ── Вкладки медиа ────────────────────────────────────────────────── */

    fun selectMediaTab(tab: SharedMediaKind) { mediaTab.value = tab }

    /* ── Аватар и баннер ──────────────────────────────────────────────── */

    /**
     * Загрузка аватара.
     *
     * Проверка Premium-перка выполняется внутри [UploadAvatarUseCase]:
     * видео-аватар — платная возможность, и блокировать её должен слой
     * домена, а не экран.
     */
    fun uploadAvatar(localUri: String, animationType: AvatarAnimationType) = viewModelScope.launch {
        isUploading.value = true
        when (val result = uploadAvatarUseCase(UploadAvatarParams(localUri, animationType))) {
            is ScResult.Success -> _events.value = ProfileEvent.AvatarUpdated
            is ScResult.Failure -> _events.value = ProfileEvent.Error(result.error.message)
            ScResult.Loading -> Unit
        }
        isUploading.value = false
    }

    fun uploadBanner(localUri: String, animationType: AvatarAnimationType) = viewModelScope.launch {
        isUploading.value = true
        when (val result = uploadBannerUseCase(UploadBannerParams(localUri, animationType))) {
            is ScResult.Success -> _events.value = ProfileEvent.BannerUpdated
            is ScResult.Failure -> _events.value = ProfileEvent.Error(result.error.message)
            ScResult.Loading -> Unit
        }
        isUploading.value = false
    }

    /* ── Действия с чужим профилем ────────────────────────────────────── */

    fun blockUser() = viewModelScope.launch {
        val id = uiState.value.user?.id ?: return@launch
        when (val result = profileRepository.blockUser(id)) {
            is ScResult.Success -> _events.value = ProfileEvent.Blocked
            is ScResult.Failure -> _events.value = ProfileEvent.Error(result.error.message)
            ScResult.Loading -> Unit
        }
    }

    fun unblockUser() = viewModelScope.launch {
        val id = uiState.value.user?.id ?: return@launch
        when (val result = profileRepository.unblockUser(id)) {
            is ScResult.Success -> _events.value = ProfileEvent.Unblocked
            is ScResult.Failure -> _events.value = ProfileEvent.Error(result.error.message)
            ScResult.Loading -> Unit
        }
    }

    /**
     * Жалоба на пользователя.
     *
     * `reason` — текст из справочника [com.silverchat.core.model.ReportReason],
     * `comment` свободный. Модерация получает оба: категория нужна для
     * фильтрации очереди, комментарий — для решения.
     */
    fun reportUser(reason: String, comment: String?) = viewModelScope.launch {
        val id = uiState.value.user?.id ?: return@launch
        when (val result = profileRepository.reportUser(id, reason, comment?.takeIf { it.isNotBlank() })) {
            is ScResult.Success -> _events.value = ProfileEvent.Reported
            is ScResult.Failure -> _events.value = ProfileEvent.Error(result.error.message)
            ScResult.Loading -> Unit
        }
    }

    fun consumeEvent() { _events.value = null }

    /* ── Вспомогательное ──────────────────────────────────────────────── */

    private data class Draft(
        val user: User?,
        val myId: String?,
        val tab: SharedMediaKind,
        val uploading: Boolean,
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/* =========================================================================
   СОСТОЯНИЕ
   ========================================================================= */

data class ProfileUiState(
    val user: User? = null,
    val isMe: Boolean = false,
    val isPremium: Boolean = false,
    val presenceText: String? = null,
    val mediaTab: SharedMediaKind = SharedMediaKind.PHOTO,
    val sharedMedia: List<Message> = emptyList(),
    val sharedChatId: String? = null,
    val isUploading: Boolean = false,
    val isLoading: Boolean = true,
) {
    val displayName: String get() = user?.fullName ?: "Профиль"
    val handle: String? get() = user?.handle
    val bio: String? get() = user?.bio?.takeIf { it.isNotBlank() }

    /** Аватар анимирован — экран решает, показывать Lottie или статику. */
    val hasAnimatedAvatar: Boolean get() = user?.avatar?.isAnimated == true
    val hasAnimatedBanner: Boolean
        get() = user?.banner?.animationType != AvatarAnimationType.NONE &&
            !user?.banner?.animatedUrl.isNullOrBlank()

    val canCall: Boolean get() = !isMe && sharedChatId != null
    val canMessage: Boolean get() = !isMe
}

sealed interface ProfileEvent {
    data object AvatarUpdated : ProfileEvent
    data object BannerUpdated : ProfileEvent
    data object Blocked : ProfileEvent
    data object Unblocked : ProfileEvent
    data object Reported : ProfileEvent
    data class Error(val message: String) : ProfileEvent
}

/* =========================================================================
   РУССКИЕ ПОДПИСИ
   ========================================================================= */

/**
 * Статус присутствия на русском.
 *
 * Сервер намеренно присылает неточные значения ([UserPresence.Recently],
 * [UserPresence.WithinWeek]) вместо времени, когда пользователь скрыл
 * «был в сети». Подменить их на клиенте нельзя — точность задаёт сервер.
 */
fun UserPresence.toRu(): String = when (this) {
    UserPresence.Online -> "в сети"
    is UserPresence.Offline -> TimeFormatter.lastSeen(lastSeenAt)
    UserPresence.Recently -> "был(а) недавно"
    UserPresence.WithinWeek -> "был(а) на этой неделе"
    UserPresence.WithinMonth -> "был(а) в этом месяце"
    UserPresence.LongAgo -> "был(а) давно"
    is UserPresence.Typing -> "печатает…"
}

val SharedMediaKind.titleRu: String
    get() = when (this) {
        SharedMediaKind.PHOTO -> "Фото"
        SharedMediaKind.VIDEO -> "Видео"
        SharedMediaKind.VOICE -> "Голосовые"
        SharedMediaKind.FILE -> "Файлы"
        SharedMediaKind.LINK -> "Ссылки"
        SharedMediaKind.MUSIC -> "Музыка"
        SharedMediaKind.CIRCLE -> "Кружки"
    }

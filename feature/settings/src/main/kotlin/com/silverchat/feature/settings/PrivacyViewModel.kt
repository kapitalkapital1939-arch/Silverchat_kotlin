package com.silverchat.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.silverchat.core.common.result.ScResult
import com.silverchat.core.domain.repository.ProfileRepository
import com.silverchat.core.model.PrivacySettings
import com.silverchat.core.model.User
import com.silverchat.core.model.UserId
import com.silverchat.core.model.VisibilityRule
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Конфиденциальность и чёрный список.
 *
 * Правила видимости ([VisibilityRule]) применяются сервером при отдаче
 * профиля: клиент лишь редактирует их. Локальная фильтрация ничего не
 * защитила бы — данные всё равно пришли бы по сети.
 *
 * Чёрный список живёт здесь, а не в профиле: это настройка приватности,
 * и пользователь ищет его именно в этом разделе.
 */
@HiltViewModel
class PrivacyViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    private val _events = MutableStateFlow<PrivacyEvent?>(null)
    val events: StateFlow<PrivacyEvent?> = _events.asStateFlow()

    /** Показывать ли раздел чёрного списка. */
    private val showBlocked = MutableStateFlow(false)

    val uiState: StateFlow<PrivacyUiState> = combine(
        profileRepository.observePrivacy(),
        profileRepository.observeBlockedUsers(),
        showBlocked,
    ) { privacy, blocked, blockedVisible ->
        PrivacyUiState(
            privacy = privacy,
            blockedUsers = blocked,
            showBlocked = blockedVisible,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = PrivacyUiState(),
    )

    fun toggleBlockedSection() {
        showBlocked.update { !it }
    }

    /**
     * Обновление правила видимости.
     *
     * Отправляем весь объект [PrivacySettings], а не отдельное поле:
     * репозиторий принимает его целиком, и частичного API на сервере нет.
     */
    fun setRule(field: PrivacyField, rule: VisibilityRule) = viewModelScope.launch {
        val current = uiState.value.privacy
        val updated = current.withRule(field, rule)
        when (val result = profileRepository.updatePrivacy(updated)) {
            is ScResult.Success -> Unit
            is ScResult.Failure -> _events.value = PrivacyEvent.Error(result.error.message)
            ScResult.Loading -> Unit
        }
    }

    /** Отдельный метод для «был в сети»: у него есть выделенный эндпоинт. */
    fun setLastSeenRule(rule: VisibilityRule) = viewModelScope.launch {
        when (val result = profileRepository.setLastSeenVisibility(rule)) {
            is ScResult.Success -> Unit
            is ScResult.Failure -> _events.value = PrivacyEvent.Error(result.error.message)
            ScResult.Loading -> Unit
        }
    }

    fun setReadReceipts(enabled: Boolean) = viewModelScope.launch {
        val current = uiState.value.privacy
        when (val result = profileRepository.updatePrivacy(
            current.copy(readReceiptsEnabled = enabled),
        )) {
            is ScResult.Success -> Unit
            is ScResult.Failure -> _events.value = PrivacyEvent.Error(result.error.message)
            ScResult.Loading -> Unit
        }
    }

    fun unblock(user: User) = viewModelScope.launch {
        when (val result = profileRepository.unblockUser(UserId(user.id.raw))) {
            is ScResult.Success -> _events.value = PrivacyEvent.Unblocked(user.fullName)
            is ScResult.Failure -> _events.value = PrivacyEvent.Error(result.error.message)
            ScResult.Loading -> Unit
        }
    }

    fun block(user: User) = viewModelScope.launch {
        when (val result = profileRepository.blockUser(UserId(user.id.raw))) {
            is ScResult.Success -> _events.value = PrivacyEvent.Blocked(user.fullName)
            is ScResult.Failure -> _events.value = PrivacyEvent.Error(result.error.message)
            ScResult.Loading -> Unit
        }
    }

    fun consumeEvent() { _events.value = null }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/** Редактируемые правила приватности. */
enum class PrivacyField(val titleRu: String, val subtitleRu: String) {
    LAST_SEEN("Последняя активность", "Кто видит время вашего последнего визита"),
    PHONE("Номер телефона", "Кто видит ваш номер"),
    AVATAR("Фотография профиля", "Кто видит ваш аватар"),
    CALLS("Звонки", "Кто может звонить вам"),
    STORIES("Сторис", "Кто может смотреть ваши сторис"),
    ADD_TO_GROUPS("Добавление в группы", "Кто может добавлять вас в группы"),
}

val VisibilityRule.titleRu: String
    get() = when (this) {
        VisibilityRule.EVERYBODY -> "Все"
        VisibilityRule.CONTACTS -> "Мои контакты"
        VisibilityRule.NOBODY -> "Никто"
    }

/**
 * Замена правила в объекте настроек.
 *
 * `when` по [PrivacyField] вместо рефлексии: поля перечислены явно,
 * и компилятор напомнит о новом значении enum.
 */
private fun PrivacySettings.withRule(field: PrivacyField, rule: VisibilityRule): PrivacySettings =
    when (field) {
        PrivacyField.LAST_SEEN -> copy(lastSeenVisibility = rule)
        PrivacyField.PHONE -> copy(phoneVisibility = rule)
        PrivacyField.AVATAR -> copy(avatarVisibility = rule)
        PrivacyField.CALLS -> copy(callsAllowed = rule)
        PrivacyField.STORIES -> copy(storiesAllowed = rule)
        PrivacyField.ADD_TO_GROUPS -> copy(addToGroups = rule)
    }

data class PrivacyUiState(
    val privacy: PrivacySettings = PrivacySettings(),
    val blockedUsers: List<User> = emptyList(),
    val showBlocked: Boolean = false,
) {
    /** Текущее правило поля — для подсветки выбранного значения. */
    fun ruleOf(field: PrivacyField): VisibilityRule = when (field) {
        PrivacyField.LAST_SEEN -> privacy.lastSeenVisibility
        PrivacyField.PHONE -> privacy.phoneVisibility
        PrivacyField.AVATAR -> privacy.avatarVisibility
        PrivacyField.CALLS -> privacy.callsAllowed
        PrivacyField.STORIES -> privacy.storiesAllowed
        PrivacyField.ADD_TO_GROUPS -> privacy.addToGroups
    }
}

sealed interface PrivacyEvent {
    data class Blocked(val name: String) : PrivacyEvent
    data class Unblocked(val name: String) : PrivacyEvent
    data class Error(val message: String) : PrivacyEvent
}

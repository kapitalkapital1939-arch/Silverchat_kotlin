package com.silverchat.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.silverchat.core.common.format.UsernameFormatter
import com.silverchat.core.common.result.ScResult
import com.silverchat.core.domain.repository.ProfileRepository
import com.silverchat.core.domain.usecase.profile.UpdateProfileBlocksParams
import com.silverchat.core.domain.usecase.profile.UpdateProfileBlocksUseCase
import com.silverchat.core.domain.usecase.profile.UploadAvatarParams
import com.silverchat.core.domain.usecase.profile.UploadAvatarUseCase
import com.silverchat.core.domain.usecase.profile.UploadBannerParams
import com.silverchat.core.domain.usecase.profile.UploadBannerUseCase
import com.silverchat.core.model.AvatarAnimationType
import com.silverchat.core.model.DaySchedule
import com.silverchat.core.model.LocationInfo
import com.silverchat.core.model.WorkingHours
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Редактирование профиля: имя, bio, username, аватар, баннер,
 * местоположение и часы работы.
 *
 * Форма хранится локально и отправляется только по «Сохранить». Авто-сохранение
 * на каждый символ породило бы запрос к серверу на каждую клавишу, а username
 * дополнительно проверяется на уникальность — это дорогая операция.
 *
 * `username` валидируется через [UsernameFormatter] до отправки: правила
 * (латиница, запрет двойного подчёркивания, зарезервированные имена) едины
 * для регистрации, смены ника и маркета, поэтому вынесены в `core:common`.
 */
@HiltViewModel
class EditProfileViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val updateBlocks: UpdateProfileBlocksUseCase,
    private val uploadAvatarUseCase: UploadAvatarUseCase,
    private val uploadBannerUseCase: UploadBannerUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditProfileUiState())
    val uiState: StateFlow<EditProfileUiState> = _uiState.asStateFlow()

    private val _events = MutableStateFlow<EditProfileEvent?>(null)
    val events: StateFlow<EditProfileEvent?> = _events.asStateFlow()

    /**
     * Исходные значения профиля.
     *
     * Заполняются один раз при загрузке, чтобы `hasChanges` сравнивал форму
     * с серверным состоянием, а не с пустой формой.
     */
    private var loaded = false

    val original: StateFlow<EditProfileUiState> = profileRepository.observeMe()
        .map { user ->
            EditProfileUiState(
                firstName = user?.firstName.orEmpty(),
                lastName = user?.lastName.orEmpty(),
                username = user?.username.orEmpty(),
                bio = user?.bio.orEmpty(),
                location = user?.location,
                workingHours = user?.workingHours,
                isPremium = user?.premium?.isActive == true,
            )
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, EditProfileUiState())

    init {
        viewModelScope.launch {
            original.collect { snapshot ->
                // Копируем серверные значения в форму только до первых правок:
                // иначе входящее обновление затрёт несохранённый ввод
                if (!loaded && snapshot.firstName.isNotEmpty()) {
                    loaded = true
                    _uiState.value = snapshot
                }
            }
        }
    }

    /* ── Поля формы ───────────────────────────────────────────────────── */

    fun onFirstNameChanged(value: String) = _uiState.update {
        it.copy(firstName = value.take(MAX_NAME_LENGTH), errorText = null)
    }

    fun onLastNameChanged(value: String) = _uiState.update {
        it.copy(lastName = value.take(MAX_NAME_LENGTH))
    }

    fun onBioChanged(value: String) = _uiState.update {
        it.copy(bio = value.take(MAX_BIO_LENGTH))
    }

    /**
     * Username нормализуется сразу: нижний регистр и отсутствие «@».
     *
     * Без нормализации пользователь мог бы ввести «@Alina» и получить
     * валидацию по строке с «@», которая не проходит `ALLOWED_PATTERN`.
     */
    fun onUsernameChanged(value: String) = _uiState.update {
        it.copy(
            username = UsernameFormatter.strip(value)?.take(MAX_NAME_LENGTH).orEmpty(),
            errorText = null,
        )
    }

    /* ── Местоположение ───────────────────────────────────────────────── */

    /** Создание пустого блока местоположения для заполнения. */
    fun addLocation() = _uiState.update { state ->
        if (state.location != null) state else state.copy(location = EMPTY_LOCATION)
    }

    fun onLocationTitleChanged(value: String) = _uiState.update { state ->
        val current = state.location ?: EMPTY_LOCATION
        state.copy(location = current.copy(title = value.takeIf { it.isNotBlank() }))
    }

    fun onLocationAddressChanged(value: String) = _uiState.update { state ->
        val current = state.location ?: EMPTY_LOCATION
        state.copy(location = current.copy(address = value.takeIf { it.isNotBlank() }))
    }

    fun onLocationCoordinatesChanged(latitude: Double?, longitude: Double?) = _uiState.update { state ->
        val current = state.location ?: EMPTY_LOCATION
        state.copy(
            location = current.copy(
                latitude = latitude ?: current.latitude,
                longitude = longitude ?: current.longitude,
            ),
        )
    }

    fun toggleLocationVisible() = _uiState.update { state ->
        val current = state.location ?: return@update state
        state.copy(location = current.copy(visible = !current.visible))
    }

    /** Полное удаление блока местоположения. */
    fun clearLocation() = _uiState.update { it.copy(location = null) }

    /* ── Часы работы ──────────────────────────────────────────────────── */

    /**
     * Создание блока часов работы с расписанием по умолчанию.
     *
     * Заполняем сразу все семь дней: пустой `schedule` сервер принимает,
     * но экран показал бы «расписание не заполнено» и потребовал семь
     * отдельных действий вместо одного.
     */
    fun addWorkingHours() = _uiState.update { state ->
        if (state.workingHours != null) return@update state
        val schedule = (1..DAYS_IN_WEEK).map { day ->
            DaySchedule(
                dayOfWeek = day,
                openMinute = DEFAULT_OPEN_MINUTE,
                closeMinute = DEFAULT_CLOSE_MINUTE,
                // Выходные закрыты по умолчанию — чаще всего это верно
                closed = day > LAST_WORKDAY,
            )
        }
        state.copy(workingHours = EMPTY_HOURS.copy(schedule = schedule))
    }

    fun toggleAlwaysOpen() = _uiState.update { state ->
        val current = state.workingHours ?: EMPTY_HOURS
        state.copy(workingHours = current.copy(alwaysOpen = !current.alwaysOpen))
    }

    fun toggleHoursVisible() = _uiState.update { state ->
        val current = state.workingHours ?: return@update state
        state.copy(workingHours = current.copy(visible = !current.visible))
    }

    /**
     * Правка расписания конкретного дня.
     *
     * @param dayOfWeek ISO-8601: 1 = понедельник … 7 = воскресенье.
     */
    fun updateDay(dayOfWeek: Int, openMinute: Int, closeMinute: Int, closed: Boolean) =
        _uiState.update { state ->
            val current = state.workingHours ?: EMPTY_HOURS
            val schedule = current.schedule.toMutableList()
            val index = schedule.indexOfFirst { it.dayOfWeek == dayOfWeek }
            val entry = DaySchedule(dayOfWeek, openMinute, closeMinute, closed)
            if (index >= 0) schedule[index] = entry else schedule.add(entry)
            // Сортировка по дню: сервер принимает любой порядок, но UI показывает
            // неделю по порядку, и пересобирать список на каждом экране избыточно
            state.copy(workingHours = current.copy(schedule = schedule.sortedBy { it.dayOfWeek }))
        }

    fun clearWorkingHours() = _uiState.update { it.copy(workingHours = null) }

    /* ── Медиа ────────────────────────────────────────────────────────── */

    fun uploadAvatar(localUri: String, animationType: AvatarAnimationType) = viewModelScope.launch {
        _uiState.update { it.copy(isUploadingAvatar = true) }
        when (val result = uploadAvatarUseCase(UploadAvatarParams(localUri, animationType))) {
            is ScResult.Success -> _events.value = EditProfileEvent.AvatarUpdated
            is ScResult.Failure -> _events.value = EditProfileEvent.Error(result.error.message)
            ScResult.Loading -> Unit
        }
        _uiState.update { it.copy(isUploadingAvatar = false) }
    }

    fun uploadBanner(localUri: String, animationType: AvatarAnimationType) = viewModelScope.launch {
        _uiState.update { it.copy(isUploadingBanner = true) }
        when (val result = uploadBannerUseCase(UploadBannerParams(localUri, animationType))) {
            is ScResult.Success -> _events.value = EditProfileEvent.BannerUpdated
            is ScResult.Failure -> _events.value = EditProfileEvent.Error(result.error.message)
            ScResult.Loading -> Unit
        }
        _uiState.update { it.copy(isUploadingBanner = false) }
    }

    /* ── Сохранение ───────────────────────────────────────────────────── */

    /**
     * Сохранение профиля.
     *
     * Каждый блок отправляется отдельным запросом: у репозитория нет
     * единого «PATCH профиля», а `UpdateProfileBlocksUseCase` валидирует
     * координаты и интервалы времени до отправки. Последовательные вызовы
     * означают, что ошибка в одном блоке не откатит уже сохранённый другой —
     * поэтому при сбое сообщаем, какой именно блок не сохранился.
     */
    fun save() = viewModelScope.launch {
        val state = _uiState.value
        if (state.isSaving) return@launch

        if (state.firstName.isBlank()) {
            _uiState.update { it.copy(errorText = "Имя не может быть пустым") }
            return@launch
        }

        // Username: пустая строка означает «убрать ник», иначе валидируем
        val usernameValidation = if (state.username.isBlank()) {
            null
        } else {
            UsernameFormatter.validate(state.username)
        }
        if (usernameValidation != null && !usernameValidation.isValid) {
            _uiState.update { it.copy(errorText = usernameValidation.reasonOrNull()) }
            return@launch
        }

        _uiState.update { it.copy(isSaving = true, errorText = null) }

        // 1. Имя
        when (val result = profileRepository.updateName(
            firstName = state.firstName.trim(),
            lastName = state.lastName.trim().takeIf { it.isNotEmpty() },
        )) {
            is ScResult.Success -> Unit
            is ScResult.Failure -> return@launch fail("Имя", result.error.message)
            ScResult.Loading -> Unit
        }

        // 2. Bio
        when (val result = profileRepository.updateBio(state.bio.trim())) {
            is ScResult.Success -> Unit
            is ScResult.Failure -> return@launch fail("Описание", result.error.message)
            ScResult.Loading -> Unit
        }

        // 3. Username — только если изменился: проверка уникальности дорогая
        val targetUsername = state.username.trim().takeIf { it.isNotEmpty() }
        if (targetUsername != original.value.username.trim().takeIf { it.isNotEmpty() }) {
            when (val result = profileRepository.updateUsername(targetUsername)) {
                is ScResult.Success -> Unit
                is ScResult.Failure -> return@launch fail("Юзернейм", result.error.message)
                ScResult.Loading -> Unit
            }
        }

        // 4. Местоположение и часы работы — один use-case с валидацией
        when (val result = updateBlocks(
            UpdateProfileBlocksParams(
                // Передаём блок как есть: `visible` — флаг показа, а не признак
                // заполненности. Отбрасывать невидимый блок значило бы терять
                // сохранённые координаты при каждом переключении видимости.
                location = state.location,
                workingHours = state.workingHours,
            ),
        )) {
            is ScResult.Success -> Unit
            is ScResult.Failure -> return@launch fail("Блоки профиля", result.error.message)
            ScResult.Loading -> Unit
        }

        loaded = false
        _uiState.update { it.copy(isSaving = false) }
        _events.value = EditProfileEvent.Saved
    }

    private suspend fun fail(block: String, message: String) {
        _uiState.update {
            it.copy(isSaving = false, errorText = "Не удалось сохранить «$block»: $message")
        }
    }

    fun dismissError() = _uiState.update { it.copy(errorText = null) }

    fun consumeEvent() { _events.value = null }

    private companion object {
        const val MAX_NAME_LENGTH = 64
        const val MAX_BIO_LENGTH = 280

        val EMPTY_LOCATION = LocationInfo(latitude = 0.0, longitude = 0.0)
        val EMPTY_HOURS = WorkingHours()

        /** Рабочий день по умолчанию: 09:00–18:00 в минутах от полуночи. */
        const val DEFAULT_OPEN_MINUTE = 9 * 60
        const val DEFAULT_CLOSE_MINUTE = 18 * 60
        const val DAYS_IN_WEEK = 7

        /** Пятница — последний рабочий день по умолчанию. */
        const val LAST_WORKDAY = 5
    }
}

/** Причина отказа валидации username: поле `reason` есть не у всех веток. */
private fun com.silverchat.core.common.format.UsernameValidation.reasonOrNull(): String? =
    when (this) {
        is com.silverchat.core.common.format.UsernameValidation.Invalid -> reason
        is com.silverchat.core.common.format.UsernameValidation.Reserved -> reason
        com.silverchat.core.common.format.UsernameValidation.Valid -> null
    }

data class EditProfileUiState(
    val firstName: String = "",
    val lastName: String = "",
    val username: String = "",
    val bio: String = "",
    val location: LocationInfo? = null,
    val workingHours: WorkingHours? = null,
    /**
     * Наличие Premium.
     *
     * Нужно редакторам медиа: видео-аватар и анимированный баннер — платные
     * перки, и заблокированный тип должен оставаться видимым, но неактивным.
     */
    val isPremium: Boolean = false,
    val isSaving: Boolean = false,
    val isUploadingAvatar: Boolean = false,
    val isUploadingBanner: Boolean = false,
    val errorText: String? = null,
) {
    val isBusy: Boolean get() = isSaving || isUploadingAvatar || isUploadingBanner

    val handle: String? get() = username.takeIf { it.isNotBlank() }?.let { "@$it" }

    val bioLeft: Int get() = (MAX_BIO_LENGTH - bio.length).coerceAtLeast(0)

    val hasLocation: Boolean get() = location != null
    val hasWorkingHours: Boolean get() = workingHours != null

    private companion object {
        const val MAX_BIO_LENGTH = 280
    }
}

sealed interface EditProfileEvent {
    data object Saved : EditProfileEvent
    data object AvatarUpdated : EditProfileEvent
    data object BannerUpdated : EditProfileEvent
    data class Error(val message: String) : EditProfileEvent
}

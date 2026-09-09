package com.silverchat.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.silverchat.core.common.result.ScResult
import com.silverchat.core.domain.repository.AccentColor
import com.silverchat.core.domain.repository.AdminAccess
import com.silverchat.core.domain.repository.AdminRepository
import com.silverchat.core.domain.repository.AppLanguage
import com.silverchat.core.domain.repository.AppTheme
import com.silverchat.core.domain.repository.AuthRepository
import com.silverchat.core.domain.repository.AutoPlayPolicy
import com.silverchat.core.domain.repository.BubbleStyle
import com.silverchat.core.domain.repository.BuildInfo
import com.silverchat.core.domain.repository.NotificationSettings
import com.silverchat.core.domain.repository.ProfileRepository
import com.silverchat.core.domain.repository.SettingsRepository
import com.silverchat.core.domain.repository.ThemeMode
import com.silverchat.core.domain.repository.ThemePalette
import com.silverchat.core.domain.repository.WalletRepository
import com.silverchat.core.domain.repository.Wallpaper
import com.silverchat.core.domain.repository.WallpaperKind
import com.silverchat.core.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Настройки приложения.
 *
 * Все значения берутся из [SettingsRepository] (DataStore), а не из локального
 * состояния: настройки применяются глобально и переживают пересоздание экрана.
 * Запись идёт через репозиторий, а наблюдаемый поток сам возвращает новое
 * значение — отдельного «pending state» не нужно.
 *
 * Вход в админ-панель показывается только при наличии прав
 * ([AdminRepository.observeAccess]). Это **не** защита: сервер проверяет
 * права на каждый запрос через middleware `AdminGuard`. Клиентская проверка
 * лишь убирает из интерфейса ссылку, которая всё равно закончилась бы 403.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val profileRepository: ProfileRepository,
    private val authRepository: AuthRepository,
    private val walletRepository: WalletRepository,
    private val adminRepository: AdminRepository,
) : ViewModel() {

    private val _events = MutableStateFlow<SettingsEvent?>(null)
    val events: StateFlow<SettingsEvent?> = _events.asStateFlow()

    /** Локальный флаг выхода: блокирует экран до завершения запроса. */
    private val isLoggingOut = MutableStateFlow(false)

    /* ── Наблюдаемые источники ────────────────────────────────────────── */

    /**
     * Материализованные `StateFlow` нужны там, где новое значение пишется
     * на базе текущего (обои, уведомления). Обычный `Flow` не имеет `.value`,
     * поэтому каждый такой поток останавливается в `StateFlow` на `viewModelScope`.
     */
    private val themeFlow = settingsRepository.observeTheme()
        .share(AppTheme())

    private val wallpaperFlow = settingsRepository.observeWallpaper()
        .share(Wallpaper())

    private val notificationsFlow = settingsRepository.observeNotifications()
        .share(NotificationSettings())

    val uiState: StateFlow<SettingsUiState> = combine(
        profileRepository.observeMe(),
        themeFlow,
        notificationsFlow,
        settingsRepository.observeLanguage(),
        combine(
            settingsRepository.observeFontSizeScale(),
            settingsRepository.observeAnimationsEnabled(),
            settingsRepository.observeChatBubbleStyle(),
            settingsRepository.observeAutoPlayMedia(),
            settingsRepository.observeDataSaver(),
        ) { fontScale, animations, bubbles, autoPlay, saver ->
            Appearance(fontScale, animations, bubbles, autoPlay, saver)
        },
    ) { me, theme, notifications, language, appearance ->
        Core(me, theme, notifications, language, appearance.toPublic())
    }
        // Второй этап: потоки, не вошедшие в лимит `combine` (пять)
        .combine(
            combine(
                settingsRepository.observeAppLockEnabled(),
                adminRepository.observeAccess(),
                walletRepository.observePremiumStatus().map { it.isActive },
                isLoggingOut,
            ) { lock, access, premium, loggingOut ->
                Flags(lock, access, premium, loggingOut)
            },
        ) { core, flags ->
            SettingsUiState(
                user = core.user,
                theme = core.theme,
                notifications = core.notifications,
                language = core.language,
                appearance = core.appearance,
                wallpaper = wallpaperFlow.value,
                appLockEnabled = flags.appLock,
                adminAccess = flags.access,
                isPremium = flags.premium,
                isLoggingOut = flags.loggingOut,
            )
        }
        .combine(wallpaperFlow) { state, wallpaper -> state.copy(wallpaper = wallpaper) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = SettingsUiState(),
        )

    val buildInfo: StateFlow<BuildInfo?> = settingsRepository.observeBuildInfo()
        .map<BuildInfo, BuildInfo?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /* ── Тема ─────────────────────────────────────────────────────────── */

    fun setThemeMode(mode: ThemeMode) = apply { settingsRepository.setThemeMode(mode) }

    fun setThemePalette(palette: ThemePalette) = apply { settingsRepository.setThemePalette(palette) }

    fun setAccent(accent: AccentColor) = apply { settingsRepository.setAccent(accent) }

    fun setDynamicColor(enabled: Boolean) = apply { settingsRepository.setDynamicColor(enabled) }

    fun setWallpaperKind(kind: WallpaperKind) = updateWallpaper { it.copy(kind = kind) }

    fun setWallpaperBlur(enabled: Boolean) = updateWallpaper { it.copy(blurEnabled = enabled) }

    /** @param amount затемнение 0..0.8; большие значения делают чат нечитаемым. */
    fun setWallpaperDim(amount: Float) =
        updateWallpaper { it.copy(dimAmount = amount.coerceIn(0f, MAX_DIM)) }

    private fun updateWallpaper(transform: (Wallpaper) -> Wallpaper) = apply {
        settingsRepository.setWallpaper(transform(wallpaperFlow.value))
    }

    /* ── Внешний вид ──────────────────────────────────────────────────── */

    /**
     * Масштаб текста.
     *
     * Значение ограничивается диапазоном: при scale < 0.85 подписи
     * перестают читаться, при > 1.6 строки не помещаются в карточки.
     */
    fun setFontSizeScale(scale: Float) = apply {
        settingsRepository.setFontSizeScale(scale.coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE))
    }

    fun setAnimationsEnabled(enabled: Boolean) = apply {
        settingsRepository.setAnimationsEnabled(enabled)
    }

    fun setChatBubbleStyle(style: BubbleStyle) = apply { settingsRepository.setChatBubbleStyle(style) }

    /* ── Уведомления ──────────────────────────────────────────────────── */

    /**
     * Обновление уведомлений.
     *
     * Принимает трансформацию, а не готовый объект: экраны переключают по
     * одному полю, и передавать весь `NotificationSettings` обратно значило
     * бы хранить его копию в слое UI.
     */
    fun updateNotifications(transform: (NotificationSettings) -> NotificationSettings) = apply {
        settingsRepository.setNotifications(transform(notificationsFlow.value))
    }

    /* ── Язык, данные, замок ──────────────────────────────────────────── */

    fun setLanguage(language: AppLanguage) = apply { settingsRepository.setLanguage(language) }

    fun setAutoPlayMedia(policy: AutoPlayPolicy) = apply { settingsRepository.setAutoPlayMedia(policy) }

    fun setDataSaver(enabled: Boolean) = apply { settingsRepository.setDataSaver(enabled) }

    /**
     * Замок приложения (биометрия / PIN).
     *
     * Сам вызов биометрии выполняется в `:app`: ViewModel не имеет доступа
     * к `FragmentActivity`, а `BiometricPrompt` требует его. Здесь хранится
     * только persisted-флаг.
     */
    fun setAppLockEnabled(enabled: Boolean) = apply { settingsRepository.setAppLockEnabled(enabled) }

    /* ── Аккаунт ──────────────────────────────────────────────────────── */

    fun logout() = viewModelScope.launch {
        if (isLoggingOut.value) return@launch
        isLoggingOut.value = true
        when (val result = authRepository.logout()) {
            is ScResult.Success -> _events.value = SettingsEvent.LoggedOut
            is ScResult.Failure -> {
                _events.value = SettingsEvent.Error(result.error.message)
                // Разблокируем экран: пользователь остался в сессии
                isLoggingOut.value = false
            }

            ScResult.Loading -> Unit
        }
    }

    fun consumeEvent() { _events.value = null }

    /* ── Вспомогательное ──────────────────────────────────────────────── */

    /** Единая обработка «записать настройку и сообщить об ошибке». */
    private fun apply(block: suspend () -> ScResult<Unit>) = viewModelScope.launch {
        when (val result = block()) {
            is ScResult.Success -> Unit
            is ScResult.Failure -> _events.value = SettingsEvent.Error(result.error.message)
            ScResult.Loading -> Unit
        }
    }

    /** Останавливает поток в `StateFlow` на `viewModelScope` (отменяется с ViewModel). */
    private fun <T> kotlinx.coroutines.flow.Flow<T>.share(initial: T): StateFlow<T> = stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = initial,
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L

        /** Диапазон масштаба текста. Вне его вёрстка ломается. */
        const val MIN_FONT_SCALE = 0.85f
        const val MAX_FONT_SCALE = 1.6f

        /** Максимальное затемнение обоев. */
        const val MAX_DIM = 0.8f
    }
}

/* =========================================================================
   СОСТОЯНИЕ
   ========================================================================= */

data class SettingsUiState(
    val user: User? = null,
    val theme: AppTheme = AppTheme(),
    val notifications: NotificationSettings = NotificationSettings(),
    val language: AppLanguage = AppLanguage.RU,
    val appearance: AppearancePublic = AppearancePublic(),
    val wallpaper: Wallpaper = Wallpaper(),
    val appLockEnabled: Boolean = false,
    val adminAccess: AdminAccess = AdminAccess.DENIED,
    val isPremium: Boolean = false,
    val isLoggingOut: Boolean = false,
) {
    val displayName: String get() = user?.fullName ?: "SilverChat"
    val handle: String? get() = user?.handle

    /** Показывать ли вход в админ-панель `@silver`. */
    val showAdminEntry: Boolean get() = adminAccess.allowed
}

data class AppearancePublic(
    val fontSizeScale: Float = 1f,
    val animationsEnabled: Boolean = true,
    val bubbleStyle: BubbleStyle = BubbleStyle.GLASS,
    val autoPlayMedia: AutoPlayPolicy = AutoPlayPolicy.WIFI_ONLY,
    val dataSaver: Boolean = false,
)

/** Первая группа `combine`: пользователь, тема, уведомления, язык, внешний вид. */
private data class Core(
    val user: User?,
    val theme: AppTheme,
    val notifications: NotificationSettings,
    val language: AppLanguage,
    val appearance: AppearancePublic,
)

/** Вторая группа: потоки, не вошедшие в лимит пяти. */
private data class Flags(
    val appLock: Boolean,
    val access: AdminAccess,
    val premium: Boolean,
    val loggingOut: Boolean,
)

/** Внутренняя группировка параметров внешнего вида. */
private data class Appearance(
    val fontSizeScale: Float,
    val animationsEnabled: Boolean,
    val bubbleStyle: BubbleStyle,
    val autoPlayMedia: AutoPlayPolicy,
    val dataSaver: Boolean,
) {
    fun toPublic() = AppearancePublic(
        fontSizeScale = fontSizeScale,
        animationsEnabled = animationsEnabled,
        bubbleStyle = bubbleStyle,
        autoPlayMedia = autoPlayMedia,
        dataSaver = dataSaver,
    )
}

sealed interface SettingsEvent {
    data object LoggedOut : SettingsEvent
    data class Error(val message: String) : SettingsEvent
}

/* =========================================================================
   РУССКИЕ ПОДПИСИ
   ========================================================================= */

val ThemeMode.titleRu: String
    get() = when (this) {
        ThemeMode.LIGHT -> "Светлая"
        ThemeMode.DARK -> "Тёмная"
        ThemeMode.SYSTEM -> "Системная"
    }

val AutoPlayPolicy.titleRu: String
    get() = when (this) {
        AutoPlayPolicy.NEVER -> "Никогда"
        AutoPlayPolicy.WIFI_ONLY -> "Только по Wi-Fi"
        AutoPlayPolicy.ALWAYS -> "Всегда"
    }

val BubbleStyle.titleRu: String
    get() = when (this) {
        BubbleStyle.CLASSIC -> "Классические"
        BubbleStyle.COMPACT -> "Компактные"
        BubbleStyle.GLASS -> "Стекло"
    }

val WallpaperKind.titleRu: String
    get() = when (this) {
        WallpaperKind.DEFAULT_PATTERN -> "Узор по умолчанию"
        WallpaperKind.GRADIENT -> "Градиент"
        WallpaperKind.SOLID -> "Однотонные"
        WallpaperKind.PHOTO -> "Фотография"
        WallpaperKind.PREMIUM_ANIMATED -> "Анимированные (Premium)"
    }

/** Масштаб текста в процентах для подписи («115 %»). */
fun fontScaleLabel(scale: Float): String = "${(scale * 100).toInt()} %"

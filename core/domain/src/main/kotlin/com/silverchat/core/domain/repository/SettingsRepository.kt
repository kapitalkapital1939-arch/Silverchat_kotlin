package com.silverchat.core.domain.repository

import com.silverchat.core.common.result.ScResult
import kotlinx.coroutines.flow.Flow

/**
 * Настройки приложения: темы оформления, акцент, размер текста, обои,
 * уведомления, язык, автовоспроизведение медиа.
 *
 * Хранение — DataStore Preferences (НЕ EncryptedSharedPreferences:
 * здесь нет секретов, а DataStore даёт flow-наблюдение и потокобезопасность).
 */
interface SettingsRepository {

    /* ── Тема ───────────────────────────────────────────────────────────── */

    fun observeTheme(): Flow<AppTheme>

    suspend fun setThemeMode(mode: ThemeMode): ScResult<Unit>

    suspend fun setThemePalette(palette: ThemePalette): ScResult<Unit>

    suspend fun setAccent(accent: AccentColor): ScResult<Unit>

    suspend fun setDynamicColor(enabled: Boolean): ScResult<Unit>

    /** Кастомная тема — расширенная кастомизация из Premium. */
    suspend fun setCustomTheme(theme: CustomTheme): ScResult<Unit>

    fun observeWallpaper(): Flow<Wallpaper>

    suspend fun setWallpaper(wallpaper: Wallpaper): ScResult<Unit>

    /* ── Внешний вид ────────────────────────────────────────────────────── */

    fun observeFontSizeScale(): Flow<Float>

    suspend fun setFontSizeScale(scale: Float): ScResult<Unit>

    fun observeAnimationsEnabled(): Flow<Boolean>

    suspend fun setAnimationsEnabled(enabled: Boolean): ScResult<Unit>

    fun observeChatBubbleStyle(): Flow<BubbleStyle>

    suspend fun setChatBubbleStyle(style: BubbleStyle): ScResult<Unit>

    /* ── Уведомления и звук ─────────────────────────────────────────────── */

    fun observeNotifications(): Flow<NotificationSettings>

    suspend fun setNotifications(settings: NotificationSettings): ScResult<Unit>

    /* ── Язык и регион ──────────────────────────────────────────────────── */

    fun observeLanguage(): Flow<AppLanguage>

    suspend fun setLanguage(language: AppLanguage): ScResult<Unit>

    /* ── Данные и хранилище ─────────────────────────────────────────────── */

    fun observeAutoPlayMedia(): Flow<AutoPlayPolicy>

    suspend fun setAutoPlayMedia(policy: AutoPlayPolicy): ScResult<Unit>

    fun observeDataSaver(): Flow<Boolean>

    suspend fun setDataSaver(enabled: Boolean): ScResult<Unit>

    /* ── Прочее ─────────────────────────────────────────────────────────── */

    fun observeAppLockEnabled(): Flow<Boolean>

    suspend fun setAppLockEnabled(enabled: Boolean): ScResult<Unit>

    fun observeBuildInfo(): Flow<BuildInfo>
}

data class AppTheme(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val palette: ThemePalette = ThemePalette.SILVER_BLUE,
    val accent: AccentColor = AccentColor.BLUE,
    val dynamicColor: Boolean = false,
    val custom: CustomTheme? = null,
)

enum class ThemeMode { LIGHT, DARK, SYSTEM }

/**
 * Предустановленные палитры. CUSTOM открывает палитру с выбором цвета —
 * доступно только в SilverChat Premium (перк CUSTOM_THEMES).
 */
enum class ThemePalette(val displayName: String) {
    SILVER_BLUE("Silver Blue"),
    GRAPHITE("Графит"),
    VIOLET("Фиолетовый"),
    EMERALD("Изумруд"),
    SUNSET("Закат"),
    ROSE("Розовый"),
    MIDNIGHT("Полночь"),
    CUSTOM("Своя тема"),
}

enum class AccentColor(val argb: Long) {
    BLUE(0xFF3E82F7),
    VIOLET(0xFF8B5CF6),
    EMERALD(0xFF10B981),
    SUNSET(0xFFF97316),
    ROSE(0xFFE5467F),
    SILVER(0xFF9AA7B8),
    CYAN(0xFF38BDF8),
}

/** Пользовательская тема (Premium). Хранится как JSON в DataStore. */
data class CustomTheme(
    val name: String,
    val accentArgb: Long,
    val backgroundArgb: Long,
    val bubbleInArgb: Long,
    val bubbleOutArgb: Long,
    val wallpaperUri: String? = null,
)

data class Wallpaper(
    val kind: WallpaperKind = WallpaperKind.DEFAULT_PATTERN,
    val assetName: String? = null,
    val customUri: String? = null,
    val blurEnabled: Boolean = false,
    val dimAmount: Float = 0f,
)

enum class WallpaperKind {
    DEFAULT_PATTERN, GRADIENT, SOLID, PHOTO, PREMIUM_ANIMATED,
}

enum class BubbleStyle { CLASSIC, COMPACT, GLASS }

data class NotificationSettings(
    val enabled: Boolean = true,
    val messageSound: String? = "default",
    val messageVibrate: Boolean = true,
    val callRingtone: String? = "default",
    val inAppSounds: Boolean = true,
    val previewEnabled: Boolean = true,
    val groupsEnabled: Boolean = true,
    val channelsEnabled: Boolean = true,
)

enum class AppLanguage(val code: String, val displayName: String) {
    RU("ru", "Русский"),
    UK("uk", "Українська"),
    EN("en", "English"),
    RO("ro", "Română"),
}

enum class AutoPlayPolicy { NEVER, WIFI_ONLY, ALWAYS }

data class BuildInfo(
    val versionName: String,
    val versionCode: Int,
    val buildType: String,
    val backendUrl: String,
    val protocolVersion: Int,
)

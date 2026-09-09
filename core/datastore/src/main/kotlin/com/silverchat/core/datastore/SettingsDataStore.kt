package com.silverchat.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.silverchat.core.common.log.LogTag
import com.silverchat.core.common.log.ScLogger
import com.silverchat.core.common.result.ScResult
import com.silverchat.core.common.result.scResult
import com.silverchat.core.datastore.BuildConfig
import com.silverchat.core.domain.repository.AccentColor
import com.silverchat.core.domain.repository.AppLanguage
import com.silverchat.core.domain.repository.AppTheme
import com.silverchat.core.domain.repository.AutoPlayPolicy
import com.silverchat.core.domain.repository.BubbleStyle
import com.silverchat.core.domain.repository.CustomTheme
import com.silverchat.core.domain.repository.NotificationSettings
import com.silverchat.core.domain.repository.SettingsRepository
import com.silverchat.core.domain.repository.ThemeMode
import com.silverchat.core.domain.repository.ThemePalette
import com.silverchat.core.domain.repository.Wallpaper
import com.silverchat.core.domain.repository.WallpaperKind
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Реализация [SettingsRepository] на DataStore Preferences.
 *
 * Каждый ключ объявлен в [Keys] с дефолтом, поэтому чтение никогда не падает
 * на повреждённом файле: [catch] отдаёт дефолтную тему, а приложение
 * продолжает работать.
 */
@Singleton
class SettingsDataStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val THEME_PALETTE = stringPreferencesKey("theme_palette")
        val ACCENT = stringPreferencesKey("accent")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val CUSTOM_THEME_JSON = stringPreferencesKey("custom_theme")
        val WALLPAPER_KIND = stringPreferencesKey("wallpaper_kind")
        val WALLPAPER_ASSET = stringPreferencesKey("wallpaper_asset")
        val WALLPAPER_URI = stringPreferencesKey("wallpaper_uri")
        val WALLPAPER_BLUR = booleanPreferencesKey("wallpaper_blur")
        val WALLPAPER_DIM = floatPreferencesKey("wallpaper_dim")
        val FONT_SCALE = floatPreferencesKey("font_scale")
        val ANIMATIONS = booleanPreferencesKey("animations_enabled")
        val BUBBLE_STYLE = stringPreferencesKey("bubble_style")
        val NOTIF_ENABLED = booleanPreferencesKey("notif_enabled")
        val NOTIF_SOUND = stringPreferencesKey("notif_sound")
        val NOTIF_VIBRATE = booleanPreferencesKey("notif_vibrate")
        val NOTIF_CALL_RINGTONE = stringPreferencesKey("notif_call_ringtone")
        val NOTIF_IN_APP_SOUNDS = booleanPreferencesKey("notif_in_app_sounds")
        val NOTIF_PREVIEW = booleanPreferencesKey("notif_preview")
        val NOTIF_GROUPS = booleanPreferencesKey("notif_groups")
        val NOTIF_CHANNELS = booleanPreferencesKey("notif_channels")
        val LANGUAGE = stringPreferencesKey("language")
        val AUTOPLAY = stringPreferencesKey("autoplay_media")
        val DATA_SAVER = booleanPreferencesKey("data_saver")
        val APP_LOCK = booleanPreferencesKey("app_lock_enabled")
        val LAST_VERSION_CODE = intPreferencesKey("last_version_code")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
    }

    /* ── Тема ───────────────────────────────────────────────────────────── */

    override fun observeTheme(): Flow<AppTheme> = dataStore.data
        .catch { e ->
            ScLogger.e(LogTag.DB, "DataStore повреждён — отдаём тему по умолчанию", e)
            emit(androidx.datastore.preferences.core.emptyPreferences())
        }
        .map { prefs ->
            AppTheme(
                mode = enumOrDefault(prefs[Keys.THEME_MODE], ThemeMode.SYSTEM),
                palette = enumOrDefault(prefs[Keys.THEME_PALETTE], ThemePalette.SILVER_BLUE),
                accent = enumOrDefault(prefs[Keys.ACCENT], AccentColor.BLUE),
                dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: false,
                custom = prefs[Keys.CUSTOM_THEME_JSON]?.let(::decodeCustomTheme),
            )
        }

    override suspend fun setThemeMode(mode: ThemeMode): ScResult<Unit> =
        write(Keys.THEME_MODE, mode.name)

    override suspend fun setThemePalette(palette: ThemePalette): ScResult<Unit> =
        write(Keys.THEME_PALETTE, palette.name)

    override suspend fun setAccent(accent: AccentColor): ScResult<Unit> =
        write(Keys.ACCENT, accent.name)

    override suspend fun setDynamicColor(enabled: Boolean): ScResult<Unit> =
        write(Keys.DYNAMIC_COLOR, enabled)

    override suspend fun setCustomTheme(theme: CustomTheme): ScResult<Unit> =
        write(Keys.CUSTOM_THEME_JSON, encodeCustomTheme(theme))

    override fun observeWallpaper(): Flow<Wallpaper> = dataStore.data.map { prefs ->
        Wallpaper(
            kind = enumOrDefault(prefs[Keys.WALLPAPER_KIND], WallpaperKind.DEFAULT_PATTERN),
            assetName = prefs[Keys.WALLPAPER_ASSET],
            customUri = prefs[Keys.WALLPAPER_URI],
            blurEnabled = prefs[Keys.WALLPAPER_BLUR] ?: false,
            dimAmount = prefs[Keys.WALLPAPER_DIM] ?: 0f,
        )
    }

    override suspend fun setWallpaper(wallpaper: Wallpaper): ScResult<Unit> = scResult {
        dataStore.edit { prefs ->
            prefs[Keys.WALLPAPER_KIND] = wallpaper.kind.name
            wallpaper.assetName?.let { prefs[Keys.WALLPAPER_ASSET] = it }
            wallpaper.customUri?.let { prefs[Keys.WALLPAPER_URI] = it }
            prefs[Keys.WALLPAPER_BLUR] = wallpaper.blurEnabled
            prefs[Keys.WALLPAPER_DIM] = wallpaper.dimAmount
        }
    }

    /* ── Внешний вид ────────────────────────────────────────────────────── */

    override fun observeFontSizeScale(): Flow<Float> =
        dataStore.data.map { it[Keys.FONT_SCALE] ?: 1f }

    override suspend fun setFontSizeScale(scale: Float): ScResult<Unit> {
        val clamped = scale.coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE)
        return write(Keys.FONT_SCALE, clamped)
    }

    override fun observeAnimationsEnabled(): Flow<Boolean> =
        dataStore.data.map { it[Keys.ANIMATIONS] ?: true }

    override suspend fun setAnimationsEnabled(enabled: Boolean): ScResult<Unit> =
        write(Keys.ANIMATIONS, enabled)

    override fun observeChatBubbleStyle(): Flow<BubbleStyle> =
        dataStore.data.map { enumOrDefault(it[Keys.BUBBLE_STYLE], BubbleStyle.CLASSIC) }

    override suspend fun setChatBubbleStyle(style: BubbleStyle): ScResult<Unit> =
        write(Keys.BUBBLE_STYLE, style.name)

    /* ── Уведомления ────────────────────────────────────────────────────── */

    override fun observeNotifications(): Flow<NotificationSettings> = dataStore.data.map { p ->
        NotificationSettings(
            enabled = p[Keys.NOTIF_ENABLED] ?: true,
            messageSound = p[Keys.NOTIF_SOUND],
            messageVibrate = p[Keys.NOTIF_VIBRATE] ?: true,
            callRingtone = p[Keys.NOTIF_CALL_RINGTONE],
            inAppSounds = p[Keys.NOTIF_IN_APP_SOUNDS] ?: true,
            previewEnabled = p[Keys.NOTIF_PREVIEW] ?: true,
            groupsEnabled = p[Keys.NOTIF_GROUPS] ?: true,
            channelsEnabled = p[Keys.NOTIF_CHANNELS] ?: true,
        )
    }

    override suspend fun setNotifications(settings: NotificationSettings): ScResult<Unit> = scResult {
        dataStore.edit { p ->
            p[Keys.NOTIF_ENABLED] = settings.enabled
            settings.messageSound?.let { p[Keys.NOTIF_SOUND] = it }
            p[Keys.NOTIF_VIBRATE] = settings.messageVibrate
            settings.callRingtone?.let { p[Keys.NOTIF_CALL_RINGTONE] = it }
            p[Keys.NOTIF_IN_APP_SOUNDS] = settings.inAppSounds
            p[Keys.NOTIF_PREVIEW] = settings.previewEnabled
            p[Keys.NOTIF_GROUPS] = settings.groupsEnabled
            p[Keys.NOTIF_CHANNELS] = settings.channelsEnabled
        }
    }

    /* ── Язык, данные, замок ────────────────────────────────────────────── */

    override fun observeLanguage(): Flow<AppLanguage> =
        dataStore.data.map { enumOrDefault(it[Keys.LANGUAGE], AppLanguage.RU) }

    override suspend fun setLanguage(language: AppLanguage): ScResult<Unit> =
        write(Keys.LANGUAGE, language.code)

    override fun observeAutoPlayMedia(): Flow<AutoPlayPolicy> =
        dataStore.data.map { enumOrDefault(it[Keys.AUTOPLAY], AutoPlayPolicy.WIFI_ONLY) }

    override suspend fun setAutoPlayMedia(policy: AutoPlayPolicy): ScResult<Unit> =
        write(Keys.AUTOPLAY, policy.name)

    override fun observeDataSaver(): Flow<Boolean> =
        dataStore.data.map { it[Keys.DATA_SAVER] ?: false }

    override suspend fun setDataSaver(enabled: Boolean): ScResult<Unit> =
        write(Keys.DATA_SAVER, enabled)

    override fun observeAppLockEnabled(): Flow<Boolean> =
        dataStore.data.map { it[Keys.APP_LOCK] ?: false }

    override suspend fun setAppLockEnabled(enabled: Boolean): ScResult<Unit> =
        write(Keys.APP_LOCK, enabled)

    override fun observeBuildInfo(): Flow<com.silverchat.core.domain.repository.BuildInfo> =
        dataStore.data.map {
            com.silverchat.core.domain.repository.BuildInfo(
                versionName = BuildConfig.APP_VERSION_NAME,
                versionCode = BuildConfig.APP_VERSION_CODE,
                buildType = BuildConfig.BUILD_TYPE,
                backendUrl = BuildConfig.BACKEND_URL,
                protocolVersion = BuildConfig.PROTOCOL_VERSION,
            )
        }

    /* ── Вспомогательное ────────────────────────────────────────────────── */

    private suspend fun <T> write(key: Preferences.Key<T>, value: T): ScResult<Unit> = scResult {
        dataStore.edit { it[key] = value }
    }

    private inline fun <reified E : Enum<E>> enumOrDefault(raw: String?, default: E): E =
        raw?.let { value -> E::class.java.enumConstants?.firstOrNull { it.name == value } } ?: default

    private fun encodeCustomTheme(theme: CustomTheme): String =
        listOf(theme.name, theme.accentArgb, theme.backgroundArgb, theme.bubbleInArgb, theme.bubbleOutArgb, theme.wallpaperUri.orEmpty())
            .joinToString(";")

    private fun decodeCustomTheme(raw: String): CustomTheme? {
        val parts = raw.split(";")
        if (parts.size < 5) return null
        return runCatching {
            CustomTheme(
                name = parts[0],
                accentArgb = parts[1].toLong(),
                backgroundArgb = parts[2].toLong(),
                bubbleInArgb = parts[3].toLong(),
                bubbleOutArgb = parts[4].toLong(),
                wallpaperUri = parts.getOrNull(5)?.takeIf { it.isNotBlank() },
            )
        }.getOrNull()
    }

    private companion object {
        const val MIN_FONT_SCALE = 0.8f
        const val MAX_FONT_SCALE = 1.6f
    }
}

/** Удобный доступ к флагам онбординга и версии — используется в :app. */
@Singleton
class AppFlagsDataStore @Inject constructor(private val dataStore: DataStore<Preferences>) {

    suspend fun onboardingDone(): Boolean =
        dataStore.data.map { it[booleanPreferencesKey("onboarding_done")] ?: false }.first()

    suspend fun setOnboardingDone() {
        dataStore.edit { it[booleanPreferencesKey("onboarding_done")] = true }
    }

    suspend fun lastVersionCode(): Int =
        dataStore.data.map { it[intPreferencesKey("last_version_code")] ?: 0 }.first()

    suspend fun setLastVersionCode(code: Int) {
        dataStore.edit { it[intPreferencesKey("last_version_code")] = code }
    }
}

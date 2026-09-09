package com.silverchat.feature.settings.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.silverchat.core.common.format.TimeFormatter
import com.silverchat.core.designsystem.component.EmptyState
import com.silverchat.core.designsystem.component.InlineSnackbar
import com.silverchat.core.designsystem.component.SettingsRow
import com.silverchat.core.designsystem.component.SettingsSection
import com.silverchat.core.designsystem.component.SilverButton
import com.silverchat.core.designsystem.component.SilverButtonSize
import com.silverchat.core.designsystem.component.SilverButtonVariant
import com.silverchat.core.designsystem.component.SilverTopBar
import com.silverchat.core.designsystem.component.UserAvatar
import com.silverchat.core.designsystem.navigation.SettingsNavigator
import com.silverchat.core.designsystem.theme.ScAvatarSize
import com.silverchat.core.designsystem.theme.ScShapes
import com.silverchat.core.designsystem.theme.ScSpacing
import com.silverchat.core.designsystem.theme.ScTheme
import com.silverchat.core.domain.repository.AccentColor
import com.silverchat.core.domain.repository.AppLanguage
import com.silverchat.core.domain.repository.AutoPlayPolicy
import com.silverchat.core.domain.repository.BubbleStyle
import com.silverchat.core.domain.repository.DeviceSession
import com.silverchat.core.domain.repository.ThemeMode
import com.silverchat.core.domain.repository.ThemePalette
import com.silverchat.core.domain.repository.WallpaperKind
import com.silverchat.core.model.labelRu
import com.silverchat.feature.settings.DevicesViewModel
import com.silverchat.feature.settings.SettingsEvent
import com.silverchat.feature.settings.SettingsUiState
import com.silverchat.feature.settings.SettingsViewModel
import com.silverchat.feature.settings.fontScaleLabel
import com.silverchat.feature.settings.titleRu

/**
 * Корневой экран настроек.
 *
 * Верхняя карточка — профиль пользователя: это самая частая точка входа,
 * поэтому она не спрятана в подменю. Ниже — тематические разделы, каждый
 * ведёт на собственный маршрут ([SettingsNavigator]).
 *
 * Вход в админ-панель появляется только при `adminAccess.allowed`.
 * Рядом с ним стоит пояснение: ссылка не является защитой, права
 * проверяет сервер.
 */
@Composable
fun SettingsScreen(
    navigator: SettingsNavigator,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val buildInfo by viewModel.buildInfo.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()

    LaunchedEffect(events) {
        // Выход из аккаунта обрабатывает :app — он сбрасывает граф навигации
        if (events != null) viewModel.consumeEvent()
    }

    Column(
        modifier
            .fillMaxSize()
            .background(ScTheme.background)
            .verticalScroll(rememberScrollState()),
    ) {
        SilverTopBar(title = "Настройки")

        AccountCard(state = state, onOpenProfile = {
            state.user?.let { navigator.openProfile(it.id.raw) }
        })

        SettingsSection("Приложение")
        SettingsRow(
            title = "Внешний вид",
            subtitle = "Размер текста, анимации, пузыри сообщений",
            icon = Icons.Filled.Palette,
            onClick = navigator::openAppearance,
        )
        SettingsRow(
            title = "Темы и обои",
            subtitle = "${state.theme.mode.titleRu} · ${state.theme.palette.displayName}",
            icon = Icons.Filled.Visibility,
            iconTint = state.theme.accent.toColor(),
            onClick = navigator::openThemes,
        )
        SettingsRow(
            title = "Уведомления",
            subtitle = if (state.notifications.enabled) "Включены" else "Отключены",
            icon = Icons.Filled.Notifications,
            onClick = navigator::openNotifications,
        )
        SettingsRow(
            title = "Язык",
            subtitle = state.language.displayName,
            icon = Icons.Filled.Language,
            onClick = navigator::openLanguage,
        )

        SettingsSection("Безопасность")
        SettingsRow(
            title = "Конфиденциальность",
            subtitle = "Кто видит номер, «был в сети», звонки",
            icon = Icons.Filled.Shield,
            onClick = navigator::openPrivacy,
        )
        SettingsRow(
            title = "Замок приложения",
            subtitle = if (state.appLockEnabled) "Биометрия включена" else "Выключен",
            icon = Icons.Filled.Lock,
            iconTint = if (state.appLockEnabled) ScTheme.success else ScTheme.textSecondary,
            onClick = navigator::openAppLock,
        )
        SettingsRow(
            title = "Устройства",
            subtitle = "Активные сессии и выход на других устройствах",
            icon = Icons.Filled.Phone,
            onClick = navigator::openDevices,
        )

        SettingsSection("Данные")
        SettingsRow(
            title = "Данные и память",
            subtitle = "Автозагрузка медиа, экономия трафика, кэш",
            icon = Icons.Filled.Storage,
            onClick = navigator::openDataAndStorage,
        )

        // Админ-панель: видна только при наличии прав
        if (state.showAdminEntry) {
            SettingsSection("Администрирование")
            SettingsRow(
                title = "Панель @silver",
                subtitle = "Роль: ${state.adminAccess.role.labelRu}",
                icon = Icons.Filled.Shield,
                iconTint = ScTheme.premium,
                onClick = navigator::openAdminPanel,
            )
            Text(
                text = "Доступ к разделу проверяется сервером на каждый запрос. " +
                    "Скрытая ссылка не даёт прав.",
                color = ScTheme.textTertiary,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                modifier = Modifier.padding(horizontal = ScSpacing.md, vertical = ScSpacing.xs),
            )
        }

        SettingsSection("Аккаунт")
        SettingsRow(
            title = "Выйти из аккаунта",
            subtitle = "Сессия на этом устройстве будет завершена",
            icon = Icons.Filled.Person,
            iconTint = ScTheme.danger,
            danger = true,
            enabled = !state.isLoggingOut,
            onClick = viewModel::logout,
        )

        buildInfo?.let { info ->
            SettingsSection("О приложении")
            Column(Modifier.padding(horizontal = ScSpacing.md, vertical = ScSpacing.sm)) {
                InfoLine("Версия", "${info.versionName} (${info.versionCode})")
                InfoLine("Сборка", info.buildType)
                InfoLine("Сервер", info.backendUrl)
                InfoLine("Протокол", "v${info.protocolVersion}")
            }
        }

        events?.let { event ->
            val message = when (event) {
                SettingsEvent.LoggedOut -> "Вы вышли из аккаунта"
                is SettingsEvent.Error -> event.message
            }
            InlineSnackbar(
                message = message,
                modifier = Modifier.fillMaxWidth().padding(ScSpacing.md),
            )
        }

        Spacer(Modifier.height(ScSpacing.xxl))
    }
}

/* =========================================================================
   КАРТОЧКА АККАУНТА
   ========================================================================= */

@Composable
private fun AccountCard(state: SettingsUiState, onOpenProfile: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(ScSpacing.md)
            .clip(ScShapes.cardLarge)
            .background(ScTheme.surfaceElevated)
            .clickable(onClick = onOpenProfile)
            .padding(ScSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserAvatar(user = state.user, size = ScAvatarSize.listItem)
        Spacer(Modifier.width(ScSpacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                text = state.displayName,
                color = ScTheme.textPrimary,
                fontSize = 16.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            state.handle?.let {
                Text(it, color = ScTheme.accent, fontSize = 13.sp, maxLines = 1)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.isPremium) {
                    Badge("Premium", ScTheme.premium)
                    Spacer(Modifier.width(6.dp))
                }
                if (state.isAdmin) Badge(state.adminAccess.role.labelRu, ScTheme.warning)
            }
        }
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Box(
        Modifier
            .clip(ScShapes.chip)
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(text, color = color, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, color = ScTheme.textTertiary, fontSize = 12.5.sp, modifier = Modifier.weight(1f))
        Text(value, color = ScTheme.textSecondary, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
    }
}

/* =========================================================================
   ВНЕШНИЙ ВИД
   ========================================================================= */

/**
 * Внешний вид: масштаб текста, анимации, стиль пузырей.
 *
 * Масштаб — слайдер, а не список фиксированных значений: пользователи
 * подбирают размер под зрение, и дискретная шкала их ограничивает.
 */
@Composable
fun AppearanceScreen(
    navigator: SettingsNavigator,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val appearance = state.appearance

    Column(
        modifier.fillMaxSize().background(ScTheme.background).verticalScroll(rememberScrollState()),
    ) {
        SilverTopBar(title = "Внешний вид", onBack = navigator::back)

        SettingsSection("Текст")
        Column(Modifier.padding(horizontal = ScSpacing.md, vertical = ScSpacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Размер текста", color = ScTheme.textPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text(
                    text = fontScaleLabel(appearance.fontSizeScale),
                    color = ScTheme.accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            // Предустановки вместо слайдера: точная подстройка rarely нужна,
            // а пять значений покрывают весь диапазон читаемости
            Row(
                Modifier.fillMaxWidth().padding(top = ScSpacing.sm),
                horizontalArrangement = Arrangement.spacedBy(ScSpacing.sm),
            ) {
                FONT_SCALE_PRESETS.forEach { scale ->
                    val selected = kotlin.math.abs(appearance.fontSizeScale - scale) < PRESET_EPSILON
                    Text(
                        text = fontScaleLabel(scale),
                        color = if (selected) Color.White else ScTheme.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier
                            .clip(ScShapes.chip)
                            .background(if (selected) ScTheme.accent else ScTheme.surfaceGlass)
                            .clickable { viewModel.setFontSizeScale(scale) }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    )
                }
            }
        }

        SettingsSection("Анимации")
        SettingsRow(
            title = "Анимации интерфейса",
            subtitle = "Отключите, если устройство тормозит",
            trailing = { Toggle(checked = appearance.animationsEnabled, onToggle = { viewModel.setAnimationsEnabled(it) }) },
        )

        SettingsSection("Сообщения")
        BubbleStyle.entries.forEach { style ->
            ChoiceRow(
                title = style.titleRu,
                selected = appearance.bubbleStyle == style,
                onClick = { viewModel.setChatBubbleStyle(style) },
            )
        }
        Spacer(Modifier.height(ScSpacing.lg))
    }
}

/** Предустановки масштаба текста. */
private val FONT_SCALE_PRESETS = listOf(0.85f, 1.0f, 1.15f, 1.3f, 1.6f)

/** Допуск сравнения float: значения приходят из DataStore после округления. */
private const val PRESET_EPSILON = 0.001f

/* =========================================================================
   ТЕМЫ И ОБОИ
   ========================================================================= */

@Composable
fun ThemesScreen(
    navigator: SettingsNavigator,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val theme = state.theme

    Column(
        modifier.fillMaxSize().background(ScTheme.background).verticalScroll(rememberScrollState()),
    ) {
        SilverTopBar(title = "Темы и обои", onBack = navigator::back)

        SettingsSection("Режим")
        ThemeMode.entries.forEach { mode ->
            ChoiceRow(
                title = mode.titleRu,
                selected = theme.mode == mode,
                onClick = { viewModel.setThemeMode(mode) },
            )
        }

        SettingsSection("Палитра")
        ThemePalette.entries.forEach { palette ->
            val locked = palette == ThemePalette.CUSTOM && !state.isPremium
            ChoiceRow(
                title = palette.displayName,
                subtitle = if (locked) "Доступно в Premium" else null,
                selected = theme.palette == palette,
                enabled = !locked,
                onClick = { viewModel.setThemePalette(palette) },
            )
        }

        SettingsSection("Акцент")
        Row(
            Modifier.fillMaxWidth().padding(horizontal = ScSpacing.md, vertical = ScSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(ScSpacing.md),
        ) {
            AccentColor.entries.forEach { accent ->
                val selected = theme.accent == accent
                Box(
                    Modifier
                        .size(if (selected) 34.dp else 28.dp)
                        .clip(CircleShape)
                        .background(accent.toColor())
                        .clickable { viewModel.setAccent(accent) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Акцент ${accent.name}",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }

        SettingsSection("Обои")
        WallpaperKind.entries.forEach { kind ->
            val locked = kind == WallpaperKind.PREMIUM_ANIMATED && !state.isPremium
            ChoiceRow(
                title = kind.titleRu,
                subtitle = if (locked) "Доступно в Premium" else null,
                selected = state.wallpaper.kind == kind,
                enabled = !locked,
                onClick = { viewModel.setWallpaperKind(kind) },
            )
        }
        SettingsRow(
            title = "Размытие обоев",
            subtitle = "Стекло выглядит выразительнее на размытом фоне",
            trailing = {
                Toggle(
                    checked = state.wallpaper.blurEnabled,
                    onToggle = viewModel::setWallpaperBlur,
                )
            },
        )

        Spacer(Modifier.height(ScSpacing.lg))
    }
}

/* =========================================================================
   УВЕДОМЛЕНИЯ
   ========================================================================= */

@Composable
fun NotificationsScreen(
    navigator: SettingsNavigator,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settings = state.notifications

    Column(
        modifier.fillMaxSize().background(ScTheme.background).verticalScroll(rememberScrollState()),
    ) {
        SilverTopBar(title = "Уведомления", onBack = navigator::back)

        SettingsSection("Общие")
        SettingsRow(
            title = "Уведомления",
            subtitle = "Главный переключатель",
            trailing = {
                Toggle(
                    checked = settings.enabled,
                    onToggle = { enabled ->
                        viewModel.updateNotifications { it.copy(enabled = enabled) }
                    },
                )
            },
        )

        // Подразделы неактивны при выключенном главном переключателе
        val enabled = settings.enabled

        SettingsSection("Личные чаты")
        SettingsRow(
            title = "Звук сообщения",
            subtitle = settings.messageSound ?: "Без звука",
            enabled = enabled,
            trailing = {
                Toggle(
                    checked = settings.messageSound != null,
                    onToggle = { on ->
                        viewModel.updateNotifications {
                            it.copy(messageSound = if (on) DEFAULT_SOUND else null)
                        }
                    },
                )
            },
        )
        SettingsRow(
            title = "Вибрация",
            enabled = enabled,
            trailing = {
                Toggle(
                    checked = settings.messageVibrate,
                    onToggle = { v -> viewModel.updateNotifications { it.copy(messageVibrate = v) } },
                )
            },
        )
        SettingsRow(
            title = "Предпросмотр текста",
            subtitle = "Показывать содержание в шторке",
            enabled = enabled,
            trailing = {
                Toggle(
                    checked = settings.previewEnabled,
                    onToggle = { p -> viewModel.updateNotifications { it.copy(previewEnabled = p) } },
                )
            },
        )

        SettingsSection("Группы и каналы")
        SettingsRow(
            title = "Группы",
            enabled = enabled,
            trailing = {
                Toggle(
                    checked = settings.groupsEnabled,
                    onToggle = { g -> viewModel.updateNotifications { it.copy(groupsEnabled = g) } },
                )
            },
        )
        SettingsRow(
            title = "Каналы",
            enabled = enabled,
            trailing = {
                Toggle(
                    checked = settings.channelsEnabled,
                    onToggle = { c -> viewModel.updateNotifications { it.copy(channelsEnabled = c) } },
                )
            },
        )

        SettingsSection("Звонки")
        SettingsRow(
            title = "Мелодия звонка",
            subtitle = settings.callRingtone ?: "Системная",
            enabled = enabled,
        )
        SettingsRow(
            title = "Звуки в приложении",
            subtitle = "Отправка, получение, реакции",
            enabled = enabled,
            trailing = {
                Toggle(
                    checked = settings.inAppSounds,
                    onToggle = { s -> viewModel.updateNotifications { it.copy(inAppSounds = s) } },
                )
            },
        )

        Spacer(Modifier.height(ScSpacing.lg))
    }
}

private const val DEFAULT_SOUND = "default"

/* =========================================================================
   ЯЗЫК
   ========================================================================= */

@Composable
fun LanguageScreen(
    navigator: SettingsNavigator,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier.fillMaxSize().background(ScTheme.background).verticalScroll(rememberScrollState()),
    ) {
        SilverTopBar(title = "Язык", onBack = navigator::back)

        SettingsSection("Язык интерфейса")
        AppLanguage.entries.forEach { language ->
            ChoiceRow(
                title = language.displayName,
                subtitle = language.code,
                selected = state.language == language,
                onClick = { viewModel.setLanguage(language) },
            )
        }

        Text(
            text = "Язык применяется сразу и не требует перезапуска.",
            color = ScTheme.textTertiary,
            fontSize = 11.5.sp,
            modifier = Modifier.padding(ScSpacing.md),
        )
        Spacer(Modifier.height(ScSpacing.lg))
    }
}

/* =========================================================================
   ДАННЫЕ И ПАМЯТЬ
   ========================================================================= */

@Composable
fun DataAndStorageScreen(
    navigator: SettingsNavigator,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val appearance = state.appearance

    Column(
        modifier.fillMaxSize().background(ScTheme.background).verticalScroll(rememberScrollState()),
    ) {
        SilverTopBar(title = "Данные и память", onBack = navigator::back)

        SettingsSection("Автозагрузка медиа")
        AutoPlayPolicy.entries.forEach { policy ->
            ChoiceRow(
                title = policy.titleRu,
                selected = appearance.autoPlayMedia == policy,
                onClick = { viewModel.setAutoPlayMedia(policy) },
            )
        }

        SettingsSection("Трафик")
        SettingsRow(
            title = "Экономия данных",
            subtitle = "Снижает качество медиа и отключает предзагрузку",
            trailing = {
                Toggle(checked = appearance.dataSaver, onToggle = viewModel::setDataSaver)
            },
        )

        Spacer(Modifier.height(ScSpacing.lg))
    }
}

/* =========================================================================
   ЗАМОК ПРИЛОЖЕНИЯ
   ========================================================================= */

/**
 * Замок приложения.
 *
 * Переключатель только сохраняет флаг. Регистрация биометрии выполняется
 * в `:app`, где доступен `FragmentActivity` для `BiometricPrompt`:
 * feature-модуль не должен зависеть от Activity-хоста.
 */
@Composable
fun AppLockScreen(
    navigator: SettingsNavigator,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
    onEnrollBiometric: (Boolean) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier.fillMaxSize().background(ScTheme.background).verticalScroll(rememberScrollState()),
    ) {
        SilverTopBar(title = "Замок приложения", onBack = navigator::back)

        SettingsSection("Блокировка")
        SettingsRow(
            title = "Запрашивать биометрию",
            subtitle = "Отпечаток пальца или Face ID при каждом входе",
            icon = Icons.Filled.Lock,
            trailing = {
                Toggle(
                    checked = state.appLockEnabled,
                    onToggle = { enabled ->
                        viewModel.setAppLockEnabled(enabled)
                        onEnrollBiometric(enabled)
                    },
                )
            },
        )

        Column(
            Modifier
                .fillMaxWidth()
                .padding(ScSpacing.md)
                .clip(ScShapes.card)
                .background(ScTheme.surfaceElevated)
                .padding(ScSpacing.md),
        ) {
            Text(
                text = "Как это работает",
                color = ScTheme.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(ScSpacing.sm))
            Text(
                text = "Токены сессии хранятся в EncryptedSharedPreferences и защищены " +
                    "аппаратным ключом Android Keystore. Биометрия блокирует доступ " +
                    "к интерфейсу, но не заменяет шифрование хранилища.",
                color = ScTheme.textTertiary,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
        }
        Spacer(Modifier.height(ScSpacing.lg))
    }
}

/* =========================================================================
   УСТРОЙСТВА
   ========================================================================= */

/**
 * Активные сессии.
 *
 * Список загружается suspend-запросом через [DevicesViewModel], а не потоком:
 * сессии меняются редко, и постоянная WebSocket-подписка ради них — лишний трафик.
 */
@Composable
fun DevicesScreen(
    navigator: SettingsNavigator,
    modifier: Modifier = Modifier,
    viewModel: DevicesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier.fillMaxSize().background(ScTheme.background).verticalScroll(rememberScrollState()),
    ) {
        SilverTopBar(title = "Устройства", onBack = navigator::back)

        state.errorText?.let { error ->
            InlineSnackbar(
                message = error,
                actionLabel = "Повторить",
                onAction = viewModel::refresh,
                modifier = Modifier.fillMaxWidth().padding(horizontal = ScSpacing.md, vertical = ScSpacing.sm),
            )
        }

        when {
            state.isLoading -> EmptyState(
                icon = Icons.Filled.Phone,
                title = "Загружаем список сессий",
                subtitle = "Список активных устройств появится через минуту.",
                modifier = Modifier.fillMaxWidth(),
            )

            state.sessions.isEmpty() -> EmptyState(
                icon = Icons.Filled.Phone,
                title = "Сессий не найдено",
                subtitle = "Обновите список — возможно, данные ещё не загружены.",
                actionLabel = "Обновить",
                onAction = viewModel::refresh,
                modifier = Modifier.fillMaxWidth(),
            )

            else -> {
                SettingsSection("Текущее устройство")
                state.current?.let { session ->
                    SessionRow(
                        session = session,
                        current = true,
                        isProcessing = false,
                        onRevoke = null,
                    )
                }

                SettingsSection("Другие устройства")
                if (state.others.isEmpty()) {
                    Text(
                        text = "Других активных сессий нет",
                        color = ScTheme.textTertiary,
                        fontSize = 12.5.sp,
                        modifier = Modifier.padding(ScSpacing.md),
                    )
                }
                state.others.forEach { session ->
                    SessionRow(
                        session = session,
                        current = false,
                        isProcessing = state.isProcessingId == session.id || state.isProcessingAll,
                        onRevoke = viewModel::revoke,
                    )
                }

                if (state.others.isNotEmpty()) {
                    Spacer(Modifier.height(ScSpacing.md))
                    SilverButton(
                        text = "Завершить все другие сессии",
                        onClick = viewModel::revokeOthers,
                        enabled = !state.isBusy,
                        loading = state.isProcessingAll,
                        variant = SilverButtonVariant.SECONDARY,
                        modifier = Modifier.padding(horizontal = ScSpacing.md),
                    )
                }

                Spacer(Modifier.height(ScSpacing.md))
                SilverButton(
                    text = "Обновить список",
                    onClick = viewModel::refresh,
                    enabled = !state.isBusy,
                    variant = SilverButtonVariant.TEXT,
                    modifier = Modifier.padding(horizontal = ScSpacing.md),
                )
            }
        }
        Spacer(Modifier.height(ScSpacing.lg))
    }
}

@Composable
private fun SessionRow(
    session: DeviceSession,
    current: Boolean,
    isProcessing: Boolean,
    onRevoke: ((String) -> Unit)?,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = ScSpacing.md, vertical = ScSpacing.sm)
            .clip(ScShapes.card)
            .background(ScTheme.surfaceElevated)
            .padding(ScSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(ScTheme.surfaceGlass),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Phone,
                contentDescription = null,
                tint = if (current) ScTheme.success else ScTheme.textSecondary,
                modifier = Modifier.size(19.dp),
            )
        }
        Spacer(Modifier.width(ScSpacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                text = session.deviceName,
                color = ScTheme.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Text(
                text = buildString {
                    append(session.platform)
                    append(" · ")
                    append(session.appVersion)
                    session.locationHint?.let {
                        append(" · ")
                        append(it)
                    }
                },
                color = ScTheme.textTertiary,
                fontSize = 11.5.sp,
                maxLines = 1,
            )
            Text(
                text = TimeFormatter.lastSeen(session.lastActiveAt),
                color = ScTheme.textTertiary,
                fontSize = 11.sp,
            )
            session.ip?.let { ip ->
                Text(ip, color = ScTheme.textTertiary, fontSize = 10.5.sp)
            }
        }
        when {
            current -> Text(
                text = "Это устройство",
                color = ScTheme.success,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )

            onRevoke != null -> SilverButton(
                text = "Завершить",
                onClick = { onRevoke(session.id) },
                enabled = !isProcessing,
                loading = isProcessing,
                variant = SilverButtonVariant.TEXT,
                size = SilverButtonSize.SMALL,
            )
        }
    }
}

/* =========================================================================
   ОБЩИЕ ЭЛЕМЕНТЫ
   ========================================================================= */

/** Строка выбора одного значения из списка (радио-кнопка без отдельного виджета). */
@Composable
private fun ChoiceRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = ScSpacing.md, vertical = ScSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = when {
                    !enabled -> ScTheme.textTertiary
                    selected -> ScTheme.accent
                    else -> ScTheme.textPrimary
                },
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
            subtitle?.let {
                Text(it, color = ScTheme.textTertiary, fontSize = 11.5.sp)
            }
        }
        Box(
            Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(
                    if (selected && enabled) ScTheme.accent else ScTheme.surface,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected && enabled) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(13.dp),
                )
            }
        }
    }
}

/** Переключатель без зависимости от Material3 Switch: единый стиль дизайн-системы. */
@Composable
private fun Toggle(checked: Boolean, onToggle: (Boolean) -> Unit) {
    Box(
        Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(if (checked) ScTheme.success else ScTheme.surface)
            .clickable { onToggle(!checked) },
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

/**
 * `AccentColor.argb` — это `Long`, а Compose требует `Color`.
 *
 * Приведение вынесено в экстеншен, чтобы не повторять `Color(...)` в каждом
 * месте и не потерять смысл: значение хранится как ARGB целиком.
 */
private fun AccentColor.toColor(): Color = Color(argb)

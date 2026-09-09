package com.silverchat.feature.profile.screen

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Schedule
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.silverchat.core.designsystem.component.InlineSnackbar
import com.silverchat.core.designsystem.component.SettingsRow
import com.silverchat.core.designsystem.component.SettingsSection
import com.silverchat.core.designsystem.component.SilverButton
import com.silverchat.core.designsystem.component.SilverButtonVariant
import com.silverchat.core.designsystem.component.SilverTextField
import com.silverchat.core.designsystem.component.SilverTopBar
import com.silverchat.core.designsystem.navigation.ProfileNavigator
import com.silverchat.core.designsystem.theme.ScShapes
import com.silverchat.core.designsystem.theme.ScSpacing
import com.silverchat.core.designsystem.theme.ScTheme
import com.silverchat.core.model.DaySchedule
import com.silverchat.core.model.WorkingHours
import com.silverchat.feature.profile.EditProfileEvent
import com.silverchat.feature.profile.EditProfileViewModel

/**
 * Редактирование профиля.
 *
 * Форма локальная и отправляется только по «Сохранить»: авто-сохранение на
 * каждый символ породило бы запрос к серверу на каждую клавишу, а username
 * дополнительно проверяется на уникальность — это дорогая операция.
 *
 * Аватар и баннер редактируются на отдельных экранах
 * ([ProfileNavigator.openAvatarEditor], [ProfileNavigator.openBannerEditor]):
 * им нужен выбор файла, кроп и, для анимации, проверка Premium-перка.
 * Здесь остаются только кнопки-входы.
 */
@Composable
fun EditProfileScreen(
    navigator: ProfileNavigator,
    modifier: Modifier = Modifier,
    viewModel: EditProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()

    LaunchedEffect(events) {
        if (events is EditProfileEvent.Saved) navigator.back()
        if (events != null) viewModel.consumeEvent()
    }

    Column(
        modifier
            .fillMaxSize()
            .background(ScTheme.background)
            .verticalScroll(rememberScrollState()),
    ) {
        SilverTopBar(
            title = "Изменить профиль",
            onBack = navigator::back,
            actions = {
                SilverButton(
                    text = "Сохранить",
                    onClick = viewModel::save,
                    enabled = !state.isBusy,
                    loading = state.isSaving,
                    variant = SilverButtonVariant.TEXT,
                    size = com.silverchat.core.designsystem.component.SilverButtonSize.SMALL,
                )
            },
        )

        state.errorText?.let { error ->
            InlineSnackbar(
                message = error,
                actionLabel = "Скрыть",
                onAction = viewModel::dismissError,
                modifier = Modifier.fillMaxWidth().padding(horizontal = ScSpacing.md, vertical = ScSpacing.sm),
            )
        }

        // ── Медиа ────────────────────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth().padding(ScSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(ScSpacing.md),
        ) {
            MediaEditTile(
                title = "Аватар",
                subtitle = if (state.isUploadingAvatar) "Загружаем…" else "Фото, GIF, Lottie или видео",
                icon = Icons.Filled.Image,
                enabled = !state.isUploadingAvatar,
                onClick = navigator::openAvatarEditor,
                modifier = Modifier.weight(1f),
            )
            MediaEditTile(
                title = "Баннер",
                subtitle = if (state.isUploadingBanner) "Загружаем…" else "Обложка профиля",
                icon = Icons.Filled.Image,
                enabled = !state.isUploadingBanner,
                onClick = navigator::openBannerEditor,
                modifier = Modifier.weight(1f),
            )
        }

        // ── Имя ──────────────────────────────────────────────────────────
        SettingsSection("Имя")
        Column(Modifier.padding(horizontal = ScSpacing.md)) {
            SilverTextField(
                value = state.firstName,
                onValueChange = viewModel::onFirstNameChanged,
                label = "Имя",
                placeholder = "Как вас зовут",
                maxLength = MAX_NAME_LENGTH,
            )
            Spacer(Modifier.height(ScSpacing.sm))
            SilverTextField(
                value = state.lastName,
                onValueChange = viewModel::onLastNameChanged,
                label = "Фамилия (необязательно)",
                maxLength = MAX_NAME_LENGTH,
            )
        }

        // ── Юзернейм ─────────────────────────────────────────────────────
        SettingsSection("Юзернейм")
        Column(Modifier.padding(horizontal = ScSpacing.md)) {
            SilverTextField(
                value = state.username,
                onValueChange = viewModel::onUsernameChanged,
                label = "@username",
                placeholder = "username",
                maxLength = MAX_NAME_LENGTH,
            )
            Spacer(Modifier.height(ScSpacing.xs))
            Text(
                text = "Латиница, цифры и «_», от 4 символов. " +
                    "По юзернейму вас находят в поиске и по ссылке silverchat://u/…",
                color = ScTheme.textTertiary,
                fontSize = 11.sp,
                lineHeight = 15.sp,
            )
            Text(
                text = "Освободившийся юзернейм попадает на маркет — " +
                    "его может купить другой пользователь.",
                color = ScTheme.warning,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                modifier = Modifier.padding(top = ScSpacing.xs),
            )
        }

        // ── О себе ───────────────────────────────────────────────────────
        SettingsSection("О себе")
        Column(Modifier.padding(horizontal = ScSpacing.md)) {
            SilverTextField(
                value = state.bio,
                onValueChange = viewModel::onBioChanged,
                label = "Описание",
                placeholder = "Пара слов о вас",
                maxLength = MAX_BIO_LENGTH,
                singleLine = false,
            )
            Spacer(Modifier.height(ScSpacing.xs))
            Text(
                text = "Осталось символов: ${state.bioLeft}",
                color = ScTheme.textTertiary,
                fontSize = 11.sp,
            )
        }

        // ── Местоположение ───────────────────────────────────────────────
        SettingsSection("Местоположение")
        if (state.hasLocation) {
            Column(Modifier.padding(horizontal = ScSpacing.md)) {
                SilverTextField(
                    value = state.location?.title.orEmpty(),
                    onValueChange = viewModel::onLocationTitleChanged,
                    label = "Название места",
                    placeholder = "Офис, студия, город",
                )
                Spacer(Modifier.height(ScSpacing.sm))
                SilverTextField(
                    value = state.location?.address.orEmpty(),
                    onValueChange = viewModel::onLocationAddressChanged,
                    label = "Адрес",
                    placeholder = "Улица, дом",
                )
                Spacer(Modifier.height(ScSpacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(ScSpacing.sm)) {
                    CoordinateField(
                        label = "Широта",
                        value = state.location?.latitude,
                        onChange = { lat -> viewModel.onLocationCoordinatesChanged(lat, state.location?.longitude) },
                    )
                    CoordinateField(
                        label = "Долгота",
                        value = state.location?.longitude,
                        onChange = { lng -> viewModel.onLocationCoordinatesChanged(state.location?.latitude, lng) },
                    )
                }
                Spacer(Modifier.height(ScSpacing.sm))
                ToggleRow(
                    title = "Показывать в профиле",
                    checked = state.location?.visible == true,
                    onToggle = viewModel::toggleLocationVisible,
                )
                Spacer(Modifier.height(ScSpacing.sm))
                SilverButton(
                    text = "Удалить местоположение",
                    onClick = viewModel::clearLocation,
                    variant = SilverButtonVariant.TEXT,
                )
            }
        } else {
            SettingsRow(
                title = "Добавить местоположение",
                subtitle = "Полезно для каналов и бизнес-аккаунтов",
                icon = Icons.Filled.LocationOn,
                onClick = viewModel::addLocation,
            )
        }

        // ── Часы работы ──────────────────────────────────────────────────
        SettingsSection("Часы работы")
        if (state.hasWorkingHours) {
            WorkingHoursEditor(
                hours = state.workingHours ?: WorkingHours(),
                onToggleAlwaysOpen = viewModel::toggleAlwaysOpen,
                onToggleVisible = viewModel::toggleHoursVisible,
                onUpdateDay = viewModel::updateDay,
                onClear = viewModel::clearWorkingHours,
            )
        } else {
            SettingsRow(
                title = "Добавить часы работы",
                subtitle = "Расписание на неделю с 09:00 до 18:00",
                icon = Icons.Filled.Schedule,
                onClick = viewModel::addWorkingHours,
            )
        }

        Spacer(Modifier.height(ScSpacing.md))

        SilverButton(
            text = "Сохранить",
            onClick = viewModel::save,
            enabled = !state.isBusy,
            loading = state.isSaving,
            modifier = Modifier.padding(horizontal = ScSpacing.md),
        )

        Spacer(Modifier.height(ScSpacing.xxl))
    }
}

private const val MAX_NAME_LENGTH = 64
private const val MAX_BIO_LENGTH = 280

/* =========================================================================
   ПЛИТКА РЕДАКТИРОВАНИЯ МЕДИА
   ========================================================================= */

@Composable
private fun MediaEditTile(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .clip(ScShapes.card)
            .background(ScTheme.surfaceElevated)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(ScSpacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(ScTheme.accentContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = ScTheme.accent,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.height(ScSpacing.sm))
        Text(
            text = title,
            color = ScTheme.textPrimary,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = subtitle,
            color = ScTheme.textTertiary,
            fontSize = 10.5.sp,
            lineHeight = 14.sp,
        )
    }
}

/* =========================================================================
   КООРДИНАТЫ
   ========================================================================= */

@Composable
private fun CoordinateField(
    label: String,
    value: Double?,
    onChange: (Double?) -> Unit,
) {
    SilverTextField(
        value = value?.toString().orEmpty(),
        onValueChange = { raw -> onChange(raw.toDoubleOrNull()) },
        label = label,
        placeholder = "0.0",
        keyboardType = KeyboardType.Decimal,
        modifier = Modifier.weight(1f),
    )
}

/* =========================================================================
   ПЕРЕКЛЮЧАТЕЛЬ
   ========================================================================= */

@Composable
private fun ToggleRow(title: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(ScShapes.card)
            .background(ScTheme.surfaceElevated)
            .clickable(onClick = onToggle)
            .padding(ScSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = ScTheme.textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        Box(
            Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (checked) ScTheme.success else ScTheme.surface),
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
}

/* =========================================================================
   ЧАСЫ РАБОТЫ
   ========================================================================= */

/**
 * Редактор часов работы.
 *
 * Время вводится в минутах от полуночи ([DaySchedule.openMinute]), а не
 * строкой: сервер хранит именно минуты, и парсинг «9:30» на клиенте
 * породил бы расхождения форматов. Экран показывает минуты как «чч:мм».
 */
@Composable
private fun WorkingHoursEditor(
    hours: WorkingHours,
    onToggleAlwaysOpen: () -> Unit,
    onToggleVisible: () -> Unit,
    onUpdateDay: (dayOfWeek: Int, openMinute: Int, closeMinute: Int, closed: Boolean) -> Unit,
    onClear: () -> Unit,
) {
    Column(Modifier.padding(horizontal = ScSpacing.md)) {
        ToggleRow(
            title = "Круглосуточно",
            checked = hours.alwaysOpen,
            onToggle = onToggleAlwaysOpen,
        )

        // Расписание не имеет смысла при круглосуточной работе
        if (!hours.alwaysOpen) {
            Spacer(Modifier.height(ScSpacing.sm))
            DAY_NAMES.forEachIndexed { index, dayName ->
                val dayOfWeek = index + 1
                val day = hours.schedule.firstOrNull { it.dayOfWeek == dayOfWeek }
                DayRow(
                    dayName = dayName,
                    day = day,
                    onUpdate = { open, close, closed -> onUpdateDay(dayOfWeek, open, close, closed) },
                )
            }
        }

        Spacer(Modifier.height(ScSpacing.sm))
        ToggleRow(
            title = "Показывать в профиле",
            checked = hours.visible,
            onToggle = onToggleVisible,
        )

        Spacer(Modifier.height(ScSpacing.sm))
        Text(
            text = "Часовой пояс: ${hours.timeZone}",
            color = ScTheme.textTertiary,
            fontSize = 11.sp,
        )

        Spacer(Modifier.height(ScSpacing.sm))
        SilverButton(
            text = "Удалить часы работы",
            onClick = onClear,
            variant = SilverButtonVariant.TEXT,
        )
    }
}

/** Рабочий день по умолчанию: 09:00–18:00 в минутах от полуночи. */
private const val DEFAULT_OPEN_MINUTE = 9 * 60
private const val DEFAULT_CLOSE_MINUTE = 18 * 60

/** Названия дней по ISO-8601: индекс 0 = понедельник. */
private val DAY_NAMES = listOf(
    "Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье",
)

@Composable
private fun DayRow(
    dayName: String,
    day: DaySchedule?,
    onUpdate: (openMinute: Int, closeMinute: Int, closed: Boolean) -> Unit,
) {
    val closed = day?.closed ?: true

    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = ScSpacing.xs)
            .clip(ScShapes.card)
            .background(ScTheme.surfaceElevated)
            .padding(ScSpacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = dayName,
                color = ScTheme.textPrimary,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (closed) "Закрыто" else "Открыть",
                color = if (closed) ScTheme.textTertiary else ScTheme.success,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(ScShapes.chip)
                    .clickable {
                        onUpdate(
                            day?.openMinute ?: DEFAULT_OPEN_MINUTE,
                            day?.closeMinute ?: DEFAULT_CLOSE_MINUTE,
                            !closed,
                        )
                    }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }

        if (!closed) {
            Spacer(Modifier.height(ScSpacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(ScSpacing.sm)) {
                MinuteField(
                    label = "Открытие",
                    minute = day?.openMinute ?: DEFAULT_OPEN_MINUTE,
                    onChange = { open -> onUpdate(open, day?.closeMinute ?: DEFAULT_CLOSE_MINUTE, false) },
                )
                MinuteField(
                    label = "Закрытие",
                    minute = day?.closeMinute ?: DEFAULT_CLOSE_MINUTE,
                    onChange = { close -> onUpdate(day?.openMinute ?: DEFAULT_OPEN_MINUTE, close, false) },
                )
            }
            // Подсказка о некорректном интервале: сервер его отклонит,
            // но лучше сообщить до отправки
            val open = day?.openMinute ?: 0
            val close = day?.closeMinute ?: 0
            if (open >= close) {
                Text(
                    text = "Время закрытия должно быть позже времени открытия",
                    color = ScTheme.danger,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = ScSpacing.xs),
                )
            }
        }
    }
}

/**
 * Поле времени в минутах от полуночи.
 *
 * Принимает «чч:мм» и отдаёт минуты. Некорректный ввод не обнуляет значение:
 * пользователь мог удалить символ на середине набора.
 */
@Composable
private fun MinuteField(
    label: String,
    minute: Int,
    onChange: (Int) -> Unit,
) {
    SilverTextField(
        value = formatMinute(minute),
        onValueChange = { raw ->
            parseMinute(raw)?.let(onChange)
        },
        label = label,
        placeholder = "09:00",
        keyboardType = KeyboardType.Number,
        modifier = Modifier.weight(1f),
    )
}

private fun formatMinute(minute: Int): String =
    "%02d:%02d".format(minute / MINUTES_IN_HOUR, minute % MINUTES_IN_HOUR)

/**
 * Разбор «чч:мм» в минуты.
 *
 * Возвращает null при неполном или некорректном вводе, чтобы не перезаписывать
 * значение во время набора. Границы проверяются: 24:00 — невалидное время.
 */
private fun parseMinute(raw: String): Int? {
    val digits = raw.filter { it.isDigit() }
    if (digits.length < 3) return null
    val hours = digits.take(digits.length - 2).toIntOrNull() ?: return null
    val minutes = digits.takeLast(2).toIntOrNull() ?: return null
    if (hours > MAX_HOUR || minutes > MAX_MINUTE) return null
    return hours * MINUTES_IN_HOUR + minutes
}

private const val MINUTES_IN_HOUR = 60
private const val MAX_HOUR = 23
private const val MAX_MINUTE = 59

package com.silverchat.feature.profile.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.silverchat.core.designsystem.component.InlineSnackbar
import com.silverchat.core.designsystem.component.ProfileBanner
import com.silverchat.core.designsystem.component.SettingsSection
import com.silverchat.core.designsystem.component.SilverButton
import com.silverchat.core.designsystem.component.SilverButtonVariant
import com.silverchat.core.designsystem.component.SilverTopBar
import com.silverchat.core.designsystem.component.UserAvatar
import com.silverchat.core.designsystem.navigation.ProfileNavigator
import com.silverchat.core.designsystem.theme.ScAvatarSize
import com.silverchat.core.designsystem.theme.ScSpacing
import com.silverchat.core.designsystem.theme.ScTheme
import com.silverchat.core.model.AvatarAnimationType
import com.silverchat.feature.profile.EditProfileEvent
import com.silverchat.feature.profile.EditProfileViewModel

/**
 * Редактор аватара.
 *
 * Выбор файла выполняется хостом (`:app`) через `ActivityResultContracts`,
 * а не внутри feature-модуля: Compose-модуль не должен зависеть от
 * `Activity`. Экран отдаёт URI наружу колбэком [onPickMedia] и получает
 * результат обратно через [pickedUri].
 *
 * Тип анимации выбирается явно. Видео-аватар — Premium-перк, и проверка
 * выполняется в `UploadAvatarUseCase`: если подписки нет, вернётся
 * `ScError.PremiumRequired`, а экран покажет причину и кнопку оформления.
 */
@Composable
fun AvatarEditorScreen(
    navigator: ProfileNavigator,
    modifier: Modifier = Modifier,
    onPickMedia: (allowAnimated: Boolean) -> Unit = {},
    pickedUri: String? = null,
    viewModel: EditProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()

    var animationType by remember { mutableStateOf(AvatarAnimationType.NONE) }

    LaunchedEffect(events) {
        if (events is EditProfileEvent.AvatarUpdated) navigator.back()
        if (events != null) viewModel.consumeEvent()
    }

    Column(
        modifier
            .fillMaxSize()
            .background(ScTheme.background)
            .verticalScroll(rememberScrollState()),
    ) {
        SilverTopBar(title = "Аватар", onBack = navigator::back)

        // ── Предпросмотр ─────────────────────────────────────────────────
        Box(
            Modifier.fillMaxWidth().padding(vertical = ScSpacing.lg),
            contentAlignment = Alignment.Center,
        ) {
            UserAvatar(size = ScAvatarSize.profileHero, allowAnimation = false)
        }

        // ── Тип анимации ─────────────────────────────────────────────────
        SettingsSection("Тип аватара")
        AvatarAnimationType.entries.forEach { type ->
            AnimationChoice(
                type = type,
                selected = animationType == type,
                isPremium = state.isPremium,
                onSelect = { animationType = type },
            )
        }

        Spacer(Modifier.height(ScSpacing.md))

        // ── Выбор файла ──────────────────────────────────────────────────
        SilverButton(
            text = if (pickedUri == null) "Выбрать файл" else "Файл выбран",
            onClick = { onPickMedia(animationType != AvatarAnimationType.NONE) },
            variant = SilverButtonVariant.SECONDARY,
            leadingIcon = Icons.Filled.Image,
            modifier = Modifier.padding(horizontal = ScSpacing.md),
        )

        pickedUri?.let { uri ->
            Spacer(Modifier.height(ScSpacing.sm))
            Text(
                text = shortenUri(uri),
                color = ScTheme.textTertiary,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = ScSpacing.md),
            )
        }

        events?.let { event ->
            Spacer(Modifier.height(ScSpacing.sm))
            InlineSnackbar(
                message = when (event) {
                    EditProfileEvent.AvatarUpdated -> "Аватар обновлён"
                    is EditProfileEvent.Error -> event.message
                    else -> return@let
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = ScSpacing.md),
            )
        }

        Spacer(Modifier.height(ScSpacing.lg))

        SilverButton(
            text = if (state.isUploadingAvatar) "Загружаем…" else "Загрузить аватар",
            onClick = { pickedUri?.let { viewModel.uploadAvatar(it, animationType) } },
            enabled = pickedUri != null && !state.isUploadingAvatar,
            loading = state.isUploadingAvatar,
            modifier = Modifier.padding(horizontal = ScSpacing.md),
        )

        Spacer(Modifier.height(ScSpacing.xxl))
    }
}

/* =========================================================================
   БАННЕР
   ========================================================================= */

/**
 * Редактор баннера профиля.
 *
 * Устроен как [AvatarEditorScreen]: тот же выбор файла через хост и та же
 * Premium-проверка, но для анимированного баннера перк другой —
 * `ANIMATED_BANNER`. Lottie здесь тоже платный (в отличие от аватара):
 * баннер занимает весь верх экрана, и анимация на нём заметно влияет
 * на производительность прокрутки.
 */
@Composable
fun BannerEditorScreen(
    navigator: ProfileNavigator,
    modifier: Modifier = Modifier,
    onPickMedia: (allowAnimated: Boolean) -> Unit = {},
    pickedUri: String? = null,
    viewModel: EditProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()

    var animationType by remember { mutableStateOf(AvatarAnimationType.NONE) }

    LaunchedEffect(events) {
        if (events is EditProfileEvent.BannerUpdated) navigator.back()
        if (events != null) viewModel.consumeEvent()
    }

    Column(
        modifier
            .fillMaxSize()
            .background(ScTheme.background)
            .verticalScroll(rememberScrollState()),
    ) {
        SilverTopBar(title = "Баннер профиля", onBack = navigator::back)

        ProfileBanner(
            banner = null,
            allowAnimation = false,
            modifier = Modifier.fillMaxWidth().height(BANNER_PREVIEW_HEIGHT),
        )

        SettingsSection("Тип баннера")
        AvatarAnimationType.entries.forEach { type ->
            AnimationChoice(
                type = type,
                selected = animationType == type,
                // Любой анимированный баннер — Premium-перк
                isPremium = state.isPremium || type == AvatarAnimationType.NONE,
                onSelect = { animationType = type },
            )
        }

        Spacer(Modifier.height(ScSpacing.md))

        SilverButton(
            text = if (pickedUri == null) "Выбрать файл" else "Файл выбран",
            onClick = { onPickMedia(animationType != AvatarAnimationType.NONE) },
            variant = SilverButtonVariant.SECONDARY,
            leadingIcon = Icons.Filled.Image,
            modifier = Modifier.padding(horizontal = ScSpacing.md),
        )

        events?.let { event ->
            Spacer(Modifier.height(ScSpacing.sm))
            InlineSnackbar(
                message = when (event) {
                    EditProfileEvent.BannerUpdated -> "Баннер обновлён"
                    is EditProfileEvent.Error -> event.message
                    else -> return@let
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = ScSpacing.md),
            )
        }

        Spacer(Modifier.height(ScSpacing.lg))

        SilverButton(
            text = if (state.isUploadingBanner) "Загружаем…" else "Загрузить баннер",
            onClick = { pickedUri?.let { viewModel.uploadBanner(it, animationType) } },
            enabled = pickedUri != null && !state.isUploadingBanner,
            loading = state.isUploadingBanner,
            modifier = Modifier.padding(horizontal = ScSpacing.md),
        )

        Spacer(Modifier.height(ScSpacing.xxl))
    }
}

private val BANNER_PREVIEW_HEIGHT = 150.dp

/* =========================================================================
   ВЫБОР ТИПА АНИМАЦИИ
   ========================================================================= */

/**
 * Строка выбора типа анимации.
 *
 * `isPremium` здесь — «доступно ли пользователю»: для баннера передаётся
 * `true` и на статичном типе, потому что статика доступна всем.
 * Заблокированный тип остаётся видимым, но неактивным: пользователь должен
 * понимать, что именно он получает с подпиской.
 */
@Composable
private fun AnimationChoice(
    type: AvatarAnimationType,
    selected: Boolean,
    isPremium: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = isPremium, onClick = onSelect)
            .padding(horizontal = ScSpacing.md, vertical = ScSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = type.titleRu,
                color = when {
                    !isPremium -> ScTheme.textTertiary
                    selected -> ScTheme.accent
                    else -> ScTheme.textPrimary
                },
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
            Text(
                text = when {
                    !isPremium -> type.premiumHintRu
                    else -> type.hintRu
                },
                color = ScTheme.textTertiary,
                fontSize = 11.5.sp,
                lineHeight = 15.sp,
            )
        }
        if (!isPremium) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = "Требуется Premium",
                tint = ScTheme.premium,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(ScSpacing.sm))
        }
        Box(
            Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(if (selected && isPremium) ScTheme.accent else ScTheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            if (selected && isPremium) {
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

val AvatarAnimationType.titleRu: String
    get() = when (this) {
        AvatarAnimationType.NONE -> "Статичное изображение"
        AvatarAnimationType.LOTTIE -> "Lottie-анимация"
        AvatarAnimationType.VIDEO -> "Видео (зацикленное)"
        AvatarAnimationType.GIF -> "GIF"
    }

val AvatarAnimationType.hintRu: String
    get() = when (this) {
        AvatarAnimationType.NONE -> "JPG или PNG, до 5 МБ"
        AvatarAnimationType.LOTTIE -> "Векторная анимация, минимальный трафик"
        AvatarAnimationType.VIDEO -> "MP4 или WebP, зацикливается автоматически"
        AvatarAnimationType.GIF -> "Анимированный GIF, до 10 МБ"
    }

val AvatarAnimationType.premiumHintRu: String
    get() = when (this) {
        AvatarAnimationType.NONE -> "Доступно всем"
        AvatarAnimationType.LOTTIE -> "Доступно в SilverChat Premium"
        AvatarAnimationType.VIDEO -> "Доступно в SilverChat Premium"
        AvatarAnimationType.GIF -> "Доступно в SilverChat Premium"
    }

/**
 * Укорачивание content-URI для подписи.
 *
 * Полный URI вида `content://com.android.providers.media.documents/…`
 * не несёт полезной информации и ломает вёрстку, поэтому показываем
 * только хвост.
 */
private fun shortenUri(uri: String): String =
    if (uri.length <= MAX_URI_LABEL) uri else "…" + uri.takeLast(MAX_URI_LABEL)

private const val MAX_URI_LABEL = 42

package com.silverchat.app.media

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.silverchat.feature.profile.navigation.ProfileMediaPicker
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Выбор аватара и баннера профиля из `Activity`.
 *
 * ── Почему реализация в :app ────────────────────────────────────────────
 * `ProfileMediaPicker` объявлен в :feature:profile: экранам редактора нужен
 * выбор файла, но feature-модуль не зависит от `Activity` и не должен —
 * иначе его нельзя было бы ни протестировать, ни собрать отдельно. Порт
 * живёт во фиче, реализация — здесь, рядом с `ActivityResultContracts`.
 *
 * ── Почему состояние — Compose `mutableStateOf` ─────────────────────────
 * Интерфейс отдаёт `String?`, а не `StateFlow`, и экран читает его прямо в
 * композиции: `pickedUri = mediaPicker.pickedAvatarUri`. Обычный геттер
 * Compose не увидел бы — выбор файла вернулся бы из системного приложения,
 * а экран остался бы прежним, потому что ничего не сообщило снапшот-системе
 * об изменении.
 *
 * `mutableStateOf` решает это без смены контракта: чтение `value` внутри
 * композиции регистрируется как зависимость, и запись из корутины запускает
 * рекомпозицию именно этого экрана.
 *
 * ── Про время жизни гранта ──────────────────────────────────────────────
 * URI из Photo Picker действует, пока жива задача приложения. Экран
 * редактора загружает файл тем же сеансом (тап «Сохранить» → `uploadAvatar`),
 * поэтому грант не успевает истечь. Сохранять URI «на потом» нельзя: после
 * перезапуска процесса чтение упадёт с `SecurityException`.
 */
@Singleton
class ActivityProfileMediaPicker @Inject constructor(
    private val bridges: ActivityResultBridgeHolder,
) : ProfileMediaPicker {

    private val scope = CoroutineScope(SupervisorJob())

    private val avatarState = mutableStateOf<String?>(null)
    private val bannerState = mutableStateOf<String?>(null)

    override val pickedAvatarUri: String?
        get() = avatarState.value

    override val pickedBannerUri: String?
        get() = bannerState.value

    /**
     * @param allowAnimated true — разрешены видео и GIF. Статичный аватар
     *   выбирается только из изображений: лишний выбор видео в диалоге
     *   сбивает, если анимация всё равно не нужна.
     */
    override fun pickAvatar(allowAnimated: Boolean) = pick(allowAnimated, avatarState)

    override fun pickBanner(allowAnimated: Boolean) = pick(allowAnimated, bannerState)

    private fun pick(allowAnimated: Boolean, target: MutableState<String?>) {
        // Предыдущий выбор сбрасываем ДО запуска: если пользователь отменит
        // системный диалог, экран не должен показывать старый файл как новый.
        target.value = null

        val mediaType = if (allowAnimated) {
            BridgeMediaType.IMAGE_AND_VIDEO
        } else {
            BridgeMediaType.IMAGE_ONLY
        }

        scope.launch {
            val uri = bridges.exclusive { bridge ->
                bridge.pickMedia(
                    mediaType = mediaType,
                    allowMultiple = false,
                    maxCount = 1,
                )
            }.orEmpty().firstOrNull()

            // `null` — отмена выбора. Состояние уже сброшено выше, поэтому
            // отдельно сообщать экрану об отмене не нужно.
            if (uri != null) target.value = uri.toString()
        }
    }
}

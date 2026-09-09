package com.silverchat.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.silverchat.core.designsystem.navigation.FeatureNavigation
import com.silverchat.core.designsystem.navigation.Routes
import com.silverchat.core.designsystem.navigation.TopLevelDestination
import com.silverchat.core.domain.repository.AppTheme
import com.silverchat.core.domain.repository.ChatRepository
import com.silverchat.core.domain.repository.SettingsRepository
import com.silverchat.core.security.AuthState
import com.silverchat.core.security.TokenStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Состояние оболочки приложения.
 *
 * Одна ViewModel на весь `MainActivity`: ей нужны данные, которые живут
 * дольше любого экрана — тема, авторизация, общее число непрочитанных и
 * реестр графов навигации. Экраны фич берут свои ViewModel отдельно,
 * через `hiltViewModel()` внутри своих `composable {}`.
 *
 * ── Зачем здесь тема ────────────────────────────────────────────────────
 * `SilverChatTheme` обязана оборачивать ВСЁ содержимое, включая диалоги и
 * нижнюю панель. Если бы тему применял каждый экран сам, смена палитры в
 * настройках перерисовала бы только открытый экран, а остальные остались бы
 * старыми до перезахода.
 *
 * ── Зачем `Eagerly` ─────────────────────────────────────────────────────
 * Подписка начинается сразу, а не по первому подписчику: тема должна быть
 * известна ДО первой композиции, иначе на старте виден кадр с палитрой по
 * умолчанию, а затем резкий переход к пользовательской.
 */
@HiltViewModel
class AppViewModel @Inject constructor(
    settings: SettingsRepository,
    chatRepository: ChatRepository,
    tokenStore: TokenStore,
    features: Set<FeatureNavigation>,
) : ViewModel() {

    /**
     * Реестр графов навигации.
     *
     * Приходит из `AppNavigationModule` и не меняется за время жизни
     * процесса, поэтому лежит отдельным полем, а не в потоке состояния.
     */
    val featureNavigations: Set<FeatureNavigation> = features

    val uiState: StateFlow<AppUiState> = combine(
        // Оформление: три потока настроек, которые меняются вместе
        combine(
            settings.observeTheme(),
            settings.observeFontSizeScale(),
            settings.observeAnimationsEnabled(),
        ) { theme, fontScale, animations ->
            Appearance(theme, fontScale, animations)
        },
        // Сессия: непрочитанные и факт входа
        combine(
            chatRepository.observeTotalUnread(),
            tokenStore.authState.map { state -> state.asLoggedInOrNull() },
        ) { unread, loggedIn ->
            Session(unread, loggedIn)
        },
    ) { appearance, session ->
        AppUiState(
            theme = appearance.theme,
            fontScale = appearance.fontScale,
            animationsEnabled = appearance.animations,
            unreadTotal = session.unreadTotal,
            isLoggedIn = session.loggedIn,
            // Пока состояние не определено, граф не создаётся вовсе (см.
            // SilverChatApp), поэтому значение здесь ни на что не влияет.
            startDestination = if (session.loggedIn == true) {
                TopLevelDestination.START.route
            } else {
                Routes.AUTH_PHONE
            },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = AppUiState(),
    )

    /**
     * Показывать ли нижнюю навигацию на текущем маршруте.
     *
     * Панель видна только на четырёх вкладках верхнего уровня. На вложенных
     * экранах (диалог, карточка пользователя, лот маркета) она скрыта: там
     * нужна обратная навигация, а не переключение разделов. Сравнение идёт
     * с `TopLevelDestination.route`, а не со списком строк, — иначе при
     * добавлении вкладки пришлось бы править два места.
     *
     * Авторизация, сторис-плеер и звонок вкладками не являются, поэтому
     * попадают в `false` автоматически.
     */
    fun showBottomBar(route: String?): Boolean =
        route != null && TopLevelDestination.entries.any { it.route == route }

    /** Группировка потоков оформления: `combine` принимает не больше пяти. */
    private data class Appearance(
        val theme: AppTheme,
        val fontScale: Float,
        val animations: Boolean,
    )

    private data class Session(
        val unreadTotal: Int,
        val loggedIn: Boolean?,
    )
}

/**
 * Трёхзначное состояние авторизации.
 *
 * `null` — приложение ещё определяет, есть ли валидный токен
 * ([AuthState.Loading]). Отличать это от «не вошёл» обязательно: иначе
 * первый кадр построил бы граф с экраном входа, а через мгновение вошедший
 * пользователь увидел бы переход на список чатов — заметную вспышку на старте.
 */
private fun AuthState.asLoggedInOrNull(): Boolean? = when (this) {
    is AuthState.Authenticated -> true
    is AuthState.LoggedOut -> false
    is AuthState.Expired -> false
    AuthState.Loading -> null
}

/**
 * Снимок состояния оболочки.
 *
 * Значения по умолчанию соответствуют «первому кадру»: системная тема,
 * обычный шрифт, анимации включены. Авторизация при этом НЕ определена —
 * и до её определения навигационный граф не создаётся, а на экране
 * удерживается системный splash.
 */
data class AppUiState(
    val theme: AppTheme = AppTheme(),
    val fontScale: Float = 1f,
    val animationsEnabled: Boolean = true,
    val unreadTotal: Int = 0,
    /** `null` — состояние авторизации ещё определяется; см. [AppViewModel]. */
    val isLoggedIn: Boolean? = null,
    val startDestination: String = Routes.AUTH_PHONE,
)

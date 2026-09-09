package com.silverchat.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.rememberNavController
import com.silverchat.app.navigation.AppDeepLink
import com.silverchat.app.navigation.AppNavHost
import com.silverchat.core.designsystem.glass.GlassState
import com.silverchat.core.designsystem.navigation.Routes
import com.silverchat.core.designsystem.navigation.SilverChatBottomBar
import com.silverchat.core.designsystem.navigation.TopLevelDestination
import com.silverchat.core.designsystem.theme.SilverChatTheme

/**
 * Корень интерфейса: тема, каркас с нижней навигацией и граф экранов.
 *
 * ── Тема оборачивает ВСЁ ────────────────────────────────────────────────
 * `SilverChatTheme` стоит снаружи `Scaffold`, поэтому палитра, масштаб шрифта
 * и отключённые анимации применяются и к нижней панели, и к любому экрану
 * фичи. Смена темы в настройках перерисовывает приложение целиком, без
 * перезапуска Activity.
 *
 * ── Граф создаётся один раз ─────────────────────────────────────────────
 * `startDestination` у `NavHost` читается только при создании графа: поменять
 * его позже нельзя. Поэтому значение фиксируется в `remember`, а смена
 * авторизации обрабатывается явным переходом с очисткой стека
 * ([navigateToRoot]) — иначе после выхода «назад» вернул бы пользователя
 * в список чатов чужого аккаунта.
 *
 * ── Стекловидная панель ─────────────────────────────────────────────────
 * `containerColor = Color.Transparent`: нижняя панель использует
 * `glassSurface` с размытием, и под ней должно быть видно содержимое экрана.
 * Непрозрачный фон контейнера убил бы эффект стекла.
 */
@Composable
fun SilverChatApp(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier,
    deepLink: AppDeepLink? = null,
    onDeepLinkConsumed: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    SilverChatTheme(
        theme = state.theme,
        fontScale = state.fontScale,
        animations = state.animationsEnabled,
    ) {
        // До определения авторизации графа нет: splash удерживается в
        // MainActivity, а здесь просто нечего рисовать.
        if (state.isLoggedIn == null) return@SilverChatTheme

        val navController = rememberNavController()
        val glassState = remember { GlassState() }
        val startDestination = remember(state.startDestination) { state.startDestination }

        var currentRoute by remember { mutableStateOf<String?>(null) }
        var knownAuth by remember { mutableStateOf(state.isLoggedIn) }

        /* Текущий маршрут нужен, чтобы решить, видна ли нижняя панель.
         * `currentBackStackEntryFlow` — единственный надёжный источник:
         * значение обновляется и при переходах внутри фич, и при «назад». */
        LaunchedEffect(navController) {
            navController.currentBackStackEntryFlow.collect { entry ->
                currentRoute = entry.destination.route
            }
        }

        /* Вход и выход: заменяем корень стека.
         * Первый проход пропускаем — граф и так создан с нужного экрана. */
        LaunchedEffect(state.isLoggedIn) {
            val loggedIn = state.isLoggedIn ?: return@LaunchedEffect
            val previous = knownAuth
            knownAuth = loggedIn
            if (previous == loggedIn) return@LaunchedEffect

            navController.navigateToRoot(
                if (loggedIn) TopLevelDestination.START.route else Routes.AUTH_PHONE,
            )
        }

        /* Переход по ссылке или тап по уведомлению. */
        LaunchedEffect(deepLink) {
            val link = deepLink ?: return@LaunchedEffect
            when (link) {
                is AppDeepLink.Route -> navController.navigate(link.route) {
                    launchSingleTop = true
                }

                // URI сопоставляет NavController по navDeepLink из графов фич;
                // необработанный URI не приводит к падению — переход просто
                // не выполняется.
                is AppDeepLink.UriLink -> runCatching { navController.navigate(link.uri) }
            }
            onDeepLinkConsumed()
        }

        Scaffold(
            modifier = modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            bottomBar = {
                if (viewModel.showBottomBar(currentRoute)) {
                    SilverChatBottomBar(
                        selected = TopLevelDestination.fromRoute(currentRoute)
                            ?: TopLevelDestination.START,
                        glassState = glassState,
                        onDestinationSelected = { destination ->
                            navController.navigateToTopLevel(destination)
                        },
                        unreadTotal = state.unreadTotal,
                    )
                }
            },
        ) { innerPadding ->
            AppNavHost(
                navController = navController,
                features = viewModel.featureNavigations,
                startDestination = startDestination,
                animationsEnabled = state.animationsEnabled,
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
            )
        }
    }
}

/**
 * Переключение вкладок нижней навигации.
 *
 * Три флага вместе дают поведение Telegram:
 *  - `popUpTo(startDestination) { saveState = true }` — стек вкладки
 *    сохраняется, а не уничтожается;
 *  - `restoreState = true` — возврат на вкладку восстанавливает её прокрутку
 *    и открытый внутри экран;
 *  - `launchSingleTop = true` — повторный тап по активной вкладке не создаёт
 *    вторую копию экрана в стеке.
 */
private fun NavController.navigateToTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * Замена корня стека: вход, выход и смена аккаунта.
 *
 * `inclusive = true` убирает и стартовый экран, иначе после выхода «назад»
 * вернул бы пользователя в список чатов уже разлогиненной сессии.
 */
private fun NavController.navigateToRoot(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) {
            inclusive = true
            saveState = false
        }
        launchSingleTop = true
        restoreState = false
    }
}

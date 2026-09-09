package com.silverchat.feature.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.silverchat.core.designsystem.navigation.FeatureNavigation
import com.silverchat.core.designsystem.navigation.RouteBuilder
import com.silverchat.core.designsystem.navigation.Routes
import com.silverchat.core.designsystem.navigation.SettingsNavigator
import com.silverchat.core.designsystem.navigation.TopLevelDestination
import com.silverchat.feature.settings.screen.AppLockScreen
import com.silverchat.feature.settings.screen.AppearanceScreen
import com.silverchat.feature.settings.screen.DataAndStorageScreen
import com.silverchat.feature.settings.screen.DevicesScreen
import com.silverchat.feature.settings.screen.LanguageScreen
import com.silverchat.feature.settings.screen.NotificationsScreen
import com.silverchat.feature.settings.screen.PrivacyScreen
import com.silverchat.feature.settings.screen.SettingsScreen
import com.silverchat.feature.settings.screen.ThemesScreen

/**
 * Граф фичи «Настройки» — вкладка нижней навигации.
 *
 * Настройки — единственный раздел, где экраны не связаны данными между собой:
 * тема, уведомления и язык независимы. Поэтому каждый подэкран получает
 * собственную `SettingsViewModel` через `hiltViewModel()` — экземпляры
 * разные, но все читают один DataStore, и изменения видны сразу везде.
 *
 * Экран устройств использует отдельную [com.silverchat.feature.settings.DevicesViewModel]:
 * сессии грузятся suspend-запросом, а не наблюдаются потоком.
 *
 * Переход в админ-панель ведёт в граф фичи `:feature:admin`. Модуль настроек
 * не зависит от него напрямую: `:app` связывает графы, а здесь вызывается
 * только [SettingsNavigator.openAdminPanel].
 */
class SettingsNavigation : FeatureNavigation {

    override val route: String = Routes.SETTINGS

    override val topLevelDestination: TopLevelDestination = TopLevelDestination.SETTINGS

    override fun NavGraphBuilder.registerGraph(navController: NavHostController) {
        val navigator: SettingsNavigator = NavControllerSettingsNavigator(navController)

        // ── Корень раздела (вкладка) ────────────────────────────────────
        composable(Routes.SETTINGS) {
            SettingsScreen(navigator = navigator)
        }

        // ── Внешний вид ─────────────────────────────────────────────────
        composable(Routes.SETTINGS_APPEARANCE) {
            AppearanceScreen(navigator = navigator)
        }

        // ── Темы и обои ─────────────────────────────────────────────────
        composable(Routes.SETTINGS_THEMES) {
            ThemesScreen(navigator = navigator)
        }

        // ── Конфиденциальность и чёрный список ──────────────────────────
        composable(Routes.SETTINGS_PRIVACY) {
            PrivacyScreen(navigator = navigator)
        }

        // ── Уведомления ─────────────────────────────────────────────────
        composable(Routes.SETTINGS_NOTIFICATIONS) {
            NotificationsScreen(navigator = navigator)
        }

        // ── Данные и память ─────────────────────────────────────────────
        composable(Routes.SETTINGS_DATA) {
            DataAndStorageScreen(navigator = navigator)
        }

        // ── Активные устройства ─────────────────────────────────────────
        composable(Routes.SETTINGS_DEVICES) {
            DevicesScreen(navigator = navigator)
        }

        // ── Язык интерфейса ─────────────────────────────────────────────
        composable(Routes.SETTINGS_LANGUAGE) {
            LanguageScreen(navigator = navigator)
        }

        // ── Замок приложения ────────────────────────────────────────────
        composable(Routes.SETTINGS_APP_LOCK) {
            AppLockScreen(navigator = navigator)
        }
    }
}

/**
 * Навигатор настроек на базе `NavController`.
 *
 * `openProfile` ведёт в граф `:feature:profile`, а `openAdminPanel` —
 * в `:feature:admin`. Оба маршрута зарегистрированы в общем `NavHost`
 * на уровне `:app`, поэтому переходы работают между графами.
 */
internal class NavControllerSettingsNavigator(
    private val navController: NavController,
) : SettingsNavigator {

    override fun openAppearance() = navController.navigateSafely(Routes.SETTINGS_APPEARANCE)

    override fun openThemes() = navController.navigateSafely(Routes.SETTINGS_THEMES)

    override fun openPrivacy() = navController.navigateSafely(Routes.SETTINGS_PRIVACY)

    override fun openNotifications() = navController.navigateSafely(Routes.SETTINGS_NOTIFICATIONS)

    override fun openDataAndStorage() = navController.navigateSafely(Routes.SETTINGS_DATA)

    override fun openDevices() = navController.navigateSafely(Routes.SETTINGS_DEVICES)

    override fun openLanguage() = navController.navigateSafely(Routes.SETTINGS_LANGUAGE)

    override fun openAppLock() = navController.navigateSafely(Routes.SETTINGS_APP_LOCK)

    override fun openAdminPanel() = navController.navigateSafely(Routes.ADMIN)

    override fun openProfile(userId: String) =
        navController.navigateSafely(RouteBuilder.profileUser(userId))

    override fun back() {
        // Корень вкладки: при пустом стеке возвращаемся на сами настройки,
        // а не выходим из приложения
        if (!navController.popBackStack()) {
            navController.navigate(Routes.SETTINGS) {
                popUpTo(Routes.SETTINGS) { inclusive = false }
                launchSingleTop = true
            }
        }
    }
}

/** Защита от двойного тапа по строке настроек. */
private fun NavController.navigateSafely(route: String) {
    navigate(route) { launchSingleTop = true }
}

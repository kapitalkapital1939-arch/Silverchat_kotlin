package com.silverchat.feature.profile.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.silverchat.core.designsystem.navigation.FeatureNavigation
import com.silverchat.core.designsystem.navigation.NavDeepLinkSpec
import com.silverchat.core.designsystem.navigation.ProfileNavigator
import com.silverchat.core.designsystem.navigation.RouteBuilder
import com.silverchat.core.designsystem.navigation.Routes
import com.silverchat.core.designsystem.navigation.TopLevelDestination
import com.silverchat.feature.profile.screen.AvatarEditorScreen
import com.silverchat.feature.profile.screen.BannerEditorScreen
import com.silverchat.feature.profile.screen.EditProfileScreen
import com.silverchat.feature.profile.screen.ProfileScreen

/**
 * Граф фичи «Профиль» — вкладка нижней навигации.
 *
 * Профиль открывается двумя маршрутами: [Routes.PROFILE_SELF] для своей
 * страницы и [Routes.PROFILE_USER] с аргументом для чужой. Оба ведут на один
 * экран [ProfileScreen]: состав блоков одинаков, различается только набор
 * действий, и дублировать экран означало бы поддерживать две копии вёрстки.
 *
 * Deep link `silverchat://u/{username}` ведёт на чужой профиль. Публичная
 * ссылка на пользователя — основной способ привести человека извне
 * (соцсети, подпись в письме), поэтому она обязана открывать приложение
 * сразу на нужной странице, а не на списке чатов.
 *
 * Экраны редакторов аватара и баннера принимают `onPickMedia` и `pickedUri`
 * от хоста: выбор файла требует `ActivityResultContracts`, доступных только
 * в `Activity`, а feature-модуль от неё не зависит.
 */
class ProfileNavigation(
    private val mediaPicker: ProfileMediaPicker = ProfileMediaPicker.NoOp,
) : FeatureNavigation {

    override val route: String = Routes.PROFILE

    override val topLevelDestination: TopLevelDestination = TopLevelDestination.PROFILE

    override fun deepLinks(): List<NavDeepLinkSpec> = listOf(
        NavDeepLinkSpec(uriPattern = "silverchat://u/{username}"),
    )

    override fun NavGraphBuilder.registerGraph(navController: NavHostController) {
        val navigator: ProfileNavigator = NavControllerProfileNavigator(navController)

        // ── Свой профиль (вкладка) ──────────────────────────────────────
        composable(Routes.PROFILE_SELF) {
            ProfileScreen(navigator = navigator)
        }

        // ── Чужой профиль ───────────────────────────────────────────────
        // Два deep link: по ID и по публичному username. Экран один.
        composable(
            route = Routes.PROFILE_USER,
            arguments = listOf(navArgument(Routes.Args.USER_ID) { type = NavType.StringType }),
            deepLinks = listOf(
                navDeepLink { uriPattern = "silverchat://u/{username}" },
            ),
        ) {
            ProfileScreen(navigator = navigator)
        }

        // ── Редактирование ──────────────────────────────────────────────
        composable(Routes.PROFILE_EDIT) {
            EditProfileScreen(navigator = navigator)
        }

        // ── Редактор аватара ────────────────────────────────────────────
        composable(Routes.PROFILE_AVATAR) {
            AvatarEditorScreen(
                navigator = navigator,
                onPickMedia = mediaPicker::pickAvatar,
                pickedUri = mediaPicker.pickedAvatarUri,
            )
        }

        // ── Редактор баннера ────────────────────────────────────────────
        composable(Routes.PROFILE_BANNER) {
            BannerEditorScreen(
                navigator = navigator,
                onPickMedia = mediaPicker::pickBanner,
                pickedUri = mediaPicker.pickedBannerUri,
            )
        }

        // ── Местоположение и часы работы ────────────────────────────────
        // Редактируются в составе EditProfileScreen: это два блока одной формы,
        // и отдельные маршруты разорвали бы сохранение на два запроса.
        composable(Routes.PROFILE_LOCATION) {
            EditProfileScreen(navigator = navigator)
        }
        composable(Routes.PROFILE_HOURS) {
            EditProfileScreen(navigator = navigator)
        }

        // ── Архив сторис ────────────────────────────────────────────────
        // Экран живёт в :feature:stories; маршрут регистрируется там же,
        // а здесь остаётся переход через навигатор.
        composable(Routes.PROFILE_STORIES_ARCHIVE) {
            ProfileScreen(navigator = navigator)
        }
    }
}

/**
 * Мост к выбору файлов из `Activity`.
 *
 * Feature-модуль не зависит от `Activity`, поэтому выбор медиа делегируется
 * наружу. Реализацию предоставляет `:app`, где доступны
 * `ActivityResultContracts.PickVisualMedia`.
 *
 * `pickedAvatarUri` / `pickedBannerUri` — не `StateFlow` намеренно:
 * значение читается один раз при композиции экрана редактора, а повторный
 * выбор перезапускает композицию через пересоздание back stack entry.
 */
interface ProfileMediaPicker {

    val pickedAvatarUri: String?
    val pickedBannerUri: String?

    /** @param allowAnimated true — разрешены GIF/видео/Lottie. */
    fun pickAvatar(allowAnimated: Boolean)

    fun pickBanner(allowAnimated: Boolean)

    /** Заглушка для превью и модульных тестов: выбор файла недоступен. */
    object NoOp : ProfileMediaPicker {
        override val pickedAvatarUri: String? = null
        override val pickedBannerUri: String? = null
        override fun pickAvatar(allowAnimated: Boolean) = Unit
        override fun pickBanner(allowAnimated: Boolean) = Unit
    }
}

internal class NavControllerProfileNavigator(
    private val navController: NavController,
) : ProfileNavigator {

    override fun openEditProfile() = navController.navigateSafely(Routes.PROFILE_EDIT)

    override fun openAvatarEditor() = navController.navigateSafely(Routes.PROFILE_AVATAR)

    override fun openBannerEditor() = navController.navigateSafely(Routes.PROFILE_BANNER)

    override fun openLocationEditor() = navController.navigateSafely(Routes.PROFILE_LOCATION)

    override fun openWorkingHoursEditor() = navController.navigateSafely(Routes.PROFILE_HOURS)

    override fun openStoriesArchive() = navController.navigateSafely(Routes.PROFILE_STORIES_ARCHIVE)

    override fun openChat(chatId: String) =
        navController.navigateSafely(RouteBuilder.chatThread(chatId))

    override fun startCall(chatId: String, video: Boolean) =
        navController.navigateSafely(RouteBuilder.call(chatId, video))

    override fun openPremium() = navController.navigateSafely(Routes.MARKET_PREMIUM)

    override fun openMarket() = navController.navigateSafely(Routes.MARKET)

    override fun back() {
        // Вкладка профиля — корень своей ветки: при пустом стеке возвращаемся
        // на сам профиль, а не выходим из приложения
        if (!navController.popBackStack()) {
            navController.navigate(Routes.PROFILE_SELF) {
                popUpTo(Routes.PROFILE_SELF) { inclusive = false }
                launchSingleTop = true
            }
        }
    }
}

/** Защита от двойного тапа: повторный переход на тот же маршрут не плодит стек. */
private fun NavController.navigateSafely(route: String) {
    navigate(route) { launchSingleTop = true }
}

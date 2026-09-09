package com.silverchat.feature.market.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.silverchat.core.designsystem.glass.GlassState
import com.silverchat.core.designsystem.navigation.FeatureNavigation
import com.silverchat.core.designsystem.navigation.MarketNavigator
import com.silverchat.core.designsystem.navigation.NavDeepLinkSpec
import com.silverchat.core.designsystem.navigation.RouteBuilder
import com.silverchat.core.designsystem.navigation.Routes
import com.silverchat.core.designsystem.navigation.TopLevelDestination
import com.silverchat.feature.market.screen.GiftsScreen
import com.silverchat.feature.market.screen.ListingDetailScreen
import com.silverchat.feature.market.screen.MarketScreen
import com.silverchat.feature.market.screen.PremiumScreen
import com.silverchat.feature.market.screen.SellUsernameScreen
import com.silverchat.feature.market.screen.WalletScreen

/**
 * Граф фичи «Маркет» — вкладка нижней навигации.
 *
 * Маркет занимает одну вкладку, но содержит семь экранов: витрина, лот,
 * предложения, продажа, подарки, отправка подарка, Premium и кошелёк.
 * Все они зарегистрированы в одном графе, чтобы :app не знал о внутреннем
 * устройстве фичи.
 *
 * Deep link `silverchat://market/{listingId}` ведёт сразу на лот: ссылку
 * на юзернейм можно получить вне приложения (в соцсетях, в переписке),
 * и открывать её через витрину с поиском было бы лишним шагом.
 */
class MarketNavigation : FeatureNavigation {

    override val route: String = Routes.MARKET

    override val topLevelDestination: TopLevelDestination = TopLevelDestination.MARKET

    override fun deepLinks(): List<NavDeepLinkSpec> = listOf(
        NavDeepLinkSpec(uriPattern = "silverchat://market/{listingId}"),
    )

    override fun NavGraphBuilder.registerGraph(navController: NavHostController) {
        val navigator: MarketNavigator = NavControllerMarketNavigator(navController)
        val glassState = GlassState()

        // ── Витрина (вкладка) ───────────────────────────────────────────
        composable(Routes.MARKET) {
            MarketScreen(navigator = navigator, glassState = glassState)
        }

        // ── Карточка лота ───────────────────────────────────────────────
        composable(
            route = Routes.MARKET_USERNAME_DETAIL,
            arguments = listOf(navArgument(Routes.Args.LISTING_ID) { type = NavType.StringType }),
            deepLinks = listOf(
                androidx.navigation.navDeepLink { uriPattern = "silverchat://market/{listingId}" },
            ),
        ) {
            ListingDetailScreen(navigator = navigator)
        }

        // ── Встречные предложения по лоту ───────────────────────────────
        composable(
            route = Routes.MARKET_OFFERS,
            arguments = listOf(navArgument(Routes.Args.LISTING_ID) { type = NavType.StringType }),
        ) {
            ListingDetailScreen(navigator = navigator, showOffers = true)
        }

        // ── Продажа своего юзернейма ────────────────────────────────────
        composable(Routes.MARKET_SELL) {
            SellUsernameScreen(navigator = navigator)
        }

        // ── Подарки ─────────────────────────────────────────────────────
        composable(Routes.MARKET_GIFTS) {
            GiftsScreen(navigator = navigator)
        }

        // ── Отправка подарка ────────────────────────────────────────────
        composable(
            route = Routes.MARKET_GIFT_SEND,
            arguments = listOf(navArgument(Routes.Args.GIFT_ID) { type = NavType.StringType }),
        ) {
            GiftsScreen(navigator = navigator, sendMode = true)
        }

        // ── Premium ─────────────────────────────────────────────────────
        composable(Routes.MARKET_PREMIUM) {
            PremiumScreen(navigator = navigator)
        }

        // ── Кошелёк и история операций ──────────────────────────────────
        composable(Routes.MARKET_WALLET) {
            WalletScreen(navigator = navigator)
        }
    }
}

internal class NavControllerMarketNavigator(
    private val navController: NavController,
) : MarketNavigator {

    override fun openListing(listingId: String) =
        navController.navigateSafely(RouteBuilder.marketListing(listingId))

    override fun openOffers(listingId: String) =
        navController.navigateSafely(RouteBuilder.marketOffers(listingId))

    override fun openSell() = navController.navigateSafely(Routes.MARKET_SELL)

    override fun openGifts() = navController.navigateSafely(Routes.MARKET_GIFTS)

    override fun openGiftSend(giftId: String) =
        navController.navigateSafely(RouteBuilder.giftSend(giftId))

    override fun openPremium() = navController.navigateSafely(Routes.MARKET_PREMIUM)

    override fun openWallet() = navController.navigateSafely(Routes.MARKET_WALLET)

    override fun openProfile(userId: String) =
        navController.navigateSafely(RouteBuilder.profileUser(userId))

    override fun back() {
        // Вкладка маркета — корень своей ветки: при пустом стеке возвращаемся
        // на витрину, а не выходим из приложения
        if (!navController.popBackStack()) {
            navController.navigate(Routes.MARKET) {
                popUpTo(Routes.MARKET) { inclusive = false }
                launchSingleTop = true
            }
        }
    }

    /** Защита от двойного тапа по карточке лота. */
    private fun navigateSafely(route: String) {
        navController.navigate(route) { launchSingleTop = true }
    }
}

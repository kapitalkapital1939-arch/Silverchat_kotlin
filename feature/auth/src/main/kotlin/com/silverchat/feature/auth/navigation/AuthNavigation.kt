package com.silverchat.feature.auth.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.silverchat.core.designsystem.navigation.FeatureNavigation
import com.silverchat.core.designsystem.navigation.RouteBuilder
import com.silverchat.core.designsystem.navigation.Routes
import com.silverchat.feature.auth.screen.OtpScreen
import com.silverchat.feature.auth.screen.PhoneScreen
import com.silverchat.feature.auth.screen.RegisterScreen

/**
 * Граф авторизации.
 *
 * Авторизация — единственный граф, который показывается ДО основного
 * (с нижней панелью). Решение о том, какой граф активен, принимает :app
 * по состоянию [com.silverchat.core.domain.repository.AuthRepository.sessionState]:
 * пока сессии нет, NavHost = auth; после входа — main.
 *
 * Это разделение важно: если держать экраны входа внутри общего графа,
 * пользователь сможет попасть в чаты кнопкой «назад» без токена,
 * а приложение упадёт на первом же запросе.
 */
class AuthNavigation : FeatureNavigation {

    override val route: String = Routes.AUTH_PHONE

    override fun NavGraphBuilder.registerGraph(navController: NavHostController) {

        composable(Routes.AUTH_PHONE) {
            PhoneScreen(
                onOtpReady = { phone -> navController.navigate(RouteBuilder.authOtp(phone)) },
            )
        }

        composable(
            route = Routes.AUTH_OTP,
            arguments = listOf(
                navArgument(Routes.Args.PHONE) { type = NavType.StringType },
            ),
        ) { entry ->
            val phone = entry.arguments?.getString(Routes.Args.PHONE).orEmpty()
            OtpScreen(
                phone = phone,
                onRegistered = {
                    // Регистрация завершена: очищаем стек авторизации полностью,
                    // чтобы «назад» не вернул на экран ввода номера
                    navController.popBackStack(Routes.AUTH_PHONE, inclusive = true)
                },
                onNeedProfile = {
                    navController.navigate(Routes.AUTH_REGISTER) {
                        popUpTo(Routes.AUTH_PHONE) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.AUTH_REGISTER) {
            RegisterScreen(
                onDone = {
                    navController.popBackStack(Routes.AUTH_PHONE, inclusive = true)
                },
            )
        }
    }
}

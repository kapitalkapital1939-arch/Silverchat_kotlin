package com.silverchat.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import com.silverchat.core.designsystem.navigation.FeatureNavigation
import com.silverchat.core.designsystem.navigation.NavigationTransitions

/**
 * Корневой граф навигации, собранный из графов фич.
 *
 * ── Как это работает ────────────────────────────────────────────────────
 * Каждая фича реализует [FeatureNavigation] и регистрирует СВОИ маршруты в
 * `registerGraph`. :app не знает ни одного экрана: он просто обходит набор
 * реализаций. Поэтому:
 *  - фичи не зависят друг от друга (только от строк в `Routes`);
 *  - удаление фичи — удаление одной строки в `AppNavigationModule`;
 *  - deep link объявлен рядом с графом фичи, а не в трёх местах сразу.
 *
 * ── Про два получателя `registerGraph` ──────────────────────────────────
 * `registerGraph` объявлен как extension-функция внутри интерфейса, то есть
 * у неё два получателя: экземпляр фичи (dispatch) и `NavGraphBuilder`
 * (extension). `with(feature) { registerGraph(...) }` внутри билдера
 * `NavHost` даёт оба, поэтому маршруты фичи попадают прямо в корневой граф —
 * без вложенного `navigation {}` и без лишнего уровня в стеке возврата.
 *
 * ── Про порядок обхода ──────────────────────────────────────────────────
 * Порядок в наборе не важен: маршруты уникальны, а стартовый задаётся
 * отдельно параметром [startDestination]. `Set` намеренно не упорядочен —
 * попытка сделать порядок значимым создала бы скрытую связь между модулями.
 */
@Composable
fun AppNavHost(
    navController: NavHostController,
    features: Set<FeatureNavigation>,
    startDestination: String,
    animationsEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        enterTransition = NavigationTransitions.enterTransition(animationsEnabled),
        exitTransition = NavigationTransitions.exitTransition(animationsEnabled),
        popEnterTransition = NavigationTransitions.popEnterTransition(animationsEnabled),
        popExitTransition = NavigationTransitions.popExitTransition(animationsEnabled),
    ) {
        features.forEach { feature ->
            // `this` здесь — NavGraphBuilder, `feature` — получатель интерфейса
            with(feature) {
                registerGraph(navController)
            }
        }
    }
}

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * `silverchat.android.feature` — модуль фичи (chats, market, profile, settings,
 * stories, calls, admin, auth).
 *
 * Контракт слоёв зашит в самом плагине: фича ВИДИТ domain/designsystem/model,
 * но НЕ видит network/database. Доступ к данным — только через интерфейсы
 * репозиториев из :core:domain. Так архитектура защищена на уровне сборки,
 * а не только договорённостью в README.
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("silverchat.android.library")
        pluginManager.apply("silverchat.android.compose")
        pluginManager.apply("silverchat.android.hilt")

        dependencies {
            add("implementation", project(":core:model"))
            add("implementation", project(":core:common"))
            add("implementation", project(":core:domain"))
            add("implementation", project(":core:designsystem"))

            add("testImplementation", project(":core:testing"))
            add("androidTestImplementation", project(":core:testing"))
        }
    }
}

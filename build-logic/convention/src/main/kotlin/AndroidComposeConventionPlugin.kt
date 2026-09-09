import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension

/**
 * `silverchat.android.compose` — добавляет Compose к любому Android-модулю.
 *
 * Ключевое: Compose BOM подключается ЗДЕСЬ и только здесь, поэтому во всех
 * модулях androidx.compose.* резолвится в одну версию. Именно разъезд версий
 * compose-артефактов порождает «Compose Compiler requires Compose Runtime X»
 * и конфликты в Gradle Module Metadata.
 */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        /* Плагин обязан работать и в :app, и в библиотеках.
         *
         * `com.android.application` и `com.android.library` взаимоисключающи,
         * а :app применяет свой базовый плагин ДО этого. Поэтому библиотеку
         * добавляем только тогда, когда ни один Android-плагин ещё не применён,
         * а расширение настраиваем по фактическому типу модуля:
         * ApplicationExtension не является LibraryExtension, и обращение к
         * библиотеке в application-модуле уронило бы конфигурацию. */
        val isApplication = pluginManager.hasPlugin("com.android.application")
        if (!isApplication && !pluginManager.hasPlugin("com.android.library")) {
            pluginManager.apply("com.android.library")
        }
        pluginManager.apply("org.jetbrains.kotlin.android")
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

        if (isApplication) {
            // configureApplication уже вызван плагином silverchat.android.application
            extensions.configure<ApplicationExtension> {
                configureCompose(this)
            }
        } else {
            extensions.configure<LibraryExtension> {
                configureAndroid(this, isApplication = false)
                configureCompose(this)
            }
        }
        configureKotlin()

        // Отчёты Compose-компилятора включаются флагом CI (-Psilverchat.compose.reports=true)
        extensions.findByType(ComposeCompilerGradlePluginExtension::class.java)?.apply {
            val reportsEnabled = providers
                .gradleProperty("silverchat.compose.reports")
                .orNull == "true"
            includeMetrics.set(reportsEnabled)
            includeReportCategories.set(reportsEnabled)
            metricsOutputDirectory.set(layout.buildDirectory.dir("compose-metrics"))
            reportsOutputDirectory.set(layout.buildDirectory.dir("compose-reports"))
        }

        dependencies {
            val composeBom = platform(libs.findLibrary("androidx.compose.bom").get())
            add("implementation", composeBom)
            add("androidTestImplementation", composeBom)

            add("implementation", libs.findBundle("compose.core").get())
            add("implementation", libs.findLibrary("androidx.activity.compose").get())
            add("implementation", libs.findBundle("lifecycle").get())
            add("implementation", libs.findLibrary("androidx.navigation.compose").get())
            add("implementation", libs.findLibrary("hilt.navigation.compose").get())
            add("implementation", libs.findLibrary("coil.compose").get())

            add("debugImplementation", libs.findLibrary("androidx.compose.ui.tooling").get())
            add("debugImplementation", libs.findLibrary("androidx.compose.ui.test.manifest").get())
            add("androidTestImplementation", libs.findBundle("compose.test").get())
        }
    }
}

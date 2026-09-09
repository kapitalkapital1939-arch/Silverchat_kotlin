import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * `silverchat.android.application` — применяется только в :app.
 */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")

        // ktlint + detekt: единые правила стиля и анализа для всех модулей
        pluginManager.apply("silverchat.quality")
        // См. gradle.properties: android.builtInKotlin=false — Kotlin-плагин
        // применяется явно и ОДИНАКОВО во всех модулях.
        pluginManager.apply("org.jetbrains.kotlin.android")

        extensions.configure<ApplicationExtension> {
            configureApplication(this)
        }
        configureKotlin()

        dependencies {
            add("coreLibraryDesugaring", libs.findLibrary("desugar.jdk.libs").get())
        }
    }
}

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * `silverchat.android.library` — базовый Android-модуль без Compose.
 * Применяется в :core:network, :core:database, :core:security, :core:media,
 * :core:webrtc, :core:notifications, :core:testing.
 */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        // См. gradle.properties → android.builtInKotlin=false:
        // Kotlin-плагин применяется явно и одинаково во всех модулях.
        pluginManager.apply("org.jetbrains.kotlin.android")

        // ktlint + detekt: единые правила стиля и анализа для всех модулей
        // pluginManager.apply("silverchat.quality")

        extensions.configure<LibraryExtension> {
            configureLibrary(this, path)
        }
        configureKotlin()

        dependencies {
            add("implementation", libs.findLibrary("kotlin.stdlib").get())
            add("implementation", libs.findBundle("coroutines").get())
            add("implementation", libs.findLibrary("timber").get())
            add("coreLibraryDesugaring", libs.findLibrary("desugar.jdk.libs").get())

            add("testImplementation", libs.findBundle("testing").get())
            add("androidTestImplementation", libs.findLibrary("androidx.junit").get())
            add("androidTestImplementation", libs.findLibrary("androidx.espresso.core").get())
        }
    }
}

import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/**
 * `silverchat.jvm.library` — чистый Kotlin/JVM модуль без Android-зависимостей.
 *
 * Используется для :core:domain (UseCase'ы, контракты репозиториев).
 * Бизнес-логика не знает про Android → её можно тестировать обычным JUnit
 * без Robolectric и переиспользовать при переносе на KMP/iOS.
 */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.jvm")

        // ktlint + detekt: единые правила стиля и анализа для всех модулей
        pluginManager.apply("silverchat.quality")
        pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")

        extensions.configure<JavaPluginExtension> {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }

        tasks.withType<KotlinCompile>().configureEach {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_17)
                freeCompilerArgs.addAll(
                    "-opt-in=kotlin.RequiresOptIn",
                    "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
                    "-opt-in=kotlinx.coroutines.FlowPreview",
                )
            }
        }

        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
        }

        dependencies {
            add("implementation", libs.findLibrary("kotlin.stdlib").get())
            // Только core: Android-вариант корутин в JVM-модуль тащить нельзя
            add("implementation", libs.findLibrary("kotlinx.coroutines.core").get())
            add("implementation", libs.findBundle("serialization").get())

            add("testImplementation", libs.findLibrary("junit").get())
            add("testImplementation", libs.findLibrary("turbine").get())
            add("testImplementation", libs.findLibrary("mockk").get())
            add("testImplementation", libs.findLibrary("kotlinx.coroutines.test").get())
        }
    }
}

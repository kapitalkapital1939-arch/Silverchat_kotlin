import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * `silverchat.android.hilt` — DI для Android-модуля.
 * KSP вместо kapt (kapt в maintenance mode, даёт конфликты с Kotlin 2.x).
 */
class AndroidHiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.google.devtools.ksp")
        pluginManager.apply("dagger.hilt.android.plugin")

        dependencies {
            add("implementation", libs.findLibrary("hilt.android").get())
            add("ksp", libs.findLibrary("hilt.compiler").get())
            add("ksp", libs.findLibrary("hilt.work.compiler").get())

            add("testImplementation", libs.findLibrary("hilt.testing").get())
            add("kspTest", libs.findLibrary("hilt.compiler").get())
            add("androidTestImplementation", libs.findLibrary("hilt.testing").get())
            add("kspAndroidTest", libs.findLibrary("hilt.compiler").get())
        }
    }
}

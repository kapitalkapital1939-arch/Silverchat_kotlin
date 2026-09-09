import com.google.devtools.ksp.gradle.KspExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * `silverchat.android.room` — офлайн-кэш (чаты, сообщения, черновики, сторис-метаданные).
 *
 * Схема БД экспортируется в <module>/schemas и коммитится в git: без неё
 * Room не может проверить миграции, а CI не может поймать breaking change.
 */
class AndroidRoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.google.devtools.ksp")

        dependencies {
            add("implementation", libs.findLibrary("room.runtime").get())
            add("implementation", libs.findLibrary("room.ktx").get())
            add("ksp", libs.findLibrary("room.compiler").get())
            add("testImplementation", libs.findLibrary("room.testing").get())
            add("androidTestImplementation", libs.findLibrary("room.testing").get())
        }

        extensions.configure<KspExtension> {
            arg("room.schemaLocation", projectDir.resolve("schemas").absolutePath)
            arg("room.incremental", "true")
            arg("room.generateKotlin", "true")
            // Схема нужна в assets, чтобы runtime-миграции можно было провалидировать
            arg("room.expandProjection", "true")
        }

        // Копируем JSON-схемы Room в assets для MigrationTestHelper
        plugins.withId("com.android.library") {
            val android = extensions.getByType(com.android.build.api.dsl.LibraryExtension::class.java)
            android.sourceSets.getByName("androidTest").assets.srcDir(projectDir.resolve("schemas"))
        }
    }
}

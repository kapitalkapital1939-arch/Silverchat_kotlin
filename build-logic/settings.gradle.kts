/*
 * build-logic — изолированный композитный билд с convention-плагинами.
 *
 * Зачем: каждый модуль (их 20+) должен получать ОДИНАКОВЫЕ compileSdk, Java/Kotlin
 * target, Compose-конфигурацию, Hilt, R8-правила. Если это копипастить по
 * build.gradle.kts — версии разъезжаются и Gradle начинает резолвить
 * несколько kotlin-stdlib / compose-compiler одновременно (те самые
 * «конфликты метаданных»). Convention-плагины убирают дублирование полностью.
 */

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }

    versionCatalogs {
        // Пробрасываем общий каталог, чтобы convention-плагины брали версии
        // из того же libs.versions.toml, что и модули.
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"

include(":convention")

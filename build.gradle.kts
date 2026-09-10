/*
 * SilverChat — корневой build.gradle.kts
 * Все плагины объявлены с `apply false`: реальное применение происходит
 * через convention-плагины из :build-logic. Это гарантирует, что версия
 * Kotlin/AGP в проекте ровно одна.
 */

plugins {
    // Все плагины объявлены здесь с `apply false`. Это кладёт их JAR'ы в
    // buildscript-classpath корневого проекта — именно поэтому convention-плагины
    // из :build-logic могут применять их по id через pluginManager.apply("..."),
    // а версия плагина во всём графе остаётся ровно одной.
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.kover) apply false
}

/*
 * Глобальная политика разрешения зависимостей.
 * Страховка от «metadata-конфликтов»: фиксируем единый Kotlin stdlib и
 * единый kotlin-compiler-embeddable во всём графе.
 */
allprojects {
    configurations.configureEach {
        resolutionStrategy {
            // Одна версия Kotlin-стандарта на весь проект — иначе KSP/Hilt/Room
            // притаскивают stdlib другой версии и сборка падает на метаданных.
    force("org.jetbrains.kotlin:kotlin-stdlib")
    force("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    force("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    force("org.jetbrains.kotlinx:kotlinx-coroutines-android")
    force("org.jetbrains.kotlinx:kotlinx-serialization-json")

            preferProjectModules()

            // Каждые 24 часа проверяем, что кэш динамических версий не протух
            cacheChangingModulesFor(0, "seconds")
        }
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}

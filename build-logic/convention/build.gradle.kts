plugins {
    `kotlin-dsl`
}

group = "com.silverchat.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // Классы плагинов нужны на этапе КОМПИЛЯЦИИ convention-плагинов.
    // В рантайме они берутся из buildscript-classpath корневого build.gradle.kts,
    // где все плагины объявлены с `apply false`. Версии — из единого каталога.
    compileOnly(libs.agp.plugin)
    compileOnly(libs.kotlin.gradle.plugin)
    compileOnly(libs.kotlin.compose.plugin)
    compileOnly(libs.ksp.gradle.plugin)
    compileOnly(libs.hilt.gradle.plugin)

    // Маркеры ktlint/detekt нужны в РАНТАЙМЕ build-logic: скриптовый плагин
    // silverchat.quality.gradle.kts применяет их сам и получает от Gradle
    // type-safe accessors для блоков `detekt { }` и `ktlint { }`.
    // В корневом build.gradle.kts эти плагины не объявлены — иначе один и тот
    // же маркер оказался бы в двух classpath, и Gradle сообщил бы о конфликте.
    implementation(libs.detekt.plugin)
    implementation(libs.ktlint.plugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "silverchat.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "silverchat.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "silverchat.android.compose"
            implementationClass = "AndroidComposeConventionPlugin"
        }
        register("androidFeature") {
            id = "silverchat.android.feature"
            implementationClass = "AndroidFeatureConventionPlugin"
        }
        register("androidHilt") {
            id = "silverchat.android.hilt"
            implementationClass = "AndroidHiltConventionPlugin"
        }
        register("androidRoom") {
            id = "silverchat.android.room"
            implementationClass = "AndroidRoomConventionPlugin"
        }
        register("jvmLibrary") {
            id = "silverchat.jvm.library"
            implementationClass = "JvmLibraryConventionPlugin"
        }
        register("quality") {
        id = "silverchat.quality"
        implementationClass = "Silverchat_quality_gradle" 
    }
        }
    }
}

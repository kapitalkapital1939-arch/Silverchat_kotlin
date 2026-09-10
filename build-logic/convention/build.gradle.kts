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
        freeCompilerArgs.addAll("-Xjvm-default=all")
    }
}

dependencies {
    compileOnly("com.android.tools.build:gradle:8.5.0") // или твоя версия AGP из каталога
    compileOnly("com.google.devtools.ksp:symbol-processing-gradle-plugin:2.3.0-1.0.24") // или версия KSP
    compileOnly(libs.kotlin.gradle.plugin)
    compileOnly(libs.kotlin.compose.plugin)
    compileOnly(libs.hilt.gradle.plugin)

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
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_0)
        languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_0)
    }
}

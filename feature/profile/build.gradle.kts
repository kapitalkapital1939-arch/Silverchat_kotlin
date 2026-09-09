plugins {
    alias(libs.plugins.silverchat.android.feature)
}

android {
    namespace = "com.silverchat.feature.profile"
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.bundles.lifecycle)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.navigation.compose)

    // Загрузка аватара/баннера, сжатие перед отправкой
    implementation(project(":core:media"))
    implementation(project(":core:datastore"))

    // Анимированные аватары и баннеры (Lottie / GIF / видео)
    implementation(libs.lottie.compose)
    implementation(libs.bundles.coil)

    // Палитра доминирующего цвета баннера для подсветки профиля
    implementation(libs.androidx.palette)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}

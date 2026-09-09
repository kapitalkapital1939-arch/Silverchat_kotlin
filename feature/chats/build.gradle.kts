plugins {
    alias(libs.plugins.silverchat.android.feature)
}

android {
    namespace = "com.silverchat.feature.chats"
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.bundles.lifecycle)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.navigation.compose)

    // Медиа нужны именно здесь: отправка фото/видео/голосовых инициируется
    // из экрана диалога, а не из :core:domain
    implementation(project(":core:media"))

    // Плейсхолдеры и «скелетоны» при загрузке ленты сообщений
    implementation(libs.shimmer)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}

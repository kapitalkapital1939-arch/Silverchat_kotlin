plugins {
    alias(libs.plugins.silverchat.android.feature)
}

android {
    namespace = "com.silverchat.feature.market"
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.bundles.lifecycle)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.navigation.compose)

    // Экономика: кошелёк, лоты, подарки, Premium
    implementation(project(":core:datastore"))

    // Анимированные витрины лотов и GIF-подарки
    implementation(libs.lottie.compose)
    implementation(libs.bundles.coil)

    // График истории цены лота
    implementation(libs.shimmer)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}

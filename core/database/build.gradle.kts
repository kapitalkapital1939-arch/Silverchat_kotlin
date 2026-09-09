/*
 * :core:database — офлайн-кэш (offline-first).
 *
 * Room хранит: чаты, сообщения, реакции, участников, сторис-метаданные,
 * черновики, очередь неотправленных сообщений, историю поиска,
 * транзакции кошелька. Экран открывается мгновенно даже без сети,
 * а WebSocket лишь применяет дельты поверх кэша.
 *
 * Схема экспортируется в schemas/ и коммитится — без неё миграции непроверяемы.
 */
plugins {
    id("silverchat.android.library")
    id("silverchat.android.hilt")
    id("silverchat.android.room")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.silverchat.core.database"
}

dependencies {
    api(project(":core:model"))
    api(project(":core:common"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.robolectric)
}

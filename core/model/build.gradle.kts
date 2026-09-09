/*
 * :core:model — единственная общая модель данных.
 * Чистый Kotlin/JVM: ни Android, ни Compose, ни Retrofit здесь нет.
 * DTO сетевого слоя (:core:network) маппятся В ЭТИ типы, а не наоборот,
 * поэтому UI никогда не зависит от формы ответа бэкенда.
 */
plugins {
    id("silverchat.jvm.library")
}

dependencies {
    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.collections.immutable)
    api(libs.kotlinx.datetime)
}

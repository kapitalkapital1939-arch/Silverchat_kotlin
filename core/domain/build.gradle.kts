/*
 * :core:domain — бизнес-логика приложения.
 *
 * Чистый Kotlin/JVM: здесь НЕТ Android, Compose, Retrofit и Room.
 * Только интерфейсы репозиториев, UseCase'ы и правила продукта.
 *
 * Следствие:
 *  - все UseCase'ы тестируются обычным JUnit за миллисекунды;
 *  - фичи не могут дотянуться до сети/БД напрямую (см. convention-плагин
 *    silverchat.android.feature: он не даёт :core:network и :core:database);
 *  - при переносе на KMP этот модуль переезжает без изменений.
 */
plugins {
    id("silverchat.jvm.library")
}

dependencies {
    api(project(":core:model"))
    api(project(":core:common"))

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)

    testImplementation(project(":core:testing"))
}

@file:Suppress("UnstableApiUsage")

/*
 * SilverChat — settings.gradle.kts
 * ---------------------------------------------------------------------------
 * ЕДИНАЯ ТОЧКА КОНФИГУРАЦИИ СБОРКИ.
 *
 * Принципы (борьба с «gradle-конфликтами метаданных»):
 *   1. Все репозитории объявлены ОДИН раз здесь и в build-logic — в модулях
 *      блоков `repositories {}` быть не должно вообще.
 *   2. `dependencyResolutionManagement` с `FAIL_ON_PROJECT_REPOS` физически
 *      запрещает модулю объявлять свой репозиторий -> конфликт версий невозможен.
 *   3. Все версии — только в gradle/libs.versions.toml (Version Catalog).
 *   4. build-logic включён как includeBuild ДО основного графа модулей,
 *      поэтому convention-плагины резолвятся из своего изолированного
 *      репозитория и не тянут транзитивные версии Kotlin в app-модуль.
 * ---------------------------------------------------------------------------
 */

pluginManagement {
    // build-logic должен быть виден pluginManagement как источник convention-плагинов
    includeBuild("build-logic")

    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
        // WebRTC-артефакты (звонки) живут в отдельном Maven
        maven("https://artifactory.stream.io/data/gradle") {
            content { includeGroupByRegex("io\\.getstream.*") }
        }
    }
}

dependencyResolutionManagement {
    // Жёсткий запрет локальных repositories {} в модулях — главный предохранитель
    // от «Module was compiled with an incompatible version of Kotlin» и
    // от подмены транзитивных версий.
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        google()
        mavenCentral()
        maven("https://artifactory.stream.io/data/gradle") {
            content { includeGroupByRegex("io\\.getstream.*") }
        }
    }
}

rootProject.name = "SilverChat"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

// ── CORE ────────────────────────────────────────────────────────────────────
include(":core:model")          // DTO / доменные модели, общие для всех слоёв
include(":core:common")         // Result, DispatcherProvider, логи, утилиты
include(":core:designsystem")     // Compose-темы, glassmorphism, навигация, UI-кит
include(":core:domain")         // UseCase'ы + интерфейсы репозиториев (чистый Kotlin)
include(":core:data")           // Реализации репозиториев: сеть + Room-кэш + WebSocket
include(":core:network")        // REST (Retrofit) + WebSocket-клиент + протокол событий
include(":core:database")       // Room: офлайн-кэш чатов, сообщений, черновиков
include(":core:datastore")      // Настройки (DataStore) + EncryptedSharedPreferences
include(":core:security")       // EncryptedSharedPreferences, Keystore, биометрия
include(":core:webrtc")         // PeerConnection, EGL, сигналинг звонков
include(":core:media")          // Голосовые/видео/«кружки», сжатие, загрузка
include(":core:notifications")    // FCM, каналы уведомлений, входящий звонок
include(":core:testing")         // Test-двойники, MainDispatcherRule, fake-репозитории

// ── FEATURES ────────────────────────────────────────────────────────────────
include(":feature:auth")          // Вход по номеру, OTP, регистрация
include(":feature:chats")         // Список чатов + экран диалога + каналы + группы
include(":feature:stories")       // Лента сторис, публикация, просмотр
include(":feature:calls")         // Голосовые и видеозвонки (WebRTC)
include(":feature:market")        // Маркет юзернеймов, сильверы, подарки, Premium
include(":feature:settings")      // Настройки, темы оформления, приватность
include(":feature:profile")       // Профиль, карточка пользователя, кастомизация
include(":feature:admin")         // Админ-панель @silver (супер-права)

// ── APP ─────────────────────────────────────────────────────────────────────
include(":app")                   // Точка сборки: DI-граф, NavHost, MainActivity

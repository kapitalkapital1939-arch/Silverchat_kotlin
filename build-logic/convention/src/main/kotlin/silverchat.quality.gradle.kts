/*
 * silverchat.quality — ktlint + detekt, одинаковые для каждого модуля.
 *
 * ── Почему precompiled script plugin, а не Kotlin-класс ─────────────────
 * Остальные convention-плагины написаны классами и применяют плагины по id
 * через `pluginManager.apply(...)`: для этого достаточно `compileOnly`, а
 * классы в рантайме берутся из buildscript-classpath корневого build.gradle.kts.
 *
 * Здесь так нельзя: DSL-блоки `detekt { }` и `ktlint { }` существуют только
 * как type-safe accessors, которые Gradle генерирует для скриптового плагина,
 * применяющего плагин в собственном блоке `plugins { }`. Классу пришлось бы
 * импортировать `DetektExtension` и `KtlintExtension` и работать с ними
 * вживую, а пакеты в detekt 2.0 переехали (`io.gitlab.arturbosch.*` →
 * `dev.detekt.*`). Accessors снимают эту проблему целиком: компилятор
 * build-logic проверяет имена свойств по фактическому API плагина.
 *
 * ── Почему маркеры в `implementation`, а не `compileOnly` ───────────────
 * Скриптовый плагин разрешает и применяет `dev.detekt` сам, из classpath
 * включённого билда build-logic. Поэтому маркер нужен именно в рантайме
 * build-logic. В корневом build.gradle.kts эти плагины намеренно НЕ
 * объявлены: дублирование маркера в двух classpath Gradle трактует как
 * конфликт версий.
 */

plugins {
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
}

detekt {
    // Наш config/detekt/detekt.yml накладывается поверх стандартного, а не
    // заменяет его: иначе пришлось бы перечислять все ~250 правил вручную.
    buildUponDefaultConfig = true

    // AST строится параллельно — на 200+ файлах это заметно быстрее.
    parallel = true

    config.setFrom(rootProject.file("config/detekt/detekt.yml"))

    // ВРЕМЕННО не блокирует сборку.
    //
    // Причина не в снисходительности, а в версии инструмента: detekt
    // 2.0.0-alpha.6 — альфа, и стабильность состава правил между альфами не
    // гарантирована. Пороги в config/detekt/detekt.yml выставлены по
    // измеренным максимумам кодовой базы, но проверить их фактическим прогоном
    // при подготовке проекта было нечем (Gradle и Android SDK недоступны).
    //
    // Порядок включения блокировки — два шага, оба описаны в README:
    //   1. ./gradlew detektBaseline  → зафиксировать текущие находки;
    //   2. ignoreFailures = false    → дальше падают только НОВЫЕ проблемы.
    //
    // ktlint при этом блокирует сборку уже сейчас: его правила стабильны,
    // а все расхождения форматирования исправляются `./gradlew ktlintFormat`.
    ignoreFailures = true

    // Release-вариант собирается из тех же исходников, что и debug. Второй
    // прогон удваивает время CI, не находя ничего нового.
    ignoredBuildTypes = listOf("release")
}

ktlint {
    ignoreFailures.set(false)
    outputToConsole.set(true)

    filter {
        // Сгенерированные Room/Hilt/KSP источники не наши и форматируются
        // по своим правилам.
        exclude("**/generated/**")
        exclude("**/build/**")
    }
}

package com.silverchat.core.data.mapper

import kotlinx.serialization.json.Json

/**
 * Проводное имя enum'а — то, что объявлено в `@SerialName`, а не `name.lowercase()`.
 *
 * Почему нельзя просто lowercase: значения расходятся. Например,
 * `UsernameCategory.PREMIUM_ONLY` по протоколу передаётся как `"premium"`.
 * Наивный `name.lowercase()` дал бы `"premium_only"`, сервер не нашёл бы
 * такую категорию, и фильтр молча перестал бы работать — без ошибки,
 * просто пустой выдачей.
 *
 * Поэтому имя берётся из того же сериализатора, которым кодируется тело
 * запроса: один источник правды на REST и на query-параметры.
 */
internal inline fun <reified T : Enum<T>> T.wireName(json: Json): String =
    json.encodeToString(this).removeSurrounding("\"")

/** Список enum'ов в query-параметр: `"short,premium"`. Пустой список -> null. */
internal inline fun <reified T : Enum<T>> List<T>.wireNamesOrNull(json: Json): String? =
    if (isEmpty()) null else joinToString(",") { it.wireName(json) }

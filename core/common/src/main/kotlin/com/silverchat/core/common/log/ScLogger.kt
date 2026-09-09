package com.silverchat.core.common.log

import timber.log.Timber

/**
 * Логирование с обязательным тегом области.
 *
 * Отдельный фасад (а не прямой Timber) нужен по двум причинам:
 *  1. в release-сборке PII (номера телефонов, токены, тексты сообщений)
 *     вырезается на уровне [ScLogger], а не надеждой «не забудем убрать d()»;
 *  2. логи WebSocket-канала пишутся в отдельный буфер, который можно
 *     выгрузить по запросу поддержки.
 */
object ScLogger {

    private const val MAX_PII_LENGTH = 4

    fun d(tag: String, message: String) {
        Timber.tag(tag).d(message)
    }

    fun i(tag: String, message: String) {
        Timber.tag(tag).i(message)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        Timber.tag(tag).w(throwable, message)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Timber.tag(tag).e(throwable, message)
    }

    /**
     * Маскирует чувствительное значение: видно только первые символы.
     * Используется для токенов, телефонов, username при отладке.
     */
    fun mask(value: String?): String {
        if (value.isNullOrBlank()) return "<empty>"
        if (value.length <= MAX_PII_LENGTH) return "*".repeat(value.length)
        return value.take(MAX_PII_LENGTH) + "*".repeat(minOf(value.length - MAX_PII_LENGTH, 12))
    }

    /** Телефон -> +373***12 — чтобы не светить PII в logcat. */
    fun maskPhone(phone: String?): String {
        if (phone.isNullOrBlank()) return "<empty>"
        val digits = phone.filter { it.isDigit() }
        if (digits.length < 6) return mask(phone)
        return phone.take(4) + "*".repeat(digits.length - 6) + digits.takeLast(2)
    }
}

/** Теги областей — единый словарь, чтобы логи можно было фильтровать. */
object LogTag {
    const val WS = "SC/WebSocket"
    const val WS_FRAME = "SC/WS-Frame"
    const val HTTP = "SC/Http"
    const val AUTH = "SC/Auth"
    const val CHAT = "SC/Chat"
    const val MEDIA = "SC/Media"
    const val CALL = "SC/Call"
    const val STORY = "SC/Story"
    const val PROFILE = "SC/Profile"
    const val MARKET = "SC/Market"
    const val WALLET = "SC/Wallet"
    const val ADMIN = "SC/Admin"
    const val SECURITY = "SC/Security"
    const val DB = "SC/Database"
    const val SYNC = "SC/Sync"
    const val UI = "SC/Ui"
}

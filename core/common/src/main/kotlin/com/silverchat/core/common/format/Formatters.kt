package com.silverchat.core.common.format

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Форматирование времени и чисел по правилам Telegram-подобных UI.
 *
 * Все функции чистые и детерминированные -> покрываются юнит-тестами.
 * Локаль фиксируется в RU, потому что интерфейс продукта русскоязычный;
 * при добавлении языков этот объект заменяется на инъектируемый Formatter.
 */
object TimeFormatter {

    private val ruLocale: Locale = Locale("ru", "RU")
    private val timeFmt = java.text.SimpleDateFormat("HH:mm", ruLocale)
    private val dayFmt = java.text.SimpleDateFormat("d MMM", ruLocale)
    private val fullFmt = java.text.SimpleDateFormat("d MMMM yyyy, HH:mm", ruLocale)

    /** Время для пузыря сообщения: «14:32». */
    fun messageTime(epochMillis: Long): String = timeFmt.format(epochMillis)

    /**
     * Время в списке чатов:
     *  - сегодня       -> «14:32»
     *  - вчера/на этой -> «Вчера», «Пн»
     *  - в этом году   -> «14 мар»
     *  - старше        -> «14.03.24»
     */
    fun chatListTime(epochMillis: Long, now: Long = System.currentTimeMillis()): String {
        val msgCal = Calendar.getInstance().apply { timeInMillis = epochMillis }
        val nowCal = Calendar.getInstance().apply { timeInMillis = now }

        if (isSameDay(msgCal, nowCal)) return timeFmt.format(epochMillis)

        val yesterday = (nowCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
        if (isSameDay(msgCal, yesterday)) return "Вчера"

        val weekStart = (nowCal.clone() as Calendar).apply {
            set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
            set(Calendar.HOUR_OF_DAY, 0)
            clear(Calendar.MINUTE); clear(Calendar.SECOND); clear(Calendar.MILLISECOND)
        }
        if (epochMillis >= weekStart.timeInMillis) {
            return java.text.SimpleDateFormat("EE", ruLocale).format(epochMillis)
                .replaceFirstChar { it.uppercase(ruLocale) }
        }
        if (msgCal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR)) return dayFmt.format(epochMillis)
        return java.text.SimpleDateFormat("dd.MM.yy", ruLocale).format(epochMillis)
    }

    /** Разделитель дней внутри диалога: «Сегодня», «Вчера», «14 марта». */
    fun daySeparator(epochMillis: Long, now: Long = System.currentTimeMillis()): String {
        val msgCal = Calendar.getInstance().apply { timeInMillis = epochMillis }
        val nowCal = Calendar.getInstance().apply { timeInMillis = now }
        return when {
            isSameDay(msgCal, nowCal) -> "Сегодня"
            isSameDay(msgCal, (nowCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }) -> "Вчера"
            else -> java.text.SimpleDateFormat("d MMMM", ruLocale).format(epochMillis)
        }
    }

    /** «был(а) в сети 5 минут назад». */
    fun lastSeen(epochMillis: Long, now: Long = System.currentTimeMillis()): String {
        val diff = (now - epochMillis).coerceAtLeast(0)
        return "был(а) в сети " + ago(diff, now)
    }

    fun ago(diffMillis: Long, @Suppress("UNUSED_PARAMETER") now: Long = System.currentTimeMillis()): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(diffMillis)
        val hours = TimeUnit.MILLISECONDS.toHours(diffMillis)
        val days = TimeUnit.MILLISECONDS.toDays(diffMillis)
        return when {
            minutes < 1 -> "только что"
            minutes < 60 -> plural(minutes, "минуту", "минуты", "минут") + " назад"
            hours < 24 -> plural(hours, "час", "часа", "часов") + " назад"
            days < 7 -> plural(days, "день", "дня", "дней") + " назад"
            else -> dayFmt.format(System.currentTimeMillis() - diffMillis)
        }
    }

    /** Длительность голосового/видео: «0:07», «1:23», «1:02:15». */
    fun duration(millis: Long): String {
        val totalSeconds = millis / 1000
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return if (h > 0) {
            String.format(ruLocale, "%d:%02d:%02d", h, m, s)
        } else {
            String.format(ruLocale, "%d:%02d", m, s)
        }
    }

    /** Таймер активного звонка. */
    fun callTimer(elapsedMillis: Long): String = duration(elapsedMillis)

    fun full(epochMillis: Long): String = fullFmt.format(epochMillis)

    fun timeInZone(epochMillis: Long, timeZoneId: String): String {
        val fmt = java.text.SimpleDateFormat("HH:mm", ruLocale)
        fmt.timeZone = TimeZone.getTimeZone(timeZoneId)
        return fmt.format(epochMillis)
    }

    private fun isSameDay(a: Calendar, b: Calendar): Boolean =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

    fun plural(value: Long, one: String, few: String, many: String): String {
        val mod10 = value % 10
        val mod100 = value % 100
        val word = when {
            mod10 == 1L && mod100 != 11L -> one
            mod10 in 2..4 && mod100 !in 12..14 -> few
            else -> many
        }
        return "$value $word"
    }
}

/** Числа и «сильверы». */
object NumberFormatter {

    private val compactSymbols = DecimalFormatSymbols(ruLocale()).apply {
        groupingSeparator = '\u00A0' // неразрывный пробел: 1 250
    }
    private val grouped = DecimalFormat("#,##0", compactSymbols)

    /** 1 250 | 15 400 | 1 200 000 — баланс сильверов всегда с разделителями. */
    fun silver(amount: Long): String = grouped.format(amount)

    /** Компактно для списков: 1,2 тыс. / 3,4 млн. */
    fun compact(value: Long): String = when {
        value < 1_000 -> value.toString()
        value < 1_000_000 -> trim(value / 1_000.0) + " тыс."
        value < 1_000_000_000 -> trim(value / 1_000_000.0) + " млн"
        else -> trim(value / 1_000_000_000.0) + " млрд"
    }

    private fun trim(d: Double): String =
        if (d >= 100) d.toInt().toString() else String.format(ruLocale(), "%.1f", d).replace(".0", "")

    /** Размер файла: 12 КБ / 4,3 МБ / 1,1 ГБ. */
    fun fileSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes Б"
        bytes < 1024 * 1024 -> "${bytes / 1024} КБ"
        bytes < 1024L * 1024 * 1024 ->
            String.format(ruLocale(), "%.1f МБ", bytes / (1024.0 * 1024.0))
        else -> String.format(ruLocale(), "%.2f ГБ", bytes / (1024.0 * 1024.0 * 1024.0))
    }

    fun percent(part: Int, total: Int): Int =
        if (total <= 0) 0 else ((part * 100f) / total).toInt().coerceIn(0, 100)

    private fun ruLocale(): Locale = Locale("ru", "RU")
}

/**
 * Форматирование username и приглашений.
 * Правила едины для клиента и бэкенда; при расхождении валидация на сервере
 * остаётся решающей.
 */
object UsernameFormatter {

    const val MIN_LENGTH = 4
    const val MAX_LENGTH = 32
    val ALLOWED_PATTERN = Regex("^[A-Za-z0-9_]+$")
    val RESERVED = setOf(
        "silver", "admin", "root", "support", "system", "null", "undefined",
        "channel", "group", "market", "premium", "official", "bot",
    )

    fun withAt(username: String?): String? =
        username?.takeIf { it.isNotBlank() }?.let { if (it.startsWith("@")) it else "@$it" }

    fun strip(username: String?): String? =
        username?.trim()?.removePrefix("@")?.lowercase()

    fun validate(raw: String): UsernameValidation {
        val value = strip(raw) ?: return UsernameValidation.Invalid("Пустой username")
        return when {
            value.length < MIN_LENGTH ->
                UsernameValidation.Invalid("Минимум $MIN_LENGTH символа")
            value.length > MAX_LENGTH ->
                UsernameValidation.Invalid("Максимум $MAX_LENGTH символов")
            !ALLOWED_PATTERN.matches(value) ->
                UsernameValidation.Invalid("Только латиница, цифры и «_»")
            value.first().isDigit() ->
                UsernameValidation.Invalid("Не может начинаться с цифры")
            value.contains("__") ->
                UsernameValidation.Invalid("Двойное подчёркивание запрещено")
            value.lowercase() in RESERVED ->
                UsernameValidation.Reserved("Зарезервировано администрацией")
            else -> UsernameValidation.Valid(value)
        }
    }

    /** Ссылка-приглашение; токен всегда URL-safe. */
    fun inviteUrl(token: String): String = "https://silver.chat/join/$token"

    /** t.me-подобная публичная ссылка на пользователя/канал. */
    fun publicUrl(username: String): String = "https://silver.chat/${strip(username)}"
}

sealed interface UsernameValidation {
    data class Valid(val value: String) : UsernameValidation
    data class Invalid(val reason: String) : UsernameValidation
    data class Reserved(val reason: String) : UsernameValidation

    val isValid: Boolean get() = this is Valid
}

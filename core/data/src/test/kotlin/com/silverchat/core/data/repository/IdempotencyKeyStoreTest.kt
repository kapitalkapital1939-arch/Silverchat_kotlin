package com.silverchat.core.data.repository

import com.google.common.truth.Truth.assertThat
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Test

/**
 * Ключи идемпотентности для операций, поглощающих сущность.
 *
 * Проверяется главное свойство: повтор одного и того же намерения обязан уйти
 * с ТЕМ ЖЕ ключом. Именно от этого зависит, спишет ли сервер второй раз, когда
 * ответ на первый запрос потерялся.
 *
 * ── Что здесь не покрыто ────────────────────────────────────────────────
 * Истечение [IdempotencyKeyStore.TTL_MS]. Хранилище читает
 * `System.currentTimeMillis()` напрямую, поэтому проверить протухание можно
 * только ожиданием в десять минут либо внедрением часов. Оба варианта хуже
 * пользы: тест на десять минут заблокировал бы CI, а абстракция часов ради
 * одной константы усложнила бы класс, от которого зависит списание денег.
 * Корректность самого окна проверяется ниже через сравнение с серверным
 * сроком хранения — это та часть инварианта, которую можно сломать правкой.
 */
class IdempotencyKeyStoreTest {

    private val store = IdempotencyKeyStore()

    /* ── Главное свойство ──────────────────────────────────────────────── */

    @Test
    fun sameIntentAlwaysGetsSameKey() {
        val first = store.keyFor(BUY_INTENT)
        val second = store.keyFor(BUY_INTENT)
        val third = store.keyFor(BUY_INTENT)

        assertThat(second).isEqualTo(first)
        assertThat(third).isEqualTo(first)
    }

    @Test
    fun differentIntentsGetDifferentKeys() {
        val buy = store.keyFor(BUY_INTENT)
        val accept = store.keyFor(ACCEPT_OFFER_INTENT)

        assertThat(buy).isNotEqualTo(accept)
    }

    @Test
    fun intentsDifferingOnlyByIdAreIndependent() {
        /* Два разных лота — два разных намерения, даже если операция одна.
         * Ключ, общий для всех покупок, заблокировал бы вторую, законную. */
        val first = store.keyFor("market.buy:listing-1")
        val second = store.keyFor("market.buy:listing-2")

        assertThat(first).isNotEqualTo(second)
    }

    /* ── Сброс ─────────────────────────────────────────────────────────── */

    @Test
    fun forgetMakesNextKeyDifferent() {
        val before = store.keyFor(BUY_INTENT)

        store.forget(BUY_INTENT)

        assertThat(store.keyFor(BUY_INTENT)).isNotEqualTo(before)
    }

    @Test
    fun forgetLeavesOtherIntentsIntact() {
        val buy = store.keyFor(BUY_INTENT)
        val accept = store.keyFor(ACCEPT_OFFER_INTENT)

        store.forget(BUY_INTENT)

        assertThat(store.keyFor(ACCEPT_OFFER_INTENT)).isEqualTo(accept)
        assertThat(store.keyFor(BUY_INTENT)).isNotEqualTo(buy)
    }

    @Test
    fun forgetOfUnknownIntentDoesNotThrow() {
        /* Сброс вызывается после определённого исхода, в том числе когда ключ
         * уже протух. Падение здесь означало бы ошибку поверх успешной покупки. */
        store.forget("market.buy:несуществующий")
    }

    @Test
    fun resetClearsEveryIntent() {
        /* Сброс при разрыве сессии: ключ предыдущего аккаунта не должен
         * доживать до следующего входа. */
        val buy = store.keyFor(BUY_INTENT)
        val accept = store.keyFor(ACCEPT_OFFER_INTENT)

        store.reset()

        assertThat(store.keyFor(BUY_INTENT)).isNotEqualTo(buy)
        assertThat(store.keyFor(ACCEPT_OFFER_INTENT)).isNotEqualTo(accept)
    }

    /* ── Операции, которые законно повторить ───────────────────────────── */

    @Test
    fun freshKeyIsUniqueEveryTime() {
        val keys = List(1000) { IdempotencyKeyStore.freshKey() }

        assertThat(keys.distinct()).hasSize(1000)
    }

    /* ── Атомарность ───────────────────────────────────────────────────── */

    @Test
    fun concurrentCallsForSameIntentShareOneKey() {
        /* Двойной тап по кнопке «Купить» приходит с разных потоков. Если бы
         * проверка и запись не были атомарны, оба потока создали бы свой ключ
         * и сервер списал бы дважды — ровно тот сценарий, ради которого
         * хранилище существует. `ConcurrentHashMap.compute` даёт атомарность,
         * этот тест не даёт её потерять. */
        val threads = 32
        val keys = ConcurrentHashMap.newKeySet<String>()
        val startGate = CountDownLatch(1)
        val doneGate = CountDownLatch(threads)

        repeat(threads) {
            Thread {
                try {
                    startGate.await()
                    keys.add(store.keyFor(BUY_INTENT))
                } finally {
                    doneGate.countDown()
                }
            }.start()
        }
        startGate.countDown()

        assertThat(doneGate.await(AWAIT_SECONDS, TimeUnit.SECONDS)).isTrue()
        assertThat(keys).hasSize(1)
    }

    /* ── Инварианты ────────────────────────────────────────────────────── */

    @Test
    fun clientTtlIsShorterThanServerRetention() {
        /* Сервер хранит соответствие ключа результату не менее 24 часов
         * (backend-contract/01-overview.md). Обратное соотношение означало бы,
         * что клиент посылает «свежий» ключ, который сервер уже считает
         * использованным, и получает чужой результат. */
        val serverRetention = TimeUnit.HOURS.toMillis(24)

        assertThat(IdempotencyKeyStore.TTL_MS).isLessThan(serverRetention)
    }

    @Test
    fun clientTtlIsLongEnoughForManualRetry() {
        /* Окно обязано переживать типичный сценарий: обрыв связи, возврат в
         * приложение, повторная попытка вручную. Минута для этого мала. */
        assertThat(IdempotencyKeyStore.TTL_MS).isAtLeast(TimeUnit.MINUTES.toMillis(5))
    }

    @Test
    fun keysLookLikeUuid() {
        /* Формат важен для сервера: ключ хранится как часть составного
         * индекса, и неограниченная строка сделала бы индекс непредсказуемым. */
        val uuidPattern = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")

        assertThat(uuidPattern.matches(store.keyFor(BUY_INTENT))).isTrue()
        assertThat(uuidPattern.matches(IdempotencyKeyStore.freshKey())).isTrue()
    }

    private companion object {
        const val BUY_INTENT = "market.buy:listing-42"
        const val ACCEPT_OFFER_INTENT = "market.accept-offer:offer-7"
        const val AWAIT_SECONDS = 10L
    }
}

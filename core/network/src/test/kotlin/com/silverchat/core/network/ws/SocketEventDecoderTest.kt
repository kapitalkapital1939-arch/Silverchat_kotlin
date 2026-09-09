package com.silverchat.core.network.ws

import com.google.common.truth.Truth.assertThat
import com.silverchat.core.model.SocketOps
import kotlinx.serialization.json.Json
import org.junit.Test

/**
 * Декодирование кадров WebSocket в типизированные события.
 *
 * Декодер — единственное место, где строковый `op` превращается в sealed-класс,
 * поэтому от него зависит всё, что ниже по стеку. Проверяются три свойства:
 *
 *  1. известные операции декодируются в свои типы с сохранением полей;
 *  2. неизвестная операция НЕ роняет приложение — сервер обязан иметь
 *     возможность выкатить новое событие раньше, чем клиент обновится;
 *  3. испорченная полезная нагрузка превращается в [SocketEvent.DecodeFailure],
 *     а не в исключение: один битый кадр не должен рвать всю подписку.
 */
class SocketEventDecoderTest {

    private val decoder = SocketEventDecoder(
        Json {
            // Конфигурация повторяет прод (NetworkModule.provideJson): тест
            // обязан проверять то же поведение, которое видит пользователь.
            ignoreUnknownKeys = true
            explicitNulls = false
            coerceInputValues = true
            isLenient = false
            encodeDefaults = true
            prettyPrint = false
            classDiscriminator = "kind"
        },
    )

    /* ── Экономика ─────────────────────────────────────────────────────── */

    @Test
    fun ledgerAppendDecodesIntoLedgerAppended() {
        val frame = frame(
            op = SocketOps.WALLET_LEDGER_APPEND,
            payload = """
                {
                  "id": "l_91",
                  "amount": -2500,
                  "reason": "market_purchase",
                  "reference": "listing-7",
                  "counterparty": "crypto_whale",
                  "balance_after": 1250,
                  "created_at": 1730000000000,
                  "reversible": true
                }
            """.trimIndent(),
        )

        val event = decoder.decode(frame) as SocketEvent.LedgerAppended

        assertThat(event.entry.id).isEqualTo("l_91")
        assertThat(event.entry.amount).isEqualTo(-2500L)
        assertThat(event.entry.reason).isEqualTo("market_purchase")
        assertThat(event.entry.reference).isEqualTo("listing-7")
        assertThat(event.entry.counterparty).isEqualTo("crypto_whale")
        assertThat(event.entry.balanceAfter).isEqualTo(1250L)
        assertThat(event.entry.createdAt).isEqualTo(1730000000000L)
        assertThat(event.entry.reversible).isTrue()
    }

    @Test
    fun ledgerAppendToleratesMissingOptionalFields() {
        /* `reference`, `counterparty` и `reversible` необязательны: сервер
         * вправе их не прислать, и клиент не должен из-за этого падать. */
        val frame = frame(
            op = SocketOps.WALLET_LEDGER_APPEND,
            payload = """{"id":"l_1","amount":50,"reason":"daily_bonus","balance_after":50,"created_at":1}""",
        )

        val event = decoder.decode(frame) as SocketEvent.LedgerAppended

        assertThat(event.entry.reference).isNull()
        assertThat(event.entry.counterparty).isNull()
        assertThat(event.entry.reversible).isFalse()
    }

    @Test
    fun walletUpdatedDecodesIntoWalletUpdated() {
        val frame = frame(
            op = SocketOps.WALLET_UPDATED,
            payload = """{"user_id":"user-1","balance":12500,"frozen":2500,"spent_total":5000}""",
        )

        val event = decoder.decode(frame) as SocketEvent.WalletUpdated

        assertThat(event.wallet.userId).isEqualTo("user-1")
        assertThat(event.wallet.balance).isEqualTo(12500L)
        assertThat(event.wallet.frozen).isEqualTo(2500L)
        assertThat(event.wallet.spentTotal).isEqualTo(5000L)
    }

    /* ── Системные кадры ───────────────────────────────────────────────── */

    @Test
    fun errorFrameDecodesIntoServerError() {
        val frame = frame(
            op = SocketOps.ERROR,
            payload = """{"code":"chat_not_found","message":"Чат не найден","retryable":false}""",
        )

        val event = decoder.decode(frame) as SocketEvent.ServerError

        assertThat(event.payload.code).isEqualTo("chat_not_found")
        assertThat(event.payload.message).isEqualTo("Чат не найден")
        assertThat(event.payload.retryable).isFalse()
    }

    @Test
    fun ackFrameDecodesIntoAck() {
        val frame = frame(op = SocketOps.ACK, payload = """{"op":"message.send","ok":true}""")

        val event = decoder.decode(frame) as SocketEvent.Ack

        assertThat(event.payload.op).isEqualTo("message.send")
        assertThat(event.payload.ok).isTrue()
    }

    /* ── Устойчивость ──────────────────────────────────────────────────── */

    @Test
    fun unknownOpBecomesUnknownEventInsteadOfCrash() {
        /* Сервер может выкатить новую операцию раньше, чем клиент обновится.
         * Падение в этом случае означало бы, что релиз сервера ломает все
         * неустановленные версии приложения. */
        val frame = frame(op = "message.telepathy", payload = """{"thought":"…"}""")

        val event = decoder.decode(frame)

        assertThat(event).isInstanceOf(SocketEvent.Unknown::class.java)
        assertThat((event as SocketEvent.Unknown).op).isEqualTo("message.telepathy")
    }

    @Test
    fun unknownFieldsInKnownPayloadAreIgnored() {
        /* Обратная совместимость: новое поле в существующей модели — не
         * breaking change (backend-contract/README.md). */
        val frame = frame(
            op = SocketOps.WALLET_UPDATED,
            payload = """{"user_id":"user-1","balance":10,"bonus_multiplier":1.5}""",
        )

        val event = decoder.decode(frame)

        assertThat(event).isInstanceOf(SocketEvent.WalletUpdated::class.java)
    }

    @Test
    fun missingPayloadBecomesDecodeFailure() {
        val frame = IncomingFrame(id = "f1", op = SocketOps.WALLET_UPDATED, timestamp = 0L, payload = null)

        val event = decoder.decode(frame)

        assertThat(event).isInstanceOf(SocketEvent.DecodeFailure::class.java)
        assertThat((event as SocketEvent.DecodeFailure).op).isEqualTo(SocketOps.WALLET_UPDATED)
    }

    @Test
    fun malformedPayloadBecomesDecodeFailure() {
        /* Строка вместо числа: сервер ошибся, но рвать подписку нельзя —
         * остальные кадры приходят исправно. */
        val frame = frame(
            op = SocketOps.WALLET_LEDGER_APPEND,
            payload = """{"id":"l_1","amount":"много","reason":"x","balance_after":1,"created_at":1}""",
        )

        val event = decoder.decode(frame)

        assertThat(event).isInstanceOf(SocketEvent.DecodeFailure::class.java)
    }

    @Test
    fun payloadOfWrongShapeBecomesDecodeFailure() {
        /* Обязательное поле отсутствует — это другой случай, чем неизвестное
         * лишнее: здесь клиент не может восстановить смысл кадра. */
        val frame = frame(op = SocketOps.WALLET_UPDATED, payload = """{"frozen":10}""")

        assertThat(decoder.decode(frame)).isInstanceOf(SocketEvent.DecodeFailure::class.java)
    }

    @Test
    fun decodeFailureCarriesReasonForLogs() {
        val frame = frame(op = SocketOps.WALLET_UPDATED, payload = """{"balance":"не число"}""")

        val event = decoder.decode(frame) as SocketEvent.DecodeFailure

        assertThat(event.reason).isNotEmpty()
    }

    /* ── Сборка кадра ──────────────────────────────────────────────────── */

    private fun frame(op: String, payload: String): IncomingFrame = IncomingFrame(
        id = "frame-$op",
        op = op,
        timestamp = 1_730_000_000_000L,
        payload = JSON.parseToJsonElement(payload),
    )

    private companion object {
        val JSON = Json { ignoreUnknownKeys = true }
    }
}

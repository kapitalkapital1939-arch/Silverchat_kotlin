package com.silverchat.core.network.ws

import com.silverchat.core.network.dto.AdminLogDto
import com.silverchat.core.network.dto.ChatDto
import com.silverchat.core.network.dto.LedgerDto
import com.silverchat.core.network.dto.MarketListingDto
import com.silverchat.core.network.dto.MessageDto
import com.silverchat.core.network.dto.PremiumGrantedPayload
import com.silverchat.core.network.dto.PremiumStatusDto
import com.silverchat.core.network.dto.PurchasePayload
import com.silverchat.core.network.dto.StoryDto
import com.silverchat.core.network.dto.StreakDto
import com.silverchat.core.network.dto.UserDto
import com.silverchat.core.network.dto.WalletDto
import kotlinx.serialization.json.JsonElement

/**
 * Типизированные события реалтайм-канала.
 *
 * Единственная точка, где строковый `op` превращается в тип. Дальше по стеку
 * (репозитории, ViewModel, UI) строк нет — `when` проверяется компилятором,
 * поэтому добавить новое событие без обработки всех веток не получится.
 */
sealed interface SocketEvent {

    /* ── Сообщения ──────────────────────────────────────────────────────── */
    data class MessageNew(val message: MessageDto) : SocketEvent
    data class MessageAck(val message: MessageDto) : SocketEvent
    data class MessageEdited(val message: MessageDto) : SocketEvent
    data class MessageDeleted(val payload: MessageDeletedPayload) : SocketEvent
    data class ReactionUpdated(val message: MessageDto) : SocketEvent
    data class DeliveryUpdated(val payload: DeliveryPayload) : SocketEvent
    data class ReadUpdated(val payload: DeliveryPayload) : SocketEvent
    data class Typing(val payload: TypingPayload) : SocketEvent

    /* ── Чаты ───────────────────────────────────────────────────────────── */
    data class ChatUpdated(val chat: ChatDto) : SocketEvent
    data class ChatCreated(val chat: ChatDto) : SocketEvent
    data class ChatListSync(val payload: ChatListSyncPayload) : SocketEvent
    data class DraftUpdated(val payload: DraftPayload) : SocketEvent
    data class PinnedUpdated(val chat: ChatDto) : SocketEvent
    data class MemberChanged(val payload: MemberPayload) : SocketEvent
    data class InviteUpdated(val payload: InvitePayload) : SocketEvent

    /* ── Presence ───────────────────────────────────────────────────────── */
    data class PresenceChanged(val payload: PresencePayload) : SocketEvent

    /* ── Сторис ─────────────────────────────────────────────────────────── */
    data class StoryNew(val story: StoryDto) : SocketEvent
    data class StoryViewed(val payload: StoryViewedPayload) : SocketEvent
    data class StoryExpired(val payload: StoryIdPayload) : SocketEvent
    data class StoryDeleted(val payload: StoryIdPayload) : SocketEvent

    /* ── Звонки ─────────────────────────────────────────────────────────── */
    data class CallIncoming(val payload: CallPayload) : SocketEvent
    data class CallStateChanged(val payload: CallPayload) : SocketEvent
    data class CallSignalReceived(val payload: CallSignalPayload) : SocketEvent
    data class CallIceServers(val payload: IceServersPayload) : SocketEvent

    /* ── Экономика ──────────────────────────────────────────────────────── */
    data class WalletUpdated(val wallet: WalletDto) : SocketEvent

    /**
     * Новая строка журнала кошелька.
     *
     * Приходит вместе с [WalletUpdated], а не вместо него: запись журнала
     * несёт `balance_after`, но не несёт `frozen` и `spent_total`, поэтому
     * баланс остаётся зоной ответственности [WalletUpdated]. Два источника,
     * пишущих одно поле, неизбежно устроили бы гонку.
     */
    data class LedgerAppended(val entry: LedgerDto) : SocketEvent
    data class StreakUpdated(val streak: StreakDto) : SocketEvent
    data class PremiumUpdated(val premium: PremiumStatusDto) : SocketEvent
    data class PremiumGranted(val payload: PremiumGrantedPayload) : SocketEvent
    data class ListingNew(val listing: MarketListingDto) : SocketEvent
    data class ListingUpdated(val listing: MarketListingDto) : SocketEvent
    data class PurchaseCompleted(val payload: PurchasePayload) : SocketEvent

    /* ── Профиль ────────────────────────────────────────────────────────── */
    data class ProfileUpdated(val user: UserDto) : SocketEvent
    data class AvatarUpdated(val user: UserDto) : SocketEvent
    data class BannerUpdated(val user: UserDto) : SocketEvent

    /* ── Админка ────────────────────────────────────────────────────────── */
    data class AdminLogAppended(val entry: AdminLogDto) : SocketEvent
    data class AdminBroadcast(val payload: BroadcastPayload) : SocketEvent

    /* ── Системные ──────────────────────────────────────────────────────── */
    data class ServerError(val payload: ErrorPayload) : SocketEvent
    data class Ack(val payload: AckPayload) : SocketEvent

    /** Сервер выкатил событие, которое клиент ещё не знает. Не ошибка. */
    data class Unknown(val op: String, val payload: JsonElement?) : SocketEvent

    data class DecodeFailure(val op: String, val reason: String) : SocketEvent
}

/**
 * Расширения для удобства в репозиториях.
 */
val SocketEvent.chatIdOrNull: String?
    get() = when (this) {
        is SocketEvent.MessageNew -> message.chatId
        is SocketEvent.MessageAck -> message.chatId
        is SocketEvent.MessageEdited -> message.chatId
        is SocketEvent.MessageDeleted -> payload.chatId
        is SocketEvent.ReactionUpdated -> message.chatId
        is SocketEvent.DeliveryUpdated -> payload.chatId
        is SocketEvent.ReadUpdated -> payload.chatId
        is SocketEvent.Typing -> payload.chatId
        is SocketEvent.ChatUpdated -> chat.id
        is SocketEvent.ChatCreated -> chat.id
        is SocketEvent.DraftUpdated -> payload.chatId
        is SocketEvent.PinnedUpdated -> chat.id
        is SocketEvent.MemberChanged -> payload.chatId
        is SocketEvent.InviteUpdated -> payload.chatId
        is SocketEvent.CallIncoming -> payload.chatId
        else -> null
    }

/** События, требующие показа уведомления (если приложение в фоне). */
val SocketEvent.isNotifiable: Boolean
    get() = when (this) {
        is SocketEvent.MessageNew -> !message.silent
        is SocketEvent.CallIncoming -> true
        is SocketEvent.StoryNew -> false
        is SocketEvent.PremiumGranted -> true
        is SocketEvent.AdminBroadcast -> true
        else -> false
    }

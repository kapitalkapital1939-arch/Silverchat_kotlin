package com.silverchat.core.domain.repository

import com.silverchat.core.common.result.ScResult
import com.silverchat.core.model.Gift
import com.silverchat.core.model.GiftTransaction
import com.silverchat.core.model.ListingId
import com.silverchat.core.model.MarketFeed
import com.silverchat.core.model.MarketQuery
import com.silverchat.core.model.Message
import com.silverchat.core.model.OwnedGift
import com.silverchat.core.model.PurchaseRequest
import com.silverchat.core.model.PurchaseResult
import com.silverchat.core.model.SaleRequest
import com.silverchat.core.model.UsernameListing
import com.silverchat.core.model.UsernameOffer
import kotlinx.coroutines.flow.Flow

/**
 * Маркет: покупка/продажа/обмен юзернеймов + подарки за сильверы.
 *
 * Все сделки атомарны на стороне бэкенда (escrow): клиент отправляет заявку
 * и получает [PurchaseResult], а не меняет баланс локально.
 */
interface MarketRepository {

    /* ── Юзернеймы ──────────────────────────────────────────────────────── */

    fun observeFeed(query: MarketQuery): Flow<MarketFeed>

    fun observeListing(listingId: ListingId): Flow<UsernameListing?>

    fun observeMyListings(): Flow<List<UsernameListing>>

    fun observeOffers(listingId: ListingId): Flow<List<UsernameOffer>>

    fun observePurchaseHistory(): Flow<List<UsernameListing>>

    suspend fun buy(request: PurchaseRequest): ScResult<PurchaseResult>

    suspend fun sell(request: SaleRequest): ScResult<UsernameListing>

    suspend fun makeOffer(listingId: ListingId, amountSilver: Long): ScResult<Unit>

    suspend fun acceptOffer(offerId: String): ScResult<Unit>

    suspend fun declineOffer(offerId: String): ScResult<Unit>

    suspend fun cancelListing(listingId: ListingId): ScResult<Unit>

    /** Свободен ли username: проверка до выставления на продажу. */
    suspend fun checkAvailability(username: String): ScResult<UsernameAvailability>

    /** Обмен юзернеймами между пользователями (swap-сделка). */
    suspend fun proposeSwap(
        myUsername: String,
        theirUsername: String,
        theirUserId: com.silverchat.core.model.UserId,
    ): ScResult<SwapProposal>

    suspend fun respondToSwap(swapId: String, accept: Boolean): ScResult<Unit>

    /* ── Подарки ────────────────────────────────────────────────────────── */

    fun observeGifts(premiumOnly: Boolean = false): Flow<List<Gift>>

    fun observeMyGifts(): Flow<List<OwnedGift>>

    suspend fun sendGift(transaction: GiftTransaction): ScResult<Message>

    /** Конвертация подарка обратно в сильверы (если [Gift.convertibleSilver] задан). */
    suspend fun convertGiftToSilver(ownedGiftId: String): ScResult<Long>

    suspend fun upgradeGift(ownedGiftId: String): ScResult<OwnedGift>
}

sealed interface UsernameAvailability {
    data object Available : UsernameAvailability
    data class Taken(val ownerId: com.silverchat.core.model.UserId?, val listingId: ListingId?) : UsernameAvailability
    data class Reserved(val reason: String) : UsernameAvailability
    data class Invalid(val reason: String) : UsernameAvailability
}

data class SwapProposal(
    val id: String,
    val myUsername: String,
    val theirUsername: String,
    val theirUserId: com.silverchat.core.model.UserId,
    val createdAt: Long,
    val expiresAt: Long,
)

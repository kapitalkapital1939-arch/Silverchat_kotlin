package com.silverchat.core.network.api

import com.silverchat.core.network.HttpHeaders
import com.silverchat.core.network.dto.AdminAccessDto
import com.silverchat.core.network.dto.AdminBanDto
import com.silverchat.core.network.dto.AdminGrantDto
import com.silverchat.core.network.dto.AdminLogDto
import com.silverchat.core.network.dto.AdminStatsDto
import com.silverchat.core.network.dto.AdminUserDetailsDto
import com.silverchat.core.network.dto.AdminWalletDto
import com.silverchat.core.network.dto.AuthResponse
import com.silverchat.core.network.dto.AvailabilityDto
import com.silverchat.core.network.dto.BroadcastDto
import com.silverchat.core.network.dto.BuyRequest
import com.silverchat.core.network.dto.BuyResponse
import com.silverchat.core.network.dto.CallHistoryDto
import com.silverchat.core.network.dto.CallSessionDto
import com.silverchat.core.network.dto.ChatDto
import com.silverchat.core.network.dto.ChatFolderDto
import com.silverchat.core.network.dto.ChatListResponse
import com.silverchat.core.network.dto.ConvertGiftResponse
import com.silverchat.core.network.dto.CreateChannelRequest
import com.silverchat.core.network.dto.CreateGroupRequest
import com.silverchat.core.network.dto.DeleteAnywhereRequest
import com.silverchat.core.network.dto.DeliveryRequest
import com.silverchat.core.network.dto.DeveloperRequest
import com.silverchat.core.network.dto.DraftRequest
import com.silverchat.core.network.dto.EditMessageRequest
import com.silverchat.core.network.dto.FolderRequest
import com.silverchat.core.network.dto.ForwardRequest
import com.silverchat.core.network.dto.GiftDto
import com.silverchat.core.network.dto.GiftPremiumRequest
import com.silverchat.core.network.dto.IceServersResponse
import com.silverchat.core.network.dto.InviteLinkDto
import com.silverchat.core.network.dto.InviteLinkRequest
import com.silverchat.core.network.dto.LedgerDto
import com.silverchat.core.network.dto.ListingPriceRequest
import com.silverchat.core.network.dto.LocationRequest
import com.silverchat.core.network.dto.MarketFeedDto
import com.silverchat.core.network.dto.MarketListingDto
import com.silverchat.core.network.dto.MembersResponse
import com.silverchat.core.network.dto.MessageDto
import com.silverchat.core.network.dto.MessageListResponse
import com.silverchat.core.network.dto.OfferDto
import com.silverchat.core.network.dto.OwnedGiftDto
import com.silverchat.core.network.dto.PremiumGrantRequest
import com.silverchat.core.network.dto.PremiumStatusDto
import com.silverchat.core.network.dto.PremiumTierDto
import com.silverchat.core.network.dto.PrivacyDto
import com.silverchat.core.network.dto.PrivacyRequest
import com.silverchat.core.network.dto.PublishStoryRequest
import com.silverchat.core.network.dto.PurchasePremiumRequest
import com.silverchat.core.network.dto.ReportDto
import com.silverchat.core.network.dto.ReportUserRequest
import com.silverchat.core.network.dto.ResolveReportDto
import com.silverchat.core.network.dto.ResolveResponse
import com.silverchat.core.network.dto.RestrictRequest
import com.silverchat.core.network.dto.RoleRequest
import com.silverchat.core.network.dto.SellRequest
import com.silverchat.core.network.dto.SendGiftRequest
import com.silverchat.core.network.dto.SendMessageRequest
import com.silverchat.core.network.dto.SendOtpResponse
import com.silverchat.core.network.dto.ServerSettingsDto
import com.silverchat.core.network.dto.SessionDto
import com.silverchat.core.network.dto.StartCallRequest
import com.silverchat.core.network.dto.StoryClusterDto
import com.silverchat.core.network.dto.StoryDto
import com.silverchat.core.network.dto.StoryReplyRequest
import com.silverchat.core.network.dto.StreakDto
import com.silverchat.core.network.dto.SwapDto
import com.silverchat.core.network.dto.SwapRequest
import com.silverchat.core.network.dto.TransferRequest
import com.silverchat.core.network.dto.UpdateChatRequest
import com.silverchat.core.network.dto.UpdateMemberRequest
import com.silverchat.core.network.dto.UpdateProfileRequest
import com.silverchat.core.network.dto.UploadTicketRequest
import com.silverchat.core.network.dto.UploadTicketResponse
import com.silverchat.core.network.dto.UserDto
import com.silverchat.core.network.dto.VerifiedRequest
import com.silverchat.core.network.dto.WalletDto
import com.silverchat.core.network.dto.WorkingHoursRequest
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * REST API бэкенда (Node.js).
 *
 * Разделение ответственности:
 *  - REST  — команды и запросы «по требованию» (история, покупка, профиль);
 *  - WSS   — всё, что приходит само (новые сообщения, реакции, сторис, звонки,
 *            изменения баланса, админ-события).
 *
 * Ответы всегда приходят в DTO (:core.network.dto) и маппятся в :core:model.
 */
interface SilverChatApi {

    /* ── Auth ───────────────────────────────────────────────────────────── */

    @POST("auth/otp/send")
    suspend fun sendOtp(@Body body: SendOtpRequest): SendOtpResponse

    @POST("auth/otp/verify")
    suspend fun verifyOtp(@Body body: VerifyOtpRequest): AuthResponse

    @POST("auth/refresh")
    suspend fun refreshTokens(@Body body: RefreshRequest): AuthResponse

    @POST("auth/logout")
    suspend fun logout(): EmptyResponse

    @GET("auth/sessions")
    suspend fun sessions(): List<SessionDto>

    @DELETE("auth/sessions/{id}")
    suspend fun revokeSession(@Path("id") sessionId: String): EmptyResponse

    /* ── Profile ────────────────────────────────────────────────────────── */

    @GET("users/me")
    suspend fun me(): UserDto

    @PATCH("users/me")
    suspend fun updateMe(@Body body: UpdateProfileRequest): UserDto

    @GET("users/{id}")
    suspend fun user(@Path("id") userId: String): UserDto

    @GET("users/by-username/{username}")
    suspend fun userByUsername(@Path("username") username: String): UserDto

    @Multipart
    @POST("users/me/avatar")
    suspend fun uploadAvatar(
        @Part file: okhttp3.MultipartBody.Part,
        @Query("animated") animated: Boolean,
    ): UserDto

    @Multipart
    @POST("users/me/banner")
    suspend fun uploadBanner(
        @Part file: okhttp3.MultipartBody.Part,
        @Query("animated") animated: Boolean,
    ): UserDto

    @PATCH("users/me/location")
    suspend fun setLocation(@Body body: LocationRequest): UserDto

    /** Снятие блока «Местоположение». См. комментарий к [clearUsername]. */
    @DELETE("users/me/location")
    suspend fun clearLocation(): UserDto

    /** Снятие блока «Часы работы». См. комментарий к [clearUsername]. */
    @DELETE("users/me/working-hours")
    suspend fun clearWorkingHours(): UserDto

    @PATCH("users/me/working-hours")
    suspend fun setWorkingHours(@Body body: WorkingHoursRequest): UserDto

    /**
     * Снятие юзернейма отдельным DELETE.
     *
     * `explicitNulls = false` в клиентском Json не записывает null в тело
     * запроса, поэтому `PATCH users/me` с `username = null` ушёл бы как
     * «поле не трогаем», а не как «очистить». Отдельный глагол убирает
     * неоднозначность и на клиенте, и на сервере.
     */
    @DELETE("users/me/username")
    suspend fun clearUsername(): UserDto

    @GET("users/me/privacy")
    suspend fun privacy(): PrivacyDto

    @PATCH("users/me/privacy")
    suspend fun setPrivacy(@Body body: PrivacyRequest): UserDto

    /* ── Search ─────────────────────────────────────────────────────────── */

    @GET("search/users")
    suspend fun searchUsers(
        @Query("q") query: String,
        @Query("limit") limit: Int = 30,
    ): List<UserDto>

    @GET("search/chats")
    suspend fun searchChats(@Query("q") query: String): List<ChatDto>

    @GET("search/messages")
    suspend fun searchMessages(
        @Query("q") query: String,
        @Query("chat_id") chatId: String? = null,
    ): List<MessageDto>

    @GET("search/resolve")
    suspend fun resolveHandle(@Query("handle") handle: String): ResolveResponse

    /* ── Chats ──────────────────────────────────────────────────────────── */

    @GET("chats")
    suspend fun chats(
        @Query("folder") folderId: String? = null,
        @Query("archived") archived: Boolean = false,
        @Query("since") sinceTs: Long? = null,
    ): ChatListResponse

    @GET("chats/{id}")
    suspend fun chat(@Path("id") chatId: String): ChatDto

    @POST("chats/personal")
    suspend fun openPersonalChat(@Body body: OpenPersonalChatRequest): ChatDto

    @POST("chats/group")
    suspend fun createGroup(@Body body: CreateGroupRequest): ChatDto

    @POST("chats/channel")
    suspend fun createChannel(@Body body: CreateChannelRequest): ChatDto

    @PATCH("chats/{id}")
    suspend fun updateChat(@Path("id") chatId: String, @Body body: UpdateChatRequest): ChatDto

    @GET("chats/{id}/members")
    suspend fun members(
        @Path("id") chatId: String,
        @Query("q") query: String = "",
        @Query("offset") offset: Int = 0,
    ): MembersResponse

    @POST("chats/{id}/members")
    suspend fun addMembers(@Path("id") chatId: String, @Body body: AddMembersRequest): EmptyResponse

    @PATCH("chats/{id}/members/{userId}")
    suspend fun updateMember(
        @Path("id") chatId: String,
        @Path("userId") userId: String,
        @Body body: UpdateMemberRequest,
    ): EmptyResponse

    @DELETE("chats/{id}/members/{userId}")
    suspend fun kickMember(
        @Path("id") chatId: String,
        @Path("userId") userId: String,
    ): EmptyResponse

    @POST("chats/{id}/invite")
    suspend fun createInviteLink(@Path("id") chatId: String, @Body body: InviteLinkRequest): InviteLinkDto

    @DELETE("chats/{id}/invite/{token}")
    suspend fun revokeInviteLink(@Path("id") chatId: String, @Path("token") token: String): EmptyResponse

    @POST("chats/join/{token}")
    suspend fun joinByInvite(@Path("token") token: String): ChatDto

    @PATCH("chats/{id}/read")
    suspend fun markChatRead(@Path("id") chatId: String): EmptyResponse

    @PATCH("chats/{id}/draft")
    suspend fun saveDraft(@Path("id") chatId: String, @Body body: DraftRequest): EmptyResponse

    /* ── Messages ───────────────────────────────────────────────────────── */

    @GET("chats/{id}/messages")
    suspend fun messages(
        @Path("id") chatId: String,
        @Query("before") before: String? = null,
        @Query("after") after: String? = null,
        @Query("limit") limit: Int = 40,
    ): MessageListResponse

    @POST("chats/{id}/messages")
    suspend fun sendMessage(@Path("id") chatId: String, @Body body: SendMessageRequest): MessageDto

    @PATCH("messages/{id}")
    suspend fun editMessage(@Path("id") messageId: String, @Body body: EditMessageRequest): MessageDto

    @DELETE("messages/{id}")
    suspend fun deleteMessage(
        @Path("id") messageId: String,
        @Query("for_everyone") forEveryone: Boolean,
    ): EmptyResponse

    @POST("messages/{id}/reactions")
    suspend fun react(@Path("id") messageId: String, @Body body: ReactionRequest): MessageDto

    @DELETE("messages/{id}/reactions")
    suspend fun clearReactions(@Path("id") messageId: String): MessageDto

    @POST("messages/{id}/pin")
    suspend fun pinMessage(@Path("id") messageId: String, @Body body: PinRequest): EmptyResponse

    @POST("messages/forward")
    suspend fun forward(@Body body: ForwardRequest): List<MessageDto>

    @POST("messages/{id}/vote")
    suspend fun vote(@Path("id") messageId: String, @Body body: VoteRequest): MessageDto

    /** Пресigned-URL для загрузки медиа напрямую в object storage. */
    @POST("media/upload-ticket")
    suspend fun uploadTicket(@Body body: UploadTicketRequest): UploadTicketResponse

    /* ── Stories ────────────────────────────────────────────────────────── */

    @GET("stories/feed")
    suspend fun storiesFeed(): List<StoryClusterDto>

    @POST("stories")
    suspend fun publishStory(@Body body: PublishStoryRequest): StoryDto

    @POST("stories/{id}/view")
    suspend fun viewStory(@Path("id") storyId: String): EmptyResponse

    @POST("stories/{id}/reaction")
    suspend fun reactStory(@Path("id") storyId: String, @Body body: ReactionRequest): EmptyResponse

    @POST("stories/{id}/reply")
    suspend fun replyStory(@Path("id") storyId: String, @Body body: StoryReplyRequest): MessageDto

    @DELETE("stories/{id}")
    suspend fun deleteStory(@Path("id") storyId: String): EmptyResponse

    /* ── Calls ──────────────────────────────────────────────────────────── */

    @POST("calls/start")
    suspend fun startCall(@Body body: StartCallRequest): CallSessionDto

    @POST("calls/{id}/accept")
    suspend fun acceptCall(@Path("id") callId: String): CallSessionDto

    @POST("calls/{id}/decline")
    suspend fun declineCall(@Path("id") callId: String): EmptyResponse

    @POST("calls/{id}/end")
    suspend fun endCall(@Path("id") callId: String): EmptyResponse

    @GET("calls/{id}/ice")
    suspend fun iceServers(@Path("id") callId: String): IceServersResponse

    /**
     * Полная сессия звонка.
     *
     * Нужен, потому что кадр `call.incoming` по WebSocket несёт только
     * идентификаторы: список участников и ICE-параметры клиент добирает здесь.
     */
    @GET("calls/{id}")
    suspend fun callSession(@Path("id") callId: String): CallSessionDto

    @GET("calls/history")
    suspend fun callHistory(): List<CallHistoryDto>

    /* ── Market ─────────────────────────────────────────────────────────── */

    @GET("market/usernames")
    suspend fun marketListings(
        @Query("q") query: String = "",
        @Query("sort") sort: String = "popular",
        @Query("max_price") maxPrice: Long? = null,
        @Query("min_length") minLength: Int? = null,
        @Query("max_length") maxLength: Int? = null,
        @Query("categories") categories: String? = null,
        @Query("rarity") rarity: String? = null,
        @Query("page") page: Int = 0,
    ): MarketFeedDto

    @GET("market/usernames/{id}")
    suspend fun listing(@Path("id") listingId: String): MarketListingDto

    @POST("market/usernames/buy")
    suspend fun buyUsername(
        @Body body: BuyRequest,
        @Header(HttpHeaders.IDEMPOTENCY_KEY) idempotencyKey: String,
    ): BuyResponse

    @POST("market/usernames/sell")
    suspend fun sellUsername(
        @Body body: SellRequest,
        @Header(HttpHeaders.IDEMPOTENCY_KEY) idempotencyKey: String,
    ): MarketListingDto

    @POST("market/usernames/{id}/offers")
    suspend fun makeOffer(
        @Path("id") listingId: String,
        @Body body: OfferRequest,
        @Header(HttpHeaders.IDEMPOTENCY_KEY) idempotencyKey: String,
    ): EmptyResponse

    @GET("market/usernames/{id}/offers")
    suspend fun offers(@Path("id") listingId: String): List<OfferDto>

    @GET("market/gifts")
    suspend fun gifts(@Query("premium_only") premiumOnly: Boolean = false): List<GiftDto>

    @POST("market/gifts/send")
    suspend fun sendGift(
        @Body body: SendGiftRequest,
        @Header(HttpHeaders.IDEMPOTENCY_KEY) idempotencyKey: String,
    ): MessageDto

    @POST("market/gifts/{ownedId}/convert")
    suspend fun convertGift(
        @Path("ownedId") ownedGiftId: String,
        @Header(HttpHeaders.IDEMPOTENCY_KEY) idempotencyKey: String,
    ): ConvertGiftResponse

    /* ── Wallet / Premium ───────────────────────────────────────────────── */

    @GET("wallet")
    suspend fun wallet(): WalletDto

    /**
     * Журнал операций кошелька.
     *
     * Курсорная пагинация по той же схеме, что и сообщения: `before` —
     * идентификатор самой старой записи, которая у клиента уже есть, сервер
     * возвращает `limit` записей старше неё. Пустой список означает конец
     * истории.
     *
     * Курсор, а не offset, потому что журнал пополняется непрерывно:
     * offset-страница сдвигается под новыми записями, и пользователь видит
     * дубликат или пропуск на стыке страниц.
     */
    @GET("wallet/ledger")
    suspend fun ledger(
        @Query("limit") limit: Int = 50,
        @Query("before") before: String? = null,
    ): List<LedgerDto>

    @GET("wallet/streak")
    suspend fun streak(): StreakDto

    @POST("wallet/streak/claim")
    suspend fun claimStreak(@Header(HttpHeaders.IDEMPOTENCY_KEY) idempotencyKey: String): StreakDto

    @POST("wallet/transfer")
    suspend fun transfer(
        @Body body: TransferRequest,
        @Header(HttpHeaders.IDEMPOTENCY_KEY) idempotencyKey: String,
    ): LedgerDto

    @GET("premium/tiers")
    suspend fun premiumTiers(): List<PremiumTierDto>

    @POST("premium/purchase")
    suspend fun purchasePremium(
        @Body body: PurchasePremiumRequest,
        @Header(HttpHeaders.IDEMPOTENCY_KEY) idempotencyKey: String,
    ): PremiumStatusDto

    /* ── Admin (@silver) ────────────────────────────────────────────────── */

    @GET("admin/access")
    suspend fun adminAccess(): AdminAccessDto

    @GET("admin/stats")
    suspend fun adminStats(): AdminStatsDto

    @GET("admin/users")
    suspend fun adminUsers(
        @Query("q") query: String = "",
        @Query("flagged") flagged: Boolean = false,
    ): List<UserDto>

    @GET("admin/users/{id}")
    suspend fun adminUserDetails(@Path("id") userId: String): AdminUserDetailsDto

    /** Универсальный эндпоинт выдачи статусов: verified / premium / developer / role. */
    @POST("admin/grant")
    suspend fun adminGrant(@Body body: AdminGrantDto): UserDto

    @POST("admin/wallet/credit")
    suspend fun adminCredit(
        @Body body: AdminWalletDto,
        @Header(HttpHeaders.IDEMPOTENCY_KEY) idempotencyKey: String,
    ): WalletDto

    @POST("admin/wallet/debit")
    suspend fun adminDebit(
        @Body body: AdminWalletDto,
        @Header(HttpHeaders.IDEMPOTENCY_KEY) idempotencyKey: String,
    ): WalletDto

    @POST("admin/ban")
    suspend fun adminBan(@Body body: AdminBanDto): EmptyResponse

    @GET("admin/reports")
    suspend fun adminReports(@Query("status") status: String? = null): List<ReportDto>

    @POST("admin/reports/{id}/resolve")
    suspend fun resolveReport(@Path("id") caseId: String, @Body body: ResolveReportDto): EmptyResponse

    @GET("admin/audit")
    suspend fun auditLog(
        @Query("action") action: String? = null,
        @Query("limit") limit: Int = 100,
    ): List<AdminLogDto>

    @POST("admin/broadcast")
    suspend fun broadcast(@Body body: BroadcastDto): EmptyResponse

    /* ── Settings ───────────────────────────────────────────────────────── */

    @GET("settings")
    suspend fun serverSettings(): ServerSettingsDto

    @PATCH("settings")
    suspend fun pushSettings(@Body body: ServerSettingsDto): EmptyResponse

    /* ══════════════════════════════════════════════════════════════════════
       РАСШИРЕНИЯ КОНТРАКТА
       ----------------------------------------------------------------------
       Доменные интерфейсы (:core:domain) описывают продуктовое поведение
       целиком. Ниже — эндпоинты, закрывающие оставшиеся методы репозиториев,
       чтобы ни один из них не был «заглушкой на клиенте».
       ══════════════════════════════════════════════════════════════════════ */

    /* ── Чаты: папки, тип, гео, график, выход, заявки, закрепление ─────── */

    @POST("chats/folders")
    suspend fun createFolder(@Body body: FolderRequest): ChatFolderDto

    @PATCH("chats/{id}/type")
    suspend fun setChatType(@Path("id") chatId: String, @Body body: ChatTypeRequest): ChatDto

    @PATCH("chats/{id}/location")
    suspend fun setChatLocation(@Path("id") chatId: String, @Body body: LocationRequest): ChatDto

    @PATCH("chats/{id}/working-hours")
    suspend fun setChatWorkingHours(
        @Path("id") chatId: String,
        @Body body: WorkingHoursRequest,
    ): ChatDto

    @POST("chats/{id}/leave")
    suspend fun leaveChat(@Path("id") chatId: String): EmptyResponse

    @POST("chats/{id}/join-requests/{userId}")
    suspend fun approveJoinRequest(
        @Path("id") chatId: String,
        @Path("userId") userId: String,
        @Body body: ApproveRequest,
    ): EmptyResponse

    @POST("chats/{id}/pin")
    suspend fun pinChat(@Path("id") chatId: String, @Body body: PinChatRequest): EmptyResponse

    @POST("chats/{id}/mute")
    suspend fun muteChat(@Path("id") chatId: String, @Body body: MuteChatRequest): EmptyResponse

    @POST("chats/{id}/archive")
    suspend fun archiveChat(@Path("id") chatId: String, @Body body: ArchiveChatRequest): EmptyResponse

    @DELETE("chats/{id}")
    suspend fun deleteChat(
        @Path("id") chatId: String,
        @Query("for_everyone") forEveryone: Boolean,
    ): EmptyResponse

    @GET("chats/{id}/invite-links")
    suspend fun inviteLinks(@Path("id") chatId: String): List<InviteLinkDto>

    /* ── Сообщения: открепление, доставка, прочтение, опросы ───────────── */

    @DELETE("messages/{id}/pin")
    suspend fun unpinMessage(@Path("id") messageId: String): EmptyResponse

    @POST("messages/unpin-all")
    suspend fun unpinAll(@Body body: UnpinAllRequest): EmptyResponse

    @POST("messages/delivered")
    suspend fun markDelivered(@Body body: DeliveryRequest): EmptyResponse

    @POST("messages/read")
    suspend fun markMessagesRead(@Body body: DeliveryRequest): EmptyResponse

    @POST("messages/{id}/poll/close")
    suspend fun closePoll(@Path("id") messageId: String): MessageDto

    @GET("chats/{id}/reactions")
    suspend fun availableReactions(@Path("id") chatId: String): List<String>

    /* ── Сторис: свои, архив, зрители, закрепление, stealth ────────────── */

    @GET("stories/mine")
    suspend fun myStories(): List<StoryDto>

    @GET("stories/archive")
    suspend fun storiesArchive(): List<StoryDto>

    @GET("stories/{id}/viewers")
    suspend fun storyViewers(@Path("id") storyId: String): List<UserDto>

    @POST("stories/{id}/pin")
    suspend fun pinStory(@Path("id") storyId: String, @Body body: StoryPinRequest): EmptyResponse

    @POST("stories/{id}/stealth")
    suspend fun viewStoryStealth(@Path("id") storyId: String): EmptyResponse

    /* ── Звонки: состояние медиа ───────────────────────────────────────── */

    @POST("calls/{id}/mute")
    suspend fun setCallMuted(@Path("id") callId: String, @Body body: MuteRequest): EmptyResponse

    @POST("calls/{id}/camera")
    suspend fun setCallCamera(@Path("id") callId: String, @Body body: CameraRequest): EmptyResponse

    @POST("calls/{id}/switch-camera")
    suspend fun switchCallCamera(@Path("id") callId: String): EmptyResponse

    @POST("calls/{id}/screen")
    suspend fun setCallScreenSharing(
        @Path("id") callId: String,
        @Body body: ScreenRequest,
    ): EmptyResponse

    /* ── Маркет: мои лоты, история, отмена, свопы, офферы, подарки ─────── */

    @GET("market/my-listings")
    suspend fun myListings(): List<MarketListingDto>

    @GET("market/purchases")
    suspend fun purchaseHistory(): List<MarketListingDto>

    @DELETE("market/listings/{id}")
    suspend fun cancelListing(@Path("id") listingId: String): EmptyResponse

    @GET("market/availability/{username}")
    suspend fun checkAvailability(@Path("username") username: String): AvailabilityDto

    @POST("market/swaps")
    suspend fun proposeSwap(
        @Body body: SwapRequest,
        @Header(HttpHeaders.IDEMPOTENCY_KEY) idempotencyKey: String,
    ): SwapDto

    @POST("market/swaps/{id}")
    suspend fun respondToSwap(
        @Path("id") swapId: String,
        @Body body: SwapRespondRequest,
    ): EmptyResponse

    @POST("market/offers/{id}/accept")
    suspend fun acceptOffer(
        @Path("id") offerId: String,
        @Header(HttpHeaders.IDEMPOTENCY_KEY) idempotencyKey: String,
    ): EmptyResponse

    @POST("market/offers/{id}/decline")
    suspend fun declineOffer(@Path("id") offerId: String): EmptyResponse

    @GET("market/gifts/mine")
    suspend fun myGifts(): List<OwnedGiftDto>

    @POST("market/gifts/{ownedId}/upgrade")
    suspend fun upgradeGift(
        @Path("ownedId") ownedGiftId: String,
        @Header(HttpHeaders.IDEMPOTENCY_KEY) idempotencyKey: String,
    ): OwnedGiftDto

    /* ── Кошелёк: Premium-статус, отмена автопродления, подарок ────────── */

    @GET("wallet/premium")
    suspend fun premiumStatus(): PremiumStatusDto

    @POST("wallet/premium/cancel-renew")
    suspend fun cancelAutoRenew(): EmptyResponse

    @POST("wallet/premium/gift")
    suspend fun giftPremium(
        @Body body: GiftPremiumRequest,
        @Header(HttpHeaders.IDEMPOTENCY_KEY) idempotencyKey: String,
    ): PremiumStatusDto

    /* ── Профиль: общие медиа, жалобы, чёрный список ───────────────────── */

    @GET("chats/{id}/media")
    suspend fun sharedMedia(
        @Path("id") chatId: String,
        @Query("kind") kind: String,
    ): List<MessageDto>

    @POST("users/{id}/report")
    suspend fun reportUser(@Path("id") userId: String, @Body body: ReportUserRequest): EmptyResponse

    @POST("users/{id}/block")
    suspend fun blockUser(@Path("id") userId: String): EmptyResponse

    @DELETE("users/{id}/block")
    suspend fun unblockUser(@Path("id") userId: String): EmptyResponse

    @GET("users/blocked")
    suspend fun blockedUsers(): List<UserDto>

    /* ── Админка: точечные действия вместо универсального grant ────────── */

    @POST("admin/users/{id}/verified")
    suspend fun adminSetVerified(
        @Path("id") userId: String,
        @Body body: VerifiedRequest,
    ): UserDto

    @POST("admin/users/{id}/premium")
    suspend fun adminSetPremium(
        @Path("id") userId: String,
        @Body body: PremiumGrantRequest,
    ): UserDto

    @POST("admin/users/{id}/developer")
    suspend fun adminSetDeveloper(
        @Path("id") userId: String,
        @Body body: DeveloperRequest,
    ): UserDto

    @POST("admin/users/{id}/role")
    suspend fun adminSetRole(@Path("id") userId: String, @Body body: RoleRequest): UserDto

    @POST("admin/users/{id}/unban")
    suspend fun adminUnban(@Path("id") userId: String, @Body body: ReasonRequest): EmptyResponse

    @POST("admin/users/{id}/restrict")
    suspend fun adminRestrict(
        @Path("id") userId: String,
        @Body body: RestrictRequest,
    ): EmptyResponse

    @DELETE("admin/chats/{chatId}/messages/{messageId}")
    suspend fun adminDeleteMessage(
        @Path("chatId") chatId: String,
        @Path("messageId") messageId: String,
        @Body body: DeleteAnywhereRequest,
    ): EmptyResponse

    @DELETE("admin/chats/{id}")
    suspend fun adminDeleteChat(
        @Path("id") chatId: String,
        @Body body: DeleteAnywhereRequest,
    ): EmptyResponse

    @POST("admin/wallet/reset")
    suspend fun adminResetWallet(
        @Body body: AdminWalletDto,
        @Header(HttpHeaders.IDEMPOTENCY_KEY) idempotencyKey: String,
    ): WalletDto

    @POST("admin/transactions/{id}/refund")
    suspend fun adminRefund(
        @Path("id") transactionId: String,
        @Body body: ReasonRequest,
        @Header(HttpHeaders.IDEMPOTENCY_KEY) idempotencyKey: String,
    ): EmptyResponse

    @POST("admin/usernames/{username}/block")
    suspend fun adminBlockUsername(
        @Path("username") username: String,
        @Body body: ReasonRequest,
    ): EmptyResponse

    @PATCH("admin/listings/{id}/price")
    suspend fun adminAdjustListingPrice(
        @Path("id") listingId: String,
        @Body body: ListingPriceRequest,
    ): MarketListingDto
}

/** Маркер пустого ответа: сервер отдаёт `{"ok":true}`. */
@kotlinx.serialization.Serializable
data class EmptyResponse(val ok: Boolean = true)

/* Имена заголовков протокола (Idempotency-Key, X-SC-Protocol и прочие) живут
 * в com.silverchat.core.network.HttpHeaders — едином словаре, который
 * используют и AuthInterceptor, и OkHttpRealtimeSocket. */

package com.silverchat.core.data.repository

import com.silverchat.core.common.log.LogTag
import com.silverchat.core.common.result.ScError
import com.silverchat.core.common.result.ScResult
import com.silverchat.core.data.mapper.toDomain
import com.silverchat.core.data.mapper.wireName
import com.silverchat.core.data.realtime.RealtimeBus
import com.silverchat.core.domain.repository.AdminAccess
import com.silverchat.core.domain.repository.AdminRepository
import com.silverchat.core.domain.repository.AdminUserDetails
import com.silverchat.core.model.AdminAction
import com.silverchat.core.model.AdminBroadcastAudience
import com.silverchat.core.model.AdminGrantRequest
import com.silverchat.core.model.AdminLogEntry
import com.silverchat.core.model.AdminRole
import com.silverchat.core.model.AdminStats
import com.silverchat.core.model.ChatId
import com.silverchat.core.model.ListingId
import com.silverchat.core.model.MessageId
import com.silverchat.core.model.ModerationCase
import com.silverchat.core.model.ModerationStatus
import com.silverchat.core.model.User
import com.silverchat.core.model.UserId
import com.silverchat.core.model.UsernameListing
import com.silverchat.core.model.Wallet
import com.silverchat.core.network.api.SilverChatApi
import com.silverchat.core.network.dto.AdminBanDto
import com.silverchat.core.network.dto.AdminGrantDto
import com.silverchat.core.network.dto.AdminWalletDto
import com.silverchat.core.network.dto.BroadcastDto
import com.silverchat.core.network.dto.DeleteAnywhereRequest
import com.silverchat.core.network.dto.DeveloperRequest
import com.silverchat.core.network.dto.ListingPriceRequest
import com.silverchat.core.network.dto.PremiumGrantRequest
import com.silverchat.core.network.dto.ReasonRequest
import com.silverchat.core.network.dto.ResolveReportDto
import com.silverchat.core.network.dto.RestrictRequest
import com.silverchat.core.network.dto.RoleRequest
import com.silverchat.core.network.dto.VerifiedRequest
import com.silverchat.core.network.mapper.apiCall
import com.silverchat.core.network.mapper.apiCallIdempotent
import com.silverchat.core.network.mapper.toDomain as dtoToDomain
import com.silverchat.core.security.AuthState
import com.silverchat.core.security.TokenStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * Админ-панель: права, модерация, экономика, маркет, рассылки, аудит.
 *
 * ── Про безопасность ────────────────────────────────────────────────────
 * Всё, что делает этот класс, — UX. Каждый вызов уходит на эндпоинт,
 * закрытый на бэкенде middleware `AdminGuard`, который сверяет роль с
 * токеном, а не с тем, что клиент сам себе нарисовал. Подмена ответа
 * [observeAccess] через прокси даст пользователю открытые кнопки и 403 на
 * каждом действии. Поэтому здесь нет «клиентской проверки прав перед
 * запросом» — она бессмысленна и только размывает ответственность.
 *
 * ── Сброс прав при выходе ───────────────────────────────────────────────
 * [accessState] обнуляется на `DENIED` при `LoggedOut`/`Expired`. Без этого
 * после смены аккаунта новый пользователь на мгновение увидел бы раздел
 * админки от предыдущего — до первого ответа сервера.
 *
 * ── Кэш одного фильтра ──────────────────────────────────────────────────
 * Жалобы и аудит-лог хранятся парой «фильтр + данные», а не просто списком.
 * Иначе при переключении вкладки экран показал бы чужой список: данные от
 * фильтра «открытые» выглядели бы как «отклонённые» до прихода ответа.
 * Несовпавший фильтр отдаёт пустой список, и UI рисует индикатор загрузки.
 */
@Singleton
class AdminRepositoryImpl @Inject constructor(
    private val api: SilverChatApi,
    private val bus: RealtimeBus,
    private val tokenStore: TokenStore,
    private val json: Json,
) : AdminRepository {

    private val scope = CoroutineScope(SupervisorJob())
    private val inFlight = InFlightGuard(scope, LogTag.ADMIN)

    /** Fail-closed: до ответа сервера прав нет. */
    private val accessState = MutableStateFlow(AdminAccess.DENIED)

    private val reportsState =
        MutableStateFlow<Pair<ReportFilter?, List<ModerationCase>>>(null to emptyList())
    private val auditState =
        MutableStateFlow<Pair<AuditFilter?, List<AdminLogEntry>>>(null to emptyList())

    init {
        tokenStore.authState
            .onEach { state ->
                if (state is AuthState.LoggedOut || state is AuthState.Expired) {
                    accessState.value = AdminAccess.DENIED
                    reportsState.value = null to emptyList()
                    auditState.value = null to emptyList()
                    inFlight.reset()
                }
            }
            .launchIn(scope)
    }

    /* ── Права и сводка ────────────────────────────────────────────────── */

    override fun observeAccess(): Flow<AdminAccess> =
        combine(tokenStore.authState, accessState) { auth, access ->
            if (auth.isLoggedIn) access else AdminAccess.DENIED
        }.onStart {
            inFlight.launch("access") { accessState.value = api.adminAccess().toDomain() }
        }

    override suspend fun stats(): ScResult<AdminStats> = apiCall {
        api.adminStats().dtoToDomain()
    }

    override suspend fun searchUsers(
        query: String,
        onlyFlagged: Boolean,
    ): ScResult<List<User>> = apiCall {
        api.adminUsers(query.trim(), onlyFlagged).map { it.dtoToDomain() }
    }

    /**
     * Расширенная карточка: профиль, кошелёк, стрик, премиум, юзернеймы,
     * сессии и свежий аудит одним запросом.
     *
     * Отдельные вызовы (`users/{id}` + `wallet` + `sessions` + …) дали бы
     * шесть round-trip'ов и рассинхрон между блоками экрана.
     */
    override suspend fun userDetails(userId: UserId): ScResult<AdminUserDetails> = apiCall {
        api.adminUserDetails(userId.raw).toDomain()
    }

    /* ── Универсальное действие и выдача статусов ──────────────────────── */

    override suspend fun grant(request: AdminGrantRequest): ScResult<User> = afterMutation(
        apiCall {
            api.adminGrant(
                AdminGrantDto(
                    targetUserId = request.targetUserId.raw,
                    action = request.action.wireName(json),
                    durationDays = request.durationDays,
                    role = request.role?.wireName(json),
                    amountSilver = request.amountSilver,
                    reason = request.reason.trim(),
                ),
            ).dtoToDomain()
        },
    )

    override suspend fun setVerified(
        userId: UserId,
        verified: Boolean,
        reason: String,
    ): ScResult<User> = requireReason(reason, "verified") { trimmed ->
        afterMutation(
            apiCall {
                api.adminSetVerified(
                    userId.raw,
                    VerifiedRequest(verified = verified, reason = trimmed),
                ).dtoToDomain()
            },
        )
    }

    /**
     * Выдача премиума. `days = null` — пожизненно: отдельного флага нет,
     * отсутствие срока и есть признак Lifetime.
     */
    override suspend fun setPremium(
        userId: UserId,
        days: Int?,
        reason: String,
    ): ScResult<User> = requireReason(reason, "premium") { trimmed ->
        afterMutation(
            apiCall {
                api.adminSetPremium(
                    userId.raw,
                    PremiumGrantRequest(days = days, reason = trimmed),
                ).dtoToDomain()
            },
        )
    }

    override suspend fun setDeveloper(
        userId: UserId,
        developer: Boolean,
        reason: String,
    ): ScResult<User> = requireReason(reason, "developer") { trimmed ->
        afterMutation(
            apiCall {
                api.adminSetDeveloper(
                    userId.raw,
                    DeveloperRequest(developer = developer, reason = trimmed),
                ).dtoToDomain()
            },
        )
    }

    override suspend fun setAdminRole(
        userId: UserId,
        role: AdminRole,
        reason: String,
    ): ScResult<User> = requireReason(reason, "role") { trimmed ->
        afterMutation(
            apiCall {
                api.adminSetRole(
                    userId.raw,
                    RoleRequest(role = role.wireName(json), reason = trimmed),
                ).dtoToDomain()
            },
        )
    }

    /* ── Модерация ─────────────────────────────────────────────────────── */

    override suspend fun ban(
        userId: UserId,
        reason: String,
        permanent: Boolean,
    ): ScResult<Unit> = requireReason(reason, "ban") { trimmed ->
        afterMutation(
            apiCall {
                api.adminBan(
                    AdminBanDto(
                        targetUserId = userId.raw,
                        reason = trimmed,
                        permanent = permanent,
                    ),
                )
            }.map { },
        )
    }

    override suspend fun unban(userId: UserId, reason: String): ScResult<Unit> =
        requireReason(reason, "unban") { trimmed ->
            afterMutation(
                apiCall {
                    api.adminUnban(userId.raw, ReasonRequest(trimmed))
                }.map { },
            )
        }

    override suspend fun restrict(
        userId: UserId,
        reason: String,
        untilTs: Long,
    ): ScResult<Unit> = requireReason(reason, "restrict") { trimmed ->
        if (untilTs <= System.currentTimeMillis()) {
            ScResult.Failure(
                ScError.Validation(
                    message = "Ограничение должно заканчиваться в будущем",
                    field = "until_ts",
                ),
            )
        } else {
            afterMutation(
                apiCall {
                    api.adminRestrict(userId.raw, RestrictRequest(trimmed, untilTs))
                }.map { },
            )
        }
    }

    override fun observeReports(status: ModerationStatus?): Flow<List<ModerationCase>> {
        val filter = ReportFilter(all = status == null, status = status)
        return reportsState
            .map { (active, entries) -> if (active == filter) entries else emptyList() }
            .onStart { inFlight.launch("reports:$filter") { loadReports(filter) } }
    }

    /**
     * Решение по жалобе.
     *
     * Комментарий обязателен: запись попадает в неизменяемый аудит-лог, и
     * решение без обоснования невозможно проверить постфактум.
     */
    override suspend fun resolveReport(
        caseId: String,
        status: ModerationStatus,
        comment: String,
    ): ScResult<Unit> = requireReason(comment, "comment") { trimmed ->
        afterMutation(
            apiCall {
                api.resolveReport(
                    caseId,
                    ResolveReportDto(status = status.wireName(json), comment = trimmed),
                )
            }.map { },
        )
    }

    override suspend fun deleteMessageAnywhere(
        chatId: ChatId,
        messageId: MessageId,
        reason: String,
    ): ScResult<Unit> = requireReason(reason, "delete_message") { trimmed ->
        afterMutation(
            apiCall {
                api.adminDeleteMessage(
                    chatId.raw,
                    messageId.raw,
                    DeleteAnywhereRequest(trimmed),
                )
            }.map { },
        )
    }

    override suspend fun deleteChatAnywhere(
        chatId: ChatId,
        reason: String,
    ): ScResult<Unit> = requireReason(reason, "delete_chat") { trimmed ->
        afterMutation(
            apiCall {
                api.adminDeleteChat(chatId.raw, DeleteAnywhereRequest(trimmed))
            }.map { },
        )
    }

    /* ── Экономика из админки ──────────────────────────────────────────── */

    /* Все четыре операции двигают чужие сильверы, поэтому `Idempotency-Key`
     * обязателен. Ключ при этом ВСЕГДА новый (`freshKey`), а стабильный из
     * `IdempotencyKeyStore` не используется намеренно: администратор может
     * законно начислить одну и ту же сумму дважды с одной и той же причиной, и
     * стабильный ключ молча проглотил бы второе начисление. Для админ-панели
     * тихий no-op хуже двойного списания — его невозможно отличить от поломки.
     *
     * От потерянного ответа защищает автоматический повтор внутри одного
     * вызова: `apiCallIdempotent` захватывает ключ замыканием, поэтому обе
     * попытки уходят с одним и тем же значением. Повторное начисление, которое
     * сервер уже выполнил, отсекается им по ключу, а не по телу запроса.
     */

    override suspend fun creditSilver(
        userId: UserId,
        amount: Long,
        reason: String,
    ): ScResult<Wallet> = requireReason(reason, "credit") { trimmed ->
        afterMutation(
            apiCallIdempotent {
                api.adminCredit(
                    AdminWalletDto(userId.raw, amount = abs(amount), reason = trimmed),
                    idempotencyKey = IdempotencyKeyStore.freshKey(),
                ).dtoToDomain()
            },
        )
    }

    /**
     * Списание.
     *
     * По протоколу `amount` — положительная величина: глагол эндпоинта уже
     * говорит, что деньги уходят. `abs` страхует от знака на входе:
     * `CreditSilverUseCase` передаёт сюда `-amount`, и двойное отрицание у
     * любого другого вызывающего отправило бы на сервер отрицательную сумму,
     * которую тот превратил бы в начисление.
     */
    override suspend fun debitSilver(
        userId: UserId,
        amount: Long,
        reason: String,
    ): ScResult<Wallet> = requireReason(reason, "debit") { trimmed ->
        afterMutation(
            apiCallIdempotent {
                api.adminDebit(
                    AdminWalletDto(userId.raw, amount = abs(amount), reason = trimmed),
                    idempotencyKey = IdempotencyKeyStore.freshKey(),
                ).dtoToDomain()
            },
        )
    }

    /** Обнуление кошелька. `amount` в теле игнорируется сервером. */
    override suspend fun resetWallet(userId: UserId, reason: String): ScResult<Wallet> =
        requireReason(reason, "reset_wallet") { trimmed ->
            afterMutation(
                apiCallIdempotent {
                    api.adminResetWallet(
                        AdminWalletDto(userId.raw, amount = 0L, reason = trimmed),
                        idempotencyKey = IdempotencyKeyStore.freshKey(),
                    ).dtoToDomain()
                },
            )
        }

    override suspend fun refundTransaction(
        transactionId: String,
        reason: String,
    ): ScResult<Unit> = requireReason(reason, "refund") { trimmed ->
        afterMutation(
            apiCallIdempotent {
                api.adminRefund(
                    transactionId,
                    ReasonRequest(trimmed),
                    idempotencyKey = IdempotencyKeyStore.freshKey(),
                )
            }.map { },
        )
    }

    /* ── Маркет из админки ─────────────────────────────────────────────── */

    /**
     * Изъятие юзернейма из продажи.
     *
     * Ник приводится к нижнему регистру: сервер хранит их нормализованными,
     * и `@Silver` с `@silver` — одна и та же запись.
     */
    override suspend fun blockUsername(username: String, reason: String): ScResult<Unit> {
        val handle = username.trim().removePrefix("@").lowercase()
        if (handle.isEmpty()) {
            return ScResult.Failure(
                ScError.Validation(message = "Юзернейм не может быть пустым", field = "username"),
            )
        }
        return requireReason(reason, "block_username") { trimmed ->
            afterMutation(
                apiCall {
                    api.adminBlockUsername(handle, ReasonRequest(trimmed))
                }.map { },
            )
        }
    }

    /**
     * Ручная корректировка цены листинга.
     *
     * Обновление самого листинга в маркет приходит событием `market.updated`
     * по WebSocket — репозиторий намеренно не публикует его в шину: запись в
     * [com.silverchat.core.data.realtime.RealtimeBus] разрешена только
     * диспетчеру, иначе появилось бы два источника одних и тех же событий.
     */
    override suspend fun adjustListingPrice(
        listingId: ListingId,
        priceSilver: Long,
        reason: String,
    ): ScResult<UsernameListing> {
        if (priceSilver < 0L) {
            return ScResult.Failure(
                ScError.Validation(message = "Цена не может быть отрицательной", field = "price"),
            )
        }
        return requireReason(reason, "adjust_price") { trimmed ->
            afterMutation(
                apiCall {
                    api.adminAdjustListingPrice(
                        listingId.raw,
                        ListingPriceRequest(priceSilver = priceSilver, reason = trimmed),
                    ).dtoToDomain()
                },
            )
        }
    }

    /* ── Рассылки и аудит ──────────────────────────────────────────────── */

    override suspend fun broadcast(
        text: String,
        audience: AdminBroadcastAudience,
    ): ScResult<Unit> {
        val message = text.trim()
        if (message.isEmpty()) {
            return ScResult.Failure(
                ScError.Validation(message = "Текст рассылки не может быть пустым", field = "text"),
            )
        }
        return afterMutation(
            apiCall {
                api.broadcast(
                    BroadcastDto(text = message, audience = audience.wireName(json)),
                )
            }.map { },
        )
    }

    /**
     * Рассылки администрации из общего реалтайм-канала.
     *
     * Репозиторий отдаёт поток шины как есть и ничего в шину не пишет:
     * публикация событий разрешена только [com.silverchat.core.data.realtime.RealtimeDispatcher],
     * иначе у одного события появилось бы два источника.
     */
    override fun observeAdminBroadcasts(): Flow<String> = bus.adminBroadcasts

    override fun observeAuditLog(action: AdminAction?, limit: Int): Flow<List<AdminLogEntry>> {
        val filter = AuditFilter(action, limit)
        return auditState
            .map { (active, entries) -> if (active == filter) entries else emptyList() }
            .onStart { inFlight.launch("audit:$filter") { loadAudit(filter) } }
    }

    /* ── Внутреннее ────────────────────────────────────────────────────── */

    private suspend fun loadReports(filter: ReportFilter) {
        val wire = if (filter.all) null else filter.status?.wireName(json)
        reportsState.value = filter to api.adminReports(wire).map { it.dtoToDomain() }
    }

    private suspend fun loadAudit(filter: AuditFilter) {
        val wire = filter.action?.wireName(json)
        auditState.value = filter to api.auditLog(wire, filter.limit).map { it.dtoToDomain() }
    }

    /**
     * После успешного действия перечитываем открытые фильтры.
     *
     * Каждое действие админки дописывает строку в аудит-лог и может закрыть
     * жалобу, поэтому экраны «Аудит» и «Жалобы» без перечитывания показали бы
     * устаревшие данные до ручного обновления. Перечитывание запускается в
     * фоне и не задерживает возврат результата вызывающему.
     */
    private fun <T> afterMutation(result: ScResult<T>): ScResult<T> {
        if (result.isSuccess) {
            scope.launch {
                reportsState.value.first?.let { runCatching { loadReports(it) } }
                auditState.value.first?.let { runCatching { loadAudit(it) } }
            }
        }
        return result
    }

    /**
     * Обоснование обязательно для каждого действия админки.
     *
     * Проверка на клиенте не заменяет серверную, а экономит round-trip:
     * без неё админ узнал бы о незаполненном поле только после 422.
     */
    private inline fun <T> requireReason(
        reason: String,
        field: String,
        block: (String) -> ScResult<T>,
    ): ScResult<T> {
        val trimmed = reason.trim()
        if (trimmed.isEmpty()) {
            return ScResult.Failure(
                ScError.Validation(message = "Причина действия обязательна", field = field),
            )
        }
        return block(trimmed)
    }

    /** `all = true` означает «без фильтра по статусу». */
    private data class ReportFilter(val all: Boolean, val status: ModerationStatus?)

    private data class AuditFilter(val action: AdminAction?, val limit: Int)
}

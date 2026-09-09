package com.silverchat.feature.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.silverchat.core.common.result.ScResult
import com.silverchat.core.domain.repository.AdminAccess
import com.silverchat.core.domain.repository.AdminRepository
import com.silverchat.core.domain.repository.AdminUserDetails
import com.silverchat.core.domain.repository.AuthRepository
import com.silverchat.core.domain.usecase.admin.CreditSilverParams
import com.silverchat.core.domain.usecase.admin.CreditSilverUseCase
import com.silverchat.core.domain.usecase.admin.GrantPrivilegeParams
import com.silverchat.core.domain.usecase.admin.GrantPrivilegeUseCase
import com.silverchat.core.domain.usecase.admin.ObserveAdminAccessUseCase
import com.silverchat.core.model.AdminAction
import com.silverchat.core.model.AdminBroadcastAudience
import com.silverchat.core.model.AdminLogEntry
import com.silverchat.core.model.AdminRole
import com.silverchat.core.model.AdminStats
import com.silverchat.core.model.ModerationCase
import com.silverchat.core.model.ModerationStatus
import com.silverchat.core.model.User
import com.silverchat.core.model.UserId
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Админ-панель `@silver`.
 *
 * ПРО БЕЗОПАСНОСТЬ. Все проверки [AdminAccess] в этом классе — только UX.
 * Каждый метод [AdminRepository] соответствует эндпоинту, закрытому на
 * бэкенде middleware `AdminGuard`, а каждое действие пишется в неизменяемый
 * аудит-лог. Если злоумышленник подменит ответ `observeAccess()` и получит
 * `OWNER`, сервер всё равно отклонит запрос с 403. Поэтому здесь нет
 * «секретной» логики: скрываем кнопки, чтобы не вводить в заблуждение.
 *
 * Обязательное поле `reason` у каждого действия — требование продукта,
 * а не формальность: аудит-лог должен объяснять, почему у пользователя
 * появилась галочка или почему списаны сильверы.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AdminViewModel @Inject constructor(
    private val adminRepository: AdminRepository,
    private val authRepository: AuthRepository,
    private val observeAccess: ObserveAdminAccessUseCase,
    private val grantPrivilege: GrantPrivilegeUseCase,
    private val creditSilver: CreditSilverUseCase,
) : ViewModel() {

    /** Права текущего администратора. */
    val access: StateFlow<AdminAccess> = observeAccess()
        .stateIn(viewModelScope, SharingStarted.Eagerly, AdminAccess.DENIED)

    /**
     * ID действующего администратора.
     *
     * Нужен [GrantPrivilegeUseCase], чтобы заблокировать изменение
     * собственных прав. `AuthRepository.currentUserId` — обычный `Flow`
     * без `.value`, поэтому материализуем в `StateFlow` с `Eagerly`:
     * значение требуется уже при первом подтверждении действия.
     */
    private val actorId: StateFlow<UserId?> = authRepository.currentUserId
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _uiState = MutableStateFlow(AdminUiState())
    val uiState: StateFlow<AdminUiState> = _uiState.asStateFlow()

    private val _events = MutableStateFlow<AdminEvent?>(null)
    val events: StateFlow<AdminEvent?> = _events.asStateFlow()

    /** Поисковый запрос по пользователям. */
    private val userQuery = MutableStateFlow("")

    /** Фильтр «только с жалобами». */
    private val onlyFlagged = MutableStateFlow(false)

    /** Фильтр статуса жалоб. */
    private val reportStatus = MutableStateFlow<ModerationStatus?>(ModerationStatus.OPEN)

    /** Фильтр действия в аудит-логе. */
    private val auditAction = MutableStateFlow<AdminAction?>(null)

    /** Открытый диалог подтверждения действия. */
    private val pendingAction = MutableStateFlow<PendingAction?>(null)

    init {
        loadStats()
    }

    /* ── Дашборд ──────────────────────────────────────────────────────── */

    /**
     * Сводка для дашборда.
     *
     * Загружается suspend-запросом, а не потоком: цифры обновляются
     * при входе на экран и по pull-to-refresh. Держать на них постоянную
     * подписку означало бы лишнюю нагрузку на сервер ради данных,
     * которые админ смотрит раз в день.
     */
    fun loadStats() = viewModelScope.launch {
        _uiState.update { it.copy(isLoadingStats = true) }
        when (val result = adminRepository.stats()) {
            is ScResult.Success -> _uiState.update {
                it.copy(stats = result.data, isLoadingStats = false)
            }

            is ScResult.Failure -> _uiState.update {
                it.copy(isLoadingStats = false, errorText = result.error.message)
            }

            ScResult.Loading -> Unit
        }
    }

    /* ── Поиск пользователей ──────────────────────────────────────────── */

    /**
     * Результаты поиска.
     *
     * Поиск идёт suspend-запросом, поэтому поток результатов собран вручную:
     * `flatMapLatest` по запросу запускает запрос и кладёт ответ в `StateFlow`.
     * Debounce обязателен — иначе сервер получил бы запрос на каждую клавишу.
     */
    val userResults: StateFlow<List<User>> = userQuery
        .debounce(SEARCH_DEBOUNCE_MS)
        .distinctUntilChanged()
        .combine(onlyFlagged) { query, flagged -> query to flagged }
        .flatMapLatest { (query, flagged) ->
            searchUsers(query, flagged)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private fun searchUsers(query: String, onlyFlagged: Boolean) = flow {
        if (query.isBlank()) {
            emit(emptyList())
            return@flow
        }
        val users = when (val result = adminRepository.searchUsers(query, onlyFlagged)) {
            is ScResult.Success -> result.data
            is ScResult.Failure -> {
                _uiState.update { it.copy(errorText = result.error.message) }
                emptyList()
            }

            ScResult.Loading -> emptyList()
        }
        emit(users)
    }

    /**
     * Состояние фильтров поиска.
     *
     * Экрану нужны текущие значения, чтобы подсветить активный чип и не
     * терять введённый текст при пересборке. Держать их только в приватных
     * `MutableStateFlow` нельзя: UI не смог бы их отрисовать.
     */
    val searchState: StateFlow<AdminSearchState> = combine(userQuery, onlyFlagged) { query, flagged ->
        AdminSearchState(query = query, onlyFlagged = flagged)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = AdminSearchState(),
    )

    /** Активный фильтр статуса жалоб. */
    val reportFilter: StateFlow<ModerationStatus?> = reportStatus
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ModerationStatus.OPEN)

    /** Активный фильтр действия в аудит-логе. */
    val auditFilter: StateFlow<AdminAction?> = auditAction
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    fun onUserQueryChanged(value: String) { userQuery.value = value }

    fun toggleOnlyFlagged() { onlyFlagged.update { !it } }

    /* ── Карточка пользователя ────────────────────────────────────────── */

    /** Детали пользователя: кошелёк, стрик, премиум, юзернеймы, сессии, аудит. */
    fun loadUserDetails(userId: String) = viewModelScope.launch {
        _uiState.update { it.copy(isLoadingDetails = true, details = null) }
        when (val result = adminRepository.userDetails(UserId(userId))) {
            is ScResult.Success -> _uiState.update {
                it.copy(details = result.data, isLoadingDetails = false)
            }

            is ScResult.Failure -> _uiState.update {
                it.copy(isLoadingDetails = false, errorText = result.error.message)
            }

            ScResult.Loading -> Unit
        }
    }

    /* ── Жалобы ───────────────────────────────────────────────────────── */

    val reports: StateFlow<List<ModerationCase>> = reportStatus
        .flatMapLatest { status -> adminRepository.observeReports(status) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    fun setReportStatus(status: ModerationStatus?) { reportStatus.value = status }

    /**
     * Решение по жалобе.
     *
     * Комментарий обязателен: он попадает в аудит-лог и виден модератору,
     * который будет разбирать апелляцию.
     */
    fun resolveReport(case: ModerationCase, status: ModerationStatus, comment: String) =
        viewModelScope.launch {
            if (comment.isBlank()) {
                _events.value = AdminEvent.Error("Укажите комментарий к решению")
                return@launch
            }
            if (!can(AdminAction.RESOLVE_REPORT)) {
                _events.value = AdminEvent.Forbidden(AdminAction.RESOLVE_REPORT)
                return@launch
            }
            when (val result = adminRepository.resolveReport(case.id, status, comment.trim())) {
                is ScResult.Success -> _events.value = AdminEvent.ReportResolved(case.id)
                is ScResult.Failure -> _events.value = AdminEvent.Error(result.error.message)
                ScResult.Loading -> Unit
            }
        }

    /* ── Аудит-лог ────────────────────────────────────────────────────── */

    val auditLog: StateFlow<List<AdminLogEntry>> = auditAction
        .flatMapLatest { action -> adminRepository.observeAuditLog(action, AUDIT_LIMIT) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    fun setAuditAction(action: AdminAction?) { auditAction.value = action }

    /* ── Действия над пользователем ───────────────────────────────────── */

    /**
     * Запрос действия с обязательным обоснованием.
     *
     * Все привилегированные действия проходят через диалог [AdminConfirmSheet]:
     * причина — часть аудит-лога, и запрашивать её нужно до отправки,
     * а не после отказа сервера.
     */
    fun requestAction(action: PendingAction) {
        val required = action.requiredPermission()
        if (!can(required)) {
            _events.value = AdminEvent.Forbidden(required)
            return
        }
        pendingAction.value = action
    }

    val confirmState: StateFlow<PendingAction?> = pendingAction.asStateFlow()

    fun dismissConfirm() { pendingAction.value = null }

    fun onConfirmReasonChanged(value: String) {
        pendingAction.update { it?.copy(reason = value) }
    }

    /**
     * Изменение суммы начисления в диалоге подтверждения.
     *
     * Сумма вводится прямо в диалоге, а не до его открытия: админ видит
     * текущий баланс пользователя рядом с полем и решает по факту.
     * Игнорируем вызов для других действий — у них суммы нет.
     */
    fun onConfirmAmountChanged(amount: Long) {
        pendingAction.update { current ->
            if (current is PendingAction.CreditSilver) {
                current.copy(amount = amount)
            } else {
                current
            }
        }
    }

    /** Подтверждение действия из диалога. */
    fun confirmAction() = viewModelScope.launch {
        val action = pendingAction.value ?: return@launch
        val target = action.targetUserId()

        when (action) {
            is PendingAction.GrantVerified -> grant(
                target = target,
                action = if (action.value) AdminAction.GRANT_VERIFIED else AdminAction.REVOKE_VERIFIED,
                reason = action.reason,
            )

            is PendingAction.GrantPremium -> grant(
                target = target,
                action = if (action.value) AdminAction.GRANT_PREMIUM else AdminAction.REVOKE_PREMIUM,
                reason = action.reason,
                // days = null означает Lifetime — пожизненный Premium
                durationDays = if (action.value) action.days else null,
            )

            is PendingAction.GrantDeveloper -> grant(
                target = target,
                action = if (action.value) AdminAction.GRANT_DEVELOVER else AdminAction.REVOKE_DEVELOVER,
                reason = action.reason,
            )

            is PendingAction.GrantRole -> grant(
                target = target,
                action = if (action.role == AdminRole.NONE) {
                    AdminAction.REVOKE_ADMIN_ROLE
                } else {
                    AdminAction.GRANT_ADMIN_ROLE
                },
                reason = action.reason,
                role = action.role,
            )

            is PendingAction.Ban -> {
                if (!executeGuarded(AdminAction.BAN_USER)) return@launch
                when (val result = adminRepository.ban(target, action.reason.trim(), action.permanent)) {
                    is ScResult.Success -> _events.value = AdminEvent.UserBanned(action.userName)
                    is ScResult.Failure -> _events.value = AdminEvent.Error(result.error.message)
                    ScResult.Loading -> Unit
                }
            }

            is PendingAction.Unban -> {
                if (!executeGuarded(AdminAction.UNBAN_USER)) return@launch
                when (val result = adminRepository.unban(target, action.reason.trim())) {
                    is ScResult.Success -> _events.value = AdminEvent.UserUnbanned(action.userName)
                    is ScResult.Failure -> _events.value = AdminEvent.Error(result.error.message)
                    ScResult.Loading -> Unit
                }
            }

            is PendingAction.Restrict -> {
                if (!executeGuarded(AdminAction.RESTRICT_USER)) return@launch
                when (val result = adminRepository.restrict(
                    userId = target,
                    reason = action.reason.trim(),
                    untilTs = System.currentTimeMillis() + action.hours * MILLIS_IN_HOUR,
                )) {
                    is ScResult.Success -> _events.value = AdminEvent.UserRestricted(action.userName)
                    is ScResult.Failure -> _events.value = AdminEvent.Error(result.error.message)
                    ScResult.Loading -> Unit
                }
            }

            is PendingAction.CreditSilver -> {
                val amount = action.amount
                if (amount == 0L) {
                    _events.value = AdminEvent.Error("Сумма не может быть нулевой")
                    return@launch
                }
                // CreditSilverUseCase сам выбирает CREDIT или DEBIT по знаку
                // и проверяет права, переполнение и наличие причины
                when (val result = creditSilver(
                    CreditSilverParams(
                        targetUserId = target,
                        amount = amount,
                        reason = action.reason,
                    ),
                )) {
                    is ScResult.Success -> _events.value = AdminEvent.SilverAdjusted(
                        userName = action.userName,
                        amount = amount,
                        newBalance = result.data.balance,
                    )

                    is ScResult.Failure -> _events.value = AdminEvent.Error(result.error.message)
                    ScResult.Loading -> Unit
                }
            }

            is PendingAction.ResetWallet -> {
                if (!executeGuarded(AdminAction.RESET_WALLET)) return@launch
                when (val result = adminRepository.resetWallet(target, action.reason.trim())) {
                    is ScResult.Success -> _events.value = AdminEvent.WalletReset(action.userName)
                    is ScResult.Failure -> _events.value = AdminEvent.Error(result.error.message)
                    ScResult.Loading -> Unit
                }
            }

            is PendingAction.BlockUsername -> {
                if (!executeGuarded(AdminAction.BLOCK_USERNAME)) return@launch
                when (val result = adminRepository.blockUsername(action.username, action.reason.trim())) {
                    is ScResult.Success -> _events.value = AdminEvent.UsernameBlocked(action.username)
                    is ScResult.Failure -> _events.value = AdminEvent.Error(result.error.message)
                    ScResult.Loading -> Unit
                }
            }

            is PendingAction.AdjustPrice -> {
                if (!executeGuarded(AdminAction.ADJUST_LISTING_PRICE)) return@launch
                when (val result = adminRepository.adjustListingPrice(
                    listingId = action.listingId,
                    priceSilver = action.priceSilver,
                    reason = action.reason.trim(),
                )) {
                    is ScResult.Success -> _events.value = AdminEvent.PriceAdjusted(
                        username = result.data.username,
                        price = action.priceSilver,
                    )

                    is ScResult.Failure -> _events.value = AdminEvent.Error(result.error.message)
                    ScResult.Loading -> Unit
                }
            }

            is PendingAction.Refund -> {
                if (!executeGuarded(AdminAction.REFUND_TRANSACTION)) return@launch
                when (val result = adminRepository.refundTransaction(
                    transactionId = action.transactionId,
                    reason = action.reason.trim(),
                )) {
                    is ScResult.Success -> _events.value = AdminEvent.Refunded(action.transactionId)
                    is ScResult.Failure -> _events.value = AdminEvent.Error(result.error.message)
                    ScResult.Loading -> Unit
                }
            }
        }

        pendingAction.value = null
        // Данные карточки устарели после действия — перечитываем
        loadUserDetails(target.raw)
    }

    /**
     * Общая выдача привилегии через [GrantPrivilegeUseCase].
     *
     * Use-case сам проверяет права, длину причины, запрет на изменение
     * собственных прав и прав мастер-аккаунта — дублировать это здесь нельзя.
     */
    private suspend fun grant(
        target: UserId,
        action: AdminAction,
        reason: String,
        durationDays: Int? = null,
        role: AdminRole? = null,
    ) {
        val pending = pendingAction.value
        val actor = actorIdOrNull()
        if (actor == null) {
            _events.value = AdminEvent.Error("Сессия не определена — войдите заново")
            return
        }
        val result = grantPrivilege(
            GrantPrivilegeParams(
                actorUserId = actor,
                targetUserId = target,
                targetIsMasterAccount = pending?.targetIsMasterAccount() == true,
                action = action,
                reason = reason,
                durationDays = durationDays,
                role = role,
            ),
        )
        when (result) {
            is ScResult.Success -> _events.value = AdminEvent.PrivilegeGranted(
                userName = result.data.fullName,
                action = action,
            )

            is ScResult.Failure -> _events.value = AdminEvent.Error(result.error.message)
            ScResult.Loading -> Unit
        }
    }

    /** Проверка права перед запросом. Возвращает false и шлёт событие, если прав нет. */
    private suspend fun executeGuarded(action: AdminAction): Boolean {
        if (!can(action)) {
            _events.value = AdminEvent.Forbidden(action)
            return false
        }
        return true
    }

    /* ── Рассылка ─────────────────────────────────────────────────────── */

    /**
     * Рассылка сообщения.
     *
     * Доступна только роли ADMIN и выше ([AdminAction.BROADCAST]).
     * Аудитория выбирается явно: отправка «всем» — необратимое действие,
     * и случайный тап по умолчанию недопустим.
     */
    fun broadcast(text: String, audience: AdminBroadcastAudience) = viewModelScope.launch {
        if (text.isBlank()) {
            _events.value = AdminEvent.Error("Текст рассылки не может быть пустым")
            return@launch
        }
        if (!executeGuarded(AdminAction.BROADCAST)) return@launch
        when (val result = adminRepository.broadcast(text.trim(), audience)) {
            is ScResult.Success -> _events.value = AdminEvent.BroadcastSent(audience)
            is ScResult.Failure -> _events.value = AdminEvent.Error(result.error.message)
            ScResult.Loading -> Unit
        }
    }

    /* ── Модерация контента ───────────────────────────────────────────── */

    /** Удаление сообщения «для всех» с обязательной причиной. */
    fun deleteMessage(chatId: String, messageId: String, reason: String) = viewModelScope.launch {
        if (!executeGuarded(AdminAction.DELETE_MESSAGE)) return@launch
        when (val result = adminRepository.deleteMessageAnywhere(
            chatId = com.silverchat.core.model.ChatId(chatId),
            messageId = com.silverchat.core.model.MessageId(messageId),
            reason = reason.trim(),
        )) {
            is ScResult.Success -> _events.value = AdminEvent.MessageDeleted
            is ScResult.Failure -> _events.value = AdminEvent.Error(result.error.message)
            ScResult.Loading -> Unit
        }
    }

    fun dismissError() { _uiState.update { it.copy(errorText = null) } }

    fun consumeEvent() { _events.value = null }

    /* ── Вспомогательное ──────────────────────────────────────────────── */

    /** Проверка права по текущему [access]. Синхронная: значение уже материализовано. */
    private fun can(action: AdminAction): Boolean = access.value.can(action)

    /**
     * ID действующего администратора для запроса.
     *
     * Если сессия не определена, возвращает null и действие не выполняется:
     * подставить «любой» ID означало бы подписать чужое действие своим именем.
     */
    private fun actorIdOrNull(): UserId? = actorId.value

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val SEARCH_DEBOUNCE_MS = 400L
        const val AUDIT_LIMIT = 200
        const val MILLIS_IN_HOUR = 3_600_000L
    }
}

/* =========================================================================
   СОСТОЯНИЕ
   ========================================================================= */

/** Состояние поиска пользователей в админке. */
data class AdminSearchState(
    val query: String = "",
    val onlyFlagged: Boolean = false,
)

data class AdminUiState(
    val stats: AdminStats? = null,
    val details: AdminUserDetails? = null,
    val isLoadingStats: Boolean = false,
    val isLoadingDetails: Boolean = false,
    val errorText: String? = null,
)

/* =========================================================================
   ДЕЙСТВИЯ, ТРЕБУЮЩИЕ ПОДТВЕРЖДЕНИЯ
   ========================================================================= */

/**
 * Действие, ожидающее обоснования в диалоге подтверждения.
 *
 * Все привилегированные операции требуют `reason`: он попадает в
 * неизменяемый аудит-лог. Модель sealed — чтобы компилятор напоминал
 * о новом действии во всех `when`.
 */
sealed interface PendingAction {

    val reason: String

    /** Пользователь, к которому применяется действие (null для маркет-операций). */
    fun targetUserId(): UserId

    /** Заменяет причину — используется полем ввода в диалоге. */
    fun copy(reason: String): PendingAction

    /** Признак мастер-аккаунта: его права неизменяемы. */
    fun targetIsMasterAccount(): Boolean = false

    /** Какое право требуется для выполнения. */
    fun requiredPermission(): AdminAction

    data class GrantVerified(
        val userId: UserId,
        val userName: String,
        val value: Boolean,
        override val reason: String = "",
    ) : PendingAction {
        override fun targetUserId() = userId
        override fun copy(reason: String) = PendingAction.GrantVerified(userId, userName, value, reason)
        override fun requiredPermission() =
            if (value) AdminAction.GRANT_VERIFIED else AdminAction.REVOKE_VERIFIED
    }

    /** @param days null означает пожизненный Premium. */
    data class GrantPremium(
        val userId: UserId,
        val userName: String,
        val value: Boolean,
        val days: Int? = PREMIUM_DEFAULT_DAYS,
        override val reason: String = "",
    ) : PendingAction {
        override fun targetUserId() = userId
        override fun copy(reason: String) =
            PendingAction.GrantPremium(userId, userName, value, days, reason)

        override fun requiredPermission() =
            if (value) AdminAction.GRANT_PREMIUM else AdminAction.REVOKE_PREMIUM

        companion object {
            /** Месяц подписки по умолчанию. */
            const val PREMIUM_DEFAULT_DAYS = 30
        }
    }

    data class GrantDeveloper(
        val userId: UserId,
        val userName: String,
        val value: Boolean,
        override val reason: String = "",
    ) : PendingAction {
        override fun targetUserId() = userId
        override fun copy(reason: String) =
            PendingAction.GrantDeveloper(userId, userName, value, reason)

        override fun requiredPermission() =
            if (value) AdminAction.GRANT_DEVELOVER else AdminAction.REVOKE_DEVELOPER
    }

    /** Выдача роли. `AdminRole.NONE` означает понижение до обычных прав. */
    data class GrantRole(
        val userId: UserId,
        val userName: String,
        val role: AdminRole,
        override val reason: String = "",
    ) : PendingAction {
        override fun targetUserId() = userId
        override fun copy(reason: String) = PendingAction.GrantRole(userId, userName, role, reason)

        // Роль выдаёт только OWNER — самая строгая проверка из двух
        override fun requiredPermission() = AdminAction.GRANT_ADMIN_ROLE
    }

    data class Ban(
        val userId: UserId,
        val userName: String,
        val permanent: Boolean = false,
        override val reason: String = "",
    ) : PendingAction {
        override fun targetUserId() = userId
        override fun copy(reason: String) = PendingAction.Ban(userId, userName, permanent, reason)
        override fun requiredPermission() = AdminAction.BAN_USER
    }

    data class Unban(
        val userId: UserId,
        val userName: String,
        override val reason: String = "",
    ) : PendingAction {
        override fun targetUserId() = userId
        override fun copy(reason: String) = PendingAction.Unban(userId, userName, reason)
        override fun requiredPermission() = AdminAction.UNBAN_USER
    }

    /** @param hours срок ограничения в часах. */
    data class Restrict(
        val userId: UserId,
        val userName: String,
        val hours: Int = RESTRICT_DEFAULT_HOURS,
        override val reason: String = "",
    ) : PendingAction {
        override fun targetUserId() = userId
        override fun copy(reason: String) = PendingAction.Restrict(userId, userName, hours, reason)
        override fun requiredPermission() = AdminAction.RESTRICT_USER

        companion object {
            /** Сутки ограничения по умолчанию. */
            const val RESTRICT_DEFAULT_HOURS = 24
        }
    }

    /**
     * Начисление или списание сильверов.
     *
     * Отрицательное [amount] означает списание: `CreditSilverUseCase`
     * сам выбирает действие по знаку и проверяет соответствующее право.
     * Лимита суммы нет — это требование продукта, поэтому единственная
     * защита: роль FINANCE/ADMIN/OWNER плюс обязательная причина.
     */
    data class CreditSilver(
        val userId: UserId,
        val userName: String,
        val amount: Long = 0L,
        override val reason: String = "",
    ) : PendingAction {
        override fun targetUserId() = userId
        override fun copy(reason: String) = PendingAction.CreditSilver(userId, userName, amount, reason)
        override fun requiredPermission() =
            if (amount >= 0) AdminAction.CREDIT_SILVER else AdminAction.DEBIT_SILVER
    }

    data class ResetWallet(
        val userId: UserId,
        val userName: String,
        override val reason: String = "",
    ) : PendingAction {
        override fun targetUserId() = userId
        override fun copy(reason: String) = PendingAction.ResetWallet(userId, userName, reason)
        override fun requiredPermission() = AdminAction.RESET_WALLET
    }

    /** Изъятие юзернейма из оборота маркета. */
    data class BlockUsername(
        val username: String,
        override val reason: String = "",
    ) : PendingAction {
        // Действие не привязано к пользователю
        override fun targetUserId() = UserId("")
        override fun copy(reason: String) = PendingAction.BlockUsername(username, reason)
        override fun requiredPermission() = AdminAction.BLOCK_USERNAME
    }

    data class AdjustPrice(
        val listingId: com.silverchat.core.model.ListingId,
        val username: String,
        val priceSilver: Long,
        override val reason: String = "",
    ) : PendingAction {
        override fun targetUserId() = UserId("")
        override fun copy(reason: String) =
            PendingAction.AdjustPrice(listingId, username, priceSilver, reason)

        override fun requiredPermission() = AdminAction.ADJUST_LISTING_PRICE
    }

    data class Refund(
        val transactionId: String,
        override val reason: String = "",
    ) : PendingAction {
        override fun targetUserId() = UserId("")
        override fun copy(reason: String) = PendingAction.Refund(transactionId, reason)
        override fun requiredPermission() = AdminAction.REFUND_TRANSACTION
    }
}

/* =========================================================================
   СОБЫТИЯ
   ========================================================================= */

sealed interface AdminEvent {
    data class PrivilegeGranted(val userName: String, val action: AdminAction) : AdminEvent
    data class UserBanned(val userName: String) : AdminEvent
    data class UserUnbanned(val userName: String) : AdminEvent
    data class UserRestricted(val userName: String) : AdminEvent
    data class SilverAdjusted(val userName: String, val amount: Long, val newBalance: Long) : AdminEvent
    data class WalletReset(val userName: String) : AdminEvent
    data class UsernameBlocked(val username: String) : AdminEvent
    data class PriceAdjusted(val username: String, val price: Long) : AdminEvent
    data class Refunded(val transactionId: String) : AdminEvent
    data class ReportResolved(val caseId: String) : AdminEvent
    data object MessageDeleted : AdminEvent
    data class BroadcastSent(val audience: AdminBroadcastAudience) : AdminEvent

    /** Прав недостаточно. Сервер отклонил бы запрос всё равно — сообщаем сразу. */
    data class Forbidden(val action: AdminAction) : AdminEvent

    data class Error(val message: String) : AdminEvent
}

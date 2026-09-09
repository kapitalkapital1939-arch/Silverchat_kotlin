package com.silverchat.app.session

import com.silverchat.app.di.ApplicationScope
import com.silverchat.core.common.log.LogTag
import com.silverchat.core.common.log.ScLogger
import com.silverchat.core.domain.repository.SyncRepository
import com.silverchat.core.security.AuthState
import com.silverchat.core.security.TokenStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

/**
 * Жизненный цикл сессии: один WebSocket на всё приложение.
 *
 * ── Почему это в :app ───────────────────────────────────────────────────
 * Подключение реалтайм-канала не привязано ни к одному экрану: оно должно
 * жить, пока пользователь вошёл, и гаснуть при выходе. Ни одна ViewModel
 * не подходит — она умирает вместе с экраном, а соединение рвалось бы при
 * каждом закрытии списка чатов. `Application` — единственный владелец
 * подходящего времени жизни.
 *
 * ── Почему ключом служит userId, а не весь [AuthState] ──────────────────
 * `AuthState.Authenticated` — data-класс с токеном внутри. Ротация токена
 * создаёт новое значение, и сравнение по состоянию целиком переподключало бы
 * сокет на каждый refresh: разрыв соединения, повторное рукопожатие и
 * потеря несообщённых событий. Идентификатор пользователя при ротации не
 * меняется, поэтому `distinctUntilChanged` по нему переподключается ровно
 * тогда, когда это действительно нужно — при входе, выходе и смене аккаунта.
 *
 * ── Про обновление токена ───────────────────────────────────────────────
 * Отдельно передавать новый токен в сокет не нужно: `SyncRepository.connect()`
 * сам вызывает `updateToken` перед рукопожатием, а `TokenAuthenticator`
 * обновляет его в живом соединении.
 */
@Singleton
class SessionLifecycle @Inject constructor(
    @ApplicationScope private val scope: CoroutineScope,
    private val tokenStore: TokenStore,
    private val syncRepository: SyncRepository,
) {

    fun start() {
        sessionKeys()
            .onEach { userId ->
                if (userId == null) {
                    ScLogger.i(LogTag.SYNC, "Сессия закрыта — отключаем реалтайм-канал")
                    syncRepository.disconnect()
                } else {
                    ScLogger.i(LogTag.SYNC, "Вход выполнен — подключаем реалтайм-канал")
                    syncRepository.connect()
                }
            }
            .catch { error ->
                // Подписка обязана выжить: без неё приложение не заметит
                // следующий вход и останется без сообщений до перезапуска.
                ScLogger.e(LogTag.SYNC, "Сбой в жизненном цикле сессии", error)
            }
            .launchIn(scope)
    }

    /** `null` — пользователь не вошёл; иначе идентификатор текущего аккаунта. */
    private fun sessionKeys(): Flow<String?> =
        tokenStore.authState
            .map { state -> (state as? AuthState.Authenticated)?.userId }
            .distinctUntilChanged()
}

package com.silverchat.core.network.interceptor

import com.silverchat.core.common.log.LogTag
import com.silverchat.core.common.log.ScLogger
import com.silverchat.core.network.api.RefreshRequest
import com.silverchat.core.network.api.SilverChatApi
import com.silverchat.core.security.TokenStore
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * Автоматическая ротация токенов при 401.
 *
 * Механика:
 *  1. OkHttp зовёт Authenticator при ответе 401;
 *  2. запрашиваем новую пару по refresh-токену;
 *  3. повторяем ИСХОДНЫЙ запрос с новым access-токеном;
 *  4. если refresh тоже не удался — разлогиниваем (UI уйдёт на экран входа).
 *
 * Защита от «шторма обновления»: если запрос уже ретраился (в цепочке
 * есть priorResponse с 401), не пытаемся снова — иначе при протухшем
 * refresh-токене получим бесконечный цикл.
 */
class TokenAuthenticator @Inject constructor(
    private val tokenStore: TokenStore,
    private val apiProvider: dagger.Lazy<SilverChatApi>,
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.code != 401) return null

        // Уже пробовали обновить — не зацикливаемся
        if (response.request.header(HEADER_RETRY) != null) {
            ScLogger.w(LogTag.HTTP, "401 после ротации токена — требуется повторный вход")
            tokenStore.onSessionInvalidated("refresh failed")
            return null
        }

        val refresh = tokenStore.refreshToken ?: run {
            tokenStore.onSessionInvalidated("no refresh token")
            return null
        }

        // Authenticator вызывается на IO-потоке OkHttp, поэтому runBlocking здесь
        // безопасен: это синхронный callback контракта OkHttp.
        val newTokens = runBlocking {
            runCatching {
                apiProvider.get().refreshTokens(RefreshRequest(refresh, tokenStore.deviceId.orEmpty()))
            }.getOrNull()
        } ?: run {
            tokenStore.onSessionInvalidated("refresh request failed")
            return null
        }

        tokenStore.saveTokens(newTokens.accessToken, newTokens.refreshToken, newTokens.user.id)
        ScLogger.i(LogTag.HTTP, "Токены обновлены, повторяем запрос ${response.request.url.encodedPath}")

        return response.request.newBuilder()
            .header("Authorization", "Bearer ${newTokens.accessToken}")
            .header(HEADER_RETRY, "1")
            .build()
    }

    private companion object {
        const val HEADER_RETRY = "X-SC-Token-Retry"
    }
}

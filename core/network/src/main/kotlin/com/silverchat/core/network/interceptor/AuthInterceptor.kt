package com.silverchat.core.network.interceptor

import com.silverchat.core.network.BuildConfig
import com.silverchat.core.network.HttpHeaders
import com.silverchat.core.security.TokenStore
import javax.inject.Inject
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Добавляет заголовки авторизации и версии протокола к каждому REST-запросу.
 *
 * Заголовки, а не query-параметры: токен в URL попадает в логи nginx/CDN
 * и в историю прокси — это одна из самых частых утечек в мобильных клиентах.
 */
class AuthInterceptor @Inject constructor(
    private val tokenStore: TokenStore,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()

        // Публичные эндпоинты (auth/otp) идут без токена
        val isPublic = original.url.encodedPath.startsWith("/v1/auth/")

        val builder = original.newBuilder()
            .header(HttpHeaders.PROTOCOL_VERSION, BuildConfig.PROTOCOL_VERSION.toString())
            .header(HttpHeaders.PLATFORM, HttpHeaders.PLATFORM_ANDROID)
            .header(HttpHeaders.APP_VERSION, BuildConfig.APP_VERSION_NAME)
            .header("Accept", "application/json")
            // Язык зафиксирован: интерфейс существует только на русском,
            // поэтому локализованные ответы сервера не нужны.
            .header("Accept-Language", "ru")

        if (!isPublic) {
            tokenStore.accessToken?.let { token ->
                builder.header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }
        }

        return chain.proceed(builder.build())
    }
}

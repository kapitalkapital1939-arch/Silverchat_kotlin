package com.silverchat.core.network.di

import com.silverchat.core.common.dispatcher.DispatcherProvider
import com.silverchat.core.common.log.LogTag
import com.silverchat.core.common.log.ScLogger
import com.silverchat.core.network.BuildConfig
import com.silverchat.core.network.api.SilverChatApi
import com.silverchat.core.network.interceptor.AuthInterceptor
import com.silverchat.core.network.interceptor.TokenAuthenticator
import com.silverchat.core.network.ws.OkHttpRealtimeSocket
import com.silverchat.core.network.ws.OutgoingQueue
import com.silverchat.core.network.ws.RealtimeSocket
import com.silverchat.core.network.ws.SocketEventDecoder
import com.silverchat.core.security.TokenStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Сетевой слой: OkHttp, Retrofit, JSON, WebSocket.
 *
 * Два отдельных OkHttpClient'а — принципиально:
 *  - [provideHttpClient]      — REST: таймауты 15–30 с, ротация токенов;
 *  - [provideWebSocketClient] — WS: readTimeout = 0 (иначе OkHttp закроет
 *    «молчащий» сокет через 10 с), pingInterval для поддержания NAT.
 *
 * Использование одного клиента для WS — классическая ошибка: read-таймаут
 * рвёт соединение между сообщениями, и приложение «само выходит из онлайна».
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        // Сервер может добавлять поля — клиент не должен из-за этого падать
        ignoreUnknownKeys = true
        // Допускаем null вместо отсутствующего поля
        explicitNulls = false
        coerceInputValues = true
        isLenient = false
        encodeDefaults = true
        prettyPrint = false
        classDiscriminator = "kind"
    }

    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor =
        HttpLoggingInterceptor { message ->
            // Логируем через Timber с фильтром: токены и тела сообщений не светим
            ScLogger.d(LogTag.HTTP, message.redact())
        }.apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
            redactHeader("Authorization")
            redactHeader("Cookie")
        }

    private fun String.redact(): String =
        if (contains("Bearer ", ignoreCase = true)) {
            replace(Regex("Bearer\\s+[\\w\\-._]+", RegexOption.IGNORE_CASE), "Bearer ***")
        } else {
            this
        }

    @Provides
    @Singleton
    @RestClient
    fun provideHttpClient(
        authInterceptor: AuthInterceptor,
        loggingInterceptor: HttpLoggingInterceptor,
    ): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        /* TLS: certificate pinning.
         *
         * Пины берутся из BuildConfig.CERT_PINS, который наполняется из секрета
         * CI или gradle.properties (см. AndroidConfig.certPins). Если значение
         * пустое — пиннинг не применяется вовсе. Это осознанное поведение:
         * заглушка вместо реального пина превратила бы release-сборку в
         * кирпич (SSLPeerUnverifiedException на каждом запросе).
         *
         * Хост должен совпадать с хостом из BuildConfig.BACKEND_URL.
         */
        .certificatePinner(
            okhttp3.CertificatePinner.Builder()
                .apply {
                    BuildConfig.CERT_PINS
                        .split(",")
                        .map { pin -> pin.trim() }
                        .filter { pin -> pin.isNotEmpty() }
                        .forEach { pin -> add(PINNED_HOST, pin) }
                }
                .build(),
        )
        .build()

    @Provides
    @Singleton
    @WsClient
    fun provideWebSocketClient(authInterceptor: AuthInterceptor): OkHttpClient =
        OkHttpClient.Builder()
            // 0 = без read-таймаута: сокет живёт, пока живой TCP
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .connectTimeout(15, TimeUnit.SECONDS)
            // Прикладной ping на уровне OkHttp — страховка для NAT
            .pingInterval(BuildConfig.WS_HEARTBEAT_SEC.toLong(), TimeUnit.SECONDS)
            .retryOnConnectionFailure(false) // ретраями управляет OkHttpRealtimeSocket
            .addInterceptor(authInterceptor)
            .build()

    @Provides
    @Singleton
    fun provideRetrofit(
        @RestClient client: OkHttpClient,
        json: Json,
        // TokenAuthenticator инжектится сюда, а сам получает SilverChatApi через
        // dagger.Lazy — так разрываем цикл Retrofit -> Authenticator -> Retrofit.
        authenticator: TokenAuthenticator,
    ): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.BACKEND_URL)
        .client(client.newBuilder().authenticator(authenticator).build())
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun provideApi(retrofit: Retrofit): SilverChatApi = retrofit.create(SilverChatApi::class.java)

    @Provides
    @Singleton
    fun provideSocketEventDecoder(json: Json): SocketEventDecoder = SocketEventDecoder(json)

    @Provides
    @Singleton
    fun provideOutgoingQueue(): OutgoingQueue = OutgoingQueue()

    @Provides
    @Singleton
    fun provideRealtimeSocket(
        @WsClient client: OkHttpClient,
        tokenStore: TokenStore,
        json: Json,
        dispatchers: DispatcherProvider,
        outgoingQueue: OutgoingQueue,
    ): RealtimeSocket = OkHttpRealtimeSocket(client, tokenStore, json, dispatchers, outgoingQueue)
}

@Module
@InstallIn(SingletonComponent::class)

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RestClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class WsClient


/**
 * Хост, для которого применяется certificate pinning.
 *
 * Должен совпадать с хостом [BuildConfig.BACKEND_URL]: OkHttp сверяет пин с
 * именем хоста из запроса, и расхождение молча отключило бы защиту.
 */
private const val PINNED_HOST = "api.silver.chat"

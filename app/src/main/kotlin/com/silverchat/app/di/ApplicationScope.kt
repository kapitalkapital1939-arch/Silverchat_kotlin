package com.silverchat.app.di

import com.silverchat.core.common.dispatcher.DispatcherProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/**
 * Область видимости корутин, живущая столько же, сколько процесс.
 *
 * Нужна для работы, которая не принадлежит ни одному экрана:
 * поддержание WebSocket-сессии, показ уведомлений об админ-рассылках,
 * отправка быстрого ответа из шторки. `viewModelScope` здесь не подходит —
 * эти задачи обязаны переживать закрытие экрана.
 */
@Retention(AnnotationRetention.BINARY)
@Qualifier
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object ApplicationScopeModule {

    /**
     * `SupervisorJob`, а не обычный `Job`: сбой одной фоновой задачи
     * (например, рассылка пришла с битым текстом) не должен отменять
     * подписку на состояние сессии.
     *
     * Диспетчер — `default`, а не `main`: задачи здесь неблокирующие,
     * а вешать их на главный поток значит конкурировать с компоновкой кадров.
     */
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(dispatchers: DispatcherProvider): CoroutineScope =
        CoroutineScope(SupervisorJob() + dispatchers.default)
}

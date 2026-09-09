package com.silverchat.core.data.di

import com.silverchat.core.data.repository.AdminRepositoryImpl
import com.silverchat.core.data.repository.AuthRepositoryImpl
import com.silverchat.core.data.repository.CallRepositoryImpl
import com.silverchat.core.data.repository.ChatRepositoryImpl
import com.silverchat.core.data.repository.MarketRepositoryImpl
import com.silverchat.core.data.repository.MediaRepositoryImpl
import com.silverchat.core.data.repository.MessageRepositoryImpl
import com.silverchat.core.data.repository.ProfileRepositoryImpl
import com.silverchat.core.data.repository.SearchRepositoryImpl
import com.silverchat.core.data.repository.StoryRepositoryImpl
import com.silverchat.core.data.repository.SyncRepositoryImpl
import com.silverchat.core.data.repository.WalletRepositoryImpl
import com.silverchat.core.domain.repository.AdminRepository
import com.silverchat.core.domain.repository.AuthRepository
import com.silverchat.core.domain.repository.CallRepository
import com.silverchat.core.domain.repository.ChatRepository
import com.silverchat.core.domain.repository.MarketRepository
import com.silverchat.core.domain.repository.MediaRepository
import com.silverchat.core.domain.repository.MessageRepository
import com.silverchat.core.domain.repository.ProfileRepository
import com.silverchat.core.domain.repository.SearchRepository
import com.silverchat.core.domain.repository.StoryRepository
import com.silverchat.core.domain.repository.SyncRepository
import com.silverchat.core.domain.repository.WalletRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/* =========================================================================
   СВЯЗКА ИНТЕРФЕЙСОВ :core:domain С РЕАЛИЗАЦИЯМИ :core:data
   ------------------------------------------------------------------------
   Все биндинги — `@Singleton`. Для мессенджера это не «оптимизация», а
   требование корректности:

   1. Кэш в памяти. `ChatRepositoryImpl` и `ProfileRepositoryImpl` держат
      `StateFlow` с последними данными. Два экземпляра репозитория — два
      независимых кэша, которые разъезжаются: список чатов показывает одно,
      а экран чата другое.

   2. Одно соединение. `RealtimeSocket` и очередь исходящих сообщений
      создаются в единственном экземпляре; второй экземпляр дал бы второй
      WebSocket на то же устройство и дубли событий `message.new`.

   3. Оптимистичная запись. `MessageRepositoryImpl` пишет сообщение в Room
      до ответа сервера и откатывает по событию. При двух экземплярах
      откат мог бы прийти не в тот, который писал.

   `@Binds`, а не `@Provides`: реализации не требуют фабричной логики, и
   `@Binds` не генерирует лишний код фабрики — Dagger связывает типы
   напрямую.

   ── Чего здесь НЕТ ──────────────────────────────────────────────────────
   • `SettingsRepository` — реализован в :core:datastore (DataStore
     Preferences) и связан в `DataStoreBindsModule`. Настройки не относятся
     к серверным данным, и тянуть их в общий репозиторий значило бы смешать
     два разных источника правды.

   • `MediaCaptureGateway` — порт объявлен в :core:data, а реализация
     предоставляется модулем :app (`ActivityCaptureGateway`), потому что
     выбор файла и съёмка возможны только через `ActivityResultContracts`
     из живой `Activity`. Модуль данных не может и не должен знать про
     `Activity`, поэтому биндинг живёт там, где есть интерфейс.
   ========================================================================= */

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindChatRepository(impl: ChatRepositoryImpl): ChatRepository

    @Binds
    @Singleton
    abstract fun bindMessageRepository(impl: MessageRepositoryImpl): MessageRepository

    @Binds
    @Singleton
    abstract fun bindSearchRepository(impl: SearchRepositoryImpl): SearchRepository

    @Binds
    @Singleton
    abstract fun bindStoryRepository(impl: StoryRepositoryImpl): StoryRepository

    @Binds
    @Singleton
    abstract fun bindCallRepository(impl: CallRepositoryImpl): CallRepository

    @Binds
    @Singleton
    abstract fun bindSyncRepository(impl: SyncRepositoryImpl): SyncRepository

    @Binds
    @Singleton
    abstract fun bindMediaRepository(impl: MediaRepositoryImpl): MediaRepository

    @Binds
    @Singleton
    abstract fun bindMarketRepository(impl: MarketRepositoryImpl): MarketRepository

    @Binds
    @Singleton
    abstract fun bindWalletRepository(impl: WalletRepositoryImpl): WalletRepository

    @Binds
    @Singleton
    abstract fun bindProfileRepository(impl: ProfileRepositoryImpl): ProfileRepository

    @Binds
    @Singleton
    abstract fun bindAdminRepository(impl: AdminRepositoryImpl): AdminRepository
}

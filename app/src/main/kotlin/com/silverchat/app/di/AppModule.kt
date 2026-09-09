package com.silverchat.app.di

import com.silverchat.app.media.ActivityCaptureGateway
import com.silverchat.app.media.ActivityProfileMediaPicker
import com.silverchat.core.data.repository.MediaCaptureGateway
import com.silverchat.core.designsystem.navigation.FeatureNavigation
import com.silverchat.feature.admin.navigation.AdminNavigation
import com.silverchat.feature.auth.navigation.AuthNavigation
import com.silverchat.feature.calls.navigation.CallsNavigation
import com.silverchat.feature.chats.navigation.ChatsNavigation
import com.silverchat.feature.market.navigation.MarketNavigation
import com.silverchat.feature.profile.navigation.ProfileMediaPicker
import com.silverchat.feature.profile.navigation.ProfileNavigation
import com.silverchat.feature.settings.navigation.SettingsNavigation
import com.silverchat.feature.stories.navigation.StoriesNavigation
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/* =========================================================================
   СВЯЗКИ, КОТОРЫЕ ВОЗМОЖНЫ ТОЛЬКО В :app
   ------------------------------------------------------------------------
   Здесь два вида биндингов:

   1. Порты, чья реализация требует `Activity`. Ни :core:data, ни
      :feature:profile не могут их предоставить: оба не зависят от
      `Activity` намеренно, иначе теряются тестируемость и переиспользуемость.

   2. Реестр навигации. Каждая фича объявляет свой граф через
      `FeatureNavigation`, а :app собирает их в один набор. Это единственное
      место в проекте, которое знает ВСЕ фичи сразу, — и единственное, где
      такое знание уместно: точка сборки.

   Что здесь НЕ появляется: реализации репозиториев (они в :core:data),
   настройки (:core:datastore), диспетчеры (:core:common), сеть
   (:core:network), база (:core:database). Если связка может жить в своём
   модуле — она должна жить там, иначе :app превращается в свалку.
   ========================================================================= */

@Module
@InstallIn(SingletonComponent::class)
abstract class AppBindsModule {

    /**
     * Захват медиа: выбор из Photo Picker, съёмка, запись «кружка».
     *
     * Единственная реализация порта в проекте. В unit-тестах
     * `MediaRepositoryImpl` подменяется фейком, поэтому `NoOpMediaCaptureGateway`
     * в DI не регистрируется: заглушка нужна для превью, а не для графа.
     */
    @Binds
    @Singleton
    abstract fun bindMediaCaptureGateway(
        impl: ActivityCaptureGateway,
    ): MediaCaptureGateway

    /** Выбор аватара и баннера профиля для экранов :feature:profile. */
    @Binds
    @Singleton
    abstract fun bindProfileMediaPicker(
        impl: ActivityProfileMediaPicker,
    ): ProfileMediaPicker
}

@Module
@InstallIn(SingletonComponent::class)
object AppNavigationModule {

    /**
     * Реестр графов навигации.
     *
     * Возвращаем именно `Set`, а не `@IntoSet`-биндинги по одному: порядок и
     * полнота набора тогда видны в одном месте. Добавление фичи — одна строка
     * ниже, удаление фичи — удаление одной строки; правки в `AppNavHost`
     * не требуются.
     *
     * `ProfileNavigation` принимает [ProfileMediaPicker]: редакторы аватара и
     * баннера не могут выбрать файл сами, поэтому зависимость прокидывается
     * в граф фичи из :app. Остальные графы не имеют внешних зависимостей.
     */
    @Provides
    @Singleton
    fun provideFeatureNavigations(
        profileMediaPicker: ProfileMediaPicker,
    ): Set<FeatureNavigation> = setOf(
        // Авторизация — не вкладка: открывается до входа и при истёкшей сессии
        AuthNavigation(),
        ChatsNavigation(),
        StoriesNavigation(),
        CallsNavigation(),
        MarketNavigation(),
        SettingsNavigation(),
        ProfileNavigation(profileMediaPicker),
        // Админ-панель @silver тоже не вкладка: вход только из «Настроек»
        AdminNavigation(),
    )
}

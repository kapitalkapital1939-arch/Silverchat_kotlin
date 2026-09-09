package com.silverchat.core.datastore.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.silverchat.core.datastore.SettingsDataStore
import com.silverchat.core.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * DataStore создаётся в single-экземпляре на процесс.
 *
 * Два DataStore над одним файлом = гарантированная потеря данных, поэтому
 * фабрика вызывается ровно здесь, а все потребители получают инъекцию.
 *
 * [ReplaceFileCorruptionHandler] обязателен: при обрыве записи (разряд батареи,
 * kill процесса) файл может остаться битым, и без обработчика DataStore
 * бросит CorruptionException на каждом чтении — приложение не запустится.
 */
@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    private const val FILE_NAME = "silverchat_settings.preferences_pb"

    @Provides
    @Singleton
    fun providePreferencesDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            produceFile = { context.preferencesDataStoreFile(FILE_NAME) },
        )
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DataStoreBindsModule {

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsDataStore): SettingsRepository
}

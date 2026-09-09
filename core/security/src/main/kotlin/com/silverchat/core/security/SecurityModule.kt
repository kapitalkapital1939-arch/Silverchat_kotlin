package com.silverchat.core.security

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {

    /**
     * SecureStorage создаётся лениво: инициализация EncryptedSharedPreferences
     * обращается к Keystore и занимает 50–200 мс. Делать это в конструкторе
     * Application — значит гарантированно получить белый экран при холодном старте.
     */
    @Provides
    @Singleton
    fun provideSecureStorage(@ApplicationContext context: Context): SecureStorage =
        EncryptedSecureStorage(context)

    @Provides
    @Singleton
    fun provideTokenStore(storage: SecureStorage): TokenStore = TokenStore(storage)

    @Provides
    @Singleton
    fun provideBiometricAuthenticator(storage: SecureStorage): BiometricAuthenticator =
        BiometricAuthenticator(storage)

    /** Политика защиты экрана: FLAG_SECURE, если пользователь включил настройку. */
    @Provides
    @Singleton
    fun provideScreenSecurityPolicy(storage: SecureStorage): ScreenSecurityPolicy =
        ScreenSecurityPolicy(storage)
}

/** Управление FLAG_SECURE (запрет скриншотов и превью в «недавних»). */
class ScreenSecurityPolicy(private val storage: SecureStorage) {
    val isSecureWindowRequired: Boolean
        get() = storage.secureFlagEnabled

    fun setSecureWindowRequired(value: Boolean) {
        storage.secureFlagEnabled = value
    }
}

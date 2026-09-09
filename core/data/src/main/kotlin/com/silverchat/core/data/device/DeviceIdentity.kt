package com.silverchat.core.data.device

import com.silverchat.core.security.SecureStorage
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Стабильный идентификатор устройства.
 *
 * Сервер использует его, чтобы:
 *  - не слать OTP-флуд на одно устройство чаще лимита;
 *  - показывать в «Активных сессиях» именно это устройство как текущее;
 *  - привязать refresh-токен к устройству (ротация токенов).
 *
 * Генерируется один раз и хранится в `EncryptedSharedPreferences`:
 * переустановка приложения даёт новый id, и старая сессия на сервере
 * перестаёт быть «текущей» — это ожидаемое поведение.
 */
@Singleton
class DeviceIdentity @Inject constructor(
    private val secureStorage: SecureStorage,
) {

    val deviceId: String by lazy {
        secureStorage.getString(KEY_DEVICE_ID)?.takeIf { it.isNotBlank() }
            ?: UUID.randomUUID().toString().also { secureStorage.putString(KEY_DEVICE_ID, it) }
    }

    private companion object {
        const val KEY_DEVICE_ID = "device_id"
    }
}

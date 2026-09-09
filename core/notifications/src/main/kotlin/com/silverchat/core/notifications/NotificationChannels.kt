package com.silverchat.core.notifications

/**
 * Каналы уведомлений.
 *
 * Разделение по каналам принципиально: на Android 8+ пользователь управляет
 * важностью КАЖДОГО канала. Если свалить сообщения и звонки в один канал,
 * то, заглушив чаты, пользователь пропустит входящий звонок.
 *
 * IMPORTANCE_HIGH + fullScreenIntent для звонков — иначе на заблокированном
 * экране звонок покажется только как обычное уведомление и будет пропущен.
 */
enum class NotificationChannel(
    val id: String,
    val nameRu: String,
    val descriptionRu: String,
    val importance: Int,
    val sound: Boolean = true,
    val vibrate: Boolean = true,
    val lightsEnabled: Boolean = false,
) {
    MESSAGES(
        id = "sc_messages",
        nameRu = "Сообщения",
        descriptionRu = "Новые сообщения в личных чатах",
        importance = android.app.NotificationManager.IMPORTANCE_DEFAULT,
    ),
    GROUPS(
        id = "sc_groups",
        nameRu = "Группы",
        descriptionRu = "Сообщения в группах",
        importance = android.app.NotificationManager.IMPORTANCE_DEFAULT,
    ),
    CHANNELS(
        id = "sc_channels",
        nameRu = "Каналы",
        descriptionRu = "Публикации в каналах",
        importance = android.app.NotificationManager.IMPORTANCE_LOW,
        sound = false,
    ),
    CALLS(
        id = "sc_calls",
        nameRu = "Звонки",
        descriptionRu = "Входящие голосовые и видеозвонки",
        importance = android.app.NotificationManager.IMPORTANCE_HIGH,
        lightsEnabled = true,
    ),
    STORIES(
        id = "sc_stories",
        nameRu = "Сторис",
        descriptionRu = "Новые сторис от контактов",
        importance = android.app.NotificationManager.IMPORTANCE_LOW,
        sound = false,
        vibrate = false,
    ),
    MARKET(
        id = "sc_market",
        nameRu = "Маркет и кошелёк",
        descriptionRu = "Сделки с юзернеймами, подарки, начисления сильверов",
        importance = android.app.NotificationManager.IMPORTANCE_DEFAULT,
    ),
    PREMIUM(
        id = "sc_premium",
        nameRu = "SilverChat Premium",
        descriptionRu = "Статус подписки и её окончание",
        importance = android.app.NotificationManager.IMPORTANCE_LOW,
    ),
    ADMIN(
        id = "sc_admin",
        nameRu = "Администрирование",
        descriptionRu = "Жалобы и события админ-панели (@silver)",
        importance = android.app.NotificationManager.IMPORTANCE_HIGH,
    ),
    UPLOADS(
        id = "sc_uploads",
        nameRu = "Загрузка файлов",
        descriptionRu = "Прогресс отправки медиа",
        importance = android.app.NotificationManager.IMPORTANCE_LOW,
        sound = false,
        vibrate = false,
    ),
    ;

    companion object {
        fun byId(id: String): NotificationChannel? = entries.firstOrNull { it.id == id }
    }
}

package com.silverchat.core.database

import androidx.room.TypeConverter
import com.silverchat.core.model.MessageStatus
import com.silverchat.core.model.Reaction
import com.silverchat.core.model.ReactionKind
import com.silverchat.core.model.TextEntity
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Конвертеры Room <-> JSON.
 *
 * Отдельный экземпляр Json (не общий с сетевым): у БД нет требования
 * ignoreUnknownKeys — наоборот, лучше упасть на повреждённой строке,
 * чем молча потерять реакции сообщения.
 */
class Converters {

    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    @TypeConverter
    fun reactionsToString(value: List<Reaction>?): String? =
        value?.let { json.encodeToString(it) }

    @TypeConverter
    fun stringToReactions(value: String?): List<Reaction>? =
        value?.let { runCatching { json.decodeFromString<List<Reaction>>(it) }.getOrNull() }

    @TypeConverter
    fun entitiesToString(value: List<TextEntity>?): String? =
        value?.let { json.encodeToString(it) }

    @TypeConverter
    fun stringToEntities(value: String?): List<TextEntity>? =
        value?.let { runCatching { json.decodeFromString<List<TextEntity>>(it) }.getOrNull() }

    @TypeConverter
    fun waveformToString(value: List<Float>?): String? =
        value?.joinToString(",") { it.toString() }

    @TypeConverter
    fun stringToWaveform(value: String?): List<Float>? =
        value?.takeIf { it.isNotBlank() }?.split(",")?.mapNotNull { it.toFloatOrNull() }

    @TypeConverter
    fun stringListToString(value: List<String>?): String? = value?.joinToString("|")

    @TypeConverter
    fun stringToStringList(value: String?): List<String>? =
        value?.takeIf { it.isNotBlank() }?.split("|")

    @TypeConverter
    fun statusToString(value: MessageStatus): String = value.name

    @TypeConverter
    fun stringToStatus(value: String): MessageStatus =
        MessageStatus.entries.firstOrNull { it.name == value } ?: MessageStatus.SENT

    @TypeConverter
    fun reactionKindToString(value: ReactionKind?): String? = value?.name

    @TypeConverter
    fun stringToReactionKind(value: String?): ReactionKind? =
        value?.let { raw -> ReactionKind.entries.firstOrNull { it.name == raw } }

    @TypeConverter
    fun contentToString(value: com.silverchat.core.model.MessageContent): String =
        json.encodeToString(com.silverchat.core.model.MessageContent.serializer(), value)

    @TypeConverter
    fun stringToContent(value: String): com.silverchat.core.model.MessageContent =
        runCatching {
            json.decodeFromString(com.silverchat.core.model.MessageContent.serializer(), value)
        }.getOrElse {
            com.silverchat.core.model.MessageContent.Text("Сообщение не поддерживается этой версией")
        }
}

package com.silverchat.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.silverchat.core.model.MessageStatus
import com.silverchat.core.model.Reaction
import com.silverchat.core.model.ReactionKind

/* =========================================================================
   ENTITIES
   ------------------------------------------------------------------------
   Храним ДОМЕННЫЕ модели напрямую (Message, Chat), а не отдельные DTO-копии:
   Room умеет сериализовать вложенные объекты через TypeConverters, а второй
   набор классов удвоил бы объём маппинга и вероятность рассинхрона.
   ========================================================================= */

@Entity(
    tableName = "chats",
    indices = [
        Index("last_message_at"),
        Index("archived"),
        Index("unread_count"),
    ],
)
data class ChatEntity(
    @PrimaryKey val id: String,
    val type: String,
    val title: String,
    val about: String?,
    val avatarStaticUrl: String?,
    val avatarAnimatedUrl: String?,
    val avatarAnimationType: String,
    val username: String?,
    val peerId: String?,
    @ColumnInfo(name = "peer_json") val peerJson: String?,
    val membersCount: Int,
    val onlineCount: Int,
    @ColumnInfo(name = "last_message_json") val lastMessageJson: String?,
    val unreadCount: Int,
    val mentionsCount: Int,
    val pinnedJson: String?,
    val muted: Boolean,
    val mutedUntil: Long?,
    val archived: Boolean,
    val verified: Boolean,
    val restricted: Boolean,
    val myRole: String,
    val permissionsJson: String?,
    val locationJson: String?,
    val workingHoursJson: String?,
    val slowModeSeconds: Int,
    val joinRequests: Int,
    @ColumnInfo(name = "last_message_at") val lastMessageAt: Long,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatEntity::class,
            parentColumns = ["id"],
            childColumns = ["chat_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("chat_id"),
        Index(value = ["chat_id", "sent_at"]),
        Index("client_message_id"),
        Index("sender_id"),
    ],
)
data class MessageEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "chat_id") val chatId: String,
    @ColumnInfo(name = "sender_id") val senderId: String,
    @ColumnInfo(name = "sender_json") val senderJson: String?,
    /** Сложный sealed-контент — JSON-строкой (см. Converters.contentToString). */
    @ColumnInfo(name = "content_json") val contentJson: String,
    val status: MessageStatus,
    @ColumnInfo(name = "reply_to_json") val replyToJson: String?,
    @ColumnInfo(name = "forward_json") val forwardJson: String?,
    val editedAt: Long?,
    val reactions: List<Reaction>,
    val myReaction: ReactionKind?,
    @ColumnInfo(name = "sent_at") val sentAt: Long,
    val scheduledAt: Long?,
    val pinned: Boolean,
    val silent: Boolean,
    val viewsCount: Int,
    val deleted: Boolean,
    @ColumnInfo(name = "client_message_id") val clientMessageId: String?,
    /** Для дедупликации ack'ов WebSocket. */
    val acknowledged: Boolean = false,
)

@Entity(
    tableName = "members",
    primaryKeys = ["chat_id", "user_id"],
    foreignKeys = [
        ForeignKey(
            entity = ChatEntity::class,
            parentColumns = ["id"],
            childColumns = ["chat_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("chat_id")],
)
data class MemberEntity(
    @ColumnInfo(name = "chat_id") val chatId: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "user_json") val userJson: String,
    val role: String,
    val customTitle: String?,
    val joinedAt: Long,
    val permissionsJson: String?,
)

@Entity(
    tableName = "drafts",
    primaryKeys = ["chat_id"],
    foreignKeys = [
        ForeignKey(
            entity = ChatEntity::class,
            parentColumns = ["id"],
            childColumns = ["chat_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class DraftEntity(
    @ColumnInfo(name = "chat_id") val chatId: String,
    val text: String,
    val replyTo: String?,
    val attachments: String?,
    val updatedAt: Long,
)

/**
 * Очередь неотправленных сообщений.
 *
 * Отдельная таблица (а не просто MessageEntity со статусом SENDING), потому что
 * здесь хранится сериализованный запрос к серверу: после переподключения
 * клиент отправляет его как есть, с тем же client_message_id.
 */
@Entity(
    tableName = "pending_outgoing",
    indices = [Index("created_at"), Index("chat_id")],
)
data class PendingOutgoingEntity(
    @PrimaryKey @ColumnInfo(name = "client_message_id") val clientMessageId: String,
    @ColumnInfo(name = "chat_id") val chatId: String,
    val op: String,
    @ColumnInfo(name = "payload_json") val payloadJson: String,
    val attempts: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    val lastAttemptAt: Long? = null,
    val lastError: String? = null,
)

@Entity(
    tableName = "stories",
    indices = [Index("author_id"), Index("expires_at"), Index("created_at")],
)
data class StoryEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "author_id") val authorId: String,
    @ColumnInfo(name = "author_json") val authorJson: String?,
    @ColumnInfo(name = "media_json") val mediaJson: String,
    val caption: String?,
    val createdAt: Long,
    @ColumnInfo(name = "expires_at") val expiresAt: Long,
    val privacy: String,
    val viewersCount: Int,
    val reactionsJson: String?,
    val seenByMe: Boolean,
    val pinnedToProfile: Boolean,
)

/** Локальный кэш движений кошелька (для мгновенной отрисовки истории). */
@Entity(
    tableName = "ledger",
    indices = [Index("created_at")],
)
data class LedgerEntity(
    @PrimaryKey val id: String,
    val amount: Long,
    val reason: String,
    val reference: String?,
    val counterparty: String?,
    val balanceAfter: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(
    tableName = "call_history",
    indices = [Index("started_at"), Index("chat_id")],
)
data class CallHistoryEntity(
    @PrimaryKey @ColumnInfo(name = "call_id") val callId: String,
    @ColumnInfo(name = "chat_id") val chatId: String,
    @ColumnInfo(name = "peer_json") val peerJson: String,
    val type: String,
    val missed: Boolean,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    val durationMs: Long,
)

@Entity(
    tableName = "search_history",
    indices = [Index(value = ["query"], unique = true)],
)
data class SearchHistoryEntity(
    @PrimaryKey val query: String,
    @ColumnInfo(name = "last_used_at") val lastUsedAt: Long,
    val useCount: Int = 1,
)

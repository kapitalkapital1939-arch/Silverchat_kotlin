package com.silverchat.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Сторис.
 *
 * Публикация -> [Story] у автора; подписчики получают её в ленте через
 * WebSocket-событие `story.new`. Просмотры считаются на сервере, клиент
 * отправляет `story.view` один раз за сегмент.
 */
@Serializable
data class Story(
    @SerialName("id") val id: StoryId,
    @SerialName("author_id") val authorId: UserId,
    @SerialName("author") val author: User? = null,
    @SerialName("media") val media: StoryMedia,
    @SerialName("caption") val caption: String? = null,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("expires_at") val expiresAt: Long,
    @SerialName("privacy") val privacy: StoryPrivacy = StoryPrivacy.CONTACTS,
    @SerialName("viewers_count") val viewersCount: Int = 0,
    @SerialName("reactions") val reactions: List<Reaction> = emptyList(),
    @SerialName("seen_by_me") val seenByMe: Boolean = false,
    @SerialName("pinned_to_profile") val pinnedToProfile: Boolean = false,
    @SerialName("replies_enabled") val repliesEnabled: Boolean = true,
    @SerialName("location") val location: LocationInfo? = null,
    @SerialName("mention_ids") val mentionIds: List<UserId> = emptyList(),
) {
    val isExpired: Boolean get() = expiresAt <= System.currentTimeMillis()
    val isMine: Boolean get() = false // проставляется в StoryRepository по currentUserId
}

@Serializable
data class StoryMedia(
    @SerialName("type") val type: StoryMediaType,
    @SerialName("url") val url: String,
    @SerialName("thumb_url") val thumbUrl: String? = null,
    @SerialName("duration_ms") val durationMs: Long = 5_000L,
    @SerialName("width") val width: Int = 1080,
    @SerialName("height") val height: Int = 1920,
    /** Градиент-подложка для текстовых сторис (ARGB). */
    @SerialName("background_gradient") val backgroundGradient: List<Long> = emptyList(),
    @SerialName("overlay_text") val overlayText: String? = null,
    @SerialName("upload_progress") val uploadProgress: Float = 1f,
)

@Serializable
enum class StoryMediaType {
    @SerialName("photo") PHOTO,
    @SerialName("video") VIDEO,
    @SerialName("text") TEXT,
    @SerialName("gif") GIF,
}

@Serializable
enum class StoryPrivacy {
    @SerialName("everyone") EVERYONE,
    @SerialName("contacts") CONTACTS,
    @SerialName("close_friends") CLOSE_FRIENDS,
    @SerialName("selected") SELECTED,
    @SerialName("private") PRIVATE,
}

/** Группировка ленты: сторис одного автора = одна «ячейка» в трейе. */
@Serializable
data class StoryCluster(
    @SerialName("author_id") val authorId: UserId,
    @SerialName("author") val author: User,
    @SerialName("stories") val stories: List<Story>,
) {
    val unseenCount: Int get() = stories.count { !it.seenByMe }
    val hasUnseen: Boolean get() = unseenCount > 0
    val latest: Story? get() = stories.maxByOrNull { it.createdAt }
}

@Serializable
data class StoryReply(
    @SerialName("story_id") val storyId: StoryId,
    @SerialName("text") val text: String? = null,
    @SerialName("reaction") val reaction: ReactionKind? = null,
    @SerialName("media") val media: StoryMedia? = null,
)

/** Ответ на публикацию сторис (для тостов/бейджей). */
@Serializable
data class StoryStats(
    @SerialName("views") val views: Int,
    @SerialName("replies") val replies: Int,
    @SerialName("reactions") val reactions: Int,
    @SerialName("forwards") val forwards: Int,
)

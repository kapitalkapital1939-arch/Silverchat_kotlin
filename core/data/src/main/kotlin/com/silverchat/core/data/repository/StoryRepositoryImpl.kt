package com.silverchat.core.data.repository

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import com.silverchat.core.common.result.ScError
import com.silverchat.core.common.result.ScResult
import com.silverchat.core.data.mapper.toDomain
import com.silverchat.core.data.mapper.toEntity
import com.silverchat.core.database.dao.StoryDao
import com.silverchat.core.domain.repository.LocalMedia
import com.silverchat.core.domain.repository.StoryRepository
import com.silverchat.core.media.upload.MediaUploader
import com.silverchat.core.media.upload.UploadKind
import com.silverchat.core.model.LocationInfo
import com.silverchat.core.model.ReactionKind
import com.silverchat.core.model.Story
import com.silverchat.core.model.StoryCluster
import com.silverchat.core.model.StoryId
import com.silverchat.core.model.StoryMediaType
import com.silverchat.core.model.StoryPrivacy
import com.silverchat.core.model.User
import com.silverchat.core.network.api.SilverChatApi
import com.silverchat.core.network.dto.PublishStoryRequest
import com.silverchat.core.network.dto.ReactionRequest
import com.silverchat.core.network.dto.StoryPinRequest
import com.silverchat.core.network.dto.StoryReplyRequest
import com.silverchat.core.network.mapper.apiCall
import com.silverchat.core.network.mapper.toDomain as dtoToDomain
import com.silverchat.core.network.mapper.toDto
import com.silverchat.core.security.AuthState
import com.silverchat.core.security.TokenStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Сторис: лента, публикация, просмотры, реакции, архив.
 *
 * Лента группируется в [StoryCluster] по автору — именно так её рисует трей
 * сверху списка чатов. Группировка происходит здесь, а не во ViewModel,
 * потому что кластеры нужны и экрану сторис, и бейджу на аватаре.
 *
 * Публикация двухэтапная: сначала медиа уходит в object storage по
 * presigned-URL ([MediaUploader]), и только получив публичный URL мы создаём
 * сторис. Иначе сервер получил бы ссылку на файл, которого ещё нет.
 */
@Singleton
class StoryRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: SilverChatApi,
    private val storyDao: StoryDao,
    private val uploader: MediaUploader,
    private val tokenStore: TokenStore,
) : StoryRepository {

    override fun observeFeed(): Flow<List<StoryCluster>> =
        storyDao.observeFeed().map { entities ->
            entities.mapNotNull { it.toDomain() }
                // Сторис без автора нечем рисовать в трее: кластер требует User.
                .filter { it.author != null }
                .groupBy { it.authorId }
                .map { (authorId, stories) ->
                    StoryCluster(
                        authorId = authorId,
                        author = stories.firstNotNullOf { it.author },
                        stories = stories.sortedByDescending { it.createdAt },
                    )
                }
                // Непросмотренные авторы — слева, как в Telegram.
                .sortedWith(
                    compareByDescending<StoryCluster> { cluster ->
                        cluster.stories.any { !it.seenByMe }
                    }.thenByDescending { cluster ->
                        cluster.stories.maxOf { it.createdAt }
                    },
                )
        }

    override fun observeStory(storyId: StoryId): Flow<Story?> =
        storyDao.observeStory(storyId).map { it?.toDomain() }

    override fun observeMyStories(): Flow<List<Story>> = myStoriesFlow(includeExpired = false)

    /**
     * Архив — мои сторис, которые уже истекли, но не удалены.
     * Отдельной таблицы нет: это срез той же `stories` по `expires_at`.
     */
    override fun observeArchive(): Flow<List<Story>> = myStoriesFlow(includeExpired = true)

    override suspend fun publish(
        localUri: String?,
        caption: String?,
        privacy: StoryPrivacy,
        backgroundGradient: List<Long>?,
        overlayText: String?,
        location: LocationInfo?,
        durationMs: Long,
    ): ScResult<Story> {
        // Текстовая сторис: медиа нет, обязателен градиент или наложенный текст.
        val isTextStory = localUri.isNullOrBlank()
        if (isTextStory && backgroundGradient.isNullOrEmpty() && overlayText.isNullOrBlank()) {
            return ScResult.Failure(
                ScError.Validation("Добавьте фото или текст сторис", field = "media"),
            )
        }

        val mediaType: StoryMediaType
        val mediaUrl: String?

        if (isTextStory) {
            mediaType = StoryMediaType.TEXT
            mediaUrl = null
        } else {
            val local = resolveLocalMedia(localUri!!)
                ?: return ScResult.Failure(ScError.Local("Не удалось прочитать файл"))
            val uploaded = uploader.upload(local, chatId = null, kind = UploadKind.STORY)
            if (uploaded is ScResult.Failure) return ScResult.Failure(uploaded.error)
            val remote = (uploaded as ScResult.Success).data
            mediaUrl = remote.url
            mediaType = if (local.durationMs > 0L) StoryMediaType.VIDEO else StoryMediaType.PHOTO
        }

        return apiCall {
            val published = api.publishStory(
                PublishStoryRequest(
                    mediaUrl = mediaUrl,
                    mediaType = mediaType.name.lowercase(),
                    caption = caption?.trim()?.ifBlank { null },
                    privacy = privacy.name.lowercase(),
                    backgroundGradient = backgroundGradient.orEmpty(),
                    overlayText = overlayText?.ifBlank { null },
                    location = location?.toDto(),
                    durationMs = durationMs,
                ),
            ).dtoToDomain()
            storyDao.upsert(published.toEntity())
            published
        }
    }

    override suspend fun markViewed(storyId: StoryId): ScResult<Unit> = apiCall {
        // Локально сразу: следующий кадр трея не должен показать её непросмотренной.
        storyDao.markSeen(storyId)
        api.viewStory(storyId)
        Unit
    }

    override suspend fun react(storyId: StoryId, kind: ReactionKind): ScResult<Unit> = apiCall {
        api.reactStory(storyId, ReactionRequest(kind.name.lowercase()))
        Unit
    }

    override suspend fun reply(storyId: StoryId, text: String): ScResult<Unit> = apiCall {
        // Ответ на сторис — это обычное сообщение в личном чате с автором,
        // поэтому сервер возвращает MessageDto; кэшировать его здесь не нужно,
        // этим занимается MessageRepository по событию message.new.
        api.replyStory(storyId, StoryReplyRequest(text = text.trim()))
        Unit
    }

    override suspend fun delete(storyId: StoryId): ScResult<Unit> = apiCall {
        api.deleteStory(storyId)
        storyDao.delete(storyId)
    }

    override suspend fun pinToProfile(storyId: StoryId, pinned: Boolean): ScResult<Unit> =
        apiCall {
            api.pinStory(storyId, StoryPinRequest(pinned))
            Unit
        }

    override suspend fun viewers(storyId: StoryId): ScResult<List<User>> = apiCall {
        api.storyViewers(storyId).map { it.toDomain() }
    }

    /**
     * Просмотр «невидимкой» — премиум-функция.
     *
     * Сервер фиксирует просмотр, но не добавляет меня в список зрителей.
     * Локально сторис всё равно помечается просмотренной: иначе трей будет
     * вечно показывать её как новую и провоцировать повторный просмотр.
     */
    override suspend fun viewStealth(storyId: StoryId): ScResult<Unit> = apiCall {
        api.viewStoryStealth(storyId)
        storyDao.markSeen(storyId)
    }

    /* ── Внутреннее ────────────────────────────────────────────────────── */

    /** Подтянуть ленту с сервера — вызывается синхронизацией. */
    suspend fun syncFeed() {
        val clusters = api.storiesFeed()
        storyDao.upsertAll(
            clusters.flatMap { cluster ->
                cluster.toDomain().stories.map { it.toEntity() }
            },
        )
        storyDao.purgeExpired()
    }

    /**
     * Мои сторис из локального кэша.
     *
     * Если пользователь ещё не авторизован, возвращаем пустой поток, а не
     * падаем: трей сторис рисуется и на экране входа, где автора просто нет.
     */
    private fun myStoriesFlow(includeExpired: Boolean): Flow<List<Story>> {
        val meId = currentUserId() ?: return flowOf(emptyList())
        return storyDao.observeByAuthor(meId).map { entities ->
            val now = System.currentTimeMillis()
            entities.mapNotNull { it.toDomain() }
                .filter { if (includeExpired) it.expiresAt <= now else it.expiresAt > now }
                .sortedByDescending { it.createdAt }
        }
    }

    private fun currentUserId(): String? =
        (tokenStore.authState.value as? AuthState.Authenticated)?.userId

    /**
     * Определяет MIME и размер по URI.
     *
     * MIME нужен серверу для presigned-URL: object storage требует, чтобы
     * `Content-Type` загрузки совпадал с типом, под который выдан билет.
     */
    private fun resolveLocalMedia(uri: String): LocalMedia? = runCatching {
        val parsed = Uri.parse(uri)
        val resolver = context.contentResolver
        val mimeType = resolver.getType(parsed)
            ?: MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(MimeTypeMap.getFileExtensionFromUrl(uri))
            ?: "application/octet-stream"
        val sizeBytes = resolver.openFileDescriptor(parsed, "r")?.use { it.statSize } ?: 0L
        LocalMedia(uri = uri, mimeType = mimeType, sizeBytes = sizeBytes)
    }.getOrNull()
}

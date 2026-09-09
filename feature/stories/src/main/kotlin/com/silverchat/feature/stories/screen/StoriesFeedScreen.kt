package com.silverchat.feature.stories.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.silverchat.core.common.format.NumberFormatter
import com.silverchat.core.common.format.TimeFormatter
import com.silverchat.core.designsystem.component.EmptyState
import com.silverchat.core.designsystem.component.SilverIconButton
import com.silverchat.core.designsystem.component.SilverTopBar
import com.silverchat.core.designsystem.component.StoryTrayAvatar
import com.silverchat.core.designsystem.navigation.StoriesNavigator
import com.silverchat.core.designsystem.theme.ScAvatarSize
import com.silverchat.core.designsystem.theme.ScShapes
import com.silverchat.core.designsystem.theme.ScSpacing
import com.silverchat.core.designsystem.theme.ScTheme
import com.silverchat.core.model.Story
import com.silverchat.core.model.StoryCluster
import com.silverchat.core.model.StoryMediaType
import com.silverchat.feature.stories.StoryFeedViewModel
import com.silverchat.feature.stories.titleRu

/**
 * Экран сторис: трей авторов и архив собственных публикаций.
 *
 * Две половины одного экрана, переключаются кнопкой в шапке:
 *  - **лента** — горизонтальный трей (как в Telegram) и вертикальный список
 *    авторов с числом непросмотренных;
 *  - **архив** — только свои сторис, включая истёкшие и закреплённые.
 *
 * Зачем архиву отдельный экран, а не вкладка профиля: сторис — короткий
 * контент, и пользователь приходит сюда «посмотреть, что я публиковал»,
 * а не «отредактировать профиль». Закреплённые показываем первыми: они
 * остаются в профиле навсегда, поэтому важнее свежих.
 */
@Composable
fun StoriesFeedScreen(
    navigator: StoriesNavigator,
    modifier: Modifier = Modifier,
    viewModel: StoryFeedViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier.fillMaxSize().background(ScTheme.background)) {
        SilverTopBar(
            title = if (state.archiveMode) "Мои сторис" else "Сторис",
            subtitle = if (state.archiveMode) {
                "Всего ${state.myStories.size}" +
                    if (state.expiredCount > 0) " · истекло ${state.expiredCount}" else ""
            } else {
                if (state.totalUnseen > 0) "Не просмотрено: ${state.totalUnseen}" else null
            },
            onBack = navigator::back,
            actions = {
                SilverIconButton(
                    icon = Icons.Filled.Archive,
                    contentDescription = if (state.archiveMode) {
                        "Вернуться к ленте"
                    } else {
                        "Мои сторис и архив"
                    },
                    onClick = viewModel::toggleArchive,
                    tint = if (state.archiveMode) ScTheme.accent else ScTheme.textPrimary,
                )
                SilverIconButton(
                    icon = Icons.Filled.Add,
                    contentDescription = "Опубликовать сторис",
                    onClick = navigator::openCreate,
                    tint = ScTheme.textPrimary,
                )
            },
        )

        if (state.isEmpty) {
            EmptyState(
                icon = Icons.Filled.PlayArrow,
                title = if (state.archiveMode) "Вы ничего не публиковали" else "Пока нет сторис",
                subtitle = if (state.archiveMode) {
                    "Опубликованные сторис появятся здесь и останутся в архиве\nдаже после истечения срока."
                } else {
                    "Сторис ваших контактов появятся здесь.\nОпубликуйте первую — её увидят согласно настройкам приватности."
                },
                actionLabel = "Опубликовать сторис",
                onAction = navigator::openCreate,
                modifier = Modifier.fillMaxSize(),
            )
            return
        }

        if (state.archiveMode) {
            ArchiveList(
                stories = state.myStories,
                onOpen = { story -> navigator.openViewer(story.authorId.raw, 0) },
                onDelete = viewModel::deleteStory,
                onTogglePin = viewModel::togglePin,
            )
        } else {
            FeedContent(
                clusters = state.clusters,
                onOpenAuthor = { cluster, index ->
                    viewModel.onViewerOpened(cluster.authorId.raw)
                    navigator.openViewer(cluster.authorId.raw, index)
                },
                onCreate = navigator::openCreate,
                onOpenProfile = { userId -> navigator.openProfile(userId) },
            )
        }
    }
}

/* =========================================================================
   ЛЕНТА
   ========================================================================= */

@Composable
private fun FeedContent(
    clusters: List<StoryCluster>,
    onOpenAuthor: (StoryCluster, Int) -> Unit,
    onCreate: () -> Unit,
    onOpenProfile: (String) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = ScSpacing.xxl)) {

        // ── Горизонтальный трей ──────────────────────────────────────────
        item(key = "tray", contentType = "tray") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = ScSpacing.md, vertical = ScSpacing.sm),
                horizontalArrangement = Arrangement.spacedBy(ScSpacing.md),
            ) {
                // Первая ячейка — «Моя сторис»: главный вход в публикацию
                item(key = "tray_mine", contentType = "tray_add") {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(ScAvatarSize.tray + 8.dp)
                            .clip(ScShapes.cardSmall)
                            .clickable(onClick = onCreate)
                            .padding(vertical = ScSpacing.xs),
                    ) {
                        Box(contentAlignment = Alignment.BottomEnd) {
                            Box(
                                Modifier
                                    .size(ScAvatarSize.tray)
                                    .clip(CircleShape)
                                    .background(ScTheme.surfaceGlass),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = null,
                                    tint = ScTheme.accent,
                                    modifier = Modifier.size(26.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(ScSpacing.xs))
                        Text(
                            text = "Моя сторис",
                            color = ScTheme.textSecondary,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                items(clusters, key = { "tray_${it.authorId.raw}" }, contentType = { "tray_item" }) { cluster ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(ScAvatarSize.tray + 8.dp)
                            .clip(ScShapes.cardSmall)
                            .clickable { onOpenAuthor(cluster, firstUnseenIndex(cluster)) }
                            .padding(vertical = ScSpacing.xs),
                    ) {
                        StoryTrayAvatar(user = cluster.author, stories = cluster.stories)
                        Spacer(Modifier.height(ScSpacing.xs))
                        Text(
                            text = cluster.author.firstName,
                            color = if (cluster.hasUnseen) ScTheme.textPrimary else ScTheme.textTertiary,
                            fontSize = 11.sp,
                            fontWeight = if (cluster.hasUnseen) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        // ── Вертикальный список авторов ──────────────────────────────────
        item(key = "hdr", contentType = "header") {
            Text(
                text = "Все сторис",
                color = ScTheme.textSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(
                    start = ScSpacing.md,
                    end = ScSpacing.md,
                    top = ScSpacing.sm,
                    bottom = ScSpacing.xs,
                ),
            )
        }

        items(clusters, key = { "row_${it.authorId.raw}" }, contentType = { "author_row" }) { cluster ->
            AuthorRow(
                cluster = cluster,
                onOpen = { onOpenAuthor(cluster, firstUnseenIndex(cluster)) },
                onOpenProfile = { onOpenProfile(cluster.authorId.raw) },
            )
        }
    }
}

/** Индекс первой непросмотренной сторис — с неё и начинаем просмотр. */
private fun firstUnseenIndex(cluster: StoryCluster): Int {
    val sorted = cluster.stories.sortedBy { it.createdAt }
    return sorted.indexOfFirst { !it.seenByMe }.takeIf { it >= 0 } ?: 0
}

@Composable
private fun AuthorRow(
    cluster: StoryCluster,
    onOpen: () -> Unit,
    onOpenProfile: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = ScSpacing.md, vertical = ScSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Аватар открывает профиль, строка — просмотрщик: две разные зоны тапа
        Box(Modifier.clip(CircleShape).clickable(onClick = onOpenProfile)) {
            StoryTrayAvatar(user = cluster.author, stories = cluster.stories)
        }
        Spacer(Modifier.width(ScSpacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                text = cluster.author.fullName,
                color = ScTheme.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    // «сторис» не склоняется, поэтому plural не нужен
                    append("${cluster.stories.size} сторис")
                    cluster.latest?.let {
                        append(" · ")
                        append(TimeFormatter.ago(System.currentTimeMillis() - it.createdAt))
                    }
                },
                color = ScTheme.textTertiary,
                fontSize = 12.5.sp,
                maxLines = 1,
            )
        }

        if (cluster.hasUnseen) {
            Box(
                Modifier
                    .clip(ScShapes.chip)
                    .background(ScTheme.accentContainer)
                    .padding(horizontal = 9.dp, vertical = 4.dp),
            ) {
                Text(
                    text = NumberFormatter.compact(cluster.unseenCount.toLong()),
                    color = ScTheme.accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/* =========================================================================
   АРХИВ
   ========================================================================= */

@Composable
private fun ArchiveList(
    stories: List<Story>,
    onOpen: (Story) -> Unit,
    onDelete: (Story) -> Unit,
    onTogglePin: (Story) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = ScSpacing.xxl)) {
        // Закреплённые первыми: они остаются в профиле навсегда
        items(
            items = stories.sortedByDescending { it.pinnedToProfile },
            key = { it.id.raw },
            contentType = { "archive_item" },
        ) { story ->
            ArchiveRow(
                story = story,
                onOpen = { onOpen(story) },
                onDelete = { onDelete(story) },
                onTogglePin = { onTogglePin(story) },
            )
        }
    }
}

@Composable
private fun ArchiveRow(
    story: Story,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onTogglePin: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = ScSpacing.md, vertical = ScSpacing.sm)
            .clip(ScShapes.card)
            .background(ScTheme.surfaceElevated)
            .clickable(onClick = onOpen)
            .padding(ScSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Превью 9:16 уменьшенное: форма сторис узнаётся без подписи
        Box(
            Modifier
                .width(48.dp)
                .height(85.dp)
                .clip(ScShapes.cardSmall)
                .background(ScTheme.surfaceGlass),
        ) {
            when (story.media.type) {
                StoryMediaType.TEXT -> Box(
                    Modifier.fillMaxSize().padding(4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = story.media.overlayText?.take(24).orEmpty(),
                        color = Color.White,
                        fontSize = 8.sp,
                        lineHeight = 10.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 4,
                    )
                }

                else -> AsyncImage(
                    model = story.media.thumbUrl ?: story.media.url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // Истёкшие сторис затемняем: их нельзя отправить, только посмотреть
            if (story.isExpired) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))
            }
        }

        Spacer(Modifier.width(ScSpacing.md))

        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = story.caption?.takeIf { it.isNotBlank() }
                        ?: story.media.overlayText?.takeIf { it.isNotBlank() }
                        ?: typeLabel(story.media.type),
                    color = ScTheme.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (story.pinnedToProfile) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Filled.PushPin,
                        contentDescription = "Закреплена в профиле",
                        tint = ScTheme.accent,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = buildString {
                    append(TimeFormatter.daySeparator(story.createdAt))
                    append(" · ")
                    append(story.privacy.titleRu())
                    append(" · ")
                    append("${story.viewersCount} просмотров")
                    if (story.isExpired) append(" · истекла")
                },
                color = ScTheme.textTertiary,
                fontSize = 11.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        SilverIconButton(
            icon = Icons.Filled.PushPin,
            contentDescription = if (story.pinnedToProfile) {
                "Открепить от профиля"
            } else {
                "Закрепить в профиле"
            },
            onClick = onTogglePin,
            tint = if (story.pinnedToProfile) ScTheme.accent else ScTheme.textTertiary,
            size = 36,
        )
        SilverIconButton(
            icon = Icons.Filled.Delete,
            contentDescription = "Удалить сторис",
            onClick = onDelete,
            tint = ScTheme.danger,
            size = 36,
        )
    }
}

private fun typeLabel(type: StoryMediaType): String = when (type) {
    StoryMediaType.PHOTO -> "Фото"
    StoryMediaType.VIDEO -> "Видео"
    StoryMediaType.TEXT -> "Текст"
    StoryMediaType.GIF -> "GIF"
}

/** Обёртка для навигационного графа: экран ленты без обязательных параметров. */
@Composable
internal fun StoriesFeedScreenHost(navigator: StoriesNavigator) {
    StoriesFeedScreen(navigator = navigator)
}

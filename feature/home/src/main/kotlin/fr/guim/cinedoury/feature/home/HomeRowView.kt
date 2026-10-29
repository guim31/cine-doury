package fr.guim.cinedoury.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import fr.guim.cinedoury.core.data.home.CardStyle
import fr.guim.cinedoury.core.data.home.HomeRowType
import fr.guim.cinedoury.core.designsystem.component.FocusPivot
import fr.guim.cinedoury.core.designsystem.component.PosterCard
import fr.guim.cinedoury.core.designsystem.component.RowTitle
import fr.guim.cinedoury.core.designsystem.component.SkeletonCard
import fr.guim.cinedoury.core.designsystem.component.SkeletonLine
import fr.guim.cinedoury.core.designsystem.component.ThumbnailCard
import fr.guim.cinedoury.core.designsystem.component.rememberCineFocus
import fr.guim.cinedoury.core.designsystem.image.PrefetchImages
import fr.guim.cinedoury.core.designsystem.theme.CineSizes
import fr.guim.cinedoury.core.designsystem.theme.CineSpacing
import fr.guim.cinedoury.core.model.MediaItem
import fr.guim.cinedoury.core.ui.ImageSources
import fr.guim.cinedoury.core.ui.cardSubtitle
import fr.guim.cinedoury.core.ui.cardTitle
import fr.guim.cinedoury.core.ui.landscapeImage
import fr.guim.cinedoury.core.ui.posterImage
import fr.guim.cinedoury.core.ui.rememberArt

/**
 * A row of the home. It remembers the card that had the focus and gives it back when the focus
 * comes back to the row, even after the row left the screen (`UI.md` § 2); it scrolls so that the
 * focused card stays at the same place: second column for posters, first for thumbnails (`UI.md` § 3).
 *
 * @param restore attached to the card to give the focus back to.
 * @param next the row below, whose first images are loaded in advance.
 * @param shared the items whose poster flies to the details screen from this row.
 * @param previewScroll the row scrolled by this much from its start (screenshots).
 */
@Composable
internal fun HomeRowView(
    row: HomeRow,
    next: HomeRow?,
    shared: Set<String>,
    sources: ImageSources,
    restore: FocusRequester,
    onFocused: (MediaItem) -> Unit,
    onOpen: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    previewScroll: Dp = CineSpacing.none,
) {
    var last by rememberSaveable(row.key) { mutableIntStateOf(0) }
    val focused by rememberUpdatedState(onFocused)
    val poster = row.style == CardStyle.Poster
    val card = if (poster) CineSizes.posterNormal else CineSizes.thumbnail
    // The list starts at the edge of the rail, so that its cards never pass under it (essai réel 4, the
    // rail has no background): the pivot and the margin count from there.
    val start = ContentStart - CineSizes.railCollapsed
    val pivot = if (poster) start + card.width + CineSpacing.m else start
    val scrolled = with(LocalDensity.current) { previewScroll.roundToPx() }
    Column(
        modifier = modifier.testTag("row-${row.key}"),
        verticalArrangement = Arrangement.spacedBy(CineSpacing.rowTitleGap),
    ) {
        RowTitle(row.title(), Modifier.padding(start = ContentStart).testTag(HomePreview.rowTitle(row.key)))
        FocusPivot(offset = pivot) {
            LazyRow(
                state = rememberLazyListState(initialFirstVisibleItemScrollOffset = scrolled),
                modifier = Modifier.padding(start = CineSizes.railCollapsed).focusRestorer(restore),
                contentPadding = PaddingValues(start = start, end = ContentStart),
                horizontalArrangement = Arrangement.spacedBy(CineSpacing.m),
            ) {
                itemsIndexed(row.items, key = {
                    _,
                    item,
                    ->
                    item.id.value
                }, contentType = { _, _ -> row.style }) { index, item ->
                    val requester = if (index == last.coerceAtMost(row.items.lastIndex)) restore else null
                    val cardModifier =
                        Modifier.testTag(HomePreview.card(row.key, index)).onFocusChanged {
                            if (it.hasFocus) {
                                last = index
                                focused(item)
                            }
                        }
                    HomeCard(
                        item = item,
                        poster = poster,
                        sources = sources,
                        onClick = { onOpen(item) },
                        focusRequester = requester,
                        previewTag = HomePreview.card(row.key, index),
                        sharedKey = item.id.value.takeIf { it in shared },
                        modifier = cardModifier,
                    )
                }
            }
        }
    }
    if (next != null) {
        val nextPoster = next.style == CardStyle.Poster
        val images = next.items.take(PREFETCHED).mapNotNull { if (nextPoster) it.posterImage else it.landscapeImage }
        PrefetchImages(
            remember(images) {
                images.map(sources)
            },
            if (nextPoster) CineSizes.posterNormal else CineSizes.thumbnail,
        )
    }
}

/**
 * Cards loaded ahead in the next row: what the screen shows of it, and one more (fewer on a small
 * device, `ImageBudget`).
 */
private const val PREFETCHED = 6

@Composable
private fun HomeCard(
    item: MediaItem,
    poster: Boolean,
    sources: ImageSources,
    onClick: () -> Unit,
    focusRequester: FocusRequester?,
    previewTag: String,
    sharedKey: String?,
    modifier: Modifier = Modifier,
) {
    val focus = rememberCineFocus(previewTag)
    if (poster) {
        PosterCard(
            title = item.cardTitle,
            subtitle = item.cardSubtitle(),
            onClick = onClick,
            progress = item.progress,
            unwatched = item.isUnwatched,
            unwatchedCount = item.unwatchedEpisodes,
            focusRequester = focusRequester,
            focus = focus,
            sharedKey = sharedKey,
            image = rememberArt(item.posterImage, sources),
            modifier = modifier,
        )
    } else {
        ThumbnailCard(
            title = item.cardTitle,
            subtitle = item.cardSubtitle(),
            onClick = onClick,
            progress = item.progress,
            unwatched = item.isUnwatched && item.progress == null,
            unwatchedCount = item.unwatchedEpisodes,
            focusRequester = focusRequester,
            focus = focus,
            image = rememberArt(item.landscapeImage, sources),
            modifier = modifier,
        )
    }
}

@Composable
internal fun HomeRow.title(): String =
    when (type) {
        HomeRowType.Resume -> {
            stringResource(R.string.row_resume)
        }

        HomeRowType.NextUp -> {
            stringResource(R.string.row_next_up)
        }

        HomeRowType.Latest -> {
            library?.let { stringResource(R.string.row_latest_in, it.name) }
                ?: stringResource(R.string.row_latest)
        }

        HomeRowType.RecentlyReleased -> {
            stringResource(R.string.row_released)
        }
    }

/** A row not read yet: a title and thumbnails, of their exact size. */
@Composable
internal fun SkeletonRow(modifier: Modifier = Modifier) {
    Column(
        modifier.padding(start = ContentStart),
        verticalArrangement = Arrangement.spacedBy(CineSpacing.rowTitleGap),
    ) {
        SkeletonLine(CineSizes.thumbnail.width)
        Row(horizontalArrangement = Arrangement.spacedBy(CineSpacing.m)) {
            repeat(SKELETON_CARDS) { SkeletonCard(CineSizes.thumbnail) }
        }
    }
}

private const val SKELETON_CARDS = 5

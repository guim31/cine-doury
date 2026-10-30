package fr.guim.cinedoury.feature.details

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import fr.guim.cinedoury.core.designsystem.component.CineIcons
import fr.guim.cinedoury.core.designsystem.component.CineTab
import fr.guim.cinedoury.core.designsystem.component.EpisodeRow
import fr.guim.cinedoury.core.designsystem.component.FocusPivot
import fr.guim.cinedoury.core.designsystem.component.PosterCard
import fr.guim.cinedoury.core.designsystem.component.PosterSize
import fr.guim.cinedoury.core.designsystem.component.RowTitle
import fr.guim.cinedoury.core.designsystem.component.SecondaryButton
import fr.guim.cinedoury.core.designsystem.component.rememberCineFocus
import fr.guim.cinedoury.core.designsystem.theme.CineSpacing
import fr.guim.cinedoury.core.model.CreditType
import fr.guim.cinedoury.core.model.ItemImages
import fr.guim.cinedoury.core.model.ItemKind
import fr.guim.cinedoury.core.model.MediaItem
import fr.guim.cinedoury.core.ui.ImageSources
import fr.guim.cinedoury.core.ui.cardSubtitle
import fr.guim.cinedoury.core.ui.cardTitle
import fr.guim.cinedoury.core.ui.landscapeImage
import fr.guim.cinedoury.core.ui.posterImage
import fr.guim.cinedoury.core.ui.quickMenu
import fr.guim.cinedoury.core.ui.rememberArt

/** A row of cards under the hero: its key, its title and its cards. */
@Immutable
internal data class DetailsRow(
    val key: String,
    val title: String,
    val items: List<MediaItem>,
    /** People: small portraits, the name and the role always shown. */
    val people: Boolean = false,
)

internal fun rowKey(row: String) = "row:$row"

/** The key of the first row of cards, where a person or a collection puts the focus. */
internal fun DetailsState.firstRowKey(): String? =
    when (item.kind) {
        ItemKind.Person -> {
            ROW_MOVIES.takeIf { filmography.any { it.kind == ItemKind.Movie } }
                ?: ROW_SERIES.takeIf { filmography.isNotEmpty() }
        }

        ItemKind.Collection -> {
            ROW_SAGA.takeIf { sagaItems.isNotEmpty() }
        }

        else -> {
            null
        }
    }

private const val ROW_CAST = "cast"
private const val ROW_SAGA = "saga"
private const val ROW_SIMILAR = "similar"
private const val ROW_MOVIES = "movies"
private const val ROW_SERIES = "series"

/** The rows of a details screen: cast, saga, similar titles, the works of a person. */
@Composable
internal fun DetailsState.detailRows(): List<DetailsRow> {
    val cast =
        item.people
            .filter { it.type == CreditType.Actor || it.type == CreditType.GuestStar }
            .map { credit ->
                MediaItem(
                    id = credit.id,
                    kind = ItemKind.Person,
                    title = credit.name,
                    overview = credit.role,
                    images = ItemImages(primary = credit.image),
                )
            }
    return listOfNotNull(
        DetailsRow(ROW_MOVIES, stringResource(R.string.row_movies), filmography.filter { it.kind == ItemKind.Movie }),
        DetailsRow(ROW_SERIES, stringResource(R.string.row_series), filmography.filter { it.kind == ItemKind.Series }),
        DetailsRow(ROW_CAST, stringResource(R.string.row_cast), cast, people = true),
        saga?.let {
            DetailsRow(
                ROW_SAGA,
                if (item.kind ==
                    ItemKind.Collection
                ) {
                    stringResource(R.string.row_collection)
                } else {
                    it.title
                },
                sagaItems,
            )
        },
        DetailsRow(ROW_SIMILAR, stringResource(R.string.row_similar), similar),
    ).filter { it.items.isNotEmpty() }
}

internal fun LazyListScope.rows(
    state: DetailsState,
    sources: ImageSources,
    onOpen: (MediaItem) -> Unit,
    requesters: FocusRequesters,
    onFocused: (String) -> Unit,
) {
    item(key = "rows", contentType = "rows") {
        Column(verticalArrangement = Arrangement.spacedBy(CineSpacing.l)) {
            // A title has one shared poster on the screen: the hero's, else its first card.
            val seen = mutableSetOf(state.item.id.value)
            state.detailRows().forEach { row ->
                val shared =
                    row.items
                        .map { it.id.value }
                        .filter(seen::add)
                        .toSet()
                CardRow(row, state, sources, onOpen, requesters, onFocused, shared)
            }
        }
    }
}

/**
 * A row of cards. It remembers the card that had the focus and gives it back when the focus comes
 * back to the row, even after another screen (`UI.md` § 2); the focused card stays in the second
 * column (`UI.md` § 3).
 */
@Composable
private fun CardRow(
    row: DetailsRow,
    state: DetailsState,
    sources: ImageSources,
    onOpen: (MediaItem) -> Unit,
    requesters: FocusRequesters,
    onFocused: (String) -> Unit,
    shared: Set<String>,
) {
    var last by rememberSaveable(row.key) { mutableIntStateOf(0) }
    val key = rowKey(row.key)
    val restore = requesters[key]
    val size = if (row.people) PosterSize.Small else PosterSize.Normal
    Column(verticalArrangement = Arrangement.spacedBy(CineSpacing.rowTitleGap), modifier = Modifier.testTag(key)) {
        RowTitle(row.title, Modifier.padding(start = CineSpacing.safeHorizontal))
        FocusPivot(offset = CineSpacing.safeHorizontal + size.dp.width + CineSpacing.m) {
            LazyRow(
                state = rememberLazyListState(),
                modifier = Modifier.focusRestorer(restore),
                contentPadding = PaddingValues(horizontal = CineSpacing.safeHorizontal),
                horizontalArrangement = Arrangement.spacedBy(CineSpacing.m),
            ) {
                itemsIndexed(row.items, key = {
                    _,
                    item,
                    ->
                    item.id.value
                }, contentType = { _, _ -> row.key }) { index, item ->
                    val tag = DetailsPreview.card(row.key, index)
                    PosterCard(
                        title = item.cardTitle,
                        subtitle = if (row.people) item.overview else item.cardSubtitle(),
                        size = size,
                        progress = item.progress,
                        unwatched = item.isUnwatched,
                        unwatchedCount = item.unwatchedEpisodes,
                        alwaysShowTitle = row.people,
                        // The film of the screen is in its saga: it is shown, not opened again.
                        onClick = { if (item.id != state.item.id) onOpen(item) },
                        focusRequester = restore.takeIf { index == last.coerceAtMost(row.items.lastIndex) },
                        focus = rememberCineFocus(tag),
                        sharedKey = item.id.value.takeIf { !row.people && it in shared },
                        image = rememberArt(item.posterImage, sources),
                        modifier =
                            Modifier
                                .testTag(
                                    tag,
                                ).quickMenu(item, anchor = "${row.key}/${item.id.value}")
                                .onFocusChanged {
                                    if (it.hasFocus) {
                                        last = index
                                        onFocused(key)
                                    }
                                },
                    )
                }
            }
        }
    }
}

/**
 * The seasons of a series in tabs (none for a single season: its episodes come straight under the
 * hero), « Marquer la saison comme vue », then the episodes of the season shown.
 */
internal fun LazyListScope.episodes(
    state: DetailsState,
    sources: ImageSources,
    actions: DetailsActions,
    onOpen: (MediaItem) -> Unit,
    requesters: FocusRequesters,
    onFocused: (String) -> Unit,
) {
    if (state.item.kind != ItemKind.Series || state.episodes.isNullOrEmpty()) return
    item(key = "seasons", contentType = "seasons") {
        Seasons(state, actions, requesters, onFocused)
    }
    val shown = state.shownEpisodes
    itemsIndexed(shown, key = {
        _,
        episode,
        ->
        episode.id.value
    }, contentType = { _, _ -> "episode" }) { index, episode ->
        val key = "episode:${episode.id.value}"
        val watched = episode.userData?.played == true
        EpisodeRow(
            title = episode.numberedTitle(),
            metadata = episode.episodeLine(),
            overview = episode.overview,
            progress = episode.progress,
            watched = watched,
            watchedLabel = stringResource(if (watched) R.string.action_unwatched else R.string.action_watched),
            onToggleWatched = { actions.toggleWatched(episode) },
            onClick = { onOpen(episode) },
            focusRequester = requesters[key],
            focus = rememberCineFocus(DetailsPreview.episode(index)),
            image = rememberArt(episode.landscapeImage, sources),
            modifier = Modifier.padding(horizontal = CineSpacing.safeHorizontal),
            rowModifier =
                Modifier
                    .testTag(
                        DetailsPreview.episode(index),
                    ).quickMenu(episode)
                    .onFocusChanged { if (it.hasFocus) onFocused(key) },
        )
    }
}

@Composable
private fun Seasons(
    state: DetailsState,
    actions: DetailsActions,
    requesters: FocusRequesters,
    onFocused: (String) -> Unit,
) {
    val season = state.seasons.firstOrNull { it.id == state.season }
    val watched = if (state.flattened) state.item.userData?.played == true else season?.userData?.played == true
    // Entering the row lands on the season shown.
    val selected = requesters["season:${state.season?.value}"]
    Row(
        modifier =
            Modifier
                .padding(horizontal = CineSpacing.safeHorizontal)
                .focusRestorer(selected)
                .focusGroup(),
        horizontalArrangement = Arrangement.spacedBy(CineSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.flattened) {
            RowTitle(stringResource(R.string.row_episodes), Modifier.padding(end = CineSpacing.m))
        } else {
            state.seasons.forEachIndexed { index, item ->
                val key = "season:${item.id.value}"
                CineTab(
                    label = item.title,
                    selected = item.id == state.season,
                    onClick = { actions.selectSeason(item.id) },
                    focusRequester = requesters[key],
                    focus = rememberCineFocus(DetailsPreview.season(index)),
                    modifier =
                        Modifier
                            .testTag(
                                DetailsPreview.season(index),
                            ).quickMenu(item)
                            .onFocusChanged { if (it.hasFocus) onFocused(key) },
                )
            }
        }
        val target = if (state.flattened) state.item else season
        if (target != null && !state.flattened) {
            SecondaryButton(
                text =
                    stringResource(
                        if (watched) R.string.action_season_unwatched else R.string.action_season_watched,
                    ),
                icon = if (watched) CineIcons.Unwatched else CineIcons.WatchedAll,
                onClick = { actions.toggleWatched(target) },
                focus = rememberCineFocus(DetailsPreview.SEASON_WATCHED),
                modifier = Modifier.padding(start = CineSpacing.m).testTag(DetailsPreview.SEASON_WATCHED),
            )
        }
    }
}

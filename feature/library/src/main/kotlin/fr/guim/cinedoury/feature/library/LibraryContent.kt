package fr.guim.cinedoury.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import fr.guim.cinedoury.core.data.library.LibraryTab
import fr.guim.cinedoury.core.data.library.LibraryView
import fr.guim.cinedoury.core.data.library.PagedItems
import fr.guim.cinedoury.core.designsystem.component.CineIcons
import fr.guim.cinedoury.core.designsystem.component.FocusPivot
import fr.guim.cinedoury.core.designsystem.component.InitialFocus
import fr.guim.cinedoury.core.designsystem.component.MediaListRow
import fr.guim.cinedoury.core.designsystem.component.MessagePanel
import fr.guim.cinedoury.core.designsystem.component.PosterCard
import fr.guim.cinedoury.core.designsystem.component.PosterSize
import fr.guim.cinedoury.core.designsystem.component.SelectionMark
import fr.guim.cinedoury.core.designsystem.component.SkeletonCard
import fr.guim.cinedoury.core.designsystem.component.SkeletonListRow
import fr.guim.cinedoury.core.designsystem.component.ThumbnailCard
import fr.guim.cinedoury.core.designsystem.component.rememberCineFocus
import fr.guim.cinedoury.core.designsystem.theme.CineSizes
import fr.guim.cinedoury.core.designsystem.theme.CineSpacing
import fr.guim.cinedoury.core.model.ItemFilters
import fr.guim.cinedoury.core.model.ItemId
import fr.guim.cinedoury.core.model.LibraryKind
import fr.guim.cinedoury.core.model.LibraryScan
import fr.guim.cinedoury.core.model.MediaItem
import fr.guim.cinedoury.core.model.SortField
import fr.guim.cinedoury.core.ui.ImageSources
import fr.guim.cinedoury.core.ui.QuickMenuState
import fr.guim.cinedoury.core.ui.SourceErrorPanel
import fr.guim.cinedoury.core.ui.cardSubtitle
import fr.guim.cinedoury.core.ui.cardTitle
import fr.guim.cinedoury.core.ui.landscapeImage
import fr.guim.cinedoury.core.ui.metadataLine
import fr.guim.cinedoury.core.ui.posterImage
import fr.guim.cinedoury.core.ui.quickMenu
import fr.guim.cinedoury.core.ui.rememberArt

/** What a library screen asks of its view model; a no-op for screenshots. */
@Suppress("TooManyFunctions") // One function per gesture of the screen.
interface LibraryActions {
    fun selectTab(tab: LibraryTab)

    fun setView(view: LibraryView)

    fun sortBy(field: SortField)

    fun setFilters(filters: ItemFilters)

    fun clearFilters()

    fun toggleGroupCollections()

    fun openGenre(genre: String)

    fun onVisible(index: Int)

    fun onFocused(index: Int)

    /**
     * The item focused last, where a grid opens: kept out of the state of the screen, so that a step
     * of the focus does not recompose the screen and every card of the grid (perf/fluidite). Null: the
     * remembered [LibraryState.position].
     */
    val focusedIndex: Int? get() = null

    fun jumpTo(letter: String)

    fun retry()

    // Management (J6); nothing by default (screenshots).

    fun scan(mode: LibraryScan) {}

    /** Selection mode, with [item] ticked (OK held on it), or none (from « ⋯ »). */
    fun startSelection(item: MediaItem?) {}

    fun toggleSelected(item: MediaItem) {}

    fun endSelection() {}

    /** Marks the selection watched or not. */
    fun markSelection(played: Boolean) {}

    fun refreshSelection() {}

    fun dismissManageError() {}
}

/** The content of the current tab: items, genres or episodes, or a message when there is none. */
@Composable
internal fun LibraryContent(
    state: LibraryUiState,
    sources: ImageSources,
    actions: LibraryActions,
    menu: QuickMenuState,
    onOpen: (MediaItem) -> Unit,
    onFocused: (MediaItem) -> Unit,
    jump: Jump?,
    onJumped: () -> Unit,
    contentEnd: Dp,
    autoFocus: Boolean,
) {
    val browse = state.browse
    val items = state.items
    val error = state.error ?: items.error
    when {
        !state.restored -> {
            SkeletonGrid(LibraryView.Posters, contentEnd)
        }

        browse.tab == LibraryTab.Genres -> {
            GenreGrid(state.options?.genres, actions::openGenre, contentEnd)
        }

        browse.tab == LibraryTab.NextUp -> {
            val episodes = state.nextUp
            if (episodes == null) {
                SkeletonGrid(LibraryView.Thumbnails, contentEnd)
            } else {
                EpisodeGrid(episodes, sources, menu, onOpen, onFocused, contentEnd)
            }
        }

        items.total == null && error != null -> {
            SourceErrorPanel(error = error, onRetry = actions::retry, modifier = Modifier.padding(top = CineSpacing.l))
        }

        items.total == null -> {
            SkeletonGrid(browse.view, contentEnd)
        }

        items.total == 0 -> {
            val filtered = !browse.filters.isEmpty && browse.tab == LibraryTab.All
            MessagePanel(
                title = stringResource(if (filtered) R.string.empty_title else R.string.empty_library),
                message = if (filtered) stringResource(R.string.empty_message) else "",
                actionLabel = stringResource(R.string.action_clear),
                onAction = actions::clearFilters,
                icon = CineIcons.Filter,
                modifier = Modifier.padding(top = CineSpacing.l),
            )
        }

        else -> {
            // A new query starts from scratch: its own scroll state and focus memory.
            key(items.generation, browse.view) {
                ItemGrid(
                    items = items,
                    selection = state.selection,
                    menu = menu,
                    view = if (browse.tab == LibraryTab.Collections) LibraryView.Posters else browse.view,
                    position = actions.focusedIndex ?: browse.position,
                    sources = sources,
                    actions = actions,
                    onOpen = onOpen,
                    onFocused = onFocused,
                    jump = jump,
                    onJumped = onJumped,
                    contentEnd = contentEnd,
                    autoFocus = autoFocus,
                )
            }
        }
    }
}

private val LibraryView.cell: DpSize
    get() =
        when (this) {
            LibraryView.SmallPosters -> CineSizes.posterSmall
            LibraryView.Posters -> CineSizes.posterNormal
            LibraryView.LargePosters -> CineSizes.posterLarge
            LibraryView.Thumbnails -> CineSizes.thumbnail
            LibraryView.List -> CineSizes.listRowPoster
        }

private val LibraryView.posterSize: PosterSize
    get() =
        when (this) {
            LibraryView.SmallPosters -> PosterSize.Small
            LibraryView.LargePosters -> PosterSize.Large
            else -> PosterSize.Normal
        }

/** Where the focused row of a grid stands, from the top of the list. */
private const val GRID_PIVOT = 0.25f

/**
 * Every item of the query, laid out at once: the places not read yet are skeletons, read as they
 * come into view. It opens on the item focused last time (`UI.md` § 3) and scrolls to a letter of
 * the A–Z jump.
 */
@Composable
private fun ItemGrid(
    items: PagedItems,
    selection: Set<ItemId>?,
    menu: QuickMenuState,
    view: LibraryView,
    position: Int,
    sources: ImageSources,
    actions: LibraryActions,
    onOpen: (MediaItem) -> Unit,
    onFocused: (MediaItem) -> Unit,
    jump: Jump?,
    onJumped: () -> Unit,
    contentEnd: Dp,
    autoFocus: Boolean,
) {
    val total = items.total ?: 0
    // Where the grid opens: read once, the grid then keeps its own place.
    val start = remember { position.coerceIn(0, (total - 1).coerceAtLeast(0)) }
    val target = remember { FocusRequester() }
    val focusIndex = jump?.index ?: start
    val padding = PaddingValues(top = CineSpacing.s, end = contentEnd, bottom = CineSpacing.xxl, start = CineSpacing.xs)
    val focused by rememberUpdatedState(onFocused)

    @Composable
    fun Item(index: Int) {
        val item = items[index]
        LaunchedEffect(index, item == null) { if (item == null) actions.onVisible(index) }
        if (item == null) {
            if (view == LibraryView.List) SkeletonListRow() else SkeletonCard(view.cell)
        } else {
            GridItem(
                item = item,
                index = index,
                view = view,
                sources = sources,
                // In a selection, OK ticks the item instead of opening it.
                onClick = { if (selection != null) actions.toggleSelected(item) else onOpen(item) },
                focusRequester = if (index == focusIndex) target else null,
                selected = selection?.let { item.id in it },
                modifier =
                    Modifier
                        .testTag(
                            LibraryPreview.item(index),
                        ).quickMenu(menu, item)
                        .onFocusChanged {
                            if (it.hasFocus) {
                                actions.onFocused(index)
                                focused(item)
                            }
                        },
            )
        }
    }

    FocusPivot(fraction = GRID_PIVOT) {
        if (view == LibraryView.List) {
            val listState = rememberLazyListState(initialFirstVisibleItemIndex = start)
            LaunchedEffect(jump) { jump?.let { listState.scrollToItem(it.index) } }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().focusRestorer(target).testTag("library-items"),
                contentPadding = padding,
                verticalArrangement = Arrangement.spacedBy(CineSpacing.xs),
            ) {
                items(total, contentType = { if (items[it] == null) "skeleton" else "item" }) { Item(it) }
            }
        } else {
            val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = start)
            LaunchedEffect(jump) { jump?.let { gridState.scrollToItem(it.index) } }
            LazyVerticalGrid(
                columns = GridCells.FixedSize(view.cell.width),
                state = gridState,
                modifier = Modifier.fillMaxSize().focusRestorer(target).testTag("library-items"),
                contentPadding = padding,
                horizontalArrangement = Arrangement.spacedBy(CineSpacing.m),
                verticalArrangement = Arrangement.spacedBy(CineSpacing.m),
            ) {
                items(total, contentType = { if (items[it] == null) "skeleton" else "item" }) { Item(it) }
            }
        }
    }
    // The remembered item when the list opens, or the letter just chosen, once its page is read.
    val loaded = items[focusIndex] != null
    var focusedOnce by remember { mutableStateOf(false) }
    val wanted = jump != null || (autoFocus && !focusedOnce)
    LaunchedEffect(focusIndex, loaded, jump, autoFocus) {
        if (loaded && wanted) {
            withFrameNanos { }
            if (runCatching { target.requestFocus() }.isSuccess) {
                focusedOnce = true
                if (jump != null) onJumped()
            }
        }
    }
}

@Composable
private fun GridItem(
    item: MediaItem,
    index: Int,
    view: LibraryView,
    sources: ImageSources,
    onClick: () -> Unit,
    focusRequester: FocusRequester?,
    modifier: Modifier = Modifier,
    selected: Boolean? = null,
) {
    // The box of the selection over the card, in its top left corner.
    Box(modifier) {
        GridCard(item, index, view, sources, onClick, focusRequester)
        if (selected != null) SelectionMark(selected, Modifier.align(Alignment.TopStart))
    }
}

@Composable
private fun GridCard(
    item: MediaItem,
    index: Int,
    view: LibraryView,
    sources: ImageSources,
    onClick: () -> Unit,
    focusRequester: FocusRequester?,
) {
    val focus = rememberCineFocus(LibraryPreview.item(index))
    when (view) {
        LibraryView.List -> {
            MediaListRow(
                title = item.cardTitle,
                metadata = item.metadataLine(),
                overview = item.overview,
                progress = item.progress,
                unwatched = item.isUnwatched,
                unwatchedCount = item.unwatchedEpisodes,
                onClick = onClick,
                focusRequester = focusRequester,
                focus = focus,
                image = rememberArt(item.posterImage, sources),
            )
        }

        LibraryView.Thumbnails -> {
            ThumbnailCard(
                title = item.cardTitle,
                subtitle = item.cardSubtitle(),
                progress = item.progress,
                unwatched = item.isUnwatched,
                unwatchedCount = item.unwatchedEpisodes,
                onClick = onClick,
                focusRequester = focusRequester,
                focus = focus,
                image = rememberArt(item.landscapeImage, sources),
            )
        }

        else -> {
            PosterCard(
                title = item.cardTitle,
                subtitle = item.cardSubtitle(),
                size = view.posterSize,
                progress = item.progress,
                unwatched = item.isUnwatched,
                unwatchedCount = item.unwatchedEpisodes,
                onClick = onClick,
                focusRequester = focusRequester,
                focus = focus,
                sharedKey = item.id.value,
                image = rememberArt(item.posterImage, sources),
            )
        }
    }
}

/** Skeletons of the exact shape of the view, while the first page is read. */
@Composable
private fun SkeletonGrid(
    view: LibraryView,
    contentEnd: Dp,
) {
    val padding = PaddingValues(top = CineSpacing.s, end = contentEnd, start = CineSpacing.xs)
    if (view == LibraryView.List) {
        LazyColumn(contentPadding = padding, verticalArrangement = Arrangement.spacedBy(CineSpacing.xs)) {
            items(SKELETONS) { SkeletonListRow() }
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.FixedSize(view.cell.width),
            contentPadding = padding,
            horizontalArrangement = Arrangement.spacedBy(CineSpacing.m),
            verticalArrangement = Arrangement.spacedBy(CineSpacing.m),
            modifier = Modifier.testTag("library-skeletons"),
            userScrollEnabled = false,
        ) {
            items(SKELETONS) { SkeletonCard(view.cell) }
        }
    }
}

private const val SKELETONS = 15

/** The « Genres » tab: a tile per genre, which opens « Tout » filtered on it. */
@Composable
private fun GenreGrid(
    genres: List<String>?,
    onGenre: (String) -> Unit,
    contentEnd: Dp,
) {
    if (genres == null) {
        SkeletonGrid(LibraryView.Thumbnails, contentEnd)
        return
    }
    val first = remember { FocusRequester() }
    LazyVerticalGrid(
        columns = GridCells.FixedSize(CineSizes.thumbnail.width),
        contentPadding =
            PaddingValues(
                top = CineSpacing.s,
                end = contentEnd,
                bottom = CineSpacing.xxl,
                start = CineSpacing.xs,
            ),
        horizontalArrangement = Arrangement.spacedBy(CineSpacing.m),
        verticalArrangement = Arrangement.spacedBy(CineSpacing.m),
        modifier = Modifier.fillMaxSize().testTag("library-genres"),
    ) {
        itemsIndexed(genres, key = { _, genre -> genre }) { index, genre ->
            ThumbnailCard(
                title = genre,
                onClick = { onGenre(genre) },
                focusRequester = if (index == 0) first else null,
                focus = rememberCineFocus(LibraryPreview.item(index)),
            )
        }
    }
    InitialFocus(first, key = genres.size, enabled = genres.isNotEmpty())
}

/** The « À suivre » tab of a series library: the next episode of each series in progress. */
@Composable
private fun EpisodeGrid(
    episodes: List<MediaItem>,
    sources: ImageSources,
    menu: QuickMenuState,
    onOpen: (MediaItem) -> Unit,
    onFocused: (MediaItem) -> Unit,
    contentEnd: Dp,
) {
    val first = remember { FocusRequester() }
    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.FixedSize(CineSizes.thumbnail.width),
            contentPadding =
                PaddingValues(
                    top = CineSpacing.s,
                    end = contentEnd,
                    bottom = CineSpacing.xxl,
                    start = CineSpacing.xs,
                ),
            horizontalArrangement = Arrangement.spacedBy(CineSpacing.m),
            verticalArrangement = Arrangement.spacedBy(CineSpacing.m),
            modifier = Modifier.fillMaxSize().testTag("library-next-up"),
        ) {
            itemsIndexed(episodes, key = { _, item -> item.id.value }) { index, item ->
                ThumbnailCard(
                    title = item.cardTitle,
                    subtitle = item.cardSubtitle(),
                    progress = item.progress,
                    onClick = { onOpen(item) },
                    focusRequester = if (index == 0) first else null,
                    focus = rememberCineFocus(LibraryPreview.item(index)),
                    image = rememberArt(item.landscapeImage, sources),
                    modifier = Modifier.quickMenu(menu, item).onFocusChanged { if (it.hasFocus) onFocused(item) },
                )
            }
        }
    }
    InitialFocus(first, key = episodes.size, enabled = episodes.isNotEmpty())
}

/** Whether this library lists films, which can be grouped by collection. */
internal val LibraryUiState.groupable: Boolean get() = library?.kind == LibraryKind.Movies

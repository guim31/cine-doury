package fr.guim.cinedoury.feature.library.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.guim.cinedoury.core.data.SessionManager
import fr.guim.cinedoury.core.data.library.LibraryTab
import fr.guim.cinedoury.core.data.library.icon
import fr.guim.cinedoury.core.designsystem.component.CineIcons
import fr.guim.cinedoury.core.designsystem.mobile.MobileChip
import fr.guim.cinedoury.core.designsystem.mobile.MobileIconButton
import fr.guim.cinedoury.core.designsystem.mobile.MobileListRow
import fr.guim.cinedoury.core.designsystem.mobile.MobileMessage
import fr.guim.cinedoury.core.designsystem.mobile.MobileSecondaryButton
import fr.guim.cinedoury.core.designsystem.mobile.MobileSheet
import fr.guim.cinedoury.core.designsystem.mobile.MobileSheetLine
import fr.guim.cinedoury.core.designsystem.mobile.MobileSheetLines
import fr.guim.cinedoury.core.designsystem.mobile.MobileSkeleton
import fr.guim.cinedoury.core.designsystem.mobile.MobileTextButton
import fr.guim.cinedoury.core.designsystem.mobile.MobileTopBar
import fr.guim.cinedoury.core.designsystem.mobile.mobileGutter
import fr.guim.cinedoury.core.designsystem.theme.CineSpacing
import fr.guim.cinedoury.core.designsystem.theme.CineTheme
import fr.guim.cinedoury.core.designsystem.theme.MobileSizes
import fr.guim.cinedoury.core.model.ItemFilters
import fr.guim.cinedoury.core.model.LibraryId
import fr.guim.cinedoury.core.model.SortField
import fr.guim.cinedoury.core.model.WatchStatus
import fr.guim.cinedoury.core.ui.CollectEvents
import fr.guim.cinedoury.core.ui.ImageSources
import fr.guim.cinedoury.core.ui.RailState
import fr.guim.cinedoury.core.ui.imageSource
import fr.guim.cinedoury.core.ui.mobile.MobileItemCard
import fr.guim.cinedoury.core.ui.mobile.MobileQuickMenu
import fr.guim.cinedoury.core.ui.mobile.MobileQuickMenuHost
import fr.guim.cinedoury.core.ui.mobile.MobileSourceError
import fr.guim.cinedoury.core.ui.mobile.rememberMobileQuickMenu
import fr.guim.cinedoury.core.ui.vector
import fr.guim.cinedoury.feature.library.Jump
import fr.guim.cinedoury.feature.library.LibrariesViewModel
import fr.guim.cinedoury.feature.library.LibraryActions
import fr.guim.cinedoury.feature.library.LibraryNavigation
import fr.guim.cinedoury.feature.library.LibraryUiState
import fr.guim.cinedoury.feature.library.LibraryViewModel
import fr.guim.cinedoury.feature.library.R
import fr.guim.cinedoury.feature.library.decadeLabel
import fr.guim.cinedoury.feature.library.directionLabel
import fr.guim.cinedoury.feature.library.label
import fr.guim.cinedoury.feature.library.summary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
import fr.guim.cinedoury.core.ui.R as UiR

/** Tags of the libraries of the phone, for tests and screenshots. */
object MobileLibraryTags {
    const val LIBRARIES = "libraries"
    const val GRID = "library-grid"
    const val SORT = "library-sort"
    const val FILTERS = "library-filters"
    const val ALPHABET = "library-alphabet"
    const val GENRES = "library-genres"

    fun library(id: String) = "library-$id"

    fun tab(tab: LibraryTab) = "library-tab-${tab.name.lowercase()}"

    fun card(index: Int) = "library-card-$index"

    fun letter(letter: String) = "library-letter-$letter"
}

@Composable
fun MobileLibrariesRoute(
    onOpen: (LibraryId) -> Unit,
    viewModel: LibrariesViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MobileLibrariesScreen(state, onOpen)
}

/** The tab « Bibliothèques » (M1): the libraries of the profile, in its order, with their icons. */
@Composable
fun MobileLibrariesScreen(
    state: RailState?,
    onOpen: (LibraryId) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(
                CineTheme.colors.background,
            ).statusBarsPadding()
            .testTag(MobileLibraryTags.LIBRARIES),
    ) {
        Text(
            stringResource(UiR.string.mobile_libraries_title),
            style = CineTheme.typography.display,
            modifier = Modifier.padding(horizontal = MobileSizes.gutter, vertical = CineSpacing.s),
        )
        when {
            state == null -> {
                repeat(SKELETON_LINES) {
                    MobileSkeleton(
                        Modifier
                            .padding(
                                horizontal = MobileSizes.gutter,
                                vertical = CineSpacing.xs,
                            ).fillMaxWidth()
                            .aspectRatio(SKELETON_RATIO),
                    )
                }
            }

            state.libraries.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    MobileMessage(
                        stringResource(UiR.string.mobile_libraries_empty_title),
                        stringResource(UiR.string.mobile_libraries_empty_message),
                    )
                }
            }

            else -> {
                LazyColumn {
                    items(state.libraries, key = { it.id.value }) { library ->
                        MobileListRow(
                            title = library.name,
                            icon = library.icon(state.icons).vector,
                            onClick = { onOpen(library.id) },
                            modifier = Modifier.testTag(MobileLibraryTags.library(library.id.value)),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MobileLibraryRoute(
    library: LibraryId,
    navigation: LibraryNavigation,
    onBack: () -> Unit,
    viewModel: LibraryViewModel = koinViewModel(key = library.value) { parametersOf(library) },
    sessions: SessionManager = koinInject(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectEvents(viewModel.sessionEnded) { navigation.sessionEnded() }
    MobileLibraryScreen(state, sessions::imageSource, viewModel, navigation, onBack, viewModel.jumps.flow)
}

/** Which sheet of the library is open. */
private enum class LibrarySheet { Sort, Filters }

/**
 * A library on the phone (M1, `UI.md` § 9): its tabs, then a grid that fits as many posters as the
 * width allows, scrolled with the finger; « Trier » and « Filtrer » in sheets; the A–Z bar along the
 * grid when the titles are in order (a tap or a slide on a letter). The tab, the sort and the filters
 * are the TV's (the same view model, remembered per profile).
 */
@Composable
fun MobileLibraryScreen(
    state: LibraryUiState,
    sources: ImageSources,
    actions: LibraryActions,
    navigation: LibraryNavigation,
    onBack: (() -> Unit)?,
    jumps: Flow<Jump> = emptyFlow(),
    menu: MobileQuickMenu = rememberMobileQuickMenu(),
    previewSheet: Boolean = false,
) {
    var sheet by remember { mutableStateOf(if (previewSheet) LibrarySheet.Filters else null) }
    val browse = state.browse
    Column(Modifier.fillMaxSize().background(CineTheme.colors.background)) {
        MobileTopBar(
            title = state.library?.name.orEmpty(),
            onBack = onBack,
            backLabel = stringResource(UiR.string.action_back),
        ) {
            if (browse.tab == LibraryTab.All || browse.tab == LibraryTab.Collections) {
                MobileIconButton(CineIcons.Sort, stringResource(R.string.action_sort), {
                    sheet = LibrarySheet.Sort
                }, Modifier.testTag(MobileLibraryTags.SORT))
                MobileIconButton(
                    CineIcons.Filter,
                    stringResource(R.string.action_filters),
                    { sheet = LibrarySheet.Filters },
                    Modifier.testTag(MobileLibraryTags.FILTERS),
                    selected = browse.filters.count > 0,
                )
            }
        }
        if (state.tabs.size > 1) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = MobileSizes.gutter),
                horizontalArrangement = Arrangement.spacedBy(CineSpacing.xs),
            ) {
                items(state.tabs, key = { it.name }) { tab ->
                    MobileChip(
                        tab.label(),
                        browse.tab == tab,
                        { actions.selectTab(tab) },
                        Modifier.testTag(MobileLibraryTags.tab(tab)),
                    )
                }
            }
        }
        if (browse.filters.count > 0) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = MobileSizes.gutter),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    browse.filters.summary(),
                    style = CineTheme.typography.caption,
                    color = CineTheme.colors.text2,
                    modifier = Modifier.weight(1f),
                )
                MobileTextButton(stringResource(R.string.action_clear), actions::clearFilters)
            }
        }
        Box(Modifier.weight(1f)) {
            LibraryContent(state, sources, actions, navigation, menu, jumps)
        }
    }
    when (sheet) {
        LibrarySheet.Sort -> SortSheet(state, actions) { sheet = null }
        LibrarySheet.Filters -> FilterSheet(state, actions) { sheet = null }
        null -> Unit
    }
    MobileQuickMenuHost(menu, navigation)
}

@Composable
private fun LibraryContent(
    state: LibraryUiState,
    sources: ImageSources,
    actions: LibraryActions,
    navigation: LibraryNavigation,
    menu: MobileQuickMenu,
    jumps: Flow<Jump>,
) {
    val browse = state.browse
    val error = state.error ?: state.items.error
    when {
        error != null && state.items.loadedCount == 0 -> {
            Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { MobileSourceError(error, actions::retry) }
        }

        browse.tab == LibraryTab.Genres -> {
            GenreChips(state.options?.genres, actions::openGenre)
        }

        browse.tab == LibraryTab.NextUp -> {
            NextUpList(state, sources, navigation, menu)
        }

        state.items.total == 0 -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (browse.filters.count > 0) {
                    MobileMessage(stringResource(R.string.empty_title), stringResource(R.string.empty_message)) {
                        MobileSecondaryButton(stringResource(R.string.action_clear), actions::clearFilters)
                    }
                } else {
                    MobileMessage(stringResource(R.string.empty_library), null)
                }
            }
        }

        else -> {
            ItemGrid(state, sources, actions, navigation, menu, jumps)
        }
    }
}

@Composable
private fun ItemGrid(
    state: LibraryUiState,
    sources: ImageSources,
    actions: LibraryActions,
    navigation: LibraryNavigation,
    menu: MobileQuickMenu,
    jumps: Flow<Jump>,
) {
    val total = state.items.total ?: GRID_PLACEHOLDERS
    // The grid opens where it was left (the position the view model remembers, as on the TV).
    val grid =
        rememberLazyGridState(initialFirstVisibleItemIndex = state.browse.position.coerceIn(0, maxOf(total - 1, 0)))
    LaunchedEffect(grid) {
        snapshotFlow {
            grid.firstVisibleItemIndex to (
                grid.layoutInfo.visibleItemsInfo
                    .lastOrNull()
                    ?.index ?: 0
            )
        }.collect { (first, last) ->
            actions.onVisible(first)
            actions.onVisible(last)
            actions.onFocused(first)
        }
    }
    LaunchedEffect(jumps) { jumps.collect { grid.scrollToItem(it.index) } }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val gutter = mobileGutter(maxWidth)
        val alphabet = state.alphabetical
        Row(Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(MobileSizes.gridPosterMin),
                state = grid,
                modifier = Modifier.weight(1f).testTag(MobileLibraryTags.GRID),
                contentPadding =
                    PaddingValues(
                        start = gutter,
                        end = if (alphabet) CineSpacing.xs else gutter,
                        top = CineSpacing.xs,
                        bottom = CineSpacing.m,
                    ),
                horizontalArrangement = Arrangement.spacedBy(CineSpacing.xs),
                verticalArrangement = Arrangement.spacedBy(CineSpacing.s),
            ) {
                items(total, key = { index ->
                    state.items[index]?.id?.value ?: "slot-$index"
                }, contentType = { "card" }) { index ->
                    val item = state.items[index]
                    if (item == null) {
                        MobileSkeleton(Modifier.fillMaxWidth().aspectRatio(POSTER_RATIO))
                    } else {
                        MobileItemCard(
                            item = item,
                            sources = sources,
                            onClick = { navigation.open(item) },
                            menu = menu,
                            fillWidth = true,
                            tag = MobileLibraryTags.card(index),
                        )
                    }
                }
            }
            if (alphabet) {
                AlphabetBar(actions::jumpTo)
            }
        }
    }
}

/**
 * The A–Z bar (`FONCTIONNALITES.md` § 3, M1): a letter per slot down the side of the grid; a tap on
 * a letter, or a slide of the finger along the bar, jumps to the first title at that letter.
 */
@Composable
private fun AlphabetBar(onLetter: (String) -> Unit) {
    val letters = LibraryViewModel.LETTERS
    var last by remember { mutableStateOf<String?>(null) }

    fun letterAt(y: Float, height: Int) = letters[(y / height * letters.size).toInt().coerceIn(letters.indices)]
    Column(
        Modifier
            .width(MobileSizes.alphabetWidth)
            .fillMaxHeight()
            .testTag(MobileLibraryTags.ALPHABET)
            .pointerInput(letters) { detectTapGestures { onLetter(letterAt(it.y, size.height)) } }
            .pointerInput(letters) {
                detectVerticalDragGestures(onDragEnd = { last = null }) { change, _ ->
                    val letter = letterAt(change.position.y, size.height)
                    if (letter != last) {
                        last = letter
                        onLetter(letter)
                    }
                }
            },
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        letters.forEach { letter ->
            Text(
                letter,
                style = CineTheme.typography.minimum,
                color = CineTheme.colors.text2,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().weight(1f).testTag(MobileLibraryTags.letter(letter)),
            )
        }
    }
}

@Composable
private fun GenreChips(
    genres: List<String>?,
    onGenre: (String) -> Unit,
) {
    if (genres == null) return
    FlowRow(
        Modifier
            .fillMaxSize()
            .verticalScroll(
                rememberScrollState(),
            ).padding(MobileSizes.gutter)
            .testTag(MobileLibraryTags.GENRES),
        horizontalArrangement = Arrangement.spacedBy(CineSpacing.xs),
        verticalArrangement = Arrangement.spacedBy(CineSpacing.xs),
    ) {
        genres.forEach { genre -> MobileChip(genre, selected = false, onClick = { onGenre(genre) }) }
    }
}

@Composable
private fun NextUpList(
    state: LibraryUiState,
    sources: ImageSources,
    navigation: LibraryNavigation,
    menu: MobileQuickMenu,
) {
    val items = state.nextUp ?: return
    LazyVerticalGrid(
        columns = GridCells.Adaptive(MobileSizes.thumbnail.width),
        contentPadding = PaddingValues(MobileSizes.gutter),
        horizontalArrangement = Arrangement.spacedBy(CineSpacing.xs),
        verticalArrangement = Arrangement.spacedBy(CineSpacing.s),
    ) {
        items(items.size, key = { items[it].id.value }) { index ->
            val item = items[index]
            MobileItemCard(item, sources, { navigation.open(item) }, poster = false, menu = menu, fillWidth = true)
        }
    }
}

@Composable
private fun SortSheet(
    state: LibraryUiState,
    actions: LibraryActions,
    onDismiss: () -> Unit,
) {
    val sort = state.browse.sort
    MobileSheet(
        title = stringResource(R.string.panel_sort),
        subtitle = listOfNotNull(sort.field.label(), sort.directionLabel()).joinToString(" · "),
        onDismiss = onDismiss,
    ) {
        // A second tap on the field in use turns its direction round (the TV's rule).
        MobileSheetLines(
            SortField.entries.map { field ->
                MobileSheetLine(field.label(), null, "sort-${field.name}") { actions.sortBy(field) }
            },
            selected = "sort-${sort.field.name}",
        )
    }
}

@Composable
private fun FilterSheet(
    state: LibraryUiState,
    actions: LibraryActions,
    onDismiss: () -> Unit,
) {
    val filters = state.browse.filters
    val options = state.options
    MobileSheet(
        title = stringResource(R.string.panel_filters),
        subtitle =
            filters.summary().ifEmpty {
                null
            },
        onDismiss = onDismiss,
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = MobileSizes.gutter),
            verticalArrangement = Arrangement.spacedBy(CineSpacing.s),
        ) {
            FilterGroup(stringResource(R.string.filter_watch)) {
                WatchStatus.entries.forEach { status ->
                    MobileChip(status.label(), filters.watch == status, {
                        actions.setFilters(filters.copy(watch = status.takeUnless { filters.watch == it }))
                    })
                }
                MobileChip(stringResource(R.string.filter_favorite_only), filters.favorite, {
                    actions.setFilters(filters.copy(favorite = !filters.favorite))
                })
            }
            options?.genres?.takeIf { it.isNotEmpty() }?.let { genres ->
                FilterGroup(stringResource(R.string.filter_genre)) {
                    genres.forEach { genre ->
                        MobileChip(
                            genre,
                            genre in filters.genres,
                            { actions.setFilters(filters.copy(genres = filters.genres.toggle(genre))) },
                        )
                    }
                }
            }
            options?.decades?.takeIf { it.isNotEmpty() }?.let { decades ->
                FilterGroup(stringResource(R.string.filter_decade)) {
                    decades.forEach { decade ->
                        MobileChip(decadeLabel(decade), decade in filters.decades, {
                            actions.setFilters(filters.copy(decades = filters.decades.toggle(decade)))
                        })
                    }
                }
            }
            if (filters.count > 0) {
                MobileSecondaryButton(stringResource(R.string.action_clear), { actions.setFilters(ItemFilters.NONE) })
            }
        }
    }
}

@Composable
private fun FilterGroup(
    title: String,
    chips: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(CineSpacing.xs)) {
        Text(title, style = CineTheme.typography.label, color = CineTheme.colors.text2)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(CineSpacing.xs),
            verticalArrangement = Arrangement.spacedBy(CineSpacing.xs),
        ) {
            chips()
        }
    }
}

private fun <T> Set<T>.toggle(value: T): Set<T> = if (value in this) this - value else this + value

private const val SKELETON_LINES = 5
private const val SKELETON_RATIO = 6f
private const val POSTER_RATIO = 2f / 3f
private const val GRID_PLACEHOLDERS = 18

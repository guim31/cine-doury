package fr.guim.cinedoury.feature.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import fr.guim.cinedoury.core.data.SessionManager
import fr.guim.cinedoury.core.data.library.LibraryTab
import fr.guim.cinedoury.core.designsystem.component.AlphabetBar
import fr.guim.cinedoury.core.designsystem.component.AmbientBackdrop
import fr.guim.cinedoury.core.designsystem.component.CineIcons
import fr.guim.cinedoury.core.designsystem.component.CineTab
import fr.guim.cinedoury.core.designsystem.component.InitialFocus
import fr.guim.cinedoury.core.designsystem.component.SecondaryButton
import fr.guim.cinedoury.core.designsystem.component.rememberCineFocus
import fr.guim.cinedoury.core.designsystem.theme.CineSizes
import fr.guim.cinedoury.core.designsystem.theme.CineSpacing
import fr.guim.cinedoury.core.designsystem.theme.CineTheme
import fr.guim.cinedoury.core.model.ItemFilters
import fr.guim.cinedoury.core.model.LibraryId
import fr.guim.cinedoury.core.model.MediaItem
import fr.guim.cinedoury.core.ui.AppRail
import fr.guim.cinedoury.core.ui.CollectEvents
import fr.guim.cinedoury.core.ui.Events
import fr.guim.cinedoury.core.ui.ImageSources
import fr.guim.cinedoury.core.ui.RailTarget
import fr.guim.cinedoury.core.ui.RemoteIndicator
import fr.guim.cinedoury.core.ui.ambience
import fr.guim.cinedoury.core.ui.imageSource
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

/** What a library asks the app to open. */
interface LibraryNavigation {
    fun navigate(target: RailTarget)

    /** The details of [item] (J3). */
    fun open(item: MediaItem)

    fun sessionEnded()
}

/** The panels of a library, over its right side. */
enum class LibraryPanel {
    Sort,
    Filters,
    View,
}

@Composable
fun LibraryRoute(
    library: LibraryId,
    navigation: LibraryNavigation,
    viewModel: LibraryViewModel = koinViewModel(key = library.value) { parametersOf(library) },
    sessions: SessionManager = koinInject(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectEvents(viewModel.sessionEnded) { navigation.sessionEnded() }
    LibraryScreen(
        state = state,
        sources = sessions::imageSource,
        actions = viewModel,
        navigation = navigation,
        jumps = viewModel.jumps,
    )
}

/** Preview tags of a library, for tests and screenshots. */
object LibraryPreview {
    const val SORT = "library-sort"
    const val FILTERS = "library-filters"
    const val VIEW = "library-view"
    const val CLEAR = "library-clear"

    fun item(index: Int) = "item-$index"

    fun tab(tab: LibraryTab) = "tab-${tab.name}"
}

/**
 * A library (`FONCTIONNALITES.md` § 3, `UI.md` § 3): its name, its tabs, sort, filters and view,
 * the summary of the filters with « Tout effacer », then its items, page by page, with the A–Z jump
 * on the right when sorted by title.
 *
 * @param previewPanel opens a panel, and [previewCategory] a filter of the filter panel (screenshots).
 */
@Composable
fun LibraryScreen(
    state: LibraryUiState,
    sources: ImageSources,
    actions: LibraryActions,
    navigation: LibraryNavigation,
    modifier: Modifier = Modifier,
    jumps: Events<Jump>? = null,
    previewPanel: LibraryPanel? = null,
    previewCategory: FilterCategory? = null,
    previewLetter: String? = null,
) {
    var panel by rememberSaveable { mutableStateOf(previewPanel) }
    var category by rememberSaveable { mutableStateOf(previewCategory) }
    var focusedItem by remember { mutableStateOf<MediaItem?>(null) }
    var jump by remember { mutableStateOf<Jump?>(null) }
    val buttons = remember { LibraryPanel.entries.associateWith { FocusRequester() } }
    var closed by remember { mutableStateOf<LibraryPanel?>(null) }
    val letter = remember { FocusRequester() }
    if (jumps != null) CollectEvents(jumps) { jump = it }

    BackHandler(enabled = panel != null) {
        if (category != null) category = null else panel = null
    }

    val browse = state.browse
    val library = state.library
    Box(modifier = modifier.fillMaxSize()) {
        AmbientBackdrop(ambience = focusedItem?.ambience)
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(start = ContentStart, top = CineSpacing.safeVertical),
            verticalArrangement = Arrangement.spacedBy(CineSpacing.s),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(end = CineSpacing.safeHorizontal),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CineSpacing.m),
            ) {
                Text(
                    text = library?.name.orEmpty(),
                    style = CineTheme.typography.title1,
                    color = CineTheme.colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val total = state.items.total
                if (total != null && browse.tab == LibraryTab.All) {
                    Text(
                        text = stringResource(R.string.count_titles, total),
                        style = CineTheme.typography.caption,
                        color = CineTheme.colors.text2,
                    )
                }
                Spacer(Modifier.weight(1f))
                if (state.remote) RemoteIndicator()
            }
            Toolbar(state, actions, onPanel = { panel = it }, buttons = buttons)
            if (!browse.filters.isEmpty && browse.tab == LibraryTab.All) {
                FilterSummary(browse.filters, onClear = actions::clearFilters)
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                LibraryContent(
                    state = state,
                    sources = sources,
                    actions = actions,
                    onOpen = navigation::open,
                    onFocused = { focusedItem = it },
                    jump = jump,
                    onJumped = { jump = null },
                    contentEnd = if (state.alphabetical) AlphabetSpace else CineSpacing.safeHorizontal,
                    // Once a panel was closed, its button keeps the focus.
                    autoFocus = panel == null && closed == null,
                )
                if (state.alphabetical && (state.items.total ?: 0) > 0) {
                    AlphabetBar(
                        letters = LibraryViewModel.LETTERS,
                        current = previewLetter ?: focusedItem?.initial(),
                        onLetter = actions::jumpTo,
                        letterRequester = letter,
                        modifier =
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(end = CineSpacing.s)
                                .testTag("alphabet"),
                    )
                }
            }
        }
        AppRail(
            state = state.rail,
            selected = library?.let { RailTarget.Library(it.id) } ?: RailTarget.Home,
            onNavigate = navigation::navigate,
        )
        LibraryPanels(
            panel = panel,
            category = category,
            state = state,
            actions = actions,
            onCategory = { category = it },
            onClose = {
                closed = panel
                panel = null
                category = null
            },
        )
    }
    // A closed panel gives the focus back to its button; an empty list gives it to the toolbar.
    val noGenre = state.browse.tab == LibraryTab.Genres && state.options?.genres.isNullOrEmpty()
    val empty = state.items.total == 0 || noGenre
    InitialFocus(buttons.getValue(closed ?: LibraryPanel.View), key = closed, enabled = closed != null || empty)
}

/** Room for the A–Z bar on the right of the grid. */
private val AlphabetSpace = CineSizes.alphabetWidth + CineSpacing.s

/** The rows of a grid and the toolbar start right of the closed rail. */
internal val ContentStart = CineSizes.railCollapsed + CineSpacing.m

/** The letter of the A–Z bar an item belongs to. */
private fun MediaItem.initial(): String {
    val first = (sortTitle ?: title).firstOrNull { it.isLetterOrDigit() } ?: return LibraryViewModel.DIGITS
    if (!first.isLetter()) return LibraryViewModel.DIGITS
    val plain =
        java.text.Normalizer
            .normalize(first.toString(), java.text.Normalizer.Form.NFD)
            .first()
    return plain.uppercaseChar().toString()
}

@Composable
private fun Toolbar(
    state: LibraryUiState,
    actions: LibraryActions,
    onPanel: (LibraryPanel) -> Unit,
    buttons: Map<LibraryPanel, FocusRequester>,
) {
    val browse = state.browse
    Row(
        modifier = Modifier.padding(end = CineSpacing.safeHorizontal),
        horizontalArrangement = Arrangement.spacedBy(CineSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        state.tabs.takeIf { it.size > 1 }?.forEach { tab ->
            CineTab(
                label = tab.label(),
                selected = tab == browse.tab,
                onClick = { actions.selectTab(tab) },
                focus = rememberCineFocus(LibraryPreview.tab(tab)),
                modifier = Modifier.testTag(LibraryPreview.tab(tab)),
            )
        }
        Spacer(Modifier.weight(1f))
        if (browse.tab == LibraryTab.All) {
            SecondaryButton(
                text = stringResource(R.string.action_sort, browse.sort.field.label()),
                icon = CineIcons.Sort,
                onClick = { onPanel(LibraryPanel.Sort) },
                focusRequester = buttons.getValue(LibraryPanel.Sort),
                focus = rememberCineFocus(LibraryPreview.SORT),
                modifier = Modifier.testTag(LibraryPreview.SORT),
            )
            SecondaryButton(
                text =
                    if (browse.filters.isEmpty) {
                        stringResource(R.string.action_filters)
                    } else {
                        stringResource(R.string.action_filters_count, browse.filters.count)
                    },
                icon = CineIcons.Filter,
                onClick = { onPanel(LibraryPanel.Filters) },
                focusRequester = buttons.getValue(LibraryPanel.Filters),
                focus = rememberCineFocus(LibraryPreview.FILTERS),
                modifier = Modifier.testTag(LibraryPreview.FILTERS),
            )
        }
        SecondaryButton(
            text = stringResource(R.string.action_view),
            icon = CineIcons.GridView,
            onClick = { onPanel(LibraryPanel.View) },
            focusRequester = buttons.getValue(LibraryPanel.View),
            focus = rememberCineFocus(LibraryPreview.VIEW),
            modifier = Modifier.testTag(LibraryPreview.VIEW),
        )
    }
}

/** « Comédie · années 80 · non vus », and « Tout effacer ». */
@Composable
private fun FilterSummary(
    filters: ItemFilters,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier.padding(end = CineSpacing.safeHorizontal),
        horizontalArrangement = Arrangement.spacedBy(CineSpacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = filters.summary(),
            style = CineTheme.typography.body,
            color = CineTheme.colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false).testTag("filter-summary"),
        )
        SecondaryButton(
            text = stringResource(R.string.action_clear),
            icon = CineIcons.Close,
            onClick = onClear,
            focus = rememberCineFocus(LibraryPreview.CLEAR),
            modifier = Modifier.testTag(LibraryPreview.CLEAR),
        )
    }
}

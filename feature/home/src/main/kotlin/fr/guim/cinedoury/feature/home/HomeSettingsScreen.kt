package fr.guim.cinedoury.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import fr.guim.cinedoury.core.data.home.CardStyle
import fr.guim.cinedoury.core.data.home.HomeRowConfig
import fr.guim.cinedoury.core.data.home.HomeRowType
import fr.guim.cinedoury.core.data.home.HomeRows
import fr.guim.cinedoury.core.data.home.hasWindow
import fr.guim.cinedoury.core.designsystem.component.CineIcons
import fr.guim.cinedoury.core.designsystem.component.InitialFocus
import fr.guim.cinedoury.core.designsystem.component.ListRow
import fr.guim.cinedoury.core.designsystem.component.ListSectionTitle
import fr.guim.cinedoury.core.designsystem.component.Wordmark
import fr.guim.cinedoury.core.designsystem.component.asHeading
import fr.guim.cinedoury.core.designsystem.component.rememberCineFocus
import fr.guim.cinedoury.core.designsystem.theme.CineSizes
import fr.guim.cinedoury.core.designsystem.theme.CineSpacing
import fr.guim.cinedoury.core.designsystem.theme.CineTheme
import fr.guim.cinedoury.core.model.LibraryKind
import fr.guim.cinedoury.core.ui.SEPARATOR
import org.koin.androidx.compose.koinViewModel
import fr.guim.cinedoury.core.ui.R as UiR

@Composable
fun HomeSettingsRoute(viewModel: HomeSettingsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeSettingsScreen(state = state, actions = viewModel)
}

/** Preview tag of the first row of the customisation screen. */
const val PREVIEW_CUSTOMIZE_ROW = "customize-row-0"

/**
 * The customisation of the home, two columns and nothing else (`UI.md` § 1, principle 5): on the
 * left the rows in their order, the rows to add and the libraries of the rail; on the right the
 * settings of the row that has the focus. Every change applies at once.
 *
 * @param selectedRow the row whose settings show (screenshots); otherwise the last one focused.
 */
@Composable
fun HomeSettingsScreen(
    state: HomeSettingsState,
    actions: HomeSettingsViewModel?,
    modifier: Modifier = Modifier,
    selectedRow: String? = null,
) {
    var selected by rememberSaveable { mutableStateOf(selectedRow ?: state.layout.firstOrNull()?.id) }
    val row = state.layout.firstOrNull { it.id == selected } ?: state.layout.firstOrNull()
    val first = remember { FocusRequester() }
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(CineTheme.colors.background),
    ) {
        Row(
            Modifier.padding(start = CineSpacing.safeHorizontal * 2, end = CineSpacing.safeHorizontal),
            horizontalArrangement = Arrangement.spacedBy(CineSpacing.xl),
        ) {
            LazyColumn(
                modifier = Modifier.width(CineSizes.panelWidth).fillMaxHeight().testTag("customize-rows"),
                contentPadding = ListPadding,
                verticalArrangement = Arrangement.spacedBy(CineSpacing.xs),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(CineSpacing.s)) {
                        Wordmark(stringResource(UiR.string.brand_name))
                        Text(
                            text = stringResource(R.string.customize_title),
                            style = CineTheme.typography.title1,
                            modifier = Modifier.asHeading(),
                            color = CineTheme.colors.text,
                        )
                        Text(
                            text = stringResource(R.string.customize_subtitle),
                            style = CineTheme.typography.caption,
                            color = CineTheme.colors.text2,
                        )
                    }
                }
                item { ListSectionTitle(stringResource(R.string.customize_rows)) }
                items(state.layout, key = { it.id }) { config ->
                    val index = state.layout.indexOf(config)
                    ListRow(
                        title = config.type.label(),
                        description = config.summary(state),
                        trailingIcon = if (config.id == row?.id) CineIcons.Edit else null,
                        onClick = { selected = config.id },
                        focusRequester = if (index == 0) first else null,
                        focus = rememberCineFocus("customize-row-$index"),
                        modifier =
                            Modifier
                                .testTag("customize-row-$index")
                                .onFocusChanged { if (it.hasFocus) selected = config.id },
                    )
                }
                item { ListSectionTitle(stringResource(R.string.customize_add)) }
                items(state.addable, key = { "add-${it.name}" }) { type ->
                    ListRow(
                        title = stringResource(R.string.customize_add_row, type.label()),
                        icon = CineIcons.Add,
                        onClick = { actions?.add(type) },
                        modifier = Modifier.testTag("customize-add-${type.name}"),
                    )
                }
                item {
                    ListRow(
                        title = stringResource(R.string.customize_reset),
                        icon = CineIcons.Retry,
                        onClick = { actions?.reset() },
                        modifier = Modifier.testTag("customize-reset"),
                    )
                }
                item { ListSectionTitle(stringResource(R.string.customize_rail)) }
                items(state.libraries.libraries, key = { "rail-${it.id.value}" }) { library ->
                    val shown = library.id !in state.railHidden
                    ListRow(
                        title = library.name,
                        description = stringResource(R.string.customize_rail_description).takeIf { !shown },
                        value = stringResource(if (shown) R.string.customize_shown else R.string.customize_hidden),
                        onClick = { actions?.setShownInRail(library.id, !shown) },
                        modifier = Modifier.testTag("customize-rail-${library.id.value}"),
                    )
                }
            }
            if (row != null) RowOptions(row, state, actions)
        }
    }
    InitialFocus(first, key = state.layout.isNotEmpty())
}

private val ListPadding =
    PaddingValues(
        top = CineSpacing.safeVertical + CineSpacing.m,
        bottom = CineSpacing.safeVertical + CineSpacing.xxl,
        start = CineSpacing.xs,
        end = CineSpacing.xs,
    )

/** The settings of one row: its place, its cards, its libraries, its window, its removal. */
@Composable
private fun RowOptions(
    row: HomeRowConfig,
    state: HomeSettingsState,
    actions: HomeSettingsViewModel?,
) {
    val libraries = HomeRows.eligible(row.type, state.libraries.libraries, state.libraries.exclusions)
    val excluded = state.libraries.libraries.filter { it.id in state.libraries.exclusions.fromHome }
    LazyColumn(
        modifier = Modifier.width(CineSizes.panelWidth).fillMaxHeight().testTag("customize-options"),
        contentPadding = ListPadding,
        verticalArrangement = Arrangement.spacedBy(CineSpacing.xs),
    ) {
        item { ListSectionTitle(stringResource(R.string.customize_options)) }
        item {
            Text(text = row.type.label(), style = CineTheme.typography.body, color = CineTheme.colors.text2)
        }
        item {
            ListRow(
                title = stringResource(R.string.customize_up),
                icon = CineIcons.Up,
                onClick = { actions?.moveUp(row.id) },
                modifier = Modifier.testTag("customize-up"),
            )
        }
        item {
            ListRow(
                title = stringResource(R.string.customize_down),
                icon = CineIcons.Down,
                onClick = { actions?.moveDown(row.id) },
                modifier = Modifier.testTag("customize-down"),
            )
        }
        item {
            ListRow(
                title = stringResource(R.string.customize_style),
                icon = if (row.style == CardStyle.Poster) CineIcons.GridView else CineIcons.ThumbnailView,
                value = row.style.label(),
                onClick = { actions?.toggleStyle(row.id) },
                modifier = Modifier.testTag("customize-style"),
            )
        }
        if (row.type.hasWindow) {
            item {
                ListRow(
                    title = stringResource(R.string.customize_window),
                    value =
                        row.windowDays?.let { stringResource(R.string.customize_window_days, it) }
                            ?: stringResource(R.string.customize_window_none),
                    onClick = { actions?.nextWindow(row.id) },
                    modifier = Modifier.testTag("customize-window"),
                )
            }
        }
        item { ListSectionTitle(stringResource(R.string.customize_libraries)) }
        items(libraries, key = { "library-${it.id.value}" }) { library ->
            val chosen = row.libraries.isEmpty() || library.id in row.libraries
            ListRow(
                title = library.name,
                icon = if (library.kind == LibraryKind.Shows) CineIcons.Shows else CineIcons.Movies,
                trailingIcon = if (chosen) CineIcons.Watched else null,
                onClick = { actions?.toggleLibrary(row.id, library.id, libraries.map { it.id }) },
                modifier = Modifier.testTag("customize-library-${library.id.value}"),
            )
        }
        items(excluded, key = { "excluded-${it.id.value}" }) { library ->
            ListRow(
                title = library.name,
                description = stringResource(R.string.customize_excluded),
                onClick = {},
            )
        }
        item {
            ListRow(
                title = stringResource(R.string.customize_remove),
                icon = CineIcons.Close,
                destructive = true,
                onClick = { actions?.remove(row.id) },
                modifier = Modifier.testTag("customize-remove"),
            )
        }
    }
}

@Composable
internal fun HomeRowType.label(): String =
    stringResource(
        when (this) {
            HomeRowType.Resume -> R.string.row_resume
            HomeRowType.NextUp -> R.string.row_next_up
            HomeRowType.Latest -> R.string.row_latest
            HomeRowType.RecentlyReleased -> R.string.row_released
            HomeRowType.Continue -> R.string.row_continue
        },
    )

@Composable
private fun CardStyle.label(): String =
    stringResource(
        if (this ==
            CardStyle.Poster
        ) {
            R.string.customize_style_poster
        } else {
            R.string.customize_style_thumbnail
        },
    )

/** « Films, Séries · Affiches ». */
@Composable
private fun HomeRowConfig.summary(state: HomeSettingsState): String {
    val names =
        if (libraries.isEmpty()) {
            stringResource(R.string.customize_all_libraries)
        } else {
            state.libraries.libraries
                .filter { it.id in libraries }
                .joinToString(", ") { it.name }
        }
    return names + SEPARATOR + style.label()
}

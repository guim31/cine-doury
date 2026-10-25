package fr.guim.cinedoury.feature.manage

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import fr.guim.cinedoury.core.data.SessionManager
import fr.guim.cinedoury.core.designsystem.component.CineIcons
import fr.guim.cinedoury.core.designsystem.component.CineTab
import fr.guim.cinedoury.core.designsystem.component.FocusPivot
import fr.guim.cinedoury.core.designsystem.component.InitialFocus
import fr.guim.cinedoury.core.designsystem.component.ListRow
import fr.guim.cinedoury.core.designsystem.component.MediaListRow
import fr.guim.cinedoury.core.designsystem.component.PrimaryButton
import fr.guim.cinedoury.core.designsystem.component.SidePanel
import fr.guim.cinedoury.core.designsystem.component.SkeletonListRow
import fr.guim.cinedoury.core.designsystem.component.previewFirstVisible
import fr.guim.cinedoury.core.designsystem.component.rememberCineFocus
import fr.guim.cinedoury.core.designsystem.theme.CineSizes
import fr.guim.cinedoury.core.designsystem.theme.CineSpacing
import fr.guim.cinedoury.core.designsystem.theme.CineTheme
import fr.guim.cinedoury.core.model.LibraryId
import fr.guim.cinedoury.core.model.MediaItem
import fr.guim.cinedoury.core.ui.ImageSources
import fr.guim.cinedoury.core.ui.SEPARATOR
import fr.guim.cinedoury.core.ui.cardTitle
import fr.guim.cinedoury.core.ui.imageSource
import fr.guim.cinedoury.core.ui.posterImage
import fr.guim.cinedoury.core.ui.rememberArt
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

@Composable
fun ProblemsRoute(
    library: LibraryId,
    libraryName: String,
    onIdentify: (MediaItem) -> Unit,
    viewModel: ProblemsViewModel = koinViewModel(key = "problems-${library.value}") { parametersOf(library) },
    sessions: SessionManager = koinInject(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ProblemsScreen(state, libraryName, sessions::imageSource, viewModel, onIdentify)
}

/** Preview tags of « Éléments à problème ». */
object ProblemsPreview {
    const val CHECK = "problems-check"
    const val IDENTIFY = "problems-identify"
    const val REMOVE = "problems-remove"

    fun tab(tab: ProblemTab) = "problems-tab-${tab.name}"

    fun row(index: Int) = "$ROW$index"

    internal const val ROW = "problems-row-"
}

/**
 * « Éléments à problème » (`FONCTIONNALITES.md` § 9): the titles without any provider id (a folder
 * name misspelt), without poster, and whose file is gone. OK on one gives its two ways out:
 * « Corriger l'identification » and « Supprimer de la bibliothèque ».
 */
@Composable
fun ProblemsScreen(
    state: ProblemsState,
    libraryName: String,
    sources: ImageSources,
    actions: ProblemsActions,
    onIdentify: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = CineTheme.colors
    val first = remember { FocusRequester() }
    val tabs = remember { ProblemTab.entries.associateWith { FocusRequester() } }
    BackHandler(enabled = state.chosen != null) { actions.choose(null) }
    Box(modifier.fillMaxSize().background(colors.background)) {
        Column(
            modifier =
                Modifier.padding(
                    start = CineSpacing.safeHorizontal,
                    end = CineSpacing.safeHorizontal,
                    top = CineSpacing.safeVertical + CineSpacing.s,
                ),
            verticalArrangement = Arrangement.spacedBy(CineSpacing.s),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CineSpacing.m),
            ) {
                Text(stringResource(R.string.problems_title), style = CineTheme.typography.title1, color = colors.text)
                Text(libraryName, style = CineTheme.typography.title2, color = colors.text2, maxLines = 1)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(CineSpacing.xs)) {
                ProblemTab.entries.forEach { tab ->
                    CineTab(
                        label = tab.label(state),
                        selected = tab == state.tab,
                        onClick = { actions.selectTab(tab) },
                        focusRequester = tabs.getValue(tab),
                        focus = rememberCineFocus(ProblemsPreview.tab(tab)),
                        modifier = Modifier.testTag(ProblemsPreview.tab(tab)),
                    )
                }
            }
            if (state.tab == ProblemTab.MissingFiles && state.missing == null) {
                FileCheckPanel(state.checking, actions::checkFiles)
            } else {
                ProblemList(state, sources, actions, first)
            }
        }
        ProblemActions(state, actions, onIdentify)
        state.deletion?.let { DeleteDialog(it, onConfirm = actions::confirmDelete, onCancel = actions::cancelDelete) }
        state.error?.let { ManageErrorDialog(it, onDismiss = actions::dismissError) }
    }
    val shown = state.shown
    InitialFocus(first, key = state.tab to shown?.size, enabled = !shown.isNullOrEmpty() && state.chosen == null)
    InitialFocus(tabs.getValue(state.tab), key = state.tab, enabled = shown?.isEmpty() == true || state.missing == null)
}

@Composable
private fun ProblemTab.label(state: ProblemsState): String {
    val problems = state.problems
    return when (this) {
        ProblemTab.Unidentified -> {
            stringResource(R.string.problems_unidentified, problems?.unidentified?.size ?: 0)
        }

        ProblemTab.WithoutPoster -> {
            stringResource(R.string.problems_no_poster, problems?.withoutPoster?.size ?: 0)
        }

        ProblemTab.MissingFiles -> {
            state.missing?.let { stringResource(R.string.problems_missing_count, it.size) }
                ?: stringResource(R.string.problems_missing)
        }
    }
}

/** Before the files are checked: what the check does, and the button (or its progress). */
@Composable
private fun FileCheckPanel(
    checking: FileCheck?,
    onCheck: () -> Unit,
) {
    val colors = CineTheme.colors
    Column(
        modifier = Modifier.widthIn(max = CineSizes.formWidth).padding(top = CineSpacing.m),
        verticalArrangement = Arrangement.spacedBy(CineSpacing.m),
    ) {
        Text(stringResource(R.string.problems_check_explained), style = CineTheme.typography.body, color = colors.text2)
        if (checking == null) {
            PrimaryButton(
                text = stringResource(R.string.problems_check_files),
                icon = CineIcons.Search,
                onClick = onCheck,
                focus = rememberCineFocus(ProblemsPreview.CHECK),
                modifier = Modifier.testTag(ProblemsPreview.CHECK),
            )
        } else {
            Text(
                text = stringResource(R.string.problems_checking, checking.checked, checking.total),
                style = CineTheme.typography.title2,
                color = colors.text,
                modifier = Modifier.testTag("problems-checking"),
            )
        }
    }
}

@Composable
private fun ProblemList(
    state: ProblemsState,
    sources: ImageSources,
    actions: ProblemsActions,
    first: FocusRequester,
) {
    val shown = state.shown
    if (shown?.isEmpty() == true) {
        Text(
            text =
                stringResource(
                    when (state.tab) {
                        ProblemTab.Unidentified -> R.string.problems_none_unidentified
                        ProblemTab.WithoutPoster -> R.string.problems_none_poster
                        ProblemTab.MissingFiles -> R.string.problems_none_missing
                    },
                ),
            style = CineTheme.typography.body,
            color = CineTheme.colors.text2,
            modifier = Modifier.padding(top = CineSpacing.m),
        )
        return
    }
    val reason =
        stringResource(
            when (state.tab) {
                ProblemTab.Unidentified -> R.string.problems_unidentified_reason
                ProblemTab.WithoutPoster -> R.string.problems_no_poster_reason
                ProblemTab.MissingFiles -> R.string.problems_missing_reason
            },
        )
    FocusPivot(fraction = LIST_PIVOT) {
        LazyColumn(
            state = rememberLazyListState(initialFirstVisibleItemIndex = previewFirstVisible(ProblemsPreview.ROW)),
            contentPadding = PaddingValues(top = CineSpacing.xs, bottom = CineSpacing.xxl, start = CineSpacing.xs),
            verticalArrangement = Arrangement.spacedBy(CineSpacing.xs),
            modifier = Modifier.widthIn(max = CineSizes.problemsListWidth).testTag("problems-list"),
        ) {
            if (shown == null) {
                items(SKELETONS) { SkeletonListRow() }
            } else {
                itemsIndexed(shown, key = {
                    _,
                    item,
                    ->
                    item.id.value
                }, contentType = { _, _ -> "problem" }) { index, item ->
                    MediaListRow(
                        title = item.cardTitle,
                        metadata = listOfNotNull(item.year?.toString(), reason).joinToString(SEPARATOR),
                        overview = item.path,
                        onClick = { actions.choose(item) },
                        focusRequester = if (index == 0) first else null,
                        focus = rememberCineFocus(ProblemsPreview.row(index)),
                        image = rememberArt(item.posterImage, sources),
                        modifier = Modifier.testTag(ProblemsPreview.row(index)),
                    )
                }
            }
        }
    }
}

private const val SKELETONS = 6
private const val LIST_PIVOT = 0.3f

/** The two ways out of a problem, in a side panel: correct its identity, or delete it. */
@Composable
private fun ProblemActions(
    state: ProblemsState,
    actions: ProblemsActions,
    onIdentify: (MediaItem) -> Unit,
) {
    val chosen = state.chosen
    SidePanel(
        visible = chosen != null,
        title = chosen?.cardTitle.orEmpty(),
        modifier = Modifier.testTag("problems-panel"),
    ) {
        val firstAction = remember { FocusRequester() }
        Column(
            modifier = Modifier.padding(CineSpacing.xs),
            verticalArrangement = Arrangement.spacedBy(CineSpacing.xs),
        ) {
            val identifiable = chosen != null && chosen.kind in ItemManager.IDENTIFIABLE
            if (identifiable) {
                ListRow(
                    title = stringResource(R.string.manage_identify),
                    description = stringResource(R.string.manage_identify_description),
                    icon = CineIcons.Search,
                    onClick = {
                        actions.choose(null)
                        chosen?.let(onIdentify)
                    },
                    focusRequester = firstAction,
                    focus = rememberCineFocus(ProblemsPreview.IDENTIFY),
                    modifier = Modifier.testTag(ProblemsPreview.IDENTIFY),
                )
            }
            if (state.rights.canDeleteSomewhere) {
                ListRow(
                    title = stringResource(R.string.problems_remove),
                    description = stringResource(R.string.manage_delete_description),
                    icon = CineIcons.Delete,
                    destructive = true,
                    onClick = { chosen?.let(actions::askDelete) },
                    focusRequester = if (identifiable) null else firstAction,
                    focus = rememberCineFocus(ProblemsPreview.REMOVE),
                    modifier = Modifier.testTag(ProblemsPreview.REMOVE),
                )
            }
        }
        InitialFocus(firstAction, key = chosen, enabled = chosen != null)
    }
}

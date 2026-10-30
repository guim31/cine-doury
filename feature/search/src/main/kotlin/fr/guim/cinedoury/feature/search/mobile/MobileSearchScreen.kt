package fr.guim.cinedoury.feature.search.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.guim.cinedoury.core.data.SessionManager
import fr.guim.cinedoury.core.designsystem.component.CineIcons
import fr.guim.cinedoury.core.designsystem.image.ImageSource
import fr.guim.cinedoury.core.designsystem.image.RemoteImage
import fr.guim.cinedoury.core.designsystem.mobile.MobileCard
import fr.guim.cinedoury.core.designsystem.mobile.MobileChip
import fr.guim.cinedoury.core.designsystem.mobile.MobileIconButton
import fr.guim.cinedoury.core.designsystem.mobile.MobileMessage
import fr.guim.cinedoury.core.designsystem.mobile.MobilePrimaryButton
import fr.guim.cinedoury.core.designsystem.mobile.MobileRowTitle
import fr.guim.cinedoury.core.designsystem.mobile.MobileSecondaryButton
import fr.guim.cinedoury.core.designsystem.mobile.MobileSheet
import fr.guim.cinedoury.core.designsystem.mobile.MobileTextButton
import fr.guim.cinedoury.core.designsystem.mobile.MobileTextField
import fr.guim.cinedoury.core.designsystem.theme.CineSpacing
import fr.guim.cinedoury.core.designsystem.theme.CineTheme
import fr.guim.cinedoury.core.designsystem.theme.MobileSizes
import fr.guim.cinedoury.core.model.MediaItem
import fr.guim.cinedoury.core.model.RequestCandidate
import fr.guim.cinedoury.core.model.SearchGroup
import fr.guim.cinedoury.core.ui.CollectEvents
import fr.guim.cinedoury.core.ui.ImageSources
import fr.guim.cinedoury.core.ui.imageSource
import fr.guim.cinedoury.core.ui.mobile.MobileItemCard
import fr.guim.cinedoury.core.ui.mobile.MobileQuickMenu
import fr.guim.cinedoury.core.ui.mobile.MobileQuickMenuHost
import fr.guim.cinedoury.core.ui.mobile.MobileSourceError
import fr.guim.cinedoury.core.ui.mobile.rememberMobileQuickMenu
import fr.guim.cinedoury.feature.search.R
import fr.guim.cinedoury.feature.search.RequestDialog
import fr.guim.cinedoury.feature.search.SearchActions
import fr.guim.cinedoury.feature.search.SearchNavigation
import fr.guim.cinedoury.feature.search.SearchState
import fr.guim.cinedoury.feature.search.SearchViewModel
import fr.guim.cinedoury.feature.search.VoiceProblem
import fr.guim.cinedoury.feature.search.VoiceSearch
import fr.guim.cinedoury.feature.search.rememberVoiceSearch
import fr.guim.cinedoury.feature.search.statusLine
import fr.guim.cinedoury.feature.search.title
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/** Tags of the search of the phone, for tests and screenshots. */
object MobileSearchTags {
    const val SCREEN = "search"
    const val FIELD = "search-field"
    const val VOICE = "search-voice"
    const val CLEAR = "search-clear"

    fun row(group: SearchGroup) = "search-row-${group.name.lowercase()}"

    fun card(
        group: SearchGroup,
        index: Int,
    ) = "search-card-${group.name.lowercase()}-$index"

    fun recent(index: Int) = "search-recent-$index"

    fun genre(index: Int) = "search-genre-$index"
}

@Composable
fun MobileSearchRoute(
    navigation: SearchNavigation,
    viewModel: SearchViewModel = koinViewModel(),
    sessions: SessionManager = koinInject(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectEvents(viewModel.sessionEnded) { navigation.sessionEnded() }
    val voice = rememberVoiceSearch(onResult = viewModel::setQuery)
    MobileSearchScreen(state, sessions::imageSource, viewModel, navigation, voice)
}

/**
 * The search on the phone (M1, `UI.md` § 9): the keyboard of the phone opens with the screen, the
 * results come at each letter typed (the title index of the device, then the server, `SPEC.md`
 * § 4.12), and the microphone hands the query to the voice recognition of the system. Before a query:
 * the recent searches, the genres and the latest additions.
 */
@Composable
fun MobileSearchScreen(
    state: SearchState,
    sources: ImageSources,
    actions: SearchActions,
    navigation: SearchNavigation,
    voice: VoiceSearch,
    menu: MobileQuickMenu = rememberMobileQuickMenu(),
    focusField: Boolean = true,
) {
    val field = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val open: (MediaItem) -> Unit = { item ->
        actions.opened()
        keyboard?.hide()
        navigation.open(item)
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(
                CineTheme.colors.background,
            ).statusBarsPadding()
            .testTag(MobileSearchTags.SCREEN),
    ) {
        Row(
            Modifier.padding(horizontal = MobileSizes.gutter, vertical = CineSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MobileTextField(
                value = state.query,
                onValueChange = actions::setQuery,
                label = stringResource(R.string.search_title),
                placeholder = stringResource(R.string.search_placeholder),
                leadingIcon = CineIcons.Search,
                imeAction = ImeAction.Search,
                onAction = { keyboard?.hide() },
                modifier = Modifier.weight(1f).focusRequester(field).testTag(MobileSearchTags.FIELD),
                trailing =
                    if (state.query.isNotEmpty()) {
                        {
                            MobileIconButton(
                                CineIcons.Close,
                                stringResource(R.string.key_clear),
                                actions::clear,
                                Modifier.testTag(MobileSearchTags.CLEAR),
                            )
                        }
                    } else {
                        null
                    },
            )
            if (voice.available) {
                MobileIconButton(
                    CineIcons.Mic,
                    stringResource(R.string.action_voice),
                    voice::start,
                    Modifier.testTag(MobileSearchTags.VOICE),
                    selected = voice.listening,
                )
            }
        }
        VoiceLine(voice)
        Box(Modifier.weight(1f)) {
            if (state.idle) {
                Suggestions(
                    state,
                    sources,
                    actions,
                    open,
                    menu,
                )
            } else {
                Results(state, sources, actions, open, menu)
            }
        }
    }
    state.dialog?.let { RequestSheet(it, actions) }
    MobileQuickMenuHost(menu, navigation)
    if (focusField) {
        LaunchedEffect(Unit) { runCatching { field.requestFocus() } }
    }
}

/** What the voice heard, while it listens, or why it stopped. */
@Composable
private fun VoiceLine(voice: VoiceSearch) {
    val text =
        when {
            voice.listening -> voice.transcript ?: stringResource(R.string.voice_listening)
            voice.problem == VoiceProblem.NoMatch -> stringResource(R.string.voice_no_match)
            voice.problem == VoiceProblem.Denied -> stringResource(R.string.mobile_voice_denied)
            voice.problem == VoiceProblem.Unavailable -> stringResource(R.string.voice_unavailable)
            else -> null
        } ?: return
    Text(
        text,
        style = CineTheme.typography.caption,
        color = if (voice.listening) CineTheme.colors.accent else CineTheme.colors.text2,
        modifier = Modifier.padding(horizontal = MobileSizes.gutter),
    )
}

@Composable
private fun Suggestions(
    state: SearchState,
    sources: ImageSources,
    actions: SearchActions,
    onOpen: (MediaItem) -> Unit,
    menu: MobileQuickMenu,
) {
    LazyColumn(
        contentPadding = PaddingValues(bottom = CineSpacing.m),
        verticalArrangement = Arrangement.spacedBy(CineSpacing.m),
    ) {
        if (state.recent.isNotEmpty()) {
            item(key = "recent") {
                ChipSection(
                    stringResource(R.string.row_recent),
                    state.recent,
                    MobileSearchTags::recent,
                    actions::setQuery,
                ) {
                    MobileTextButton(stringResource(R.string.action_clear_recent), actions::clearRecent)
                }
            }
        }
        if (state.suggestions.genres.isNotEmpty()) {
            item(key = "genres") {
                ChipSection(
                    stringResource(R.string.row_genres),
                    state.suggestions.genres,
                    MobileSearchTags::genre,
                    actions::chooseGenre,
                )
            }
        }
        if (state.suggestions.recentlyAdded.isNotEmpty()) {
            item(key = "latest") {
                CardRow(
                    stringResource(R.string.row_latest),
                    SearchGroup.Movies,
                    state.suggestions.recentlyAdded,
                    sources,
                    onOpen,
                    menu,
                )
            }
        }
    }
}

@Composable
private fun Results(
    state: SearchState,
    sources: ImageSources,
    actions: SearchActions,
    onOpen: (MediaItem) -> Unit,
    menu: MobileQuickMenu,
) {
    val error = state.error
    LazyColumn(
        contentPadding = PaddingValues(bottom = CineSpacing.m),
        verticalArrangement = Arrangement.spacedBy(CineSpacing.m),
    ) {
        state.genre?.let { genre ->
            item(key = "genre") {
                Text(
                    stringResource(R.string.genre_title, genre),
                    style = CineTheme.typography.title1,
                    modifier = Modifier.padding(horizontal = MobileSizes.gutter),
                )
            }
        }
        when {
            state.empty -> {
                item(key = "empty") {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        MobileMessage(
                            stringResource(R.string.results_empty_title, state.query),
                            stringResource(R.string.results_empty_message),
                        )
                    }
                }
            }

            error != null && state.rows.isEmpty() -> {
                item(key = "error") { MobileSourceError(error, actions::retry) }
            }

            error != null -> {
                item(key = "error") {
                    Row(
                        Modifier.padding(horizontal = MobileSizes.gutter),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.results_error),
                            style = CineTheme.typography.caption,
                            color = CineTheme.colors.text2,
                            modifier = Modifier.weight(1f),
                        )
                        MobileTextButton(stringResource(R.string.results_retry), actions::retry)
                    }
                }
            }

            state.pending -> {
                item(key = "pending") {
                    Text(
                        stringResource(R.string.results_pending),
                        style = CineTheme.typography.caption,
                        color = CineTheme.colors.text2,
                        modifier = Modifier.padding(horizontal = MobileSizes.gutter),
                    )
                }
            }
        }
        items(state.rows, key = { it.group.name }) { row ->
            CardRow(stringResource(row.group.title), row.group, row.items, sources, onOpen, menu)
        }
        state.requests?.candidates?.takeIf { it.isNotEmpty() && state.genre == null }?.let { candidates ->
            item(key = "requests") { RequestRow(candidates, actions) }
        }
    }
}

@Composable
private fun CardRow(
    title: String,
    group: SearchGroup,
    items: List<MediaItem>,
    sources: ImageSources,
    onOpen: (MediaItem) -> Unit,
    menu: MobileQuickMenu,
) {
    val landscape = group == SearchGroup.Episodes
    Column(Modifier.testTag(MobileSearchTags.row(group))) {
        MobileRowTitle(title, Modifier.padding(horizontal = MobileSizes.gutter))
        LazyRow(
            contentPadding = PaddingValues(horizontal = MobileSizes.gutter),
            horizontalArrangement = Arrangement.spacedBy(CineSpacing.s),
        ) {
            items(items.size, key = { items[it].id.value }) { index ->
                val item = items[index]
                MobileItemCard(
                    item = item,
                    sources = sources,
                    onClick = { onOpen(item) },
                    poster = !landscape,
                    menu = menu.takeIf { group != SearchGroup.People },
                    tag = MobileSearchTags.card(group, index),
                )
            }
        }
    }
}

@Composable
private fun RequestRow(
    candidates: List<RequestCandidate>,
    actions: SearchActions,
) {
    Column {
        MobileRowTitle(stringResource(R.string.row_request), Modifier.padding(horizontal = MobileSizes.gutter))
        LazyRow(
            contentPadding = PaddingValues(horizontal = MobileSizes.gutter),
            horizontalArrangement = Arrangement.spacedBy(CineSpacing.s),
        ) {
            items(candidates, key = { it.key }) { candidate ->
                MobileCard(
                    title = candidate.title,
                    subtitle = candidate.statusLine(),
                    size = MobileSizes.posterNormal,
                    onClick = { actions.ask(candidate) },
                    image =
                        candidate.posterUrl?.let { url ->
                            { RemoteImage(ImageSource { _, _ -> url }, null, null, Modifier.fillMaxSize()) }
                        },
                )
            }
        }
    }
}

@Composable
private fun ChipSection(
    title: String,
    chips: List<String>,
    tag: (Int) -> String,
    onChip: (String) -> Unit,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        Modifier.padding(horizontal = MobileSizes.gutter),
        verticalArrangement = Arrangement.spacedBy(CineSpacing.xs),
    ) {
        MobileRowTitle(title, action = action)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(CineSpacing.xs),
            verticalArrangement = Arrangement.spacedBy(CineSpacing.xs),
        ) {
            chips.forEachIndexed {
                index,
                chip,
                ->
                MobileChip(chip, selected = false, onClick = { onChip(chip) }, modifier = Modifier.testTag(tag(index)))
            }
        }
    }
}

/** « Demander » on a title of Seerr (J5): a film is confirmed, a series asks for its seasons. */
@Composable
private fun RequestSheet(
    dialog: RequestDialog,
    actions: SearchActions,
) {
    val candidate = dialog.candidate
    when (dialog) {
        is RequestDialog.Confirm -> {
            MobileSheet(stringResource(R.string.request_confirm_title, candidate.title), actions::dismissRequest) {
                Column(
                    Modifier.padding(MobileSizes.gutter),
                    verticalArrangement = Arrangement.spacedBy(CineSpacing.s),
                ) {
                    Text(
                        stringResource(R.string.request_confirm_message),
                        style = CineTheme.typography.body,
                        color = CineTheme.colors.text2,
                    )
                    MobilePrimaryButton(
                        stringResource(R.string.request_confirm),
                        actions::confirmRequest,
                        Modifier.fillMaxWidth(),
                        busy = dialog.sending,
                    )
                    MobileSecondaryButton(
                        stringResource(R.string.request_cancel),
                        actions::dismissRequest,
                        Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        is RequestDialog.Seasons -> {
            MobileSheet(stringResource(R.string.request_seasons_title, candidate.title), actions::dismissRequest) {
                Column(
                    Modifier.padding(MobileSizes.gutter),
                    verticalArrangement = Arrangement.spacedBy(CineSpacing.s),
                ) {
                    val seasons = dialog.seasons
                    when {
                        seasons == null -> {
                            Text(stringResource(R.string.request_seasons_loading), color = CineTheme.colors.text2)
                        }

                        seasons.isEmpty() -> {
                            Text(stringResource(R.string.request_seasons_none), color = CineTheme.colors.text2)
                        }

                        else -> {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(CineSpacing.xs),
                                verticalArrangement = Arrangement.spacedBy(CineSpacing.xs),
                            ) {
                                seasons.forEach { season ->
                                    MobileChip(
                                        season.name ?: stringResource(R.string.request_season, season.number),
                                        selected = season.number in dialog.chosen,
                                        onClick = { actions.toggleSeason(season.number) },
                                    )
                                }
                            }
                        }
                    }
                    MobilePrimaryButton(
                        pluralStringResource(R.plurals.request_seasons_confirm, dialog.chosen.size, dialog.chosen.size),
                        actions::confirmRequest,
                        Modifier.fillMaxWidth(),
                        enabled = dialog.chosen.isNotEmpty(),
                        busy = dialog.sending,
                    )
                }
            }
        }

        is RequestDialog.Failed -> {
            MobileSheet(stringResource(R.string.request_failed_title), actions::dismissRequest) {
                Column(
                    Modifier.padding(MobileSizes.gutter),
                    verticalArrangement = Arrangement.spacedBy(CineSpacing.s),
                ) {
                    Text(
                        stringResource(R.string.request_failed_message),
                        style = CineTheme.typography.body,
                        color = CineTheme.colors.text2,
                    )
                    MobilePrimaryButton(
                        stringResource(R.string.request_retry),
                        actions::confirmRequest,
                        Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

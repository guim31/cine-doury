package fr.guim.cinedoury.feature.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import fr.guim.cinedoury.core.data.SessionManager
import fr.guim.cinedoury.core.designsystem.component.AmbientBackdrop
import fr.guim.cinedoury.core.designsystem.component.CineIcons
import fr.guim.cinedoury.core.designsystem.component.CineTextField
import fr.guim.cinedoury.core.designsystem.component.FocusPivot
import fr.guim.cinedoury.core.designsystem.component.InitialFocus
import fr.guim.cinedoury.core.designsystem.component.PosterCard
import fr.guim.cinedoury.core.designsystem.component.PosterSize
import fr.guim.cinedoury.core.designsystem.component.RowTitle
import fr.guim.cinedoury.core.designsystem.component.SearchChip
import fr.guim.cinedoury.core.designsystem.component.SearchQueryLine
import fr.guim.cinedoury.core.designsystem.component.ThumbnailCard
import fr.guim.cinedoury.core.designsystem.component.VoiceListening
import fr.guim.cinedoury.core.designsystem.component.keySpan
import fr.guim.cinedoury.core.designsystem.component.rememberCineFocus
import fr.guim.cinedoury.core.designsystem.image.ImageSource
import fr.guim.cinedoury.core.designsystem.image.RemoteImage
import fr.guim.cinedoury.core.designsystem.theme.CineSizes
import fr.guim.cinedoury.core.designsystem.theme.CineSpacing
import fr.guim.cinedoury.core.designsystem.theme.CineTheme
import fr.guim.cinedoury.core.model.MediaItem
import fr.guim.cinedoury.core.model.RequestCandidate
import fr.guim.cinedoury.core.model.RequestStatus
import fr.guim.cinedoury.core.model.SearchGroup
import fr.guim.cinedoury.core.ui.AppRail
import fr.guim.cinedoury.core.ui.CollectEvents
import fr.guim.cinedoury.core.ui.ImageSources
import fr.guim.cinedoury.core.ui.RailTarget
import fr.guim.cinedoury.core.ui.ambience
import fr.guim.cinedoury.core.ui.cardSubtitle
import fr.guim.cinedoury.core.ui.cardTitle
import fr.guim.cinedoury.core.ui.imageSource
import fr.guim.cinedoury.core.ui.landscapeImage
import fr.guim.cinedoury.core.ui.posterImage
import fr.guim.cinedoury.core.ui.rememberArt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/** What the search asks the app to open. */
interface SearchNavigation {
    fun navigate(target: RailTarget)

    /** The details of [item] (J3). */
    fun open(item: MediaItem)

    fun sessionEnded()
}

/**
 * The search screen.
 *
 * @param voiceAtStart opened by the voice key of the remote: listens at once.
 * @param voiceRequests the voice key pressed while the search is shown.
 */
@Composable
fun SearchRoute(
    navigation: SearchNavigation,
    voiceAtStart: Boolean = false,
    voiceRequests: Flow<Unit> = emptyFlow(),
    viewModel: SearchViewModel = koinViewModel(),
    sessions: SessionManager = koinInject(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectEvents(viewModel.sessionEnded) { navigation.sessionEnded() }
    val voice = rememberVoiceSearch(onResult = viewModel::setQuery)
    var voiceStarted by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (voiceAtStart && !voiceStarted && voice.available) voice.start()
        voiceStarted = true
    }
    LaunchedEffect(voiceRequests) { voiceRequests.collect { if (voice.available) voice.start() } }
    SearchScreen(
        state = state,
        sources = sessions::imageSource,
        actions = viewModel,
        navigation = navigation,
        voice = voice,
    )
}

/** Preview tags of the search, for tests and screenshots. */
object SearchPreview {
    const val RECENT_CLEAR = "recent-clear"
    const val SEASONS_CONFIRM = "seasons-confirm"
    const val DIALOG_CONFIRM = "request-confirm"
    const val DIALOG_CANCEL = "request-cancel"
    const val SYSTEM_FIELD = "system-field"

    fun card(
        row: String,
        index: Int,
    ) = "search-$row-$index"

    fun recent(index: Int) = "recent-$index"

    fun genre(index: Int) = "genre-$index"

    fun season(index: Int) = "season-$index"
}

/** The keys of the rows of the screen, for tests and screenshots. */
object SearchRows {
    const val LATEST = "latest"
    const val REQUESTS = "requests"

    fun of(group: SearchGroup) = group.name.lowercase()
}

/**
 * The search (`UI.md` § 8, `FONCTIONNALITES.md` § 6): the rail; on the left, the query and the
 * on-screen keyboard; on the right, the results in rows (films, series, episodes, people,
 * collections, then « Demander » with Seerr), or, with nothing typed, the recent searches, the
 * latest additions and the genres. The ambient backdrop follows the focused card.
 *
 * « Retour » closes a dialog or the keyboard of the system, else goes back. Coming back from a
 * details screen finds the query, the results and the focused card again.
 *
 * @param previewPage the page of the keyboard (screenshots).
 * @param previewSystemKeyboard the keyboard of the system open (screenshots).
 */
@Composable
fun SearchScreen(
    state: SearchState,
    sources: ImageSources,
    actions: SearchActions,
    navigation: SearchNavigation,
    voice: VoiceSearch,
    modifier: Modifier = Modifier,
    previewRail: RailTarget? = null,
    previewPage: KeyboardPage = KeyboardPage.Letters,
    previewSystemKeyboard: Boolean = false,
) {
    var focusedItem by remember { mutableStateOf<MediaItem?>(null) }
    // The row that had the focus: « Retour » from a details screen comes back to it.
    var focusedRow by rememberSaveable { mutableStateOf<String?>(null) }
    var systemKeyboard by rememberSaveable { mutableStateOf(previewSystemKeyboard) }
    val first = remember { FocusRequester() }
    val keyboard = remember { FocusRequester() }
    val rowRequesters = remember { mutableMapOf<String, FocusRequester>() }
    val open: (MediaItem) -> Unit = { item ->
        actions.opened()
        navigation.open(item)
    }

    BackHandler(enabled = state.dialog != null || systemKeyboard || voice.listening) {
        when {
            voice.listening -> voice.cancel()
            state.dialog != null -> actions.dismissRequest()
            else -> systemKeyboard = false
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AmbientBackdrop(ambience = focusedItem?.ambience)
        Row(
            Modifier
                .fillMaxSize()
                .padding(start = ContentStart),
        ) {
            Column(
                modifier =
                    Modifier
                        .padding(top = CineSpacing.safeVertical + CineSpacing.s)
                        .width(keySpan(KEYS_PER_ROW)),
                verticalArrangement = Arrangement.spacedBy(CineSpacing.s),
            ) {
                if (systemKeyboard) {
                    SystemKeyboardField(
                        query = state.query,
                        onQuery = actions::setQuery,
                        onClose = { systemKeyboard = false },
                    )
                } else {
                    SearchQueryLine(
                        text = state.genre?.let { stringResource(R.string.genre_title, it) } ?: state.query,
                        placeholder = stringResource(R.string.search_placeholder),
                        modifier = Modifier.fillMaxWidth().testTag("query"),
                    )
                }
                SearchKeyboard(
                    onType = actions::type,
                    onErase = actions::erase,
                    onClear = actions::clear,
                    onVoice = if (voice.available) voice::start else null,
                    onSystemKeyboard = { systemKeyboard = true },
                    first = first,
                    page = previewPage,
                    // The key left, for → from the open rail (essai réel 3).
                    modifier =
                        Modifier
                            .onFocusChanged { if (it.hasFocus) focusedRow = null }
                            .focusRequester(keyboard)
                            .focusRestorer(first)
                            .focusGroup(),
                )
                voice.problem?.let { problem ->
                    Text(
                        text = stringResource(problem.message),
                        style = CineTheme.typography.caption,
                        color = CineTheme.colors.text2,
                        modifier = Modifier.testTag("voice-problem"),
                    )
                }
            }
            Results(
                state = state,
                sources = sources,
                actions = actions,
                onOpen = open,
                requesters = rowRequesters,
                onFocused = { row, item ->
                    focusedRow = row
                    focusedItem = item
                },
                modifier =
                    Modifier
                        .padding(start = CineSpacing.l)
                        .fillMaxHeight()
                        .weight(1f),
            )
        }
        AppRail(
            state = state.rail,
            selected = RailTarget.Search,
            onNavigate = navigation::navigate,
            // → from the open rail: the row of results left (it gives back its card), else the key left.
            onContentFocus = { (focusedRow?.let { rowRequesters.requester(it) } ?: keyboard).requestFocus() },
            previewFocused = previewRail,
        )
        RequestDialogs(state.dialog, actions)
        if (voice.listening) {
            VoiceListening(
                title = stringResource(R.string.voice_listening),
                transcript = voice.transcript,
                hint = stringResource(R.string.voice_hint),
                level = { voice.level },
                modifier = Modifier.testTag("voice"),
            )
        }
    }
    // The first focus on « a »; back from a details screen, the card left.
    val restore = focusedRow?.let { rowRequesters.requester(it) }
    InitialFocus(restore ?: first, enabled = !systemKeyboard)
}

/** The rows start right of the closed rail. */
internal val ContentStart = CineSizes.railCollapsed + CineSpacing.m

private const val KEYS_PER_ROW = 7

private val VoiceProblem.message: Int
    get() =
        when (this) {
            VoiceProblem.NoMatch -> R.string.voice_no_match
            VoiceProblem.Denied -> R.string.voice_denied
            VoiceProblem.Unavailable -> R.string.voice_unavailable
        }

internal fun MutableMap<String, FocusRequester>.requester(key: String): FocusRequester =
    getOrPut(key) {
        FocusRequester()
    }

/**
 * The keyboard of the system, in place of the query line: it opens at once; « OK » of the keyboard,
 * ↑ or ↓ close it (a text field keeps the focus on ↓ otherwise: the focus would be trapped).
 */
@Composable
private fun SystemKeyboardField(
    query: String,
    onQuery: (String) -> Unit,
    onClose: () -> Unit,
) {
    val requester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    CineTextField(
        value = query,
        onValueChange = onQuery,
        label = stringResource(R.string.system_keyboard_label),
        placeholder = stringResource(R.string.search_placeholder),
        imeAction = ImeAction.Search,
        onImeAction = {
            keyboard?.hide()
            onClose()
        },
        width = keySpan(KEYS_PER_ROW),
        focusRequester = requester,
        focus = rememberCineFocus(SearchPreview.SYSTEM_FIELD),
        modifier =
            Modifier
                .testTag(SearchPreview.SYSTEM_FIELD)
                .onFocusChanged {
                    if (focused && !it.hasFocus) onClose()
                    focused = it.hasFocus
                }.onPreviewKeyEvent { event ->
                    val vertical = event.key == Key.DirectionDown || event.key == Key.DirectionUp
                    if (vertical && event.type == KeyEventType.KeyDown) {
                        keyboard?.hide()
                        focusManager.moveFocus(
                            if (event.key ==
                                Key.DirectionDown
                            ) {
                                FocusDirection.Down
                            } else {
                                FocusDirection.Up
                            },
                        )
                        onClose()
                        true
                    } else {
                        false
                    }
                },
    )
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { requester.requestFocus() }
        keyboard?.show()
    }
}

/** The right side: suggestions with nothing typed, else the rows of results. */
@Composable
private fun Results(
    state: SearchState,
    sources: ImageSources,
    actions: SearchActions,
    onOpen: (MediaItem) -> Unit,
    requesters: MutableMap<String, FocusRequester>,
    onFocused: (String, MediaItem?) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        state = rememberLazyListState(),
        modifier = modifier.testTag("results"),
        contentPadding = PaddingValues(top = CineSpacing.safeVertical + CineSpacing.s, bottom = CineSpacing.xxl),
        verticalArrangement = Arrangement.spacedBy(CineSpacing.l),
    ) {
        if (state.idle) {
            suggestions(state, sources, actions, onOpen, requesters, onFocused)
        } else {
            results(state, sources, actions, onOpen, requesters, onFocused)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.suggestions(
    state: SearchState,
    sources: ImageSources,
    actions: SearchActions,
    onOpen: (MediaItem) -> Unit,
    requesters: MutableMap<String, FocusRequester>,
    onFocused: (String, MediaItem?) -> Unit,
) {
    if (state.recent.isNotEmpty()) {
        item(key = "recent", contentType = "chips") {
            ChipSection(
                title = stringResource(R.string.row_recent),
                chips = state.recent,
                icon = CineIcons.History,
                tag = SearchPreview::recent,
                onChip = actions::setQuery,
                trailing = stringResource(R.string.action_clear_recent) to actions::clearRecent,
                trailingTag = SearchPreview.RECENT_CLEAR,
                restore = requesters.requester("recent"),
                onFocused = { onFocused("recent", null) },
            )
        }
    }
    val latest = state.suggestions.recentlyAdded
    if (latest.isNotEmpty()) {
        item(key = SearchRows.LATEST, contentType = "posters") {
            ResultRow(
                key = SearchRows.LATEST,
                title = stringResource(R.string.row_latest),
                group = SearchGroup.Movies,
                items = latest,
                sources = sources,
                onOpen = onOpen,
                restore = requesters.requester(SearchRows.LATEST),
                onFocused = onFocused,
            )
        }
    }
    if (state.suggestions.genres.isNotEmpty()) {
        item(key = "genres", contentType = "chips") {
            ChipSection(
                title = stringResource(R.string.row_genres),
                chips = state.suggestions.genres,
                icon = null,
                tag = SearchPreview::genre,
                onChip = actions::chooseGenre,
                restore = requesters.requester("genres"),
                onFocused = { onFocused("genres", null) },
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.results(
    state: SearchState,
    sources: ImageSources,
    actions: SearchActions,
    onOpen: (MediaItem) -> Unit,
    requesters: MutableMap<String, FocusRequester>,
    onFocused: (String, MediaItem?) -> Unit,
) {
    status(state, actions)
    state.rows.forEach { row ->
        val key = SearchRows.of(row.group)
        item(key = key, contentType = if (row.group == SearchGroup.Episodes) "thumbnails" else "posters") {
            ResultRow(
                key = key,
                title = stringResource(row.group.title),
                group = row.group,
                items = row.items,
                sources = sources,
                onOpen = onOpen,
                restore = requesters.requester(key),
                onFocused = onFocused,
            )
        }
    }
    val requests = state.requests
    if (requests != null && state.genre == null) {
        when {
            requests.candidates.isNotEmpty() -> {
                item(key = SearchRows.REQUESTS, contentType = "posters") {
                    RequestRow(requests.candidates, actions, requesters.requester(SearchRows.REQUESTS), onFocused)
                }
            }

            requests.failed -> {
                item(
                    key = "requests-failed",
                    contentType = "status",
                ) { StatusLine(stringResource(R.string.request_failed_row)) }
            }
        }
    }
}

/** Where the search stands: a failure, the server still searching, nothing found. */
private fun androidx.compose.foundation.lazy.LazyListScope.status(
    state: SearchState,
    actions: SearchActions,
) {
    val error = state.error
    when {
        error != null -> {
            item(key = "error", contentType = "status") {
                Column(verticalArrangement = Arrangement.spacedBy(CineSpacing.s)) {
                    StatusLine(stringResource(R.string.results_error))
                    SearchChip(
                        text = stringResource(R.string.results_retry),
                        icon = CineIcons.Retry,
                        onClick = actions::retry,
                        modifier = Modifier.testTag("retry"),
                    )
                }
            }
        }

        state.pending && state.rows.isEmpty() -> {
            item(key = "pending", contentType = "status") { StatusLine(stringResource(R.string.results_pending)) }
        }

        state.empty -> {
            item(key = "empty", contentType = "status") {
                Column(
                    verticalArrangement = Arrangement.spacedBy(CineSpacing.xs),
                    modifier = Modifier.testTag("empty"),
                ) {
                    Text(
                        text = stringResource(R.string.results_empty_title, state.query),
                        style = CineTheme.typography.title2,
                        color = CineTheme.colors.text,
                    )
                    StatusLine(stringResource(R.string.results_empty_message))
                }
            }
        }
    }
}

@Composable
private fun StatusLine(text: String) {
    Text(text = text, style = CineTheme.typography.body, color = CineTheme.colors.text2)
}

private val SearchGroup.title: Int
    get() =
        when (this) {
            SearchGroup.Movies -> R.string.row_movies
            SearchGroup.Series -> R.string.row_series
            SearchGroup.Episodes -> R.string.row_episodes
            SearchGroup.People -> R.string.row_people
            SearchGroup.Collections -> R.string.row_collections
        }

/**
 * A row of results. It remembers its focused card, gives it back when the focus returns (even after
 * a details screen) and keeps it in its first column.
 */
@Composable
private fun ResultRow(
    key: String,
    title: String,
    group: SearchGroup,
    items: List<MediaItem>,
    sources: ImageSources,
    onOpen: (MediaItem) -> Unit,
    restore: FocusRequester,
    onFocused: (String, MediaItem?) -> Unit,
) {
    var last by rememberSaveable(key) { mutableIntStateOf(0) }
    Column(
        modifier = Modifier.testTag("row-$key"),
        verticalArrangement = Arrangement.spacedBy(CineSpacing.s),
    ) {
        RowTitle(title)
        FocusPivot(offset = CineSpacing.xs) {
            LazyRow(
                state = rememberLazyListState(),
                modifier = Modifier.focusRestorer(restore),
                contentPadding = PaddingValues(horizontal = CineSpacing.xs),
                horizontalArrangement = Arrangement.spacedBy(CineSpacing.m),
            ) {
                itemsIndexed(items, key = {
                    _,
                    item,
                    ->
                    item.id.value
                }, contentType = { _, _ -> group }) { index, item ->
                    val tag = SearchPreview.card(key, index)
                    val cardModifier =
                        Modifier.testTag(tag).onFocusChanged {
                            if (it.hasFocus) {
                                last = index
                                onFocused(key, item)
                            }
                        }
                    val requester = restore.takeIf { index == last.coerceAtMost(items.lastIndex) }
                    ResultCard(item, group, sources, { onOpen(item) }, requester, tag, cardModifier)
                }
            }
        }
    }
}

@Composable
private fun ResultCard(
    item: MediaItem,
    group: SearchGroup,
    sources: ImageSources,
    onClick: () -> Unit,
    focusRequester: FocusRequester?,
    tag: String,
    modifier: Modifier,
) {
    val focus = rememberCineFocus(tag)
    if (group == SearchGroup.Episodes) {
        ThumbnailCard(
            title = item.cardTitle,
            subtitle = item.cardSubtitle(),
            onClick = onClick,
            progress = item.progress,
            unwatched = item.isUnwatched && item.progress == null,
            focusRequester = focusRequester,
            focus = focus,
            image = rememberArt(item.landscapeImage, sources),
            modifier = modifier,
        )
    } else {
        val person = group == SearchGroup.People
        PosterCard(
            title = item.cardTitle,
            subtitle = if (person) null else item.cardSubtitle(),
            size = PosterSize.Small,
            progress = item.progress,
            unwatched = item.isUnwatched,
            unwatchedCount = item.unwatchedEpisodes,
            alwaysShowTitle = person,
            onClick = onClick,
            focusRequester = focusRequester,
            focus = focus,
            sharedKey = item.id.value.takeIf { !person },
            image = rememberArt(item.posterImage, sources),
            modifier = modifier,
        )
    }
}

/** « Pas dans la bibliothèque ? Demander »: the titles of Seerr, with their year and status. */
@Composable
private fun RequestRow(
    candidates: List<RequestCandidate>,
    actions: SearchActions,
    restore: FocusRequester,
    onFocused: (String, MediaItem?) -> Unit,
) {
    var last by rememberSaveable(SearchRows.REQUESTS) { mutableIntStateOf(0) }
    Column(
        modifier = Modifier.testTag("row-${SearchRows.REQUESTS}"),
        verticalArrangement = Arrangement.spacedBy(CineSpacing.s),
    ) {
        RowTitle(stringResource(R.string.row_request))
        FocusPivot(offset = CineSpacing.xs) {
            LazyRow(
                state = rememberLazyListState(),
                modifier = Modifier.focusRestorer(restore),
                contentPadding = PaddingValues(horizontal = CineSpacing.xs),
                horizontalArrangement = Arrangement.spacedBy(CineSpacing.m),
            ) {
                itemsIndexed(candidates, key = {
                    _,
                    candidate,
                    ->
                    candidate.key
                }, contentType = { _, _ -> "request" }) { index, candidate ->
                    val tag = SearchPreview.card(SearchRows.REQUESTS, index)
                    val poster = candidate.posterUrl
                    val image =
                        remember(poster) {
                            poster?.let { url ->
                                @Composable {
                                    RemoteImage(
                                        ImageSource {
                                            _,
                                            _,
                                            ->
                                            url
                                        },
                                        null,
                                        contentDescription = null,
                                        Modifier.fillMaxSize(),
                                    )
                                }
                            }
                        }
                    PosterCard(
                        title = candidate.title,
                        subtitle = candidate.statusLine(),
                        size = PosterSize.Small,
                        alwaysShowTitle = true,
                        onClick = { actions.ask(candidate) },
                        focusRequester = restore.takeIf { index == last.coerceAtMost(candidates.lastIndex) },
                        focus = rememberCineFocus(tag),
                        image = image,
                        modifier =
                            Modifier.testTag(tag).onFocusChanged {
                                if (it.hasFocus) {
                                    last = index
                                    onFocused(SearchRows.REQUESTS, null)
                                }
                            },
                    )
                }
            }
        }
    }
}

/** « 2001 · Demandé », « 2022 », « Disponible ». */
@Composable
private fun RequestCandidate.statusLine(): String? {
    val status =
        when (status) {
            RequestStatus.None -> null
            RequestStatus.Requested -> stringResource(R.string.status_requested)
            RequestStatus.PartlyAvailable -> stringResource(R.string.status_partly)
            RequestStatus.Available -> stringResource(R.string.status_available)
        }
    val year = year?.toString()
    return when {
        year != null && status != null -> stringResource(R.string.status_line, year, status)
        else -> status ?: year
    }
}

/** Recent searches or genres: a title, then chips on as many lines as needed. */
@Composable
private fun ChipSection(
    title: String,
    chips: List<String>,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    tag: (Int) -> String,
    onChip: (String) -> Unit,
    restore: FocusRequester,
    onFocused: () -> Unit,
    trailing: Pair<String, () -> Unit>? = null,
    trailingTag: String? = null,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(CineSpacing.s),
        modifier = Modifier.onFocusChanged { if (it.hasFocus) onFocused() },
    ) {
        RowTitle(title)
        FlowRow(
            modifier = Modifier.padding(horizontal = CineSpacing.xs).focusRestorer(restore),
            horizontalArrangement = Arrangement.spacedBy(CineSpacing.s),
            verticalArrangement = Arrangement.spacedBy(CineSpacing.s),
        ) {
            chips.forEachIndexed { index, chip ->
                SearchChip(
                    text = chip,
                    icon = icon,
                    onClick = { onChip(chip) },
                    focusRequester = restore.takeIf { index == 0 },
                    focus = rememberCineFocus(tag(index)),
                    modifier = Modifier.testTag(tag(index)),
                )
            }
            if (trailing != null) {
                SearchChip(
                    text = trailing.first,
                    icon = CineIcons.Close,
                    onClick = trailing.second,
                    focus = rememberCineFocus(trailingTag),
                    modifier = Modifier.testTag(trailingTag.orEmpty()),
                )
            }
        }
    }
}

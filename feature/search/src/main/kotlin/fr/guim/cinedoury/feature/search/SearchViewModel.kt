package fr.guim.cinedoury.feature.search

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.guim.cinedoury.core.data.ActiveSession
import fr.guim.cinedoury.core.data.SessionManager
import fr.guim.cinedoury.core.data.home.HomeRepository
import fr.guim.cinedoury.core.data.home.ProfileKey
import fr.guim.cinedoury.core.data.library.LibraryIconRepository
import fr.guim.cinedoury.core.data.search.IndexedTitle
import fr.guim.cinedoury.core.data.search.RequestSettings
import fr.guim.cinedoury.core.data.search.SearchRepository
import fr.guim.cinedoury.core.data.search.SearchSuggestions
import fr.guim.cinedoury.core.model.ItemKind
import fr.guim.cinedoury.core.model.LibraryId
import fr.guim.cinedoury.core.model.RequestCandidate
import fr.guim.cinedoury.core.model.RequestKind
import fr.guim.cinedoury.core.model.RequestSeason
import fr.guim.cinedoury.core.model.RequestStatus
import fr.guim.cinedoury.core.model.SearchHit
import fr.guim.cinedoury.core.model.SearchRanking
import fr.guim.cinedoury.core.model.SearchRow
import fr.guim.cinedoury.core.model.SearchText
import fr.guim.cinedoury.core.source.MediaRequests
import fr.guim.cinedoury.core.source.RequestConnector
import fr.guim.cinedoury.core.source.SourceException
import fr.guim.cinedoury.core.ui.Events
import fr.guim.cinedoury.core.ui.RailState
import fr.guim.cinedoury.core.ui.railState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/** The Seerr row: what the request server found that the library lacks. */
@Immutable
data class RequestsState(
    val candidates: List<RequestCandidate> = emptyList(),
    val failed: Boolean = false,
)

/** « Demander » open on a title: confirm a film, choose the seasons of a series. */
@Immutable
sealed interface RequestDialog {
    val candidate: RequestCandidate
    val sending: Boolean

    data class Confirm(
        override val candidate: RequestCandidate,
        override val sending: Boolean = false,
    ) : RequestDialog

    /** [seasons] null while the server reads them. */
    data class Seasons(
        override val candidate: RequestCandidate,
        val seasons: List<RequestSeason>? = null,
        val chosen: Set<Int> = emptySet(),
        override val sending: Boolean = false,
    ) : RequestDialog

    data class Failed(
        override val candidate: RequestCandidate,
        val error: SourceException,
    ) : RequestDialog {
        override val sending: Boolean get() = false
    }
}

/**
 * What the search shows (`FONCTIONNALITES.md` § 6).
 *
 * @param genre a genre chosen among the suggestions: its titles instead of a query.
 * @param pending the server has not answered for this query yet (the local results are shown).
 * @param error the server search failed; the local results stay.
 * @param requests null when no request server is set up: nothing of Seerr shows.
 */
@Immutable
data class SearchState(
    val rail: RailState = RailState(),
    val query: String = "",
    val genre: String? = null,
    val rows: List<SearchRow> = emptyList(),
    val pending: Boolean = false,
    val error: SourceException? = null,
    val recent: List<String> = emptyList(),
    val suggestions: SearchSuggestions = SearchSuggestions(),
    val requests: RequestsState? = null,
    val dialog: RequestDialog? = null,
) {
    /** Nothing typed nor chosen: recent searches and suggestions. */
    val idle: Boolean get() = query.isBlank() && genre == null

    /** The server answered and nothing matches. */
    val empty: Boolean get() = !idle && !pending && rows.isEmpty() && requests?.candidates.isNullOrEmpty()
}

/** What the search screen asks its view model; a no-op for screenshots. */
@Suppress("TooManyFunctions") // One function per gesture of the screen.
interface SearchActions {
    /** A key of the on-screen keyboard. */
    fun type(text: String)

    fun erase()

    fun clear()

    /** The whole query: the keyboard of the system, the voice, a recent search. */
    fun setQuery(text: String)

    fun chooseGenre(genre: String)

    /** A result is opened: the query becomes a recent search. */
    fun opened()

    fun clearRecent()

    fun retry()

    /** « Demander » on a title of Seerr. */
    fun ask(candidate: RequestCandidate)

    fun toggleSeason(number: Int)

    fun confirmRequest()

    fun dismissRequest()
}

/**
 * The search (`SPEC.md` § 4.12): at each keystroke, the title index on the device; once the typing
 * pauses ([DEBOUNCE_MILLIS]), the server confirms and completes, and Seerr, when set up, adds what
 * the library lacks.
 */
@Suppress("TooManyFunctions") // One function per gesture of the screen.
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModel(
    private val sessions: SessionManager,
    private val search: SearchRepository,
    home: HomeRepository,
    icons: LibraryIconRepository,
    requestSettings: RequestSettings,
    private val requestConnector: RequestConnector,
) : ViewModel(),
    SearchActions {
    private val active: ActiveSession? = sessions.active.value
    private val profile = active?.let { ProfileKey(it.profile.serverId, it.profile.user.id.value) }
    private val local = MutableStateFlow(SearchState())
    private val input = MutableStateFlow(Input("", null))

    /** The request server, once its settings are read: a search waits for it, or runs again. */
    private val requests = MutableStateFlow<MediaRequests?>(null)
    private var sending: Job? = null

    val state: StateFlow<SearchState> = local.asStateFlow()

    /** The server rejected the token: the profile is signed out. */
    val sessionEnded = Events<Unit>()

    private data class Run(
        val input: Input,
        val titles: List<IndexedTitle>,
        val libraries: Set<LibraryId>,
        val requests: MediaRequests?,
    )

    private data class Input(
        val query: String,
        val genre: String?,
        /** Bumped by « Réessayer »: the same query again. */
        val attempt: Int = 0,
    )

    init {
        if (profile != null && active != null) {
            viewModelScope.launch {
                home.railState(sessions, icons).collect { rail ->
                    local.update { it.copy(rail = rail) }
                }
            }
            viewModelScope.launch {
                search.recent(profile).collect { recent ->
                    local.update { it.copy(recent = recent) }
                }
            }
            viewModelScope.launch {
                requestSettings.server(profile.serverId).collect { server ->
                    requests.value = server?.let { requestConnector.open(it.address, it.apiKey, profile.userId) }
                    local.update { it.copy(requests = if (server == null) null else it.requests ?: RequestsState()) }
                }
            }
            val libraries =
                home
                    .libraries(profile)
                    .map { cached -> cached.libraries.map { it.id }.toSet() }
                    .distinctUntilChanged()
            viewModelScope.launch { suggest(active) }
            viewModelScope.launch {
                combine(input, search.titles(profile.serverId), libraries, requests) { query, titles, shown, client ->
                    Run(query, titles, shown, client)
                }.collectLatest { run(active, it) }
            }
        }
    }

    private suspend fun suggest(active: ActiveSession) {
        try {
            val libraries = active.library.libraries()
            val suggestions = search.suggestions(active.library, libraries)
            local.update { it.copy(suggestions = suggestions) }
        } catch (_: SourceException.Unauthorized) {
            ended()
        } catch (_: SourceException) {
            // No suggestions: the recent searches and the keyboard remain.
        }
    }

    private suspend fun run(
        active: ActiveSession,
        run: Run,
    ) {
        val input = run.input
        val genre = input.genre
        when {
            genre != null -> {
                local.update { it.copy(rows = emptyList(), pending = true, error = null) }
                serverStep { search.byGenre(active.library, genre) }
            }

            input.query.isBlank() -> {
                local.update {
                    it.copy(
                        rows = emptyList(),
                        pending = false,
                        error = null,
                        requests = it.requests?.let { RequestsState() },
                    )
                }
            }

            else -> {
                val found = withContext(Dispatchers.Default) { search.local(input.query, run.titles, run.libraries) }
                local.update { it.copy(rows = SearchRanking.rows(found), pending = true, error = null) }
                delay(DEBOUNCE_MILLIS)
                coroutineScope {
                    run.requests?.let { client -> launch { askSeerr(client, input.query) } }
                    serverStep { search.complete(active.library, input.query, found) }
                }
            }
        }
    }

    private suspend fun serverStep(block: suspend () -> List<SearchHit>) {
        try {
            val hits = block()
            local.update { state ->
                val rows = SearchRanking.rows(hits)
                state.copy(rows = rows, pending = false, error = null, requests = state.requests?.notIn(rows))
            }
        } catch (_: SourceException.Unauthorized) {
            ended()
        } catch (failure: SourceException) {
            local.update { it.copy(pending = false, error = failure) }
        }
    }

    private suspend fun askSeerr(
        client: MediaRequests,
        query: String,
    ) {
        val found =
            try {
                RequestsState(client.search(query, Locale.getDefault().language))
            } catch (_: SourceException) {
                RequestsState(failed = true)
            }
        local.update { state -> state.copy(requests = found.notIn(state.rows)) }
    }

    /** The titles of Seerr the library already holds are left out (by their Jellyfin id, else by title and year). */
    private fun RequestsState.notIn(rows: List<SearchRow>): RequestsState {
        val items = rows.flatMap { it.items }.filter { it.kind == ItemKind.Movie || it.kind == ItemKind.Series }
        val ids = items.map { it.id.value.lowercase() }.toSet()
        val titles = items.map { SearchText.normalize(it.title) to it.year }.toSet()
        return copy(
            candidates =
                candidates.filterNot { candidate ->
                    candidate.libraryItemId in ids ||
                        (SearchText.normalize(candidate.title) to candidate.year) in titles
                },
        )
    }

    private fun ended() {
        viewModelScope.launch {
            sessions.sessionRejected()
            sessionEnded.send(Unit)
        }
    }

    private fun setInput(
        query: String,
        genre: String? = null,
    ) {
        input.value = Input(query, genre)
        local.update { it.copy(query = query, genre = genre) }
    }

    override fun type(text: String) {
        val current = local.value.query
        // A space never starts a query nor doubles.
        if (text == " " && (current.isEmpty() || current.endsWith(" "))) return
        if (current.length >= MAX_LENGTH) return
        setInput(current + text)
    }

    override fun erase() {
        val state = local.value
        if (state.genre != null) setInput("") else setInput(state.query.dropLast(1))
    }

    override fun clear() = setInput("")

    override fun setQuery(text: String) = setInput(text.take(MAX_LENGTH))

    override fun chooseGenre(genre: String) = setInput("", genre)

    override fun opened() {
        val profile = profile ?: return
        val query = local.value.query
        if (query.isBlank()) return
        viewModelScope.launch { search.remember(profile, query) }
    }

    override fun clearRecent() {
        val profile = profile ?: return
        viewModelScope.launch { search.clearRecent(profile) }
    }

    override fun retry() {
        input.update { it.copy(attempt = it.attempt + 1) }
    }

    override fun ask(candidate: RequestCandidate) {
        if (!candidate.status.requestable) return
        if (candidate.kind == RequestKind.Movie) {
            local.update { it.copy(dialog = RequestDialog.Confirm(candidate)) }
            return
        }
        local.update { it.copy(dialog = RequestDialog.Seasons(candidate)) }
        val client = requests.value ?: return
        viewModelScope.launch {
            try {
                val seasons = client.seasons(candidate.tmdbId, Locale.getDefault().language)
                // Proposed: every season not there nor asked for yet.
                val chosen = seasons.filter { it.status.requestable }.map { it.number }.toSet()
                local.update { state ->
                    val dialog = state.dialog as? RequestDialog.Seasons
                    if (dialog?.candidate ==
                        candidate
                    ) {
                        state.copy(dialog = dialog.copy(seasons = seasons, chosen = chosen))
                    } else {
                        state
                    }
                }
            } catch (failure: SourceException) {
                local.update { state ->
                    if (state.dialog?.candidate ==
                        candidate
                    ) {
                        state.copy(dialog = RequestDialog.Failed(candidate, failure))
                    } else {
                        state
                    }
                }
            }
        }
    }

    override fun toggleSeason(number: Int) {
        local.update { state ->
            val dialog = state.dialog as? RequestDialog.Seasons ?: return@update state
            val season = dialog.seasons?.firstOrNull { it.number == number } ?: return@update state
            if (!season.status.requestable) return@update state
            val chosen = if (number in dialog.chosen) dialog.chosen - number else dialog.chosen + number
            state.copy(dialog = dialog.copy(chosen = chosen))
        }
    }

    override fun confirmRequest() {
        when (val dialog = local.value.dialog ?: return) {
            // « Réessayer »: from the start (the seasons are read again).
            is RequestDialog.Failed -> {
                ask(dialog.candidate)
            }

            is RequestDialog.Confirm -> {
                send(dialog, emptyList()) { dialog.copy(sending = true) }
            }

            is RequestDialog.Seasons -> {
                if (dialog.chosen.isNotEmpty()) {
                    send(dialog, dialog.chosen.sorted()) {
                        dialog.copy(sending = true)
                    }
                }
            }
        }
    }

    private fun send(
        dialog: RequestDialog,
        seasons: List<Int>,
        sendingDialog: () -> RequestDialog,
    ) {
        val client = requests.value ?: return
        if (dialog.sending || sending?.isActive == true) return
        local.update { it.copy(dialog = sendingDialog()) }
        sending =
            viewModelScope.launch {
                try {
                    val status = client.request(dialog.candidate, seasons)
                    local.update { state ->
                        val requests = state.requests
                        state.copy(
                            dialog = null,
                            requests =
                                requests?.copy(
                                    candidates =
                                        requests.candidates.map {
                                            if (it.key == dialog.candidate.key) it.copy(status = status) else it
                                        },
                                ),
                        )
                    }
                } catch (failure: SourceException) {
                    local.update { it.copy(dialog = RequestDialog.Failed(dialog.candidate, failure)) }
                }
            }
    }

    override fun dismissRequest() {
        sending?.cancel()
        local.update { it.copy(dialog = null) }
    }

    companion object {
        /** The server is asked once the typing pauses that long; the local index answers at once. */
        const val DEBOUNCE_MILLIS = 250L

        /** Longer than any title. */
        const val MAX_LENGTH = 80
    }
}

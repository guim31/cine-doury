package fr.guim.cinedoury.feature.details

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.guim.cinedoury.core.data.ActiveSession
import fr.guim.cinedoury.core.data.SessionManager
import fr.guim.cinedoury.core.data.UserDataUpdates
import fr.guim.cinedoury.core.data.index.LibraryIndex
import fr.guim.cinedoury.core.model.ItemId
import fr.guim.cinedoury.core.model.ItemKind
import fr.guim.cinedoury.core.model.MediaItem
import fr.guim.cinedoury.core.model.MediaSource
import fr.guim.cinedoury.core.model.PlaybackPreferences
import fr.guim.cinedoury.core.model.PlaybackRequest
import fr.guim.cinedoury.core.model.SmartPlay
import fr.guim.cinedoury.core.model.TechBadge
import fr.guim.cinedoury.core.model.TechnicalSummary
import fr.guim.cinedoury.core.model.TrackPreselection
import fr.guim.cinedoury.core.model.TrackSelection
import fr.guim.cinedoury.core.model.UserData
import fr.guim.cinedoury.core.source.LibrarySource
import fr.guim.cinedoury.core.source.SourceException
import fr.guim.cinedoury.core.ui.Events
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.time.Duration

/** A trailer of an item: stored on the server (played like a film), or on the web (opened in its app). */
@Immutable
sealed interface Trailer {
    data class Local(
        val item: MediaItem,
    ) : Trailer

    data class Remote(
        val url: String,
    ) : Trailer
}

/**
 * What a details screen shows (`FONCTIONNALITES.md` § 4). Everything but [item] arrives after the
 * server answered; [item] is first the card that was opened, so that the screen shows at once.
 *
 * @param loaded the server answered with the whole item.
 * @param badges from the local index at once, then from the media sources.
 * @param episodes every episode of a series, in order; null while read.
 * @param season the season shown; null for a series flattened to its single season.
 * @param series the series of an episode, for « Aller à la série ».
 * @param saga the collection of a film and its films in release order; for a collection, itself.
 * @param selection the version and tracks chosen for playback, preset from the profile.
 */
@Immutable
data class DetailsState(
    val item: MediaItem,
    val loaded: Boolean = false,
    val badges: List<TechBadge> = emptyList(),
    val seasons: List<MediaItem> = emptyList(),
    val episodes: List<MediaItem>? = null,
    val season: ItemId? = null,
    val smartPlay: SmartPlay? = null,
    val series: MediaItem? = null,
    val saga: MediaItem? = null,
    val sagaItems: List<MediaItem> = emptyList(),
    val similar: List<MediaItem> = emptyList(),
    val filmography: List<MediaItem> = emptyList(),
    val trailer: Trailer? = null,
    val selection: TrackSelection? = null,
    val error: SourceException? = null,
) {
    /** The version chosen, for the track panel. */
    val source: MediaSource? get() =
        item.mediaSources.firstOrNull { it.id == selection?.sourceId }
            ?: item.mediaSources.firstOrNull()

    /** A series with one season shows its episodes without tabs (`FONCTIONNALITES.md` § 4). */
    val flattened: Boolean get() = seasons.size <= 1

    /** The episodes of the season shown. */
    val shownEpisodes: List<MediaItem>
        get() = episodes.orEmpty().filter { flattened || it.episode?.seasonId == season }

    val playable: Boolean get() = item.kind in setOf(ItemKind.Movie, ItemKind.Episode, ItemKind.Video)
}

/** What the details screen asks its view model; a no-op for screenshots. */
@Suppress("TooManyFunctions") // One function per gesture of the screen.
interface DetailsActions {
    fun retry()

    fun toggleWatched(item: MediaItem)

    fun toggleFavorite()

    fun selectSeason(season: ItemId)

    fun selectVersion(sourceId: String)

    fun selectAudio(index: Int)

    fun selectSubtitles(index: Int?)

    /** The request to play the item of the screen: resumed, or [fromStart]. */
    fun playRequest(fromStart: Boolean = false): PlaybackRequest?
}

/**
 * A details screen: a film, a series, an episode, a person or a collection (`SPEC.md` § 4.10). It
 * shows the card that was opened at once, with the badges of the local index, then everything the
 * server says. Watched and favourite change on screen at once, then on the server.
 */
@Suppress("TooManyFunctions") // One function per gesture of the screen.
class DetailsViewModel(
    initial: MediaItem,
    private val sessions: SessionManager,
    private val index: LibraryIndex,
    private val updates: UserDataUpdates = UserDataUpdates(),
) : ViewModel(),
    DetailsActions {
    private val active: ActiveSession? = sessions.active.value
    private val local = MutableStateFlow(DetailsState(initial))
    private var preferences = PlaybackPreferences()

    val state: StateFlow<DetailsState> = local.asStateFlow()

    /** The server rejected the token: the profile is signed out. */
    val sessionEnded = Events<Unit>()

    init {
        viewModelScope.launch {
            val serverId = active?.profile?.serverId ?: return@launch
            val summary = index.summary(serverId, initial) ?: return@launch
            local.update { if (it.badges.isEmpty()) it.copy(badges = summary.badges(language())) else it }
        }
        load()
        // Back from the player: the position and « vu » it left, at once (`SPEC.md` § 4.11).
        viewModelScope.launch {
            updates.updates.collect { (id, data) ->
                local.update { state ->
                    val changed = state.withUserData(id) { data }
                    if (changed.item.kind == ItemKind.Series) {
                        changed.copy(smartPlay = SmartPlay.of(changed.episodes.orEmpty()))
                    } else {
                        changed
                    }
                }
            }
        }
    }

    private fun language(): String = preferences.audioLanguage ?: Locale.getDefault().isO3Language

    private fun load() {
        val source = active?.library ?: return
        viewModelScope.launch {
            guarded {
                coroutineScope {
                    val details = async { source.item(local.value.item.id) }
                    val preferences = async { source.playbackPreferences() }
                    this@DetailsViewModel.preferences = preferences.await()
                    val item = details.await()
                    local.update { state ->
                        state.copy(
                            item = item,
                            loaded = true,
                            error = null,
                            badges =
                                if (item.mediaSources.isNotEmpty()) {
                                    TechnicalSummary.of(item.mediaSources).badges(language())
                                } else {
                                    state.badges
                                },
                            selection =
                                item.mediaSources.firstOrNull()?.let {
                                    TrackPreselection.choose(
                                        it,
                                        this@DetailsViewModel.preferences,
                                    )
                                },
                        )
                    }
                    loadAround(source, item)
                }
            }
        }
    }

    /** What surrounds the item: by kind, its episodes, saga, similar titles, trailer, series, works. */
    private suspend fun loadAround(
        source: LibrarySource,
        item: MediaItem,
    ) = coroutineScope {
        when (item.kind) {
            ItemKind.Series -> {
                launch { loadEpisodes(source, item.id) }
                launch { similar(source, item) }
                launch { trailer(source, item) }
            }

            ItemKind.Episode -> {
                item.episode?.seriesId?.let { series ->
                    launch {
                        optional { source.item(series) }?.let { found ->
                            local.update { it.copy(series = found) }
                        }
                    }
                }
            }

            ItemKind.Person -> {
                launch {
                    optional { source.filmography(item.id) }?.let { works ->
                        local.update { it.copy(filmography = works) }
                    }
                }
            }

            ItemKind.Collection -> {
                launch {
                    optional { source.collectionItems(item.id) }?.let { items ->
                        local.update { it.copy(saga = item, sagaItems = items) }
                    }
                }
            }

            else -> {
                launch { saga(source, item) }
                launch { similar(source, item) }
                launch { trailer(source, item) }
            }
        }
    }

    private suspend fun loadEpisodes(
        source: LibrarySource,
        series: ItemId,
    ) = coroutineScope {
        val seasons = async { optional { source.seasons(series) }.orEmpty() }
        val episodes = optional { source.episodes(series) }.orEmpty()
        val play = SmartPlay.of(episodes)
        local.update { state ->
            val list =
                seasons.await().filter {
                    it.episode?.seasonNumber != 0 ||
                        episodes.all { e -> e.episode?.seasonNumber == 0 }
                }
            val shown =
                state.season?.takeIf { id -> list.any { it.id == id } } ?: play?.episode?.episode?.seasonId
                    ?: list.firstOrNull()?.id
            state.copy(seasons = list, episodes = episodes, smartPlay = play, season = shown)
        }
    }

    private suspend fun saga(
        source: LibrarySource,
        item: MediaItem,
    ) {
        val saga = optional { source.collectionsOf(item.id) }?.firstOrNull() ?: return
        val items = optional { source.collectionItems(saga.id) }.orEmpty()
        local.update { it.copy(saga = saga, sagaItems = items) }
    }

    private suspend fun similar(
        source: LibrarySource,
        item: MediaItem,
    ) {
        val similar = optional { source.similar(item.id, SIMILAR) }.orEmpty()
        local.update { it.copy(similar = similar) }
    }

    /** The local trailer first; else the first trailer on the web. */
    private suspend fun trailer(
        source: LibrarySource,
        item: MediaItem,
    ) {
        val trailer =
            (if (item.localTrailerCount > 0) optional { source.localTrailers(item.id) }?.firstOrNull() else null)
                ?.let { Trailer.Local(it) }
                ?: item.remoteTrailers.firstOrNull()?.let { Trailer.Remote(it.url) }
        local.update { it.copy(trailer = trailer) }
    }

    override fun retry() {
        local.update { it.copy(error = null) }
        load()
    }

    /**
     * Watched or not: on screen at once, then on the server. A season or a series changes its
     * episodes; the list is read again once the server has answered.
     */
    override fun toggleWatched(item: MediaItem) {
        val source = active?.library ?: return
        val played = item.userData?.played != true
        local.update {
            it.withUserData(
                item.id,
            ) { data -> data.copy(played = played, playbackPosition = Duration.ZERO) }
        }
        viewModelScope.launch {
            guarded {
                val data = source.setPlayed(item.id, played)
                local.update { it.withUserData(item.id) { data } }
                val series = local.value.item.takeIf { it.kind == ItemKind.Series }
                if (series != null) loadEpisodes(source, series.id)
            }
        }
    }

    override fun toggleFavorite() {
        val source = active?.library ?: return
        val item = local.value.item
        val favorite = item.userData?.isFavorite != true
        local.update { it.withUserData(item.id) { data -> data.copy(isFavorite = favorite) } }
        viewModelScope.launch {
            guarded {
                val data = source.setFavorite(item.id, favorite)
                local.update { it.withUserData(item.id) { data } }
            }
        }
    }

    override fun selectSeason(season: ItemId) = local.update { it.copy(season = season) }

    override fun selectVersion(sourceId: String) {
        val source =
            local.value.item.mediaSources
                .firstOrNull { it.id == sourceId } ?: return
        local.update { it.copy(selection = TrackPreselection.choose(source, preferences)) }
    }

    override fun selectAudio(index: Int) = local.update { it.copy(selection = it.selection?.copy(audioIndex = index)) }

    override fun selectSubtitles(index: Int?) =
        local.update {
            it.copy(selection = it.selection?.copy(subtitleIndex = index))
        }

    override fun playRequest(fromStart: Boolean): PlaybackRequest? {
        val state = local.value
        return when {
            state.item.kind == ItemKind.Series -> {
                state.smartPlay?.let { PlaybackRequest(it.episode, selection = null, resume = it.resume && !fromStart) }
            }

            state.playable -> {
                PlaybackRequest(state.item, state.selection, resume = state.item.progress != null && !fromStart)
            }

            else -> {
                null
            }
        }
    }

    private suspend fun guarded(block: suspend () -> Unit) {
        try {
            block()
        } catch (_: SourceException.Unauthorized) {
            sessions.sessionRejected()
            sessionEnded.send(Unit)
        } catch (error: SourceException) {
            local.update { it.copy(error = error) }
        }
    }

    /** A part of the screen that may fail alone: it stays empty. */
    private suspend fun <T> optional(block: suspend () -> T): T? =
        try {
            block()
        } catch (error: SourceException.Unauthorized) {
            throw error
        } catch (_: SourceException) {
            null
        }

    private companion object {
        const val SIMILAR = 12
    }
}

/** [change] applied to the user data of [id], wherever it shows: the item, its episodes, its seasons. */
private fun DetailsState.withUserData(
    id: ItemId,
    change: (UserData) -> UserData,
): DetailsState {
    fun MediaItem.changed(): MediaItem =
        if (this.id == id) copy(userData = change(userData ?: UserData(false, Duration.ZERO, 0, false))) else this
    return copy(
        item = item.changed(),
        episodes = episodes?.map { it.changed() },
        seasons = seasons.map { it.changed() },
    )
}

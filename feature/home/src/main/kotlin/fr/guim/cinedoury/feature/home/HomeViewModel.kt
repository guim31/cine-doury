package fr.guim.cinedoury.feature.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.guim.cinedoury.core.data.ActiveSession
import fr.guim.cinedoury.core.data.ConnectionMode
import fr.guim.cinedoury.core.data.SessionManager
import fr.guim.cinedoury.core.data.home.CardStyle
import fr.guim.cinedoury.core.data.home.HomeLayouts
import fr.guim.cinedoury.core.data.home.HomeRepository
import fr.guim.cinedoury.core.data.home.HomeRowType
import fr.guim.cinedoury.core.data.home.HomeRows
import fr.guim.cinedoury.core.data.home.ProfileKey
import fr.guim.cinedoury.core.data.home.Tonight
import fr.guim.cinedoury.core.data.home.TonightSources
import fr.guim.cinedoury.core.data.home.continues
import fr.guim.cinedoury.core.data.library.LibraryIconRepository
import fr.guim.cinedoury.core.data.prefs.AppPreferences
import fr.guim.cinedoury.core.model.ItemId
import fr.guim.cinedoury.core.model.Library
import fr.guim.cinedoury.core.model.MediaItem
import fr.guim.cinedoury.core.source.SourceException
import fr.guim.cinedoury.core.ui.Events
import fr.guim.cinedoury.core.ui.RailState
import fr.guim.cinedoury.core.ui.avatarSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock

/** A row as the home draws it. */
@Immutable
data class HomeRow(
    val key: String,
    val type: HomeRowType,
    /** The library of a row per library (« Ajouts récents dans Films »). */
    val library: Library?,
    val style: CardStyle,
    val items: List<MediaItem>,
    /** « Continuer »: the row of the cache each playback in progress comes from (« Retirer de Continuer »). */
    val resumeRows: Map<ItemId, String> = emptyMap(),
)

/**
 * @param rows null until something is known: the cache is empty and the server has not answered
 * yet (skeletons).
 * @param error shown only when there is nothing to show; otherwise the cache stays on screen.
 */
@Immutable
data class HomeState(
    val rail: RailState = RailState(),
    val remote: Boolean = false,
    val rows: List<HomeRow>? = null,
    val error: SourceException? = null,
    /** « Bandeau à la une » (J7). */
    val featuredBanner: Boolean = true,
    /** The mosaic « Ce soir » in place of the banner (essai réel 5); null when off, or nothing to show. */
    val tonight: TonightState? = null,
)

/**
 * Under the mosaic « Ce soir » (essai réel 5): « Reprendre » and « À suivre » as one row « Continuer »,
 * where the first of them stood. The playbacks in progress, then the next episodes, as in the mosaic;
 * one card per title or series, and none the mosaic shows ([inMosaic]: its titles and series). No
 * row when nothing is left.
 */
fun withContinue(
    rows: List<HomeRow>,
    inMosaic: Set<ItemId>,
): List<HomeRow> {
    val at = rows.indexOfFirst { it.type.continues }
    if (at < 0) return rows
    val seen = inMosaic.toMutableSet()
    val resumeRows = mutableMapOf<ItemId, String>()
    val items =
        buildList {
            for (type in listOf(HomeRowType.Resume, HomeRowType.NextUp)) {
                rows.filter { it.type == type }.forEach { row ->
                    row.items.filter { seen.add(it.seriesOrSelf) }.forEach { item ->
                        add(item)
                        if (type == HomeRowType.Resume) resumeRows[item.id] = row.key
                    }
                }
            }
        }
    val others = rows.filterNot { it.type.continues }
    if (items.isEmpty()) return others
    val row =
        HomeRow(HomeLayouts.CONTINUE_ID, HomeRowType.Continue, null, rows[at].style, items, resumeRows)
    // The rows before the first merged one are all kept: « Continuer » goes back at the same index.
    return others.toMutableList().apply { add(at, row) }
}

/** A title, or the series of an episode: the mosaic and « Continuer » show each once. */
private val MediaItem.seriesOrSelf: ItemId get() = episode?.seriesId ?: id

/**
 * The home: the cached rows at once, then the server's (`UI.md` principle 2). Room gives the new
 * rows to the screen; keys stay the same, so that nothing jumps and the focus stays put.
 */
class HomeViewModel(
    private val sessions: SessionManager,
    private val home: HomeRepository,
    icons: LibraryIconRepository,
    private val preferences: AppPreferences,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {
    private val active: ActiveSession? = sessions.active.value
    private val profile = active?.let { ProfileKey(it.profile.serverId, it.profile.user.id.value) }
    private val refreshed = MutableStateFlow(false)
    private val error = MutableStateFlow<SourceException?>(null)
    private var refreshing: Job? = null
    private val avatar = active?.let { sessions.avatarSource(it.profile.user) }

    /** The server rejected the token: the profile is signed out. */
    val sessionEnded = Events<Unit>()

    val state: StateFlow<HomeState> =
        if (profile == null || active == null) {
            MutableStateFlow(HomeState())
        } else {
            val cached =
                combine(home.layout(profile), home.libraries(profile), home.rows(profile)) { layout, libraries, rows ->
                    val specs = HomeRows.plan(layout, libraries.libraries, libraries.exclusions)
                    Triple(libraries.libraries, rows, specs)
                }
            combine(
                cached,
                home.railHidden(profile),
                icons.chosen(profile.serverId),
                combine(refreshed, error, preferences.browsing(profile)) { done, failure, browsing ->
                    Triple(done, failure, browsing)
                },
            ) { (libraries, rows, specs), hidden, chosen, (done, failure, browsing) ->
                val tonight = if (browsing.tonightMosaic) tonight(rows) else null
                val shown =
                    specs.mapNotNull { spec ->
                        rows[spec.key]?.takeIf { it.isNotEmpty() }?.let {
                            HomeRow(spec.key, spec.config.type, spec.library, spec.config.style, it)
                        }
                    }
                HomeState(
                    rail =
                        RailState(
                            libraries = libraries.filterNot { it.id in hidden },
                            profileName = active.profile.user.name,
                            avatar = avatar,
                            icons = chosen,
                        ),
                    remote = active.connection.mode == ConnectionMode.Remote,
                    rows =
                        when {
                            rows.isEmpty() && !done -> null
                            tonight != null -> withContinue(shown, tonight.tiles.map { it.item.seriesOrSelf }.toSet())
                            else -> shown
                        },
                    error = failure,
                    featuredBanner = browsing.featuredBanner,
                    tonight = tonight,
                )
            }.stateIn(viewModelScope, SharingStarted.Eagerly, HomeState())
        }

    init {
        refresh()
    }

    /** The mosaic « Ce soir » from its sources in the cache, null when they hold nothing yet. */
    private fun tonight(rows: Map<String, List<MediaItem>>): TonightState? {
        val keys = HomeRows.TonightKeys
        val sources =
            TonightSources(
                resume = rows[keys.RESUME].orEmpty(),
                nextUp = rows[keys.NEXT_UP].orEmpty(),
                latest = rows[keys.LATEST].orEmpty(),
                rediscover = rows[keys.REDISCOVER].orEmpty(),
            )
        return Tonight.pick(sources, clock.instant()).takeIf { it.isNotEmpty() }?.let(::TonightState)
    }

    /** Asks the server for the rows again (when the home opens, or comes back into view). */
    fun refresh() {
        val profile = profile ?: return
        val source = active?.library ?: return
        if (refreshing?.isActive == true) return
        refreshing =
            viewModelScope.launch {
                try {
                    val tonight = preferences.browsing(profile).first().tonightMosaic
                    val written = home.refresh(profile, source, tonight)
                    // Room gives the new rows a moment later: until then the home is not « empty ».
                    if (written.values.any { it.isNotEmpty() }) home.rows(profile).first { it.isNotEmpty() }
                    error.value = null
                } catch (_: SourceException.Unauthorized) {
                    sessions.sessionRejected()
                    sessionEnded.send(Unit)
                } catch (failure: SourceException) {
                    error.value = failure
                } finally {
                    refreshed.value = true
                }
            }
    }
}

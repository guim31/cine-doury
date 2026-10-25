package fr.guim.cinedoury.feature.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.guim.cinedoury.core.data.ActiveSession
import fr.guim.cinedoury.core.data.ConnectionMode
import fr.guim.cinedoury.core.data.SessionManager
import fr.guim.cinedoury.core.data.home.CardStyle
import fr.guim.cinedoury.core.data.home.HomeRepository
import fr.guim.cinedoury.core.data.home.HomeRowType
import fr.guim.cinedoury.core.data.home.HomeRows
import fr.guim.cinedoury.core.data.home.ProfileKey
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A row as the home draws it. */
@Immutable
data class HomeRow(
    val key: String,
    val type: HomeRowType,
    /** The library of a row per library (« Ajouts récents dans Films »). */
    val library: Library?,
    val style: CardStyle,
    val items: List<MediaItem>,
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
)

/**
 * The home: the cached rows at once, then the server's (`UI.md` principle 2). Room gives the new
 * rows to the screen; keys stay the same, so that nothing jumps and the focus stays put.
 */
class HomeViewModel(
    private val sessions: SessionManager,
    private val home: HomeRepository,
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
                refreshed,
                error,
            ) { (libraries, rows, specs), hidden, done, failure ->
                val shown =
                    specs.mapNotNull { spec ->
                        rows[spec.key]?.takeIf { it.isNotEmpty() }?.let { items ->
                            HomeRow(spec.key, spec.config.type, spec.library, spec.config.style, items)
                        }
                    }
                HomeState(
                    rail =
                        RailState(
                            libraries = libraries.filterNot { it.id in hidden },
                            profileName = active.profile.user.name,
                            avatar = avatar,
                        ),
                    remote = active.connection.mode == ConnectionMode.Remote,
                    rows = if (rows.isEmpty() && !done) null else shown,
                    error = failure,
                )
            }.stateIn(viewModelScope, SharingStarted.Eagerly, HomeState())
        }

    init {
        refresh()
    }

    /** Asks the server for the rows again (when the home opens, or comes back into view). */
    fun refresh() {
        val profile = profile ?: return
        val source = active?.library ?: return
        if (refreshing?.isActive == true) return
        refreshing =
            viewModelScope.launch {
                try {
                    home.refresh(profile, source)
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

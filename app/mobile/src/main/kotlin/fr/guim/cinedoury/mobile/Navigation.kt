package fr.guim.cinedoury.mobile

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import fr.guim.cinedoury.core.data.Profile
import fr.guim.cinedoury.core.data.SavedServer
import fr.guim.cinedoury.core.model.ItemId
import fr.guim.cinedoury.core.model.LibraryId
import fr.guim.cinedoury.core.model.MediaItem
import fr.guim.cinedoury.core.model.PlaybackRequest
import fr.guim.cinedoury.core.source.SourceException
import fr.guim.cinedoury.feature.auth.AddressMode
import fr.guim.cinedoury.feature.auth.PinPurpose

/** The screens of the phone (M1). */
sealed interface Route {
    data object Startup : Route

    data object Welcome : Route

    data object ServerSearch : Route

    /** [offer]: the remote address offered after a first sign-in on the network of the house (F, M2). */
    data class Address(
        val mode: AddressMode,
        val offer: Boolean = false,
    ) : Route

    data class Unreachable(
        val server: SavedServer,
        val error: SourceException,
    ) : Route

    data object Profiles : Route

    data class QuickConnect(
        val username: String?,
    ) : Route

    data class Password(
        val username: String?,
    ) : Route

    data class Pin(
        val profile: Profile,
        val purpose: PinPurpose,
    ) : Route

    // The tabs of the bar at the bottom (`UI.md` § 9).

    data object Home : Route

    data object Search : Route

    data object Libraries : Route

    data object ProfileTab : Route

    // Over a tab.

    data class Library(
        val id: LibraryId,
    ) : Route

    data class Details(
        val item: MediaItem,
        val season: ItemId? = null,
    ) : Route

    data class Player(
        val request: PlaybackRequest,
    ) : Route

    /** « Connecter un appareil » (M1): approves the Quick Connect code of a TV. */
    data object ConnectDevice : Route
}

/** The tabs, in the order of the bar. « Téléchargements » comes with M5: no empty tab before. */
enum class Tab(
    val root: Route,
) {
    Home(Route.Home),
    Search(Route.Search),
    Libraries(Route.Libraries),
    Profile(Route.ProfileTab),
}

/** The tab a screen belongs to: the bar shows it selected; null outside a session, and in the player. */
fun Navigator.currentTab(): Tab? {
    if (current.route is Route.Player || current.route.beforeSession) return null
    return routes.lastOrNull { route -> Tab.entries.any { it.root == route } }?.let { route ->
        Tab.entries.first { it.root == route }
    } ?: Tab.Home
}

/** The screens before a profile is open: no bar at the bottom. */
val Route.beforeSession: Boolean
    get() =
        this is Route.Startup || this is Route.Welcome || this is Route.ServerSearch || this is Route.Address ||
            this is Route.Unreachable || this is Route.Profiles || this is Route.QuickConnect ||
            this is Route.Password || this is Route.Pin

/** A screen in the back stack, with its own view models: they go when the screen goes. */
class BackStackEntry(
    val route: Route,
    val id: Long,
) : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
}

/**
 * The back stack of the phone. The home is always at its bottom once a profile is open: another tab
 * goes over it, so that « Retour » (the key or the gesture of the system) leads back home, then out
 * of the app. A finished path (signed in, server forgotten) replaces the whole stack.
 */
@Stable
class Navigator(
    start: Route,
) {
    private var nextId = 0L
    private var entries by mutableStateOf(listOf(entry(start)))

    val current: BackStackEntry get() = entries.last()
    val canGoBack: Boolean get() = entries.size > 1
    val routes: List<Route> get() = entries.map { it.route }

    /** Called when an entry leaves the stack, to drop its saved state. */
    var onRemoved: (BackStackEntry) -> Unit = {}

    private fun entry(route: Route) = BackStackEntry(route, nextId++)

    fun push(route: Route) {
        entries = entries + entry(route)
    }

    fun replaceTop(route: Route) {
        val removed = entries.last()
        entries = entries.dropLast(1) + entry(route)
        dispose(listOf(removed))
    }

    fun replaceAll(route: Route) {
        val removed = entries
        entries = listOf(entry(route))
        dispose(removed)
    }

    fun pop(): Boolean {
        if (!canGoBack) return false
        val removed = entries.last()
        entries = entries.dropLast(1)
        dispose(listOf(removed))
        return true
    }

    /**
     * A tap on a tab: its first screen, over the home. On the tab already shown, back to its first
     * screen (the screens opened from it go).
     */
    fun select(tab: Tab) {
        val home = entries.firstOrNull { it.route == Route.Home } ?: entry(Route.Home)
        val existing = entries.firstOrNull { it.route == tab.root }
        val kept =
            when {
                tab == Tab.Home -> listOf(home)
                existing != null -> listOf(home, existing)
                else -> listOf(home, entry(tab.root))
            }
        val removed = entries.filter { it !in kept }
        entries = kept
        dispose(removed)
    }

    private fun dispose(removed: List<BackStackEntry>) {
        removed.forEach {
            it.viewModelStore.clear()
            onRemoved(it)
        }
    }
}

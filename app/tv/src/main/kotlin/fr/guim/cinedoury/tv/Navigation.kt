package fr.guim.cinedoury.tv

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import fr.guim.cinedoury.core.data.Profile
import fr.guim.cinedoury.core.data.SavedServer
import fr.guim.cinedoury.core.model.LibraryId
import fr.guim.cinedoury.core.model.MediaItem
import fr.guim.cinedoury.core.model.PlaybackRequest
import fr.guim.cinedoury.core.source.SourceException
import fr.guim.cinedoury.feature.auth.AddressMode
import fr.guim.cinedoury.feature.auth.PinPurpose

/** The screens of the app. */
sealed interface Route {
    data object Startup : Route

    data object Welcome : Route

    data object ServerSearch : Route

    data class Address(
        val mode: AddressMode,
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

    data object Home : Route

    data class Library(
        val id: LibraryId,
    ) : Route

    data object HomeSettings : Route

    data object Search : Route

    /** The details of an item: a film, a series, an episode, a person, a collection. */
    data class Details(
        val item: MediaItem,
    ) : Route

    /** The player (J4): a film, an episode, a local trailer. */
    data class Player(
        val request: PlaybackRequest,
    ) : Route

    /** « Corriger l'identification » (J6). */
    data class Identify(
        val item: MediaItem,
    ) : Route

    /** « Changer l'affiche, le fond ou le logo » (J6). */
    data class Images(
        val item: MediaItem,
    ) : Route

    /** « Éléments à problème » of a library (J6). */
    data class Problems(
        val library: LibraryId,
        val name: String,
    ) : Route

    data object Settings : Route

    /** « Lecture et son » (J4). */
    data object PlaybackSettings : Route

    /** « Apparence des sous-titres » (J4). */
    data object SubtitleSettings : Route

    /** The playback profile of the device, in debug builds (J4). */
    data object DeviceProfile : Route

    data object Catalog : Route
}

/** A screen in the back stack, with its own view models: they go when the screen goes. */
class BackStackEntry(
    val route: Route,
    val id: Long,
) : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
}

/**
 * A plain back stack (no navigation library: a dozen screens, no deep link). « Retour » pops
 * one screen; a finished path (signed in, server forgotten) replaces the whole stack, so that
 * « Retour » never leads back into it.
 */
@Stable
class Navigator(
    start: Route,
) {
    private var nextId = 0L
    private var entries by mutableStateOf(listOf(entry(start)))

    val current: BackStackEntry get() = entries.last()
    val canGoBack: Boolean get() = entries.size > 1

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

    /** Pops the screens above the last one that matches [predicate]; false if none does. */
    fun popTo(predicate: (Route) -> Boolean): Boolean {
        val index = entries.indexOfLast { predicate(it.route) }
        if (index < 0) return false
        val removed = entries.drop(index + 1)
        entries = entries.take(index + 1)
        dispose(removed)
        return true
    }

    fun pop(): Boolean {
        if (!canGoBack) return false
        val removed = entries.last()
        entries = entries.dropLast(1)
        dispose(listOf(removed))
        return true
    }

    val routes: List<Route> get() = entries.map { it.route }

    private fun dispose(removed: List<BackStackEntry>) {
        removed.forEach {
            it.viewModelStore.clear()
            onRemoved(it)
        }
    }
}

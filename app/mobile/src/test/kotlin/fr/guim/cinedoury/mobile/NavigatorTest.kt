package fr.guim.cinedoury.mobile

import fr.guim.cinedoury.core.model.ItemId
import fr.guim.cinedoury.core.model.ItemKind
import fr.guim.cinedoury.core.model.LibraryId
import fr.guim.cinedoury.core.model.MediaItem
import fr.guim.cinedoury.core.model.PlaybackRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The back stack of the phone (M1): the home at its bottom, the tabs over it. */
class NavigatorTest {
    private val film = MediaItem(ItemId("film"), ItemKind.Movie, "Le Phare")

    @Test
    fun `a tab goes over the home, and « Retour » goes back to it`() {
        val navigator = Navigator(Route.Home)
        navigator.select(Tab.Libraries)
        navigator.push(Route.Library(LibraryId("films")))
        assertEquals(listOf(Route.Home, Route.Libraries, Route.Library(LibraryId("films"))), navigator.routes)
        assertEquals(Tab.Libraries, navigator.currentTab())
        navigator.pop()
        navigator.pop()
        assertEquals(listOf(Route.Home), navigator.routes)
    }

    @Test
    fun `the tab shown, tapped again, goes back to its first screen`() {
        val navigator = Navigator(Route.Home)
        navigator.select(Tab.Search)
        navigator.push(Route.Details(film))
        navigator.select(Tab.Search)
        assertEquals(listOf(Route.Home, Route.Search), navigator.routes)
        navigator.select(Tab.Home)
        assertEquals(listOf(Route.Home), navigator.routes)
    }

    @Test
    fun `no bar in the player, nor before a profile is open`() {
        val navigator = Navigator(Route.Welcome)
        assertNull(navigator.currentTab())
        navigator.replaceAll(Route.Home)
        navigator.push(Route.Details(film))
        assertEquals(Tab.Home, navigator.currentTab())
        navigator.push(Route.Player(PlaybackRequest(film)))
        assertNull(navigator.currentTab())
    }

    @Test
    fun `a screen that leaves the stack drops its view models`() {
        val navigator = Navigator(Route.Home)
        val removed = mutableListOf<Route>()
        navigator.onRemoved = { removed += it.route }
        navigator.select(Tab.Profile)
        navigator.push(Route.ConnectDevice)
        navigator.select(Tab.Home)
        assertEquals(listOf<Route>(Route.ProfileTab, Route.ConnectDevice), removed)
    }
}

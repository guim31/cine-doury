package fr.guim.cinedoury.tv

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performKeyPress
import androidx.compose.ui.test.pressKey
import fr.guim.cinedoury.core.data.library.LibraryTab
import fr.guim.cinedoury.core.data.library.LibraryView
import fr.guim.cinedoury.core.designsystem.theme.CineTheme
import fr.guim.cinedoury.core.designsystem.theme.ThemeVariant
import fr.guim.cinedoury.core.model.ItemFilters
import fr.guim.cinedoury.core.model.ItemId
import fr.guim.cinedoury.core.model.MediaItem
import fr.guim.cinedoury.core.model.PlaybackRequest
import fr.guim.cinedoury.core.model.SortField
import fr.guim.cinedoury.core.ui.LocalClock
import fr.guim.cinedoury.feature.details.DetailsActions
import fr.guim.cinedoury.feature.details.DetailsNavigation
import fr.guim.cinedoury.feature.details.DetailsPreview
import fr.guim.cinedoury.feature.details.DetailsScreen
import fr.guim.cinedoury.feature.details.DetailsState
import fr.guim.cinedoury.feature.details.panelLine
import fr.guim.cinedoury.feature.library.LibraryActions
import fr.guim.cinedoury.feature.library.LibraryPreview
import fr.guim.cinedoury.feature.library.LibraryScreen
import fr.guim.cinedoury.feature.manage.Deletion
import fr.guim.cinedoury.feature.manage.ManagePreview
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import android.view.KeyEvent as NativeKeyEvent

/**
 * Management with the remote alone (`FONCTIONNALITES.md` § 9, `UI.md` § 8): « Supprimer » is two
 * presses away from the details (« ⋯ », then « Supprimer »), its confirmation opens on « Annuler »,
 * a series asks twice, a child profile sees no « Gestion », OK held on a poster starts a selection.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = TV_1080P)
class J6NavigationTest {
    @get:Rule
    val compose = createTvComposeRule()

    private fun focusable(tag: String) =
        compose.onNode(hasClickAction() and (hasTestTag(tag) or hasAnyAncestor(hasTestTag(tag))))

    /** A button of the dialog on screen, by its label. */
    private fun dialogButton(label: String) =
        compose.onNode(hasClickAction() and hasText(label) and hasAnyAncestor(hasTestTag(ManagePreview.DIALOG)))

    private fun cancel() = dialogButton("Annuler")

    private fun confirm(label: String = "Supprimer") = dialogButton(label)

    private fun press(
        key: Key,
        times: Int = 1,
    ) = repeat(times) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    /** The details screen with what a view model would do with the deletion. */
    private class Details(
        initial: DetailsState,
    ) : DetailsActions,
        DetailsNavigation {
        var state by mutableStateOf(initial)
        var deleted = 0
        var presses = 0

        override fun retry() = Unit

        override fun toggleWatched(item: MediaItem) = Unit

        override fun toggleFavorite() = Unit

        override fun selectSeason(season: ItemId) = Unit

        override fun selectVersion(sourceId: String) = Unit

        override fun selectAudio(index: Int) = Unit

        override fun selectSubtitles(index: Int?) = Unit

        override fun playRequest(fromStart: Boolean): PlaybackRequest? = null

        override fun askDelete() {
            val preview =
                if (state.item.kind == J6Samples.series.item.kind) {
                    J6Samples.seriesDeletion
                } else {
                    J6Samples.filmDeletion
                }
            state = state.copy(deletion = preview)
        }

        override fun confirmDelete() {
            val deletion: Deletion = state.deletion ?: return
            if (deletion.preview.needsSecondConfirmation && !deletion.second) {
                state = state.copy(deletion = deletion.copy(second = true))
            } else {
                deleted++
                state = state.copy(deletion = null)
            }
        }

        override fun cancelDelete() {
            state = state.copy(deletion = null)
        }

        override fun open(item: MediaItem) = Unit

        override fun play(request: PlaybackRequest) = Unit

        override fun openTrailer(url: String) = Unit

        override fun sessionEnded() = Unit
    }

    private fun show(details: Details) {
        compose.setTvContent {
            CineTheme(ThemeVariant.Dark) {
                CompositionLocalProvider(LocalClock provides J3Samples.clock) {
                    DetailsScreen(
                        state = details.state,
                        sources = J2Samples.noImages,
                        actions = details,
                        navigation = details,
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    /** From « Reprendre », right to « ⋯ »: Depuis le début, Bande-annonce, Pistes, Vu, Favori, ⋯. */
    private fun toMore() {
        focusable(DetailsPreview.PLAY).assertIsFocused()
        press(Key.DirectionRight, times = 6)
        focusable(DetailsPreview.MORE).assertIsFocused()
    }

    @Test
    fun `« Supprimer » is two presses away, and its confirmation opens on « Annuler »`() {
        val details = Details(J6Samples.film)
        show(details)
        toMore()
        press(Key.DirectionCenter) // 1: « ⋯ »
        focusable(panelLine(0)).assertIsFocused()
        // Marquer comme vu, Informations, then the three refreshes, Corriger, Images, Supprimer.
        press(Key.DirectionDown, times = 7)
        focusable(panelLine(8)).assertIsFocused()
        press(Key.DirectionCenter) // 2: « Supprimer »
        compose.onNode(hasTestTag(ManagePreview.DIALOG)).assertExists()
        compose
            .onNode(
                hasText("/media/films/Le Phare (2001)/Le Phare (2001) - 2160p.mkv", substring = true),
            ).assertExists()
        cancel().assertIsFocused()
        // OK on « Annuler » deletes nothing.
        press(Key.DirectionCenter)
        assertEquals(0, details.deleted)
        focusable(DetailsPreview.MORE).assertIsFocused()
        compose.onNode(hasTestTag(ManagePreview.DIALOG)).assertDoesNotExist()
        // Again, then → « Supprimer » and OK: deleted.
        details.askDelete()
        compose.waitForIdle()
        cancel().assertIsFocused()
        press(Key.DirectionRight)
        confirm().assertIsFocused()
        press(Key.DirectionCenter)
        assertEquals(1, details.deleted)
    }

    @Test
    fun `a whole series asks twice, each time on « Annuler »`() {
        val details = Details(J6Samples.series)
        show(details)
        details.askDelete()
        compose.waitForIdle()
        cancel().assertIsFocused()
        press(Key.DirectionRight)
        press(Key.DirectionCenter)
        assertEquals(0, details.deleted, "the first « Supprimer » only asks again")
        compose.onNode(hasText("Supprimer toute la série ?")).assertExists()
        cancel().assertIsFocused()
        press(Key.DirectionRight)
        press(Key.DirectionCenter)
        assertEquals(1, details.deleted)
    }

    @Test
    fun `a child profile sees no management`() {
        val details = Details(J6Samples.child)
        show(details)
        toMore()
        press(Key.DirectionCenter)
        compose.onNode(hasText("Gestion")).assertDoesNotExist()
        compose.onNode(hasText("Supprimer")).assertDoesNotExist()
        compose.onNode(hasText("Corriger l'identification")).assertDoesNotExist()
    }

    private class Library : LibraryActions {
        val started = mutableListOf<MediaItem?>()
        val toggled = mutableListOf<MediaItem>()

        override fun selectTab(tab: LibraryTab) = Unit

        override fun setView(view: LibraryView) = Unit

        override fun sortBy(field: SortField) = Unit

        override fun setFilters(filters: ItemFilters) = Unit

        override fun clearFilters() = Unit

        override fun toggleGroupCollections() = Unit

        override fun openGenre(genre: String) = Unit

        override fun onVisible(index: Int) = Unit

        override fun onFocused(index: Int) = Unit

        override fun jumpTo(letter: String) = Unit

        override fun retry() = Unit

        override fun startSelection(item: MediaItem?) {
            started += item
        }

        override fun toggleSelected(item: MediaItem) {
            toggled += item
        }
    }

    private var library by mutableStateOf(J6Samples.library.copy(scan = null))

    private fun showLibrary(actions: Library) {
        compose.setTvContent {
            CineTheme(ThemeVariant.Dark) {
                LibraryScreen(
                    state = library,
                    sources = J2Samples.noImages,
                    actions = actions,
                    navigation = NoLibraryNavigation,
                )
            }
        }
        compose.waitForIdle()
    }

    private fun holdOk() {
        val down = { repeat: Int ->
            NativeKeyEvent(0L, 0L, NativeKeyEvent.ACTION_DOWN, NativeKeyEvent.KEYCODE_DPAD_CENTER, repeat)
        }
        compose.onRoot().performKeyPress(KeyEvent(down(0)))
        compose.onRoot().performKeyPress(KeyEvent(down(1)))
        compose.onRoot().performKeyPress(KeyEvent(down(2)))
        compose.onRoot().performKeyPress(
            KeyEvent(NativeKeyEvent(0L, 0L, NativeKeyEvent.ACTION_UP, NativeKeyEvent.KEYCODE_DPAD_CENTER, 0)),
        )
        compose.waitForIdle()
    }

    @Test
    fun `OK held on a poster starts a selection with it, then OK ticks`() {
        val actions = Library()
        showLibrary(actions)
        focusable(LibraryPreview.item(0)).assertIsFocused()
        holdOk()
        val first = J2Samples.library().items[0]
        assertEquals(listOf<MediaItem?>(first), actions.started, "held once, started once, not opened")
        library = J6Samples.selection
        compose.waitForIdle()
        press(Key.DirectionCenter)
        assertTrue(actions.toggled.isNotEmpty())
    }
}

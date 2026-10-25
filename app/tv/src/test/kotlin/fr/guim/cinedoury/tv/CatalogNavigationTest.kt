package fr.guim.cinedoury.tv

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import fr.guim.cinedoury.core.designsystem.theme.CineTheme
import fr.guim.cinedoury.core.designsystem.theme.ThemeVariant
import fr.guim.cinedoury.tv.catalog.CatalogScreen
import fr.guim.cinedoury.tv.catalog.CatalogSection
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The catalogue is usable with the D-pad alone (`UI.md` § 2). */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = TV_1080P)
class CatalogNavigationTest {
    @get:Rule
    val compose = createComposeRule()

    private fun focusable(tag: String) =
        compose.onNode(hasClickAction() and (hasTestTag(tag) or hasAnyAncestor(hasTestTag(tag))))

    private fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    private fun showCatalog(firstSection: Int = CatalogSection.HEADER) {
        compose.setContent {
            CineTheme(ThemeVariant.Dark) {
                CatalogScreen(
                    variant = ThemeVariant.Dark,
                    onToggleVariant = {},
                    listState = rememberLazyListState(initialFirstVisibleItemIndex = firstSection),
                    requestInitialFocus = firstSection == CatalogSection.HEADER,
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun opensWithTheFocusOnTheThemeButton() {
        showCatalog()
        focusable("theme-toggle").assertIsFocused()

        press(Key.DirectionDown)
        compose.onNode(isFocused() and hasAnyAncestor(hasTestTag("rail"))).assertDoesNotExist()
        compose.onNode(isFocused()).assertExists()
    }

    @Test
    fun movesAlongThePostersAndIntoTheRail() {
        showCatalog(CatalogSection.POSTERS)
        focusable("poster-0").requestFocus()
        compose.waitForIdle()

        press(Key.DirectionRight)
        focusable("poster-1").assertIsFocused()

        press(Key.DirectionLeft)
        focusable("poster-0").assertIsFocused()

        press(Key.DirectionLeft)
        compose.onNode(isFocused() and hasAnyAncestor(hasTestTag("rail"))).assertExists()
    }
}

package fr.guim.cinedoury.tv

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.requestFocus
import com.github.takahirom.roborazzi.captureRoboImage
import fr.guim.cinedoury.core.designsystem.theme.CineTheme
import fr.guim.cinedoury.core.designsystem.theme.ThemeVariant
import fr.guim.cinedoury.tv.catalog.CatalogScreen
import fr.guim.cinedoury.tv.catalog.CatalogSection
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Reference screenshots of the catalogue, 1920 × 1080, in the dark and OLED black themes
 * (`UI.md` § 12). Record with `./gradlew recordRoborazziDebug`, check with
 * `./gradlew verifyRoborazziDebug`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = TV_1080P)
class CatalogScreenshotTest(
    private val variant: ThemeVariant,
) {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun colors() = shoot("01-couleurs", CatalogSection.HEADER)

    @Test
    fun typography() = shoot("02-typographie", CatalogSection.TYPOGRAPHY)

    @Test
    fun posters() = shoot("03-affiches", CatalogSection.POSTERS) { clickable(inside = "poster-1") }

    @Test
    fun thumbnails() = shoot("04-vignettes", CatalogSection.THUMBNAILS) { clickable(inside = "thumbnail-0") }

    @Test
    fun buttons() =
        shoot("05-boutons", CatalogSection.BUTTONS) {
            onNode(hasClickAction() and hasContentDescription(string(R.string.button_watched)))
        }

    @Test
    fun railOpen() =
        shoot("06-rail-ouvert", CatalogSection.POSTERS) {
            onAllNodes(hasClickAction() and hasContentDescription(string(R.string.rail_movies))).onLast()
        }

    @Test
    fun railSpecimens() = shoot("07-rail", CatalogSection.RAIL)

    @Test
    fun scrims() = shoot("08-voiles", CatalogSection.SCRIMS)

    @Test
    fun spacingAndMotion() = shoot("09-espacements-mouvement", CatalogSection.SPACING)

    private fun string(id: Int): String = RuntimeEnvironment.getApplication().getString(id)

    private fun ComposeContentTestRule.clickable(inside: String): SemanticsNodeInteraction =
        onNode(hasClickAction() and (hasTestTag(inside) or hasAnyAncestor(hasTestTag(inside))))

    private fun shoot(
        name: String,
        firstSection: Int,
        focus: (ComposeContentTestRule.() -> SemanticsNodeInteraction)? = null,
    ) {
        compose.setTvContent {
            CineTheme(variant = variant) {
                CatalogScreen(
                    variant = variant,
                    onToggleVariant = {},
                    listState = rememberLazyListState(initialFirstVisibleItemIndex = firstSection),
                    requestInitialFocus = firstSection == CatalogSection.HEADER,
                )
            }
        }
        val target =
            focus?.invoke(compose) ?: compose.clickable(inside = "theme-toggle").takeIf {
                firstSection == CatalogSection.HEADER
            }
        if (focus != null) target?.requestFocus()
        compose.waitForIdle()
        // A screenshot without its focus would be a wrong reference.
        target?.assertIsFocused()
        compose.onRoot().captureRoboImage("src/test/screenshots/$name-${variant.slug}.png")
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants(): List<Array<Any>> = ThemeVariant.entries.map { arrayOf(it) }
    }
}

internal val ThemeVariant.slug: String
    get() =
        when (this) {
            ThemeVariant.Dark -> "sombre"
            ThemeVariant.OledBlack -> "oled"
        }

package fr.guim.cinedoury.tv

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.test.SemanticsNodeInteraction
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
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Reference screenshots of the catalogue, 1920 × 1080 (960 × 540 dp at xhdpi), in the dark and
 * OLED black themes (`UI.md` § 12). Record with `./gradlew recordRoborazziDebug`, check with
 * `./gradlew verifyRoborazziDebug`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = TV_1080P)
class CatalogScreenshotTest(private val variant: ThemeVariant) {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun colorsAndTypography() = shoot("01-couleurs-typographie", CatalogSection.HEADER)

    @Test
    fun postersWithFocus() =
        shoot("02-affiches", CatalogSection.POSTERS) {
            clickable(inside = "poster-1")
        }

    @Test
    fun thumbnailsAndButtons() =
        shoot("03-vignettes-boutons", CatalogSection.THUMBNAILS) {
            clickable(inside = "primary-button")
        }

    @Test
    fun railOpen() =
        shoot("04-rail-ouvert", CatalogSection.POSTERS) {
            onAllNodes(hasClickAction() and hasContentDescription(context.getString(R.string.rail_movies)))
                .onLast()
        }

    @Test
    fun railSpecimensAndScrims() = shoot("05-rail-voiles", CatalogSection.RAIL)

    @Test
    fun spacingAndMotion() = shoot("06-espacements-mouvement", CatalogSection.SCRIMS)

    private val context get() = org.robolectric.RuntimeEnvironment.getApplication()

    private fun ComposeContentTestRule.clickable(inside: String): SemanticsNodeInteraction =
        onNode(hasClickAction() and (hasTestTag(inside) or hasAnyAncestor(hasTestTag(inside))))

    private fun shoot(
        name: String,
        firstSection: Int,
        focus: (ComposeContentTestRule.() -> SemanticsNodeInteraction)? = null,
    ) {
        compose.setContent {
            CineTheme(variant = variant) {
                CatalogScreen(
                    variant = variant,
                    onToggleVariant = {},
                    listState = rememberLazyListState(firstVisibleItemIndex = firstSection),
                    requestInitialFocus = firstSection == CatalogSection.HEADER,
                )
            }
        }
        focus?.invoke(compose)?.requestFocus()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/$name-${variant.slug}.png")
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants(): List<Array<Any>> = ThemeVariant.entries.map { arrayOf(it) }
    }
}

internal const val TV_1080P = "w960dp-h540dp-land-television-xhdpi"

internal val ThemeVariant.slug: String
    get() =
        when (this) {
            ThemeVariant.Dark -> "sombre"
            ThemeVariant.OledBlack -> "oled"
        }

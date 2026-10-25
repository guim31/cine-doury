package fr.guim.cinedoury.tv

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import fr.guim.cinedoury.core.designsystem.theme.CineTheme
import fr.guim.cinedoury.core.designsystem.theme.ThemeVariant
import fr.guim.cinedoury.tv.catalog.CatalogScreen
import fr.guim.cinedoury.tv.catalog.CatalogSection
import fr.guim.cinedoury.tv.catalog.PREVIEW_RAIL
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
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
    val compose = createTvComposeRule()

    @Test
    fun colors() = shoot("01-couleurs", CatalogSection.HEADER, "theme-toggle")

    @Test
    fun typography() = shoot("02-typographie", CatalogSection.TYPOGRAPHY)

    @Test
    fun posters() = shoot("03-affiches", CatalogSection.POSTERS, "poster-1")

    @Test
    fun thumbnails() = shoot("04-vignettes", CatalogSection.THUMBNAILS, "thumbnail-0")

    @Test
    fun buttons() = shoot("05-boutons", CatalogSection.BUTTONS, "button-watched")

    @Test
    fun railOpen() = shoot("06-rail-ouvert", CatalogSection.POSTERS, PREVIEW_RAIL)

    @Test
    fun railSpecimens() = shoot("07-rail", CatalogSection.RAIL)

    @Test
    fun scrims() = shoot("08-voiles", CatalogSection.SCRIMS)

    @Test
    fun spacingAndMotion() = shoot("09-espacements-mouvement", CatalogSection.SPACING)

    @Test
    fun ambientGenerated() = shoot("10-ambiance-carte-generee", CatalogSection.AMBIENT, "ambient-0")

    @Test
    fun ambientImage() = shoot("11-ambiance-image", CatalogSection.AMBIENT, "ambient-1")

    /**
     * Under native graphics Robolectric never gives the focus (`CLAUDE.md`, « Pièges »): the
     * focused element is drawn with `CineFocus.preview()`, through the same code. Real focus and
     * D-pad navigation are covered by [CatalogNavigationTest].
     */
    private fun shoot(
        name: String,
        firstSection: Int,
        focused: String? = null,
    ) {
        compose.setContent {
            CineTheme(variant = variant) {
                CatalogScreen(
                    variant = variant,
                    onToggleVariant = {},
                    listState = rememberLazyListState(initialFirstVisibleItemIndex = firstSection),
                    requestInitialFocus = false,
                    previewFocus = focused,
                )
            }
        }
        compose.waitForIdle()
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

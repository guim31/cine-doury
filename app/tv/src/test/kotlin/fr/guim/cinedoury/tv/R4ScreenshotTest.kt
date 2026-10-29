package fr.guim.cinedoury.tv

import fr.guim.cinedoury.core.designsystem.theme.LayoutSize
import fr.guim.cinedoury.core.designsystem.theme.ThemeVariant
import fr.guim.cinedoury.feature.home.HomePreview
import fr.guim.cinedoury.feature.home.HomeScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Essai réel 4 (`SPEC.md` § 4.22), in the default size: a row of the home scrolled to the right, its
 * cards cut at the edge of the rail (F). Dark theme, numbered from 210 (`docs/screenshots/r4/`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = TV_1080P)
class R4ScreenshotTest {
    @get:Rule
    val compose = createTvComposeRule()

    @Test
    fun scrolledRow() =
        compose.shootJ8(
            "210-accueil-rangee-defilee",
            ThemeVariant.Dark,
            1f,
            HomePreview.card("latest:films", 3),
            LayoutSize.Default,
        ) {
            HomeScreen(
                state = J2Samples.home.copy(rows = J2Samples.homeRows.drop(2)),
                sources = J2Samples.noImages,
                navigation = NoHomeNavigation,
                onRetry = {},
                previewFeatured = J2Samples.homeRows[2].items[3],
                previewScrolledRow = "latest:films",
            )
        }
}

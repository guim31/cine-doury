package fr.guim.cinedoury.tv

import fr.guim.cinedoury.core.designsystem.theme.LayoutSize
import fr.guim.cinedoury.core.designsystem.theme.ThemeVariant
import fr.guim.cinedoury.feature.home.HomePreview
import fr.guim.cinedoury.feature.home.HomeScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The fixes after 1.0.0 (`SPEC.md` § 4.24), numbered from 230 (`docs/screenshots/r6/`). H: each tile of
 * the mosaic « Ce soir » focused, its outline and halo whole, in the default size in 1080p (230), then
 * a tile of the top line in each size in 4K (231).
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = TV_1080P)
class R6TonightScreenshotTest(
    private val tile: Int,
) {
    @get:Rule
    val compose = createTvComposeRule()

    @Test
    fun focused() =
        compose.shootJ8("230-accueil-mosaique-tuile-$tile", ThemeVariant.Dark, 1f, HomePreview.tonight(tile), LayoutSize.Default) {
            Tonight()
        }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun tiles(): List<Array<Any>> = (0 until TILES).map { arrayOf(it) }
    }
}

@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = TV_4K_SHIELD)
class R6Tonight4kScreenshotTest(
    private val size: LayoutSize,
) {
    @get:Rule
    val compose = createTvComposeRule()

    @Test
    fun focused() =
        compose.shootJ8("231-accueil-mosaique-4k-${size.slug}", ThemeVariant.Dark, 1f, HomePreview.tonight(1), size) {
            Tonight()
        }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun sizes(): List<Array<Any>> = LayoutSize.entries.map { arrayOf(it) }
    }
}

@androidx.compose.runtime.Composable
private fun Tonight() =
    HomeScreen(state = R5Samples.tonight, sources = J2Samples.noImages, navigation = NoHomeNavigation, onRetry = {})

private const val TILES = 5

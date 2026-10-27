package fr.guim.cinedoury.tv

import androidx.compose.runtime.Composable
import fr.guim.cinedoury.core.designsystem.theme.LayoutSize
import fr.guim.cinedoury.core.designsystem.theme.ThemeVariant
import fr.guim.cinedoury.feature.details.DetailsPreview
import fr.guim.cinedoury.feature.home.HomePreview
import fr.guim.cinedoury.feature.library.LibraryPreview
import fr.guim.cinedoury.feature.player.PlayerPanel
import fr.guim.cinedoury.feature.player.PlayerPreview
import fr.guim.cinedoury.feature.player.playerPanelLine
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * « Taille de l'interface » (essai réel 3, `SPEC.md` § 4.21): the home, a library and a details screen
 * in each of the three sizes, for the maintainer to choose; the library's bar in each size, in 1080p
 * and in 4K (« Gestion » with its label). Dark theme, numbered from 200 (`docs/screenshots/r3/`).
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = TV_1080P)
class R3SizesScreenshotTest(
    private val size: LayoutSize,
) {
    @get:Rule
    val compose = createTvComposeRule()

    private fun shoot(
        name: String,
        focused: String?,
        content: @Composable () -> Unit,
    ) = compose.shootJ8("$name-${size.slug}", ThemeVariant.Dark, 1f, focused, size, content)

    @Test
    fun home() = shoot("200-accueil", HomePreview.card("latest:films", 1)) { KeyScreens.Home() }

    @Test
    fun library() = shoot("201-bibliotheque", LibraryPreview.item(2)) { KeyScreens.Library() }

    @Test
    fun film() = shoot("202-fiche-film", DetailsPreview.PLAY) { KeyScreens.Details(J3Samples.film) }

    @Test
    fun player() = shoot("203-lecteur-interface", PlayerPreview.PROGRESS) { KeyScreens.Player() }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun sizes(): List<Array<Any>> = LayoutSize.entries.map { arrayOf(it) }
    }
}

/** The bar of a library with « Gestion » (an administrator), in 4K, in each size: nothing cut. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = TV_4K_SHIELD)
class R3LibraryBar4kScreenshotTest(
    private val size: LayoutSize,
) {
    @get:Rule
    val compose = createTvComposeRule()

    @Test
    fun library() =
        compose.shootJ8(
            "204-bibliotheque-gestion-4k-${size.slug}",
            ThemeVariant.Dark,
            1f,
            LibraryPreview.MANAGE,
            size,
        ) {
            KeyScreens.ManagedLibrary()
        }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun sizes(): List<Array<Any>> = LayoutSize.entries.map { arrayOf(it) }
    }
}

/** The player in the default size: its smaller controls, « Passer l'intro », the bar of the subtitles. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = TV_1080P)
class R3PlayerScreenshotTest {
    @get:Rule
    val compose = createTvComposeRule()

    @Test
    fun intro() =
        compose.shootJ8("205-lecteur-passer-intro", ThemeVariant.Dark, 1f, PlayerPreview.SKIP, LayoutSize.Default) {
            J4Player(J4Samples.intro)
        }

    @Test
    fun subtitleBarPosition() =
        compose.shootJ8(
            "206-barre-sous-titres-position",
            ThemeVariant.Dark,
            1f,
            playerPanelLine(0, 1),
            LayoutSize.Default,
        ) {
            J4Player(J4Samples.resizing, panel = PlayerPanel.SubtitleSize, cue = J4Samples.SUBTITLE_LINE)
        }
}

/** « compacte », « normale », « grande », in the names of the screenshots. */
private val LayoutSize.slug: String
    get() =
        when (this) {
            LayoutSize.Compact -> "compacte"
            LayoutSize.Normal -> "normale"
            LayoutSize.Large -> "grande"
        }

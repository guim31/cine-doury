package fr.guim.cinedoury.tv

import androidx.compose.runtime.Composable
import fr.guim.cinedoury.core.data.ConnectionMode
import fr.guim.cinedoury.core.designsystem.theme.LayoutSize
import fr.guim.cinedoury.core.designsystem.theme.ThemeVariant
import fr.guim.cinedoury.core.model.Library
import fr.guim.cinedoury.core.model.LibraryId
import fr.guim.cinedoury.core.model.LibraryKind
import fr.guim.cinedoury.core.ui.RailState
import fr.guim.cinedoury.core.ui.RailTarget
import fr.guim.cinedoury.feature.details.DetailsPreview
import fr.guim.cinedoury.feature.home.HomePreview
import fr.guim.cinedoury.feature.home.HomeScreen
import fr.guim.cinedoury.feature.library.LibraryPreview
import fr.guim.cinedoury.feature.player.PlayerPanel
import fr.guim.cinedoury.feature.player.PlayerPreview
import fr.guim.cinedoury.feature.player.playerPanelLine
import fr.guim.cinedoury.feature.settings.SettingsPageScreen
import fr.guim.cinedoury.feature.settings.SettingsSection
import fr.guim.cinedoury.feature.settings.SettingsState
import fr.guim.cinedoury.feature.settings.page.SettingsPagePreview
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

/**
 * The icons of the libraries (essai réel 3, second pass): a rail of libraries of every sort, each
 * with its own icon from its name, and the picker of « Bibliothèques » with every icon.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = TV_1080P)
class R3IconsScreenshotTest {
    @get:Rule
    val compose = createTvComposeRule()

    private val libraries =
        listOf(
            Library(LibraryId("films"), "Films", LibraryKind.Movies),
            Library(LibraryId("4k"), "Films 4K", LibraryKind.Movies),
            Library(LibraryId("series"), "Séries", LibraryKind.Shows),
            Library(LibraryId("animes"), "Animes", LibraryKind.Shows),
            Library(LibraryId("dessins"), "Films animés", LibraryKind.Movies),
            Library(LibraryId("classiques"), "Classiques", LibraryKind.Movies),
            Library(LibraryId("docs"), "Documentaires", LibraryKind.Movies),
            Library(LibraryId("concerts"), "Concerts", LibraryKind.Mixed),
            Library(LibraryId("humour"), "Humour", LibraryKind.Movies),
            Library(LibraryId("famille"), "Vidéos de famille", LibraryKind.HomeVideos),
            Library(LibraryId("youtube"), "YouTube", LibraryKind.Mixed),
        )

    @Test
    fun rail() =
        compose.shootJ8("207-rail-icones-des-bibliotheques", ThemeVariant.Dark, 1f, null, LayoutSize.Default) {
            HomeScreen(
                state = J3Samples.home.copy(rail = RailState(libraries = libraries, profileName = "Camille")),
                sources = SampleArt.sources,
                navigation = NoHomeNavigation,
                onRetry = {},
                previewFeatured = J3Samples.phare,
                previewRail = RailTarget.Library(libraries[1].id),
            )
        }

    @Test
    fun picker() =
        compose.shootJ8(
            "208-choix-de-l-icone",
            ThemeVariant.Dark,
            1f,
            SettingsPagePreview.option(0),
            LayoutSize.Default,
        ) {
            SettingsPageScreen(
                section = SettingsSection.Libraries,
                state =
                    SettingsState(
                        session = J1Samples.session(ConnectionMode.Local),
                        libraries = libraries,
                        chosen = emptyMap(),
                        index = J3Samples.indexReady,
                    ),
                actions = NoSettingsActions,
                navigation = NoNavigation,
                previewChoice = "icon-${libraries[1].id.value}",
            )
        }
}

/** « compacte », « normale », « grande », in the names of the screenshots. */
internal val LayoutSize.slug: String
    get() =
        when (this) {
            LayoutSize.VeryCompact -> "tres-compacte"
            LayoutSize.Compact -> "compacte"
            LayoutSize.Normal -> "normale"
        }

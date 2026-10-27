package fr.guim.cinedoury.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import fr.guim.cinedoury.core.data.prefs.TextSize
import fr.guim.cinedoury.core.designsystem.component.LocalPreviewFocus
import fr.guim.cinedoury.core.designsystem.theme.CineTheme
import fr.guim.cinedoury.core.designsystem.theme.ThemeVariant
import fr.guim.cinedoury.core.model.SearchGroup
import fr.guim.cinedoury.core.ui.LocalClock
import fr.guim.cinedoury.feature.auth.AuthPreview
import fr.guim.cinedoury.feature.auth.ProfilesScreen
import fr.guim.cinedoury.feature.auth.ProfilesState
import fr.guim.cinedoury.feature.details.DetailsPreview
import fr.guim.cinedoury.feature.details.DetailsScreen
import fr.guim.cinedoury.feature.details.DetailsState
import fr.guim.cinedoury.feature.home.HomePreview
import fr.guim.cinedoury.feature.home.HomeScreen
import fr.guim.cinedoury.feature.library.LibraryPreview
import fr.guim.cinedoury.feature.library.LibraryScreen
import fr.guim.cinedoury.feature.player.PlayerPreview
import fr.guim.cinedoury.feature.search.PreviewVoice
import fr.guim.cinedoury.feature.search.SearchPreview
import fr.guim.cinedoury.feature.search.SearchRows
import fr.guim.cinedoury.feature.search.SearchScreen
import fr.guim.cinedoury.feature.settings.SettingsPageScreen
import fr.guim.cinedoury.feature.settings.SettingsSection
import fr.guim.cinedoury.feature.settings.page.SettingsPagePreview
import fr.guim.cinedoury.feature.update.UpdatePreview
import fr.guim.cinedoury.feature.update.UpdateScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The key screens of the app in 4K (`UI.md` § 12), as a Shield TV lays them out: 1920 × 1080 dp at
 * xhdpi, 3840 × 2160 pixels, the app on its 960 × 540 dp (`SPEC.md` § 4.16). Dark and OLED black,
 * numbered from 180 (`docs/screenshots/j8/`).
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = TV_4K_SHIELD)
class J8Screenshot4kTest(
    private val variant: ThemeVariant,
) {
    @get:Rule
    val compose = createTvComposeRule()

    private fun shoot(
        name: String,
        focused: String?,
        content: @Composable () -> Unit,
    ) = compose.shootJ8("$name-4k", variant, textScale = 1f, focused, content)

    @Test
    fun profiles() = shoot("180-qui-regarde", AuthPreview.PROFILE) { KeyScreens.Profiles() }

    @Test
    fun home() = shoot("181-accueil", HomePreview.card("latest:films", 1)) { KeyScreens.Home() }

    @Test
    fun library() = shoot("182-bibliotheque", LibraryPreview.item(2)) { KeyScreens.Library() }

    @Test
    fun film() = shoot("183-fiche-film", DetailsPreview.PLAY) { KeyScreens.Details(J3Samples.film) }

    @Test
    fun series() = shoot("184-fiche-serie", DetailsPreview.PLAY) { KeyScreens.Details(J3Samples.series) }

    @Test
    fun player() = shoot("185-lecteur-interface", PlayerPreview.PROGRESS) { KeyScreens.Player() }

    @Test
    fun search() = shoot("186-recherche", KeyScreens.SEARCH_FOCUS) { KeyScreens.Search() }

    @Test
    fun settings() = shoot("187-reglages-apparence", SettingsPagePreview.row("theme")) { KeyScreens.Settings() }

    @Test
    fun update() = shoot("188-mise-a-jour", UpdatePreview.PRIMARY) { KeyScreens.Update() }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants(): List<Array<Any>> = ThemeVariant.entries.map { arrayOf(it) }
    }
}

/**
 * The same key screens in 1080p with the largest text (« Taille du texte » : Très grande, 130 %,
 * `SPEC.md` § 4.17): no text cut where it matters, nothing over anything else. Numbered from 190.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = TV_1080P)
class J8LargeTextScreenshotTest(
    private val variant: ThemeVariant,
) {
    @get:Rule
    val compose = createTvComposeRule()

    private fun shoot(
        name: String,
        focused: String?,
        content: @Composable () -> Unit,
    ) = compose.shootJ8("$name-texte-130", variant, TextSize.Larger.scale, focused, content)

    @Test
    fun profiles() = shoot("190-qui-regarde", AuthPreview.PROFILE) { KeyScreens.Profiles() }

    @Test
    fun home() = shoot("191-accueil", HomePreview.card("latest:films", 1)) { KeyScreens.Home() }

    @Test
    fun library() = shoot("192-bibliotheque", LibraryPreview.item(2)) { KeyScreens.Library() }

    @Test
    fun film() = shoot("193-fiche-film", DetailsPreview.PLAY) { KeyScreens.Details(J3Samples.film) }

    @Test
    fun series() = shoot("194-fiche-serie", DetailsPreview.PLAY) { KeyScreens.Details(J3Samples.series) }

    @Test
    fun player() = shoot("195-lecteur-interface", PlayerPreview.PROGRESS) { KeyScreens.Player() }

    @Test
    fun search() = shoot("196-recherche", KeyScreens.SEARCH_FOCUS) { KeyScreens.Search() }

    @Test
    fun settings() = shoot("197-reglages-apparence", SettingsPagePreview.row("theme")) { KeyScreens.Settings() }

    @Test
    fun update() = shoot("198-mise-a-jour", UpdatePreview.PRIMARY) { KeyScreens.Update() }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants(): List<Array<Any>> = ThemeVariant.entries.map { arrayOf(it) }
    }
}

/** The key screens of `UI.md` § 12, on the samples of their own milestone. */
private object KeyScreens {
    val SEARCH_FOCUS = SearchPreview.card(SearchRows.of(SearchGroup.Movies), 0)

    @Composable
    fun Profiles() =
        ProfilesScreen(ProfilesState(J1Samples.profiles, remote = true, loading = false), { null }, {}, {}, {}, {})

    @Composable
    fun Home() =
        HomeScreen(
            state = J2Samples.home,
            sources = J2Samples.noImages,
            navigation = NoHomeNavigation,
            onRetry = {},
            previewFeatured = J2Samples.homeRows[2].items[1],
        )

    @Composable
    fun Library() =
        LibraryScreen(
            state = J2Samples.library(),
            sources = J2Samples.noImages,
            actions = NoLibraryActions,
            navigation = NoLibraryNavigation,
        )

    @Composable
    fun Details(state: DetailsState) =
        DetailsScreen(
            state = state,
            sources = SampleArt.sources,
            actions = NoDetailsActions,
            navigation = NoDetailsNavigation,
        )

    @Composable
    fun Player() = J4Player(J4Samples.playing, osd = true)

    @Composable
    fun Search() =
        SearchScreen(
            state = J5Samples.partial,
            sources = SampleArt.sources,
            actions = NoSearchActions,
            navigation = NoSearchNavigation,
            voice = PreviewVoice(),
        )

    @Composable
    fun Settings() =
        SettingsPageScreen(
            section = SettingsSection.Appearance,
            state = J7Samples.settings,
            actions = NoSettingsActions,
            navigation = NoNavigation,
            showDebug = true,
        )

    @Composable
    fun Update() = UpdateScreen(J7Samples.available, NoUpdateActions)
}

private const val SETTLE_MILLIS = 1_000L

/** Shoots [content] in [variant] with the text at [textScale], the element [focused] drawn as focused. */
private fun ComposeContentTestRule.shootJ8(
    name: String,
    variant: ThemeVariant,
    textScale: Float,
    focused: String?,
    content: @Composable () -> Unit,
) {
    setContent {
        CineTheme(variant = variant, textScale = textScale) {
            CompositionLocalProvider(LocalPreviewFocus provides focused, LocalClock provides J3Samples.clock) {
                SampleArt.Provide(content)
            }
        }
    }
    waitForIdle()
    // The banner and the backdrops settle 250 ms after the focus, then fade in 300 ms.
    mainClock.advanceTimeBy(SETTLE_MILLIS)
    waitForIdle()
    onRoot().captureRoboImage("src/test/screenshots/$name-${variant.slug}.png")
}

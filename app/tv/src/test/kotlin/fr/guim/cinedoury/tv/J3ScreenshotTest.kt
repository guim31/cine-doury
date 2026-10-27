package fr.guim.cinedoury.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import fr.guim.cinedoury.core.data.ConnectionMode
import fr.guim.cinedoury.core.data.index.IndexStatus
import fr.guim.cinedoury.core.data.library.LibraryState
import fr.guim.cinedoury.core.designsystem.component.LocalPreviewFocus
import fr.guim.cinedoury.core.designsystem.theme.CineTheme
import fr.guim.cinedoury.core.designsystem.theme.ThemeVariant
import fr.guim.cinedoury.core.model.AudioFormat
import fr.guim.cinedoury.core.model.DynamicRange
import fr.guim.cinedoury.core.model.ItemFilters
import fr.guim.cinedoury.core.model.ItemId
import fr.guim.cinedoury.core.model.LibraryIcon
import fr.guim.cinedoury.core.model.LibraryId
import fr.guim.cinedoury.core.model.MediaItem
import fr.guim.cinedoury.core.model.PlaybackRequest
import fr.guim.cinedoury.core.ui.LocalClock
import fr.guim.cinedoury.core.ui.RailTarget
import fr.guim.cinedoury.feature.details.DetailsActions
import fr.guim.cinedoury.feature.details.DetailsNavigation
import fr.guim.cinedoury.feature.details.DetailsPanel
import fr.guim.cinedoury.feature.details.DetailsPreview
import fr.guim.cinedoury.feature.details.DetailsScreen
import fr.guim.cinedoury.feature.details.DetailsState
import fr.guim.cinedoury.feature.details.panelLine
import fr.guim.cinedoury.feature.home.HomePreview
import fr.guim.cinedoury.feature.home.HomeScreen
import fr.guim.cinedoury.feature.library.FilterCategory
import fr.guim.cinedoury.feature.library.LibraryPanel
import fr.guim.cinedoury.feature.library.LibraryScreen
import fr.guim.cinedoury.feature.library.LibraryUiState
import fr.guim.cinedoury.feature.settings.SettingsPageScreen
import fr.guim.cinedoury.feature.settings.SettingsSection
import fr.guim.cinedoury.feature.settings.SettingsState
import fr.guim.cinedoury.feature.settings.page.SettingsPagePreview
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Reference screenshots of J3, 1920 × 1080, dark and OLED black (`UI.md` § 12): the retouched home
 * and rail, the filters of the local index, the details screens and their panels, the provisional
 * player, the settings of the libraries and of the index. The artwork is the generated one of the
 * test server ([SampleArt]); the focused element is drawn through `LocalPreviewFocus`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = TV_1080P)
class J3ScreenshotTest(
    private val variant: ThemeVariant,
) {
    @get:Rule
    val compose = createTvComposeRule()

    @Test
    fun home() = shoot("70-accueil", HomePreview.card("resume", 0)) { Home() }

    @Test
    fun rail() = shoot("71-rail-icones", null) { Home(rail = RailTarget.Library(J3Samples.libraries[2].id)) }

    @Test
    fun filters() =
        shoot("72-filtres-hdr-et-audio", "panel-6") {
            val filters = ItemFilters(ranges = setOf(DynamicRange.DolbyVision), audioFormats = setOf(AudioFormat.Atmos))
            Library(
                J2Samples.library(LibraryState(filters = filters)).copy(index = J3Samples.indexReady),
                LibraryPanel.Filters,
            )
        }

    @Test
    fun rangeFilter() =
        shoot("73-filtre-hdr", "panel-0") {
            val filters = ItemFilters(ranges = setOf(DynamicRange.DolbyVision, DynamicRange.Hdr10))
            Library(
                J2Samples.library(LibraryState(filters = filters)).copy(index = J3Samples.indexRunning),
                LibraryPanel.Filters,
                FilterCategory.Range,
            )
        }

    @Test
    fun audioFilter() =
        shoot("74-filtre-audio", "panel-3") {
            val filters = ItemFilters(audioFormats = setOf(AudioFormat.Atmos))
            Library(
                J2Samples.library(LibraryState(filters = filters)).copy(index = J3Samples.indexReady),
                LibraryPanel.Filters,
                FilterCategory.AudioFormat,
            )
        }

    @Test
    fun film() = shoot("75-fiche-film", DetailsPreview.PLAY) { Details(J3Samples.film) }

    @Test
    fun filmOverview() =
        shoot("76-fiche-film-synopsis-deplie", DetailsPreview.OVERVIEW) {
            Details(J3Samples.film, expanded = true)
        }

    @Test
    fun filmRows() =
        shoot("77-fiche-film-distribution-et-saga", DetailsPreview.card("cast", 0)) {
            Details(J3Samples.film, firstItem = 1)
        }

    @Test
    fun tracks() = shoot("78-pistes", panelLine(6)) { Details(J3Samples.film, panel = DetailsPanel.Tracks) }

    @Test
    fun versions() =
        shoot(
            "79-choix-de-la-version",
            panelLine(2),
        ) { Details(J3Samples.film, panel = DetailsPanel.Tracks) }

    @Test
    fun fileInfo() =
        shoot("80-informations-fichier", panelLine(1)) {
            Details(J3Samples.film, panel = DetailsPanel.FileInfo)
        }

    @Test
    fun series() = shoot("81-fiche-serie", DetailsPreview.PLAY) { Details(J3Samples.series) }

    @Test
    fun seasons() =
        shoot(
            "82-fiche-serie-saisons",
            DetailsPreview.episode(1),
        ) { Details(J3Samples.series, firstItem = 1) }

    @Test
    fun singleSeason() =
        shoot("83-fiche-serie-saison-unique", DetailsPreview.episode(1)) {
            Details(J3Samples.singleSeason, firstItem = 1)
        }

    @Test
    fun episode() = shoot("84-fiche-episode", DetailsPreview.PLAY) { Details(J3Samples.episode) }

    @Test
    fun person() = shoot("85-fiche-personne", DetailsPreview.card("movies", 0)) { Details(J3Samples.person) }

    @Test
    fun settings() =
        shoot(
            "87-reglages-bibliotheques-et-index",
            SettingsPagePreview.row("place-${J3Samples.libraries[2].id.value}"),
        ) {
            Settings(J3Samples.indexRunning)
        }

    @Test
    fun settingsReady() =
        shoot("88-reglages-index-a-jour", SettingsPagePreview.row("index")) {
            Settings(J3Samples.indexReady)
        }

    @Test
    fun iconPanel() =
        shoot("89-icone-de-bibliotheque", SettingsPagePreview.option(4)) {
            Settings(J3Samples.indexReady, iconOf = J3Samples.libraries[4].id)
        }

    @Composable
    private fun Home(rail: RailTarget? = null) {
        HomeScreen(
            state = J3Samples.home,
            sources = SampleArt.sources,
            navigation = NoHomeNavigation,
            onRetry = {},
            previewFeatured = J3Samples.phare,
            previewRail = rail,
        )
    }

    @Composable
    private fun Library(
        state: LibraryUiState,
        panel: LibraryPanel? = null,
        category: FilterCategory? = null,
    ) {
        LibraryScreen(
            state = state,
            sources = J2Samples.noImages,
            actions = NoLibraryActions,
            navigation = NoLibraryNavigation,
            previewPanel = panel,
            previewCategory = category,
        )
    }

    @Composable
    private fun Details(
        state: DetailsState,
        panel: DetailsPanel? = null,
        expanded: Boolean = false,
        firstItem: Int = 0,
    ) {
        DetailsScreen(
            state = state,
            sources = SampleArt.sources,
            actions = NoDetailsActions,
            navigation = NoDetailsNavigation,
            previewPanel = panel,
            previewExpanded = expanded,
            previewFirstItem = firstItem,
        )
    }

    @Composable
    private fun Settings(
        index: IndexStatus,
        iconOf: LibraryId? = null,
    ) {
        SettingsPageScreen(
            section = SettingsSection.Libraries,
            state =
                SettingsState(
                    session = J1Samples.session(ConnectionMode.Local),
                    libraries = J3Samples.libraries,
                    chosen = mapOf(J3Samples.libraries[3].id to LibraryIcon.Kids),
                    index = index,
                ),
            actions = NoSettingsActions,
            navigation = NoNavigation,
            previewChoice = iconOf?.let { "icon-${it.value}" },
        )
    }

    private fun shoot(
        name: String,
        focused: String?,
        content: @Composable () -> Unit,
    ) {
        compose.setContent {
            CineTheme(variant = variant) {
                CompositionLocalProvider(LocalPreviewFocus provides focused, LocalClock provides J3Samples.clock) {
                    SampleArt.Provide(content)
                }
            }
        }
        compose.waitForIdle()
        // The banner and the backdrop settle 250 ms after the focus, then fade in 300 ms.
        compose.mainClock.advanceTimeBy(SETTLE_MILLIS)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/$name-${variant.slug}.png")
    }

    companion object {
        private const val SETTLE_MILLIS = 1_000L

        /** The first row of « Bibliothèques » in the settings list. */

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants(): List<Array<Any>> = ThemeVariant.entries.map { arrayOf(it) }
    }
}

internal object NoDetailsActions : DetailsActions {
    override fun retry() = Unit

    override fun toggleWatched(item: MediaItem) = Unit

    override fun toggleFavorite() = Unit

    override fun selectSeason(season: ItemId) = Unit

    override fun selectVersion(sourceId: String) = Unit

    override fun selectAudio(index: Int) = Unit

    override fun selectSubtitles(index: Int?) = Unit

    override fun playRequest(fromStart: Boolean): PlaybackRequest? = null
}

internal object NoDetailsNavigation : DetailsNavigation {
    override fun open(item: MediaItem) = Unit

    override fun play(request: PlaybackRequest) = Unit

    override fun openTrailer(url: String) = Unit

    override fun sessionEnded() = Unit
}

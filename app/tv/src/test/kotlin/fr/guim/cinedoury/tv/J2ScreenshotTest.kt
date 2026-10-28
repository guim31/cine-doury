package fr.guim.cinedoury.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import fr.guim.cinedoury.core.data.library.LibraryState
import fr.guim.cinedoury.core.data.library.LibraryTab
import fr.guim.cinedoury.core.data.library.LibraryView
import fr.guim.cinedoury.core.data.library.PagedItems
import fr.guim.cinedoury.core.designsystem.component.LocalPreviewFocus
import fr.guim.cinedoury.core.designsystem.theme.CineTheme
import fr.guim.cinedoury.core.designsystem.theme.ThemeVariant
import fr.guim.cinedoury.core.model.ItemFilters
import fr.guim.cinedoury.core.model.ItemSort
import fr.guim.cinedoury.core.model.MediaItem
import fr.guim.cinedoury.core.model.SortField
import fr.guim.cinedoury.core.model.WatchStatus
import fr.guim.cinedoury.core.ui.RailTarget
import fr.guim.cinedoury.feature.home.HomeNavigation
import fr.guim.cinedoury.feature.home.HomePreview
import fr.guim.cinedoury.feature.home.HomeScreen
import fr.guim.cinedoury.feature.home.HomeSettingsScreen
import fr.guim.cinedoury.feature.home.HomeState
import fr.guim.cinedoury.feature.home.PREVIEW_CUSTOMIZE_ROW
import fr.guim.cinedoury.feature.library.FilterCategory
import fr.guim.cinedoury.feature.library.LibraryActions
import fr.guim.cinedoury.feature.library.LibraryNavigation
import fr.guim.cinedoury.feature.library.LibraryPanel
import fr.guim.cinedoury.feature.library.LibraryPreview
import fr.guim.cinedoury.feature.library.LibraryScreen
import fr.guim.cinedoury.feature.library.LibraryUiState
import fr.guim.cinedoury.feature.library.PREVIEW_PANEL_FIRST
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Reference screenshots of the J2 screens, 1920 × 1080, dark and OLED black (`UI.md` § 12). The
 * focused element is drawn through `LocalPreviewFocus` (`CLAUDE.md`, « Pièges »).
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = TV_1080P)
class J2ScreenshotTest(
    private val variant: ThemeVariant,
) {
    @get:Rule
    val compose = createTvComposeRule()

    @Test
    fun homeEpisode() = shoot("40-accueil-episode-a-reprendre", HomePreview.PLAY) { Home() }

    @Test
    fun homeFilm() =
        shoot("41-accueil-film", HomePreview.card("latest:films", 1)) {
            Home(featured = J2Samples.homeRows[2].items[1])
        }

    @Test
    fun rail() = shoot("42-rail-ouvert", null) { Home(rail = RailTarget.Library(J2Samples.films.id)) }

    @Test
    fun homeSkeletons() = shoot("43-accueil-squelettes", null) { Home(J2Samples.home.copy(rows = null)) }

    @Test
    @Test
    fun customization() =
        shoot("45-personnaliser-l-accueil", PREVIEW_CUSTOMIZE_ROW) {
            HomeSettingsScreen(J2Samples.customization, actions = null, selectedRow = "latest")
        }

    @Test
    fun grid() = shoot("50-bibliotheque-grille", LibraryPreview.item(2)) { Library(J2Samples.library()) }

    @Test
    fun smallGrid() =
        shoot("51-bibliotheque-petites-affiches", LibraryPreview.item(9)) {
            Library(J2Samples.library(LibraryState(view = LibraryView.SmallPosters)))
        }

    @Test
    fun list() =
        shoot("52-bibliotheque-liste", LibraryPreview.item(1)) {
            Library(J2Samples.library(LibraryState(view = LibraryView.List)))
        }

    @Test
    fun thumbnails() =
        shoot("53-bibliotheque-vignettes", LibraryPreview.item(0)) {
            Library(J2Samples.library(LibraryState(view = LibraryView.Thumbnails)))
        }

    @Test
    fun filters() =
        shoot("54-filtres-et-resume", PREVIEW_PANEL_FIRST) {
            val filters = ItemFilters(watch = WatchStatus.Unwatched, genres = setOf("Comédie"), decades = setOf(1960))
            Library(
                J2Samples.library(LibraryState(filters = filters), loaded = 2).let {
                    it.copy(
                        items =
                            PagedItems(
                                total = 2,
                                pages = mapOf(0 to J2Samples.movies.take(2)),
                                pageSize = 60,
                            ),
                    )
                },
                panel = LibraryPanel.Filters,
            )
        }

    @Test
    fun filterValues() =
        shoot("55-filtre-genre", "panel-2") {
            val filters = ItemFilters(genres = setOf("Comédie"))
            Library(
                J2Samples.library(LibraryState(filters = filters)),
                panel = LibraryPanel.Filters,
                category = FilterCategory.Genre,
            )
        }

    @Test
    fun sort() =
        shoot("56-tri", "panel-1") {
            Library(J2Samples.library(LibraryState(sort = ItemSort(SortField.DateAdded))), panel = LibraryPanel.Sort)
        }

    @Test
    fun alphabet() = shoot("57-saut-a-z", "alpha-M") { Library(J2Samples.library(), letter = "M") }

    @Test
    fun collections() =
        shoot("58-collections", LibraryPreview.item(0)) {
            Library(
                J2Samples.library(LibraryState(tab = LibraryTab.Collections)).copy(
                    items = PagedItems(total = 2, pages = mapOf(0 to J2Samples.collections), pageSize = 60),
                ),
            )
        }

    @Test
    fun genres() =
        shoot("59-genres", LibraryPreview.item(2)) {
            Library(J2Samples.library(LibraryState(tab = LibraryTab.Genres)))
        }

    @Test
    fun librarySkeletons() =
        shoot("60-bibliotheque-squelettes", null) {
            Library(J2Samples.library().copy(items = PagedItems()))
        }

    @Composable
    private fun Home(
        state: HomeState = J2Samples.home,
        featured: MediaItem? = null,
        rail: RailTarget? = null,
    ) {
        HomeScreen(
            state = state,
            sources = J2Samples.noImages,
            navigation = NoHomeNavigation,
            onRetry = {},
            previewFeatured = featured,
            previewRail = rail,
        )
    }

    @Composable
    private fun Library(
        state: LibraryUiState,
        panel: LibraryPanel? = null,
        category: FilterCategory? = null,
        letter: String? = null,
    ) {
        LibraryScreen(
            state = state,
            sources = J2Samples.noImages,
            actions = NoLibraryActions,
            navigation = NoLibraryNavigation,
            previewPanel = panel,
            previewCategory = category,
            previewLetter = letter,
        )
    }

    private fun shoot(
        name: String,
        focused: String?,
        content: @Composable () -> Unit,
    ) {
        compose.setContent {
            CineTheme(variant = variant) {
                CompositionLocalProvider(LocalPreviewFocus provides focused, content = content)
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

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants(): List<Array<Any>> = ThemeVariant.entries.map { arrayOf(it) }
    }
}

internal object NoHomeNavigation : HomeNavigation {
    override fun navigate(target: RailTarget) = Unit

    override fun open(item: MediaItem) = Unit

    override fun play(item: MediaItem) = Unit

    override fun customize() = Unit

    override fun quit() = Unit

    override fun sessionEnded() = Unit
}

internal object NoLibraryNavigation : LibraryNavigation {
    override fun navigate(target: RailTarget) = Unit

    override fun open(item: MediaItem) = Unit

    override fun sessionEnded() = Unit
}

internal object NoLibraryActions : LibraryActions {
    override fun selectTab(tab: LibraryTab) = Unit

    override fun setView(view: LibraryView) = Unit

    override fun sortBy(field: SortField) = Unit

    override fun setFilters(filters: ItemFilters) = Unit

    override fun clearFilters() = Unit

    override fun toggleGroupCollections() = Unit

    override fun openGenre(genre: String) = Unit

    override fun onVisible(index: Int) = Unit

    override fun onFocused(index: Int) = Unit

    override fun jumpTo(letter: String) = Unit

    override fun retry() = Unit
}

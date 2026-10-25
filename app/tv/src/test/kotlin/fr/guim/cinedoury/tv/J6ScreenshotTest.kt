package fr.guim.cinedoury.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import fr.guim.cinedoury.core.designsystem.component.LocalPreviewFocus
import fr.guim.cinedoury.core.designsystem.theme.CineTheme
import fr.guim.cinedoury.core.designsystem.theme.ThemeVariant
import fr.guim.cinedoury.core.model.IdentifyMatch
import fr.guim.cinedoury.core.model.ImageType
import fr.guim.cinedoury.core.model.MediaItem
import fr.guim.cinedoury.core.model.RemoteImage
import fr.guim.cinedoury.core.source.SourceException
import fr.guim.cinedoury.core.ui.LocalClock
import fr.guim.cinedoury.feature.details.DetailsPanel
import fr.guim.cinedoury.feature.details.DetailsScreen
import fr.guim.cinedoury.feature.details.DetailsState
import fr.guim.cinedoury.feature.details.panelLine
import fr.guim.cinedoury.feature.library.LibraryPanel
import fr.guim.cinedoury.feature.library.LibraryPreview
import fr.guim.cinedoury.feature.library.LibraryScreen
import fr.guim.cinedoury.feature.library.LibraryUiState
import fr.guim.cinedoury.feature.manage.FileCheck
import fr.guim.cinedoury.feature.manage.IdentifyActions
import fr.guim.cinedoury.feature.manage.IdentifyPreview
import fr.guim.cinedoury.feature.manage.IdentifyScreen
import fr.guim.cinedoury.feature.manage.IdentifyState
import fr.guim.cinedoury.feature.manage.ImagesActions
import fr.guim.cinedoury.feature.manage.ImagesPreview
import fr.guim.cinedoury.feature.manage.ImagesScreen
import fr.guim.cinedoury.feature.manage.ImagesState
import fr.guim.cinedoury.feature.manage.ManageNotice
import fr.guim.cinedoury.feature.manage.ManagePreview
import fr.guim.cinedoury.feature.manage.ProblemTab
import fr.guim.cinedoury.feature.manage.ProblemsActions
import fr.guim.cinedoury.feature.manage.ProblemsPreview
import fr.guim.cinedoury.feature.manage.ProblemsScreen
import fr.guim.cinedoury.feature.manage.ProblemsState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Reference screenshots of J6, 1920 × 1080, dark and OLED black (`UI.md` § 12): « ⋯ » of a film and
 * of a series for an administrator (and for a child profile, without « Gestion »), the galleries of
 * the identification and of the posters, the deletions, the file, the « Gestion » of a library with
 * a scan running, the items with problems and a selection.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = TV_1080P)
class J6ScreenshotTest(
    private val variant: ThemeVariant,
) {
    @get:Rule
    val compose = createTvComposeRule()

    @Test
    fun filmMenu() = shoot("130-menu-film", panelLine(3)) { Details(J6Samples.film, DetailsPanel.More) }

    @Test
    fun seriesMenu() = shoot("131-menu-serie", panelLine(5)) { Details(J6Samples.series, DetailsPanel.More) }

    @Test
    fun childMenu() = shoot("132-menu-profil-enfant", panelLine(0)) { Details(J6Samples.child, DetailsPanel.More) }

    @Test
    fun identify() = shoot("133-identification", IdentifyPreview.result(0)) { Identify(J6Samples.identify) }

    @Test
    fun identifyEmpty() =
        shoot("134-identification-sans-resultat", IdentifyPreview.TITLE) {
            Identify(J6Samples.identify.copy(results = emptyList()))
        }

    @Test
    fun posters() = shoot("135-galerie-affiches", ImagesPreview.image(1)) { Images(J6Samples.images) }

    @Test
    fun backdrops() =
        shoot("136-galerie-fonds", ImagesPreview.type(ImageType.Backdrop)) {
            Images(J6Samples.images.copy(type = ImageType.Backdrop, images = emptyList()))
        }

    @Test
    fun deleteFilm() =
        shoot("137-suppression-film", ManagePreview.DELETE_CANCEL) {
            Details(J6Samples.withDeletion(J6Samples.film, J6Samples.filmDeletion))
        }

    @Test
    fun deleteSeries() =
        shoot("138-suppression-serie", ManagePreview.DELETE_CANCEL) {
            Details(J6Samples.withDeletion(J6Samples.series, J6Samples.seriesDeletion))
        }

    @Test
    fun deleteSeriesAgain() =
        shoot("139-suppression-serie-seconde-confirmation", ManagePreview.DELETE_CANCEL) {
            Details(J6Samples.withDeletion(J6Samples.series, J6Samples.seriesSecondDeletion))
        }

    @Test
    fun fileInfo() = shoot("140-informations-fichier", panelLine(18)) { Details(J6Samples.film, DetailsPanel.FileInfo) }

    @Test
    fun libraryManage() =
        shoot("141-bibliotheque-gestion-analyse", "panel-0") { Library(J6Samples.library, LibraryPanel.Manage) }

    @Test
    fun problems() = shoot("142-elements-a-probleme", ProblemsPreview.row(0)) { Problems(J6Samples.problems) }

    @Test
    fun problemActions() =
        shoot("143-element-a-probleme-actions", ProblemsPreview.IDENTIFY) { Problems(J6Samples.problemsChosen) }

    @Test
    fun missingFiles() =
        shoot("144-fichiers-disparus-verification", ProblemsPreview.tab(ProblemTab.MissingFiles)) {
            Problems(J6Samples.problems.copy(tab = ProblemTab.MissingFiles, checking = FileCheck(3_200, 11_523)))
        }

    @Test
    fun selection() = shoot("145-selection-multiple", LibraryPreview.item(2)) { Library(J6Samples.selection) }

    @Test
    fun notice() =
        shoot(
            "146-actualisation-demandee",
            null,
        ) { Details(J6Samples.film.copy(notice = ManageNotice.RefreshRequested)) }

    @Test
    fun error() =
        shoot("147-echec-de-gestion", ManagePreview.ERROR_CLOSE) {
            Details(J6Samples.film.copy(manageError = SourceException.Forbidden()))
        }

    @Composable
    private fun Details(
        state: DetailsState,
        panel: DetailsPanel? = null,
    ) {
        DetailsScreen(
            state = state,
            sources = SampleArt.sources,
            actions = NoDetailsActions,
            navigation = NoDetailsNavigation,
            previewPanel = panel,
        )
    }

    @Composable
    private fun Library(
        state: LibraryUiState,
        panel: LibraryPanel? = null,
    ) {
        LibraryScreen(
            state = state,
            sources = J2Samples.noImages,
            actions = NoLibraryActions,
            navigation = NoLibraryNavigation,
            previewPanel = panel,
        )
    }

    @Composable
    private fun Identify(state: IdentifyState) = IdentifyScreen(state, NoIdentifyActions)

    @Composable
    private fun Images(state: ImagesState) = ImagesScreen(state, NoImagesActions)

    @Composable
    private fun Problems(state: ProblemsState) =
        ProblemsScreen(state, J2Samples.films.name, SampleArt.sources, NoProblemsActions, onIdentify = {})

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

internal object NoIdentifyActions : IdentifyActions {
    override fun setTitle(title: String) = Unit

    override fun setYear(year: String) = Unit

    override fun selectProvider(provider: String) = Unit

    override fun toggleReplaceImages() = Unit

    override fun search() = Unit

    override fun apply(match: IdentifyMatch) = Unit

    override fun dismissError() = Unit
}

internal object NoImagesActions : ImagesActions {
    override fun selectType(type: ImageType) = Unit

    override fun selectProvider(provider: String?) = Unit

    override fun toggleAllLanguages() = Unit

    override fun apply(image: RemoteImage) = Unit

    override fun dismissError() = Unit
}

internal object NoProblemsActions : ProblemsActions {
    override fun selectTab(tab: ProblemTab) = Unit

    override fun checkFiles() = Unit

    override fun choose(item: MediaItem?) = Unit

    override fun askDelete(item: MediaItem) = Unit

    override fun confirmDelete() = Unit

    override fun cancelDelete() = Unit

    override fun dismissError() = Unit
}

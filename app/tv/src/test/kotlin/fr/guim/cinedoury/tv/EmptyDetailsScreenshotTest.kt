package fr.guim.cinedoury.tv

import androidx.compose.runtime.Composable
import fr.guim.cinedoury.core.designsystem.theme.LayoutSize
import fr.guim.cinedoury.core.designsystem.theme.ThemeVariant
import fr.guim.cinedoury.core.model.EpisodeInfo
import fr.guim.cinedoury.core.model.ItemId
import fr.guim.cinedoury.feature.details.DetailsPanel
import fr.guim.cinedoury.feature.details.DetailsPreview
import fr.guim.cinedoury.feature.details.DetailsScreen
import fr.guim.cinedoury.feature.details.DetailsState
import fr.guim.cinedoury.feature.details.panelLine
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Fiches vides (`SPEC.md` § 4.30), numbered from 260 (`docs/screenshots/fiches-vides/`). « Avant »:
 * the card as the screen first shows it, before the server answered, which is what 1.0.4 kept once
 * it had (nothing more came for a season or a folder).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = TV_1080P)
class EmptyDetailsScreenshotTest {
    @get:Rule
    val compose = createTvComposeRule()

    private fun shoot(
        name: String,
        focused: String?,
        content: @Composable () -> Unit,
    ) = compose.shootJ8(name, ThemeVariant.Dark, 1f, focused, LayoutSize.Default, content)

    @Composable
    private fun Details(
        state: DetailsState,
        panel: DetailsPanel? = null,
        firstItem: Int = 0,
    ) = DetailsScreen(
        state = state,
        sources = SampleArt.sources,
        actions = NoDetailsActions,
        navigation = NoDetailsNavigation,
        previewPanel = panel,
        previewFirstItem = firstItem,
    )

    /** A season of « Ajouts récents », as 1.0.4 showed it: no « Lire », no episodes. */
    private val season =
        J3Samples.mareesSeasons[1].copy(
            images = J3Samples.marees.images,
            episode = EpisodeInfo(J3Samples.marees.id, J3Samples.marees.title, null, 2, null),
        )

    /** S, before: the season alone. */
    @Test
    fun seasonBefore() = shoot("260-avant-saison-des-ajouts", DetailsPreview.FAVORITE) { Details(DetailsState(season)) }

    /** S: the series, on that season, « Reprendre » of the series. */
    @Test
    fun season() =
        shoot("260-saison-des-ajouts", DetailsPreview.PLAY) {
            Details(J3Samples.series.copy(season = ItemId("Les Marées-s2")))
        }

    private val folder = EmptyDetailsSamples.manyFolder

    /** T, before: a folder of home videos, nothing under its title. */
    @Test
    fun folderBefore() = shoot("261-avant-dossier", DetailsPreview.FAVORITE) { Details(DetailsState(folder)) }

    /** T: what the folder holds, in a grid, the first card focused. */
    @Test
    fun folder() =
        shoot("261-dossier-grille", DetailsPreview.card("children", 0)) {
            Details(DetailsState(folder, loaded = true, children = EmptyDetailsSamples.many), firstItem = 1)
        }

    private val empty = DetailsState(EmptyDetailsSamples.emptyFolder, loaded = true, children = emptyList())

    /** U: an empty folder says so, « ⋯ » beside « Favori ». */
    @Test
    fun nothingToPlay() = shoot("262-rien-a-lire", DetailsPreview.MORE) { Details(empty) }

    /** U: « ⋯ » says why: the kind of the item and what it holds. */
    @Test
    fun nothingToPlayWhy() =
        shoot("262-rien-a-lire-pourquoi", panelLine(0)) { Details(empty, panel = DetailsPanel.More) }
}

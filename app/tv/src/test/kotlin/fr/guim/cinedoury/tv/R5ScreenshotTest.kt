package fr.guim.cinedoury.tv

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import fr.guim.cinedoury.core.data.home.HomeRowType
import fr.guim.cinedoury.core.data.home.TonightKind
import fr.guim.cinedoury.core.data.home.TonightTile
import fr.guim.cinedoury.core.designsystem.theme.LayoutSize
import fr.guim.cinedoury.core.designsystem.theme.ThemeVariant
import fr.guim.cinedoury.core.model.MediaItem
import fr.guim.cinedoury.core.ui.QuickMenuHost
import fr.guim.cinedoury.core.ui.rememberQuickMenu
import fr.guim.cinedoury.feature.home.HomePreview
import fr.guim.cinedoury.feature.home.HomeScreen
import fr.guim.cinedoury.feature.home.HomeState
import fr.guim.cinedoury.feature.home.TonightState
import fr.guim.cinedoury.feature.library.LibraryPreview
import fr.guim.cinedoury.feature.library.LibraryScreen
import fr.guim.cinedoury.feature.player.PlayerPreview
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Essai réel 5 (`SPEC.md` § 4.23), in the default size, dark theme, numbered from 220
 * (`docs/screenshots/r5/`, with the « avant » of each point).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = TV_1080P)
class R5ScreenshotTest {
    @get:Rule
    val compose = createTvComposeRule()

    private fun shoot(
        name: String,
        focused: String?,
        content: @androidx.compose.runtime.Composable () -> Unit,
    ) = compose.shootJ8(name, ThemeVariant.Dark, 1f, focused, LayoutSize.Default, content)

    /** A: 25 s before the end of an episode without credits, the card shows without a countdown yet. */
    @Test
    fun nextEpisodeWithoutCredits() =
        shoot("220-lecteur-episode-suivant-sans-generique", PlayerPreview.NEXT) {
            J4Player(R5Samples.endWithoutCredits)
        }

    /** A, before: the same moment in 0.9.1, the countdown already running out 20 s before the end. */
    @Test
    fun nextEpisodeWithoutCreditsBefore() =
        shoot("220-avant-lecteur-episode-suivant-sans-generique", PlayerPreview.NEXT) {
            J4Player(R5Samples.endWithoutCreditsBefore)
        }

    /** B: an episode, « Épisode précédent » and « Épisode suivant » on each side of « Lecture ». */
    @Test
    fun episodeControls() =
        shoot("221-lecteur-episode-precedent-suivant", PlayerPreview.PREVIOUS_EPISODE) {
            J4Player(R5Samples.episodePlaying, osd = true)
        }

    /** B, before: the controls of an episode in 0.9.1. */
    @Test
    fun episodeControlsBefore() =
        shoot("221-avant-lecteur-episode-precedent-suivant", PlayerPreview.PLAY) {
            J4Player(R5Samples.episodePlaying.copy(previous = null, next = null), osd = true)
        }

    /** D: the mosaic « Ce soir » at the top of the home, its large tile focused. */
    @Test
    fun tonight() =
        shoot("224-accueil-mosaique-ce-soir", HomePreview.tonight(0)) {
            HomeScreen(
                state = R5Samples.tonight,
                sources = J2Samples.noImages,
                navigation = NoHomeNavigation,
                onRetry = {},
            )
        }

    /** D, before: the banner of 0.9.1, which repeated the first playback in progress. */
    @Test
    fun tonightBefore() =
        shoot("224-avant-accueil-mosaique-ce-soir", HomePreview.PLAY) {
            HomeScreen(
                state = J2Samples.home,
                sources = J2Samples.noImages,
                navigation = NoHomeNavigation,
                onRetry = {},
            )
        }

    /** C: OK held on an episode of « Reprendre », on the home. */
    @Test
    fun menuOnTheHome() =
        shoot("222-accueil-menu-carte", HomePreview.card("resume", 0)) {
            MenuOver(J2Samples.resumeEpisode, continueRow = "resume") {
                HomeScreen(
                    state = J2Samples.home,
                    sources = J2Samples.noImages,
                    navigation = NoHomeNavigation,
                    onRetry = {},
                )
            }
        }

    /** C, before: OK held on the home did nothing in 0.9.1. */
    @Test
    fun menuOnTheHomeBefore() =
        shoot("222-avant-accueil-menu-carte", HomePreview.card("resume", 0)) {
            HomeScreen(
                state = J2Samples.home,
                sources = J2Samples.noImages,
                navigation = NoHomeNavigation,
                onRetry = {},
            )
        }

    /** C: in a library, for a profile that manages it, « Sélectionner » last. */
    @Test
    fun menuInALibrary() =
        shoot("223-bibliotheque-menu-carte", LibraryPreview.item(6)) {
            MenuOver(J2Samples.movies[6], selectable = true) {
                LibraryScreen(
                    state = J6Samples.library.copy(scan = null),
                    sources = J2Samples.noImages,
                    actions = NoLibraryActions,
                    navigation = NoLibraryNavigation,
                )
            }
        }
}

/** The menu of a card open for [item] over [content], its first line focused (screenshots). */
@androidx.compose.runtime.Composable
private fun MenuOver(
    item: MediaItem,
    continueRow: String? = null,
    selectable: Boolean = false,
    content: @androidx.compose.runtime.Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        content()
        val menu = rememberQuickMenu()
        LaunchedEffect(item) { menu.open(item, continueRow = continueRow) }
        val select: ((MediaItem) -> Unit)? = if (selectable) ({ _ -> }) else null
        QuickMenuHost(menu, NoHomeNavigation, onSelect = select, previewFocusedIndex = 0)
    }
}

/** The samples of the essai réel 5. */
object R5Samples {
    /**
     * The home with its mosaic « Ce soir »: an episode in progress on the large tile, a film in progress,
     * an episode to watch next, a film added yesterday, a film never watched. « Reprendre » keeps what
     * the mosaic has no room for.
     */
    val tonight: HomeState
        get() {
            val rows = J2Samples.homeRows
            val resume = rows.first { it.key == "resume" }.items
            val next = rows.first { it.key == "next-up" }.items.first()
            val added = J2Samples.movies[10].copy(dateAdded = J3Samples.clock.instant().minusSeconds(DAY))
            val tiles =
                listOf(
                    TonightTile(resume[0], TonightKind.Resume),
                    TonightTile(resume[1], TonightKind.Resume),
                    TonightTile(next, TonightKind.NextUp),
                    TonightTile(added, TonightKind.Added),
                    TonightTile(J2Samples.movies[3], TonightKind.Rediscover),
                )
            val shown = tiles.map { it.item.id }.toSet()
            return J2Samples.home.copy(
                tonight = TonightState(tiles),
                rows =
                    rows.mapNotNull { row ->
                        val keep = row.type != HomeRowType.Resume && row.type != HomeRowType.NextUp
                        row.copy(items = row.items.filter { keep || it.id !in shown }).takeIf { it.items.isNotEmpty() }
                    },
            )
        }

    private const val DAY = 86_400L

    /** An episode of « Les Marées » in its middle, with an episode before and after it. */
    val episodePlaying =
        J4Samples.credits.copy(
            position = 21.minutes,
            nextShown = false,
            nextCountdown = null,
            nextSeconds = null,
            segments = emptyList(),
            previous = J3Samples.mareesPlayable[0],
        )

    val endWithoutCredits =
        episodePlaying.copy(position = 42.minutes - 25.seconds, nextShown = true)

    val endWithoutCreditsBefore =
        endWithoutCredits.copy(nextCountdown = 0.5f, nextSeconds = 5)
}

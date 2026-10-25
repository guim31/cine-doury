package fr.guim.cinedoury.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import fr.guim.cinedoury.core.designsystem.component.LocalPreviewFocus
import fr.guim.cinedoury.core.designsystem.theme.CineTheme
import fr.guim.cinedoury.core.designsystem.theme.ThemeVariant
import fr.guim.cinedoury.core.model.MediaItem
import fr.guim.cinedoury.core.model.RequestCandidate
import fr.guim.cinedoury.core.model.SearchGroup
import fr.guim.cinedoury.core.ui.RailTarget
import fr.guim.cinedoury.feature.search.KeyboardPage
import fr.guim.cinedoury.feature.search.KeyboardTags
import fr.guim.cinedoury.feature.search.PreviewVoice
import fr.guim.cinedoury.feature.search.SearchActions
import fr.guim.cinedoury.feature.search.SearchNavigation
import fr.guim.cinedoury.feature.search.SearchPreview
import fr.guim.cinedoury.feature.search.SearchRows
import fr.guim.cinedoury.feature.search.SearchScreen
import fr.guim.cinedoury.feature.search.SearchState
import fr.guim.cinedoury.feature.search.SeerrPreview
import fr.guim.cinedoury.feature.search.SeerrSettingsScreen
import fr.guim.cinedoury.feature.search.VoiceSearch
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Reference screenshots of J5, 1920 × 1080, dark and OLED black (`UI.md` § 12): the search empty
 * with its recent searches and suggestions, the keyboard of accents, results grouped by kind, a
 * typo forgiven, a person, the Seerr row, its confirmation and its seasons, the voice listening, the
 * settings of Seerr. The focused element is drawn through `LocalPreviewFocus`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = TV_1080P)
class J5ScreenshotTest(
    private val variant: ThemeVariant,
) {
    @get:Rule
    val compose = createTvComposeRule()

    @Test
    fun idle() = shoot("110-recherche-vide", KeyboardTags.key("a")) { Search(J5Samples.idle) }

    @Test
    fun accents() =
        shoot("111-recherche-clavier-accents", KeyboardTags.key("é")) {
            Search(J5Samples.idle.copy(query = "zé"), page = KeyboardPage.Accents)
        }

    @Test
    fun partial() =
        shoot("112-recherche-saisie-partielle", SearchPreview.card(SearchRows.of(SearchGroup.Movies), 0)) {
            Search(J5Samples.partial)
        }

    @Test
    fun typo() = shoot("113-recherche-faute-corrigee", KeyboardTags.key("e")) { Search(J5Samples.typo) }

    @Test
    fun person() =
        shoot("114-recherche-personne", SearchPreview.card(SearchRows.of(SearchGroup.People), 0)) {
            Search(J5Samples.person)
        }

    @Test
    fun requests() =
        shoot("115-recherche-seerr", SearchPreview.card(SearchRows.REQUESTS, 0)) { Search(J5Samples.requests) }

    @Test
    fun confirm() = shoot("116-recherche-seerr-confirmation", SearchPreview.DIALOG_CANCEL) { Search(J5Samples.confirm) }

    @Test
    fun seasons() = shoot("117-recherche-seerr-saisons", SearchPreview.season(2)) { Search(J5Samples.seasons) }

    @Test
    fun voice() =
        shoot("118-recherche-vocale", null) {
            Search(
                J5Samples.idle,
                voice = PreviewVoice(listening = true, transcript = "le seigneur des", level = VOICE_LEVEL),
            )
        }

    @Test
    fun rail() = shoot("119-recherche-rail", null) { Search(J5Samples.idle, rail = RailTarget.Search) }

    @Test
    fun seerrSettings() =
        shoot("120-reglages-seerr", SeerrPreview.SAVE) {
            SeerrSettingsScreen(J5Samples.seerrSettings, onAddress = {}, onKey = {}, onSave = {}, onRemove = {})
        }

    @Composable
    private fun Search(
        state: SearchState,
        page: KeyboardPage = KeyboardPage.Letters,
        voice: VoiceSearch = PreviewVoice(),
        rail: RailTarget? = null,
    ) {
        SearchScreen(
            state = state,
            sources = SampleArt.sources,
            actions = NoSearchActions,
            navigation = NoSearchNavigation,
            voice = voice,
            previewRail = rail,
            previewPage = page,
        )
    }

    private fun shoot(
        name: String,
        focused: String?,
        content: @Composable () -> Unit,
    ) {
        compose.setContent {
            CineTheme(variant = variant) {
                CompositionLocalProvider(LocalPreviewFocus provides focused) {
                    SampleArt.Provide(content)
                }
            }
        }
        compose.waitForIdle()
        // The backdrop settles 250 ms after the focus, then fades in 300 ms.
        compose.mainClock.advanceTimeBy(SETTLE_MILLIS)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/$name-${variant.slug}.png")
    }

    companion object {
        private const val SETTLE_MILLIS = 1_000L
        private const val VOICE_LEVEL = 0.6f

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants(): List<Array<Any>> = ThemeVariant.entries.map { arrayOf(it) }
    }
}

internal object NoSearchActions : SearchActions {
    override fun type(text: String) = Unit

    override fun erase() = Unit

    override fun clear() = Unit

    override fun setQuery(text: String) = Unit

    override fun chooseGenre(genre: String) = Unit

    override fun opened() = Unit

    override fun clearRecent() = Unit

    override fun retry() = Unit

    override fun ask(candidate: RequestCandidate) = Unit

    override fun toggleSeason(number: Int) = Unit

    override fun confirmRequest() = Unit

    override fun dismissRequest() = Unit
}

internal object NoSearchNavigation : SearchNavigation {
    override fun navigate(target: RailTarget) = Unit

    override fun open(item: MediaItem) = Unit

    override fun sessionEnded() = Unit
}

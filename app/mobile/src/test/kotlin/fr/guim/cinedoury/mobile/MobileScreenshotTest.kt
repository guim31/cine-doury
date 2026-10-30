package fr.guim.cinedoury.mobile

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.github.takahirom.roborazzi.captureScreenRoboImage
import fr.guim.cinedoury.core.data.ConnectionMode
import fr.guim.cinedoury.core.data.library.LibraryTab
import fr.guim.cinedoury.core.data.library.LibraryView
import fr.guim.cinedoury.core.designsystem.mobile.CineMobileTheme
import fr.guim.cinedoury.core.designsystem.theme.ThemeVariant
import fr.guim.cinedoury.core.model.ItemFilters
import fr.guim.cinedoury.core.model.ItemId
import fr.guim.cinedoury.core.model.LibraryId
import fr.guim.cinedoury.core.model.MarginStatus
import fr.guim.cinedoury.core.model.MediaItem
import fr.guim.cinedoury.core.model.PlayMethod
import fr.guim.cinedoury.core.model.PlaybackRequest
import fr.guim.cinedoury.core.model.PlaybackStream
import fr.guim.cinedoury.core.model.PlayerSettings
import fr.guim.cinedoury.core.model.RequestCandidate
import fr.guim.cinedoury.core.model.SortField
import fr.guim.cinedoury.core.model.TranscodeReason
import fr.guim.cinedoury.core.playback.PlayerSnapshot
import fr.guim.cinedoury.core.source.SourceException
import fr.guim.cinedoury.core.ui.LocalClock
import fr.guim.cinedoury.core.ui.RailTarget
import fr.guim.cinedoury.core.ui.mobile.MobileQuickMenu
import fr.guim.cinedoury.feature.auth.AddressMode
import fr.guim.cinedoury.feature.auth.AddressState
import fr.guim.cinedoury.feature.auth.PasswordState
import fr.guim.cinedoury.feature.auth.PinPurpose
import fr.guim.cinedoury.feature.auth.PinState
import fr.guim.cinedoury.feature.auth.ProfilesState
import fr.guim.cinedoury.feature.auth.QuickConnectState
import fr.guim.cinedoury.feature.auth.ServerSearchState
import fr.guim.cinedoury.feature.auth.mobile.MobileAddressScreen
import fr.guim.cinedoury.feature.auth.mobile.MobilePasswordScreen
import fr.guim.cinedoury.feature.auth.mobile.MobilePinScreen
import fr.guim.cinedoury.feature.auth.mobile.MobileProfilesScreen
import fr.guim.cinedoury.feature.auth.mobile.MobileQuickConnectScreen
import fr.guim.cinedoury.feature.auth.mobile.MobileServerSearchScreen
import fr.guim.cinedoury.feature.auth.mobile.MobileUnreachableScreen
import fr.guim.cinedoury.feature.auth.mobile.MobileWelcomeScreen
import fr.guim.cinedoury.feature.details.DetailsActions
import fr.guim.cinedoury.feature.details.DetailsNavigation
import fr.guim.cinedoury.feature.details.mobile.MobileDetailsScreen
import fr.guim.cinedoury.feature.home.HomeNavigation
import fr.guim.cinedoury.feature.home.HomeState
import fr.guim.cinedoury.feature.home.mobile.MobileHomeScreen
import fr.guim.cinedoury.feature.library.LibraryActions
import fr.guim.cinedoury.feature.library.LibraryNavigation
import fr.guim.cinedoury.feature.library.mobile.MobileLibrariesScreen
import fr.guim.cinedoury.feature.library.mobile.MobileLibraryScreen
import fr.guim.cinedoury.feature.player.PlayerActions
import fr.guim.cinedoury.feature.player.PlayerState
import fr.guim.cinedoury.feature.player.mobile.MobilePlaybackSettings
import fr.guim.cinedoury.feature.player.mobile.MobilePlaybackSheet
import fr.guim.cinedoury.feature.player.mobile.MobilePlayerPreview
import fr.guim.cinedoury.feature.player.mobile.MobilePlayerScreen
import fr.guim.cinedoury.feature.search.PreviewVoice
import fr.guim.cinedoury.feature.search.SearchActions
import fr.guim.cinedoury.feature.search.SearchNavigation
import fr.guim.cinedoury.feature.search.mobile.MobileSearchScreen
import fr.guim.cinedoury.feature.settings.ConnectDeviceState
import fr.guim.cinedoury.feature.settings.mobile.MobileConnectDeviceScreen
import fr.guim.cinedoury.feature.settings.mobile.MobileProfileScreen
import fr.guim.cinedoury.feature.settings.mobile.MobileProfileState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** A phone held upright: 412 × 915 dp (a recent phone under Android 15), in French. */
internal const val PHONE = "fr-rFR-w412dp-h915dp-port-xhdpi"

/** The same phone on its side. */
internal const val PHONE_LAND = "fr-rFR-w915dp-h412dp-land-xhdpi"

/** An 11-inch tablet in landscape: 1 280 × 800 dp. */
internal const val TABLET = "fr-rFR-w1280dp-h800dp-land-hdpi"

/**
 * Reference screenshots of the phone and the tablet (M1, `UI.md` § 12), dark and OLED black,
 * numbered from 200 (`docs/screenshots/m1/`). Held upright unless the name says « paysage » or
 * « tablette ».
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = PHONE)
class MobileScreenshotTest(
    private val variant: ThemeVariant,
) {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val samples = MobileSamples

    @Test
    fun welcome() = shoot("200-premier-lancement") { MobileWelcomeScreen(onStart = {}) }

    @Test
    fun servers() =
        shoot("201-serveurs-trouves") {
            MobileServerSearchScreen(ServerSearchState(samples.servers, searching = false), {}, {}, {}, {}, {})
        }

    @Test
    fun address() =
        shoot(
            "202-saisie-adresse",
        ) { MobileAddressScreen(AddressState(AddressMode.Add, text = "192.0.2.10"), {}, {}, {}) }

    @Test
    fun profiles() =
        shoot("203-choix-du-profil") {
            MobileProfilesScreen(ProfilesState(samples.profiles, loading = false), { null }, {}, {}, {}, {})
        }

    @Test
    fun quickConnect() =
        shoot("204-quick-connect") { MobileQuickConnectScreen(QuickConnectState.Waiting("482913"), "Léo", {}, {}) }

    @Test
    fun password() =
        shoot("205-identifiants") {
            MobilePasswordScreen(PasswordState(username = "Léo", password = "secret"), {}, {}, {}, {}, {})
        }

    @Test
    fun pin() =
        shoot(
            "206-code-pin",
        ) { MobilePinScreen(PinState(PinPurpose.Unlock, samples.profiles.first(), digits = "25"), {}, {}) }

    @Test
    fun unreachable() =
        shoot(
            "207-erreur-serveur-injoignable",
        ) { MobileUnreachableScreen("Maison", SourceException.Unreachable(), {}, {}) }

    @Test
    fun home() = shoot("208-accueil") { Home(samples.home) }

    @Test
    @Config(qualifiers = PHONE_LAND)
    fun homeLandscape() = shoot("209-accueil-paysage") { Home(samples.home) }

    @Test
    @Config(qualifiers = TABLET)
    fun homeTablet() = shoot("210-accueil-tablette") { Home(samples.home) }

    @Test
    fun homeLoading() = shoot("211-accueil-chargement") { Home(HomeState(rail = samples.rail)) }

    @Test
    fun homeError() =
        shoot("212-accueil-erreur") {
            Home(HomeState(rail = samples.rail, rows = emptyList(), error = SourceException.Unreachable()))
        }

    @Test
    fun libraries() = shoot("213-bibliotheques") { MobileLibrariesScreen(samples.rail) { } }

    @Test
    fun library() = shoot("214-bibliotheque") { Library() }

    @Test
    @Config(qualifiers = TABLET)
    fun libraryTablet() = shoot("215-bibliotheque-tablette") { Library() }

    @Test
    fun libraryFilters() = shoot("216-bibliotheque-filtres") { Library(previewSheet = true) }

    @Test
    fun film() =
        shoot("217-fiche-film") {
            MobileDetailsScreen(samples.film, SampleArt.sources, NoDetails, NoNavigation, {})
        }

    @Test
    @Config(qualifiers = PHONE_LAND)
    fun filmLandscape() =
        shoot(
            "218-fiche-film-paysage",
        ) { MobileDetailsScreen(samples.film, SampleArt.sources, NoDetails, NoNavigation, {}) }

    @Test
    fun series() =
        shoot(
            "219-fiche-serie",
        ) { MobileDetailsScreen(samples.seriesDetails, SampleArt.sources, NoDetails, NoNavigation, {}) }

    @Test
    fun person() =
        shoot("220-fiche-personne") {
            MobileDetailsScreen(samples.personDetails, SampleArt.sources, NoDetails, NoNavigation, {})
        }

    @Test
    fun tracks() =
        shoot("221-fiche-pistes") {
            MobileDetailsScreen(samples.film, SampleArt.sources, NoDetails, NoNavigation, {}, previewTracks = true)
        }

    @Test
    fun searchIdle() =
        shoot("222-recherche") {
            MobileSearchScreen(
                samples.searchIdle,
                SampleArt.sources,
                NoSearch,
                NoNavigation,
                PreviewVoice(),
                focusField = false,
            )
        }

    @Test
    fun searchResults() =
        shoot("223-recherche-resultats") {
            MobileSearchScreen(
                samples.searchResults,
                SampleArt.sources,
                NoSearch,
                NoNavigation,
                PreviewVoice(),
                focusField = false,
            )
        }

    @Test
    @Config(qualifiers = PHONE_LAND)
    fun player() =
        shoot("224-lecteur") {
            MobilePlayerScreen(playerState(), NoPlayer, {}, previewControls = true) { Box(Modifier.fillMaxSize()) }
        }

    @Test
    @Config(qualifiers = PHONE_LAND)
    fun playerTracks() =
        shoot("225-lecteur-pistes") {
            MobilePlayerScreen(playerState(), NoPlayer, {}, previewTracks = true)
        }

    @Test
    fun profile() =
        shoot("226-profil") {
            MobileProfileScreen(
                MobileProfileState("Camille", "Maison", ConnectionMode.Remote, "https://example.com", null, "1.1.0"),
                {},
                {},
                {},
                playback = { MobilePlaybackSettings(PlayerSettings(), {}) },
            )
        }

    // The touch player and the margin (M2, from 240).

    @Test
    @Config(qualifiers = PHONE_LAND)
    fun playerMargin() = shootPlayer("240-lecteur-marge", MobilePlayerPreview.Controls)

    @Test
    @Config(qualifiers = PHONE_LAND)
    fun playerLocked() = shootPlayer("241-lecteur-verrouille", MobilePlayerPreview.Locked)

    @Test
    @Config(qualifiers = PHONE_LAND)
    fun playerQuality() = shootPlayer("242-lecteur-qualite", MobilePlayerPreview.Quality)

    @Test
    @Config(qualifiers = PHONE_LAND)
    fun playerInfo() = shootPlayer("243-lecteur-informations", MobilePlayerPreview.Info)

    @Test
    @Config(qualifiers = PHONE_LAND)
    fun playerJump() = shootPlayer("244-lecteur-double-appui", MobilePlayerPreview.Jump)

    @Test
    @Config(qualifiers = PHONE_LAND)
    fun playerSeek() = shootPlayer("245-lecteur-glisser-rechercher", MobilePlayerPreview.Seek)

    @Test
    @Config(qualifiers = PHONE_LAND)
    fun playerBrightness() = shootPlayer("246-lecteur-luminosite", MobilePlayerPreview.Brightness)

    @Test
    @Config(qualifiers = PHONE_LAND)
    fun playerOffline() =
        shoot("247-lecteur-reseau-coupe") {
            MobilePlayerScreen(nomadState().copy(buffering = true, offline = true), NoPlayer, {})
        }

    @Test
    @Config(qualifiers = PHONE_LAND)
    fun playerQualityReduced() =
        shoot("248-lecteur-qualite-reduite") {
            MobilePlayerScreen(nomadState().copy(qualityReduced = true), NoPlayer, {})
        }

    @Test
    fun playerPortrait() =
        shoot("249-lecteur-portrait") {
            MobilePlayerScreen(nomadState(), NoPlayer, {}, portrait = true, preview = MobilePlayerPreview.Controls)
        }

    @Test
    fun marginSetting() =
        shoot("250-reglage-marge") {
            MobilePlaybackSettings(PlayerSettings(), {}, previewSheet = MobilePlaybackSheet.Margin)
        }

    @Test
    fun mobileQualitySetting() =
        shoot("251-reglage-qualite-reseau-mobile") {
            MobilePlaybackSettings(PlayerSettings(), {}, previewSheet = MobilePlaybackSheet.Mobile)
        }

    private fun shootPlayer(
        name: String,
        preview: MobilePlayerPreview,
    ) = shoot(name) {
        MobilePlayerScreen(nomadState(), NoPlayer, {}, preview = preview) { Box(Modifier.fillMaxSize()) }
    }

    /** A film transcoded at 4 Mb/s on a mobile network, 12 min of margin ahead, 2 of them in memory. */
    private fun nomadState(): PlayerState {
        val state = playerState()
        val margin =
            MarginStatus(
                position = state.position,
                end = state.position + 12.minutes,
                memoryEnd = state.position + 2.minutes,
                target = 15.minutes,
                bandwidth = 6_400_000,
            )
        val film = state.item
        val stream =
            PlaybackStream(
                url = "http://192.0.2.10:8096/videos/film-0/master.m3u8",
                method = PlayMethod.Transcode,
                reasons = setOf(TranscodeReason.ContainerBitrateExceedsLimit),
                playSessionId = "session",
                source = film.mediaSources.first(),
                container = "ts",
                isHls = true,
                subtitles = emptyList(),
                audioIndex = 1,
                subtitleIndex = null,
                maxBitrate = 4_000_000,
            )
        return state.copy(
            stream = stream,
            maxBitrate = 4_000_000,
            margin = margin,
            info =
                PlayerSnapshot(position = state.position, buffered = 2.minutes, margin = margin, bandwidth = 7_100_000),
        )
    }

    @Test
    fun connectDevice() =
        shoot(
            "227-connecter-un-appareil",
        ) { MobileConnectDeviceScreen(ConnectDeviceState(code = "4829"), {}, {}, {}, {}) }

    @Test
    fun connectDeviceDone() =
        shoot(
            "228-appareil-connecte",
        ) { MobileConnectDeviceScreen(ConnectDeviceState(approved = "482913"), {}, {}, {}, {}) }

    @Test
    fun quickMenu() =
        shoot("229-menu-d-une-carte") {
            val menu = remember { MobileQuickMenu().also { it.open(samples.movies[1], continueRow = "resume") } }
            MobileHomeScreen(samples.home, SampleArt.sources, NoNavigation, menu = menu)
        }

    @Composable
    private fun Home(state: HomeState) = MobileHomeScreen(state, SampleArt.sources, NoNavigation)

    @Composable
    private fun Library(previewSheet: Boolean = false) =
        MobileLibraryScreen(
            samples.library,
            SampleArt.sources,
            NoLibrary,
            NoNavigation,
            {},
            previewSheet = previewSheet,
        )

    private fun playerState() =
        PlayerState(
            item = samples.movies[0],
            loading = false,
            playing = true,
            position = 64.minutes,
            duration = 112.minutes,
            audioIndex = 1,
            next = null,
        )

    private fun shoot(
        name: String,
        content: @Composable () -> Unit,
    ) {
        compose.setContent {
            // A fixed clock: « se termine à » on the details does not follow the day of the run.
            CompositionLocalProvider(LocalClock provides SCREENSHOT_CLOCK) {
                CineMobileTheme(variant = variant) { SampleArt.Provide(content) }
            }
        }
        compose.waitForIdle()
        // The whole screen: a sheet from the bottom is a window of its own.
        captureScreenRoboImage("src/test/screenshots/$name-${variant.slug}.png")
    }

    companion object {
        private val SCREENSHOT_CLOCK: Clock =
            Clock.fixed(Instant.parse("2026-10-25T19:58:10Z"), ZoneId.of("Europe/Paris"))

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants(): List<Array<Any>> = ThemeVariant.entries.map { arrayOf(it) }
    }
}

/** The name of a theme in the files of the screenshots: « sombre », « oled ». */
internal val ThemeVariant.slug: String
    get() =
        when (this) {
            ThemeVariant.Dark -> "sombre"
            ThemeVariant.OledBlack -> "oled"
        }

/** Navigation that goes nowhere, for screenshots. */
internal object NoNavigation : HomeNavigation, LibraryNavigation, DetailsNavigation, SearchNavigation {
    override fun navigate(target: RailTarget) = Unit

    override fun open(item: MediaItem) = Unit

    override fun play(item: MediaItem) = Unit

    override fun play(request: PlaybackRequest) = Unit

    override fun openSeries(
        series: MediaItem,
        season: ItemId?,
    ) = Unit

    override fun openTrailer(url: String) = Unit

    override fun problems(
        library: LibraryId,
        name: String,
    ) = Unit

    override fun customize() = Unit

    override fun quit() = Unit

    override fun sessionEnded() = Unit
}

internal object NoDetails : DetailsActions {
    override fun retry() = Unit

    override fun toggleWatched(item: MediaItem) = Unit

    override fun toggleFavorite() = Unit

    override fun selectSeason(season: ItemId) = Unit

    override fun selectVersion(sourceId: String) = Unit

    override fun selectAudio(index: Int) = Unit

    override fun selectSubtitles(index: Int?) = Unit

    override fun playRequest(fromStart: Boolean): PlaybackRequest? = null
}

internal object NoLibrary : LibraryActions {
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

internal object NoSearch : SearchActions {
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

/** No gesture: the screenshots only draw. */
internal object NoPlayer : PlayerActions {
    override fun interacted() = Unit

    override fun togglePlay() = Unit

    override fun play() = Unit

    override fun pause() = Unit

    override fun jump(forward: Boolean) = Unit

    override fun seekTo(position: Duration) = Unit

    override fun scrub(
        forward: Boolean,
        repeat: Int,
    ) = Unit

    override fun commitScrub() = Unit

    override fun cancelScrub() = Unit

    override fun chapter(forward: Boolean) = Unit

    override fun skipSegment() = Unit

    override fun playNext() = Unit

    override fun playPrevious() = Unit

    override fun dismissNext() = Unit

    override fun keepWatching() = Unit

    override fun selectAudio(index: Int) = Unit

    override fun selectSubtitles(index: Int?) = Unit

    override fun selectQuality(maxBitrate: Int?) = Unit

    override fun updateSettings(change: (PlayerSettings) -> PlayerSettings) = Unit

    override fun toggleInfo() = Unit

    override fun retry() = Unit

    override fun trickplayUrl(tile: Int): String? = null

    override val streamHeaders: Map<String, String> = emptyMap()
}

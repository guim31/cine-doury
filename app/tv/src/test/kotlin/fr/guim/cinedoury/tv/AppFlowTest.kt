package fr.guim.cinedoury.tv

import android.os.Looper
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import fr.guim.cinedoury.core.data.di.dataModule
import fr.guim.cinedoury.core.data.security.TokenCipher
import fr.guim.cinedoury.core.designsystem.theme.CineTheme
import fr.guim.cinedoury.core.designsystem.theme.ThemeVariant
import fr.guim.cinedoury.core.model.ServerInfo
import fr.guim.cinedoury.core.source.DiscoveredServer
import fr.guim.cinedoury.core.source.ServerAddress
import fr.guim.cinedoury.core.source.ServerDiscovery
import fr.guim.cinedoury.core.source.SourceConnector
import fr.guim.cinedoury.core.source.fake.InMemorySource
import fr.guim.cinedoury.feature.auth.authModule
import fr.guim.cinedoury.feature.home.homeModule
import fr.guim.cinedoury.feature.settings.settingsModule
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/**
 * The whole J1 path on an in-memory source, through the real view models, Room and DataStore:
 * first launch, server found on the network, profile, Quick Connect approved from the « phone »,
 * home, a PIN set in the settings, then asked when the profile opens again.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = TV_1080P)
class AppFlowTest {
    @get:Rule
    val compose = createTvComposeRule()

    private val address = ServerAddress.of("http://192.0.2.10:8096")
    private val source =
        InMemorySource(
            server = ServerInfo(id = "home", name = "Maison", version = "12.3.0"),
            accounts = mapOf("admin" to "secret", "enfant" to ""),
        )

    /** Robolectric has no Android Keystore. */
    private object PlainCipher : TokenCipher {
        override fun encrypt(token: String) = token

        override fun decrypt(encrypted: String) = encrypted
    }

    @Before
    fun koin() {
        stopKoin()
        startKoin {
            allowOverride(true)
            androidContext(RuntimeEnvironment.getApplication())
            modules(
                dataModule,
                module {
                    single<SourceConnector> { source }
                    single<ServerDiscovery> { ServerDiscovery { flowOf(DiscoveredServer("home", "Maison", address)) } }
                    single<TokenCipher> { PlainCipher }
                },
                authModule,
                homeModule,
                settingsModule,
            )
        }
    }

    @After
    fun stop() {
        stopKoin()
    }

    private fun waitFor(text: String) {
        compose.waitUntil(TIMEOUT) { compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun click(text: String) {
        waitFor(text)
        compose.onNodeWithText(text).performClick()
        compose.waitForIdle()
    }

    /** A profile tile: its name sits under the clickable avatar. Profiles are sorted by name. */
    private fun clickProfile(index: Int) {
        val tile = hasClickAction() and hasAnyAncestor(hasTestTag("profile-$index"))
        compose.waitUntil(TIMEOUT) { compose.onAllNodes(tile).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(tile).performClick()
        compose.waitForIdle()
    }

    /** Lets the main looper run its delayed work (the Quick Connect polling). */
    private fun wait(seconds: Long) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(seconds))
        compose.waitForIdle()
    }

    @Test
    fun `from the first launch to a profile behind its PIN`() {
        compose.setTvContent {
            CineTheme(ThemeVariant.Dark) { CineApp(ThemeVariant.Dark, onToggleVariant = {}, showCatalog = false) }
        }

        click("Commencer")
        click("Maison")
        waitFor("enfant")
        clickProfile(ENFANT)

        // Quick Connect: the code of the in-memory server, approved from the « phone ».
        waitFor("482  913")
        source.approveQuickConnect("482913", "enfant")
        wait(seconds = 3)
        waitFor("Bonjour, enfant")

        compose.onNodeWithContentDescription("Réglages").performClick()
        click("Définir un code PIN")
        repeat(2) { "2580".forEach { digit -> click(digit.toString()) } }
        waitFor("Changer le code PIN")

        click("Changer de profil")
        clickProfile(ENFANT)
        waitFor("Saisissez le code PIN")
        "2580".forEach { digit -> click(digit.toString()) }
        waitFor("Bonjour, enfant")
    }

    private companion object {
        const val TIMEOUT = 10_000L

        /** « admin », then « enfant ». */
        const val ENFANT = 1
    }
}

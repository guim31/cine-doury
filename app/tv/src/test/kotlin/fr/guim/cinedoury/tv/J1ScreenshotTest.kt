package fr.guim.cinedoury.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import fr.guim.cinedoury.core.data.ConnectionMode
import fr.guim.cinedoury.core.data.Profile
import fr.guim.cinedoury.core.designsystem.component.LocalPreviewFocus
import fr.guim.cinedoury.core.designsystem.theme.CineTheme
import fr.guim.cinedoury.core.designsystem.theme.ThemeVariant
import fr.guim.cinedoury.core.source.SourceException
import fr.guim.cinedoury.core.ui.PREVIEW_DETAILS
import fr.guim.cinedoury.core.ui.PREVIEW_RETRY
import fr.guim.cinedoury.feature.auth.AddressMode
import fr.guim.cinedoury.feature.auth.AddressScreen
import fr.guim.cinedoury.feature.auth.AddressState
import fr.guim.cinedoury.feature.auth.AuthPreview
import fr.guim.cinedoury.feature.auth.PasswordScreen
import fr.guim.cinedoury.feature.auth.PasswordState
import fr.guim.cinedoury.feature.auth.PinPurpose
import fr.guim.cinedoury.feature.auth.PinScreen
import fr.guim.cinedoury.feature.auth.PinState
import fr.guim.cinedoury.feature.auth.ProfilesScreen
import fr.guim.cinedoury.feature.auth.ProfilesState
import fr.guim.cinedoury.feature.auth.QuickConnectScreen
import fr.guim.cinedoury.feature.auth.QuickConnectState
import fr.guim.cinedoury.feature.auth.ServerSearchScreen
import fr.guim.cinedoury.feature.auth.ServerSearchState
import fr.guim.cinedoury.feature.auth.UnreachableScreen
import fr.guim.cinedoury.feature.auth.WelcomeScreen
import fr.guim.cinedoury.feature.settings.PREVIEW_PIN_ROW
import fr.guim.cinedoury.feature.settings.SettingsNavigation
import fr.guim.cinedoury.feature.settings.SettingsScreen
import fr.guim.cinedoury.feature.settings.SettingsState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Reference screenshots of every J1 screen, 1920 × 1080, dark and OLED black (`UI.md` § 12). The
 * focused element is drawn through `LocalPreviewFocus` (`CLAUDE.md`, « Pièges »).
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = TV_1080P)
class J1ScreenshotTest(
    private val variant: ThemeVariant,
) {
    @get:Rule
    val compose = createTvComposeRule()

    @Test
    fun welcome() = shoot("20-premier-lancement", AuthPreview.START) { WelcomeScreen(onStart = {}) }

    @Test
    fun servers() =
        shoot("21-serveurs-trouves", AuthPreview.SERVER) {
            ServerSearchScreen(ServerSearchState(J1Samples.servers, searching = false), {}, {}, {}, {}, {})
        }

    @Test
    fun address() =
        shoot("22-saisie-adresse", AuthPreview.ADDRESS_FIELD) {
            AddressScreen(AddressState(AddressMode.Add, text = "192.0.2.10"), {}, {}, {})
        }

    @Test
    fun quickConnect() =
        shoot("23-quick-connect", AuthPreview.USE_PASSWORD) {
            QuickConnectScreen(
                QuickConnectState.Waiting("482913"),
                username = "Léo",
                onNewCode = {},
                onUsePassword = {},
            )
        }

    @Test
    fun password() =
        shoot("24-identifiants", AuthPreview.PASSWORD_FIELD) {
            PasswordScreen(PasswordState(username = "Léo", password = "••••"), {}, {}, {}, {}, {})
        }

    @Test
    fun profiles() =
        shoot("25-choix-du-profil", AuthPreview.PROFILE) {
            ProfilesScreen(ProfilesState(J1Samples.profiles, remote = true, loading = false), { null }, {}, {}, {}, {})
        }

    @Test
    fun pin() =
        shoot("26-code-pin", AuthPreview.KEY) {
            PinScreen(PinState(PinPurpose.Unlock, J1Samples.profiles.first(), digits = "25"), {}, {})
        }

    @Test
    fun pinCreate() =
        shoot("27-code-pin-nouveau", AuthPreview.KEY) {
            PinScreen(PinState(PinPurpose.Create, J1Samples.profiles[1], confirming = true), {}, {})
        }

    @Test
    fun unreachable() =
        shoot("28-erreur-serveur-injoignable", PREVIEW_RETRY) {
            UnreachableScreen("Maison", SourceException.Unreachable(), {}, {})
        }

    @Test
    fun refused() =
        shoot("29-erreur-identifiants", AuthPreview.PASSWORD_FIELD) {
            PasswordScreen(PasswordState(username = "Léo", refused = true), {}, {}, {}, {}, {})
        }

    @Test
    fun expired() =
        shoot("30-erreur-code-expire", PREVIEW_RETRY) {
            QuickConnectScreen(QuickConnectState.Expired, username = "Léo", onNewCode = {}, onUsePassword = {})
        }

    @Test
    fun tooOld() =
        shoot("31-erreur-serveur-trop-ancien", PREVIEW_RETRY) {
            AddressScreen(
                AddressState(
                    AddressMode.Add,
                    text = "192.0.2.10",
                    error = SourceException.Incompatible("10.11.2", "12.3.0"),
                ),
                {},
                {},
                {},
            )
        }

    @Test
    fun errorDetails() =
        shoot("35-erreur-plus-d-infos", PREVIEW_DETAILS) {
            PasswordScreen(
                PasswordState(
                    username = "Léo",
                    error =
                        SourceException
                            .Unexpected(IllegalStateException("Network read on the main thread"))
                            .at("POST /Users/AuthenticateByName", 200),
                ),
                {},
                {},
                {},
                {},
                {},
                showErrorDetails = true,
            )
        }

    @Test
    fun hiddenAccount() =
        shoot("36-compte-masque", AuthPreview.PROFILES_QUICK_CONNECT) {
            ProfilesScreen(ProfilesState(emptyList(), loading = false), { null }, {}, {}, {}, {})
        }

    @Test
    fun settings() = shoot("33-reglages", PREVIEW_PIN_ROW) { Settings() }

    @Test
    fun forgetServer() = shoot("34-oublier-le-serveur", "forget-cancel") { Settings(confirming = true) }

    @Composable
    private fun Settings(confirming: Boolean = false) {
        SettingsScreen(
            state = SettingsState(J1Samples.session(ConnectionMode.Remote)),
            showCatalog = true,
            navigation = NoNavigation,
            onToggleStartup = {},
            onSwitchProfile = {},
            onSignOut = {},
            onForgetServer = {},
            confirmingForget = confirming,
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
        compose.onRoot().captureRoboImage("src/test/screenshots/$name-${variant.slug}.png")
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants(): List<Array<Any>> = ThemeVariant.entries.map { arrayOf(it) }
    }
}

internal object NoNavigation : SettingsNavigation {
    override fun customizeHome() = Unit

    override fun pin(
        profile: Profile,
        remove: Boolean,
    ) = Unit

    override fun address(remote: Boolean) = Unit

    override fun catalog() = Unit

    override fun profileClosed() = Unit

    override fun serverForgotten() = Unit
}

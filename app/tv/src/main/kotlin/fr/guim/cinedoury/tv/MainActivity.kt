package fr.guim.cinedoury.tv

import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.lifecycle.lifecycleScope
import fr.guim.cinedoury.core.data.DeviceIdentity
import fr.guim.cinedoury.core.data.SessionManager
import fr.guim.cinedoury.core.data.home.ProfileKey
import fr.guim.cinedoury.core.data.prefs.AppPreferences
import fr.guim.cinedoury.core.data.prefs.Appearance
import fr.guim.cinedoury.core.data.prefs.Browsing
import fr.guim.cinedoury.core.data.prefs.ThemeChoice
import fr.guim.cinedoury.core.designsystem.theme.CardPreferences
import fr.guim.cinedoury.core.designsystem.theme.CineMotion
import fr.guim.cinedoury.core.designsystem.theme.CineTheme
import fr.guim.cinedoury.core.designsystem.theme.ThemeVariant
import fr.guim.cinedoury.core.source.SourceConnector
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * The app opens on its start screen; the design system catalogue is in the settings (debug). The
 * theme follows « Apparence » (this device) and the cards « Accueil et navigation » (the open
 * profile), `SPEC.md` § 4.17.
 */
class MainActivity : ComponentActivity() {
    private val preferences: AppPreferences by inject()
    private val sessions: SessionManager by inject()
    private val connector: SourceConnector by inject()
    private val identity: DeviceIdentity by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val debuggable = application.isDebuggable
        // The name of the device on the server follows « Nom de l'appareil » (SPEC.md § 4.17).
        lifecycleScope.launch {
            preferences.deviceName.collect { name -> connector.setDeviceName(name ?: identity.deviceName) }
        }
        setContent {
            val appearance by preferences.appearance.collectAsState(initial = Appearance())
            val active by sessions.active.collectAsState()
            val profile = active?.profile?.let { ProfileKey(it.serverId, it.user.id.value) }
            val browsing by remember(profile) {
                profile?.let { preferences.browsing(it) } ?: flowOf(Browsing())
            }.collectAsState(initial = Browsing())
            val scope = rememberCoroutineScope()
            val variant = if (appearance.theme == ThemeChoice.OledBlack) ThemeVariant.OledBlack else ThemeVariant.Dark
            // The test tags are resource ids for UiAutomator: :benchmark drives the optimised APK from
            // outside, where it cannot reach Compose's semantics otherwise.
            Box(Modifier.semantics { testTagsAsResourceId = true }) {
                CineTheme(
                    variant = variant,
                    reducedMotion = CineMotion.isReduced(appearance.reducedMotion, systemAnimatorScale()),
                    textScale = appearance.textSize.scale,
                    cards =
                        CardPreferences(
                            alwaysShowTitles = browsing.posterTitles,
                            unwatchedMarks = browsing.unwatchedMarks,
                        ),
                ) {
                    CineApp(
                        variant = variant,
                        onToggleVariant = {
                            scope.launch {
                                preferences.setAppearance {
                                    it.copy(
                                        theme =
                                            if (it.theme == ThemeChoice.Dark) ThemeChoice.OledBlack else ThemeChoice.Dark,
                                    )
                                }
                            }
                        },
                        showCatalog = debuggable,
                    )
                }
            }
        }
    }

    /** The animator duration scale of the system: 0 when its animations are turned off. */
    private fun systemAnimatorScale(): Float =
        Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
}

package fr.guim.cinedoury.mobile

import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.lifecycle.lifecycleScope
import fr.guim.cinedoury.core.data.SessionManager
import fr.guim.cinedoury.core.data.prefs.AppPreferences
import fr.guim.cinedoury.core.data.prefs.Appearance
import fr.guim.cinedoury.core.data.prefs.ThemeChoice
import fr.guim.cinedoury.core.designsystem.mobile.CineMobileTheme
import fr.guim.cinedoury.core.designsystem.theme.CineMotion
import fr.guim.cinedoury.core.designsystem.theme.ThemeVariant
import org.koin.android.ext.android.inject

/**
 * The phone's activity (M1): edge to edge, the theme of « Apparence » (sombre or noir OLED, the
 * device's setting, as on the TV), and the network followed while it is in front ([NetworkWatcher]).
 * It handles its own rotation (the manifest): nothing is lost, the player keeps playing.
 */
class MainActivity : ComponentActivity() {
    private val preferences: AppPreferences by inject()
    private val sessions: SessionManager by inject()
    private val network by lazy { NetworkWatcher(this, sessions, lifecycleScope) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val version = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
        setContent {
            val appearance by preferences.appearance.collectAsState(initial = Appearance())
            val variant = if (appearance.theme == ThemeChoice.OledBlack) ThemeVariant.OledBlack else ThemeVariant.Dark
            // The test tags are resource ids for UiAutomator (the journey of the phone emulator).
            Box(Modifier.semantics { testTagsAsResourceId = true }) {
                CineMobileTheme(
                    variant = variant,
                    reducedMotion = CineMotion.isReduced(appearance.reducedMotion, systemAnimatorScale()),
                ) {
                    MobileApp(version = version)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        network.start()
    }

    override fun onStop() {
        network.stop()
        super.onStop()
    }

    private fun systemAnimatorScale(): Float =
        Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
}

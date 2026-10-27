package fr.guim.cinedoury.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import fr.guim.cinedoury.core.designsystem.theme.CineTheme
import fr.guim.cinedoury.core.designsystem.theme.ThemeVariant

/** The app opens on its start screen; the design system catalogue is in the settings (debug). */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val debuggable = application.isDebuggable
        setContent {
            var variant by rememberSaveable { mutableStateOf(ThemeVariant.Dark) }
            // The test tags are resource ids for UiAutomator: :benchmark drives the optimised APK from
            // outside, where it cannot reach Compose's semantics otherwise.
            Box(Modifier.semantics { testTagsAsResourceId = true }) {
                CineTheme(variant = variant) {
                    CineApp(
                        variant = variant,
                        onToggleVariant = {
                            variant = if (variant == ThemeVariant.Dark) ThemeVariant.OledBlack else ThemeVariant.Dark
                        },
                        showCatalog = debuggable,
                    )
                }
            }
        }
    }
}

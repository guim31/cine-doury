package fr.guim.cinedoury.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import fr.guim.cinedoury.core.designsystem.theme.CineTheme
import fr.guim.cinedoury.core.designsystem.theme.ThemeVariant

/** The app opens on its start screen; the design system catalogue is in the settings (debug). */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val debuggable = application.isDebuggable
        setContent {
            var variant by rememberSaveable { mutableStateOf(ThemeVariant.Dark) }
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

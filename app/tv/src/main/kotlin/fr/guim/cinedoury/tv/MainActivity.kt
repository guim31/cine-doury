package fr.guim.cinedoury.tv

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import fr.guim.cinedoury.core.designsystem.theme.CineTheme
import fr.guim.cinedoury.core.designsystem.theme.ThemeVariant
import kotlinx.coroutines.flow.MutableSharedFlow

/** The app opens on its start screen; the design system catalogue is in the settings (debug). */
class MainActivity : ComponentActivity() {
    /** The « Recherche » keys of the remote, from any screen (`SPEC.md` § 4.12). */
    private val searchKeys = MutableSharedFlow<SearchKey>(extraBufferCapacity = 1)

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val key =
            when (event.keyCode) {
                KeyEvent.KEYCODE_SEARCH -> SearchKey.Search

                // Most TVs keep the voice key for their assistant; when it reaches the app, the search listens.
                KeyEvent.KEYCODE_VOICE_ASSIST -> SearchKey.Voice

                else -> null
            }
        if (key == null) return super.dispatchKeyEvent(event)
        if (event.action == KeyEvent.ACTION_UP && event.repeatCount == 0) searchKeys.tryEmit(key)
        return true
    }

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
                    searchKeys = searchKeys,
                )
            }
        }
    }
}

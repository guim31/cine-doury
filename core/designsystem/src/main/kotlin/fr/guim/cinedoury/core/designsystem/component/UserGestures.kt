package fr.guim.cinedoury.core.designsystem.component

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Tells a move of the focus made by the remote from one the app makes on its own (1.0.0, I and J).
 * The root of the app [mark]s each key; until the main thread has finished with it, what moves the
 * focus is the user's doing (← towards the rail, « Retour » on the home). A focus given back later,
 * a frame or more after (a screen that loads, whose rail is the only focusable element for a while),
 * is the app's: the rail does not open for it.
 */
@Stable
class UserGestures {
    /** A key is being handled now. */
    var active: Boolean = false
        private set

    private val handler = Handler(Looper.getMainLooper())
    private val end = Runnable { active = false }

    /**
     * A key arrives (or the app acts for one, as `BackHandler` does for « Retour »). The end goes at the
     * front of the queue: no frame, which would compose the next screen, runs before it.
     */
    fun mark() {
        active = true
        handler.removeCallbacks(end)
        handler.postAtFrontOfQueue(end)
    }
}

/** The gestures of the app; none in previews and screens tested alone: the rail opens on any focus. */
val LocalUserGestures = staticCompositionLocalOf<UserGestures?> { null }

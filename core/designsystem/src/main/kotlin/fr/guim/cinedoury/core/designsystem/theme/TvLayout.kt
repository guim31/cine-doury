package fr.guim.cinedoury.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Density

/**
 * The TV screen of the design: 960 × 540 dp, whatever the screen (`UI.md` § 2, `SPEC.md` § 4.16).
 * A Shield TV in 4K lays its apps out on 1920 × 1080 dp (3840 px at xhdpi): without this, every
 * screen there is drawn at half its size, with twice the cards to compose.
 */
object TvLayout {
    /** Width of the screen of the design, in dp. */
    const val WIDTH_DP = 960f

    /** Pixels per dp of a 1080p screen laid out on [WIDTH_DP]: the size images are asked at ([imageScale]). */
    const val IMAGE_DENSITY = 2f

    /** The density that lays a window [widthPx] wide out on [WIDTH_DP]. */
    fun density(
        widthPx: Int,
        fontScale: Float,
    ): Density = Density(widthPx / WIDTH_DP, fontScale)

    /**
     * What an image's size in pixels is multiplied by before it is asked of the server: 1 up to a
     * 1080p screen, 0.5 in 4K. A poster asked at 4K would weigh four times the memory of the 1080p
     * one for a difference nobody sees from the sofa, and evict the others from Coil's cache.
     */
    fun imageScale(density: Float): Float = (IMAGE_DENSITY / density).coerceAtMost(1f)
}

/**
 * Lays [content] out on the screen of the design, [TvLayout.WIDTH_DP] wide: the density follows the
 * width of the window, so that a 4K screen shows exactly what a 1080p one does, twice as sharp. It
 * follows the window when it changes (a mode of the screen set while the app runs), which a density
 * fixed when the activity starts would not. Everything Compose draws follows it (dialogs included,
 * they are drawn in the same window); the views inside (the video, its subtitles) size themselves
 * in fractions of the picture.
 */
@Composable
fun TvLayoutDensity(content: @Composable () -> Unit) {
    val width = LocalWindowInfo.current.containerSize.width
    val screen = LocalDensity.current
    val density =
        remember(width, screen) {
            if (width > 0) TvLayout.density(width, screen.fontScale) else screen
        }
    CompositionLocalProvider(LocalDensity provides density, content = content)
}

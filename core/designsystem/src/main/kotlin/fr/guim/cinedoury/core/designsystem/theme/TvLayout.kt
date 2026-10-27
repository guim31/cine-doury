package fr.guim.cinedoury.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Density

/**
 * « Taille de l'interface » (essai réel 3, `SPEC.md` § 4.21): the width, in dp, of the screen the
 * whole interface is laid out on, whatever the screen. The wider, the smaller everything is drawn
 * (text, cards, margins), and the more a screen shows.
 *
 * - [Compact], 1 200 × 675 dp, the default: everything at 80 % of `UI.md`. The smallest text of the
 *   scale (« Minimum absolu », 14 sp) is 22 px high on a 1080p screen, the « Corps » 32 px: the most
 *   compact still read from 3 m;
 * - [Normal], 1 080 × 608 dp, at 89 %;
 * - [Large], 960 × 540 dp, the size of `UI.md` (and of every version up to 0.7).
 */
enum class LayoutSize(
    val widthDp: Float,
) {
    Compact(1200f),
    Normal(1080f),
    Large(960f),
    ;

    companion object {
        val Default = Compact
    }
}

/**
 * The TV screen of the design, [LayoutSize.widthDp] wide whatever the screen (`UI.md` § 2,
 * `SPEC.md` § 4.16 and § 4.21). A Shield TV in 4K lays its apps out on 1920 × 1080 dp (3840 px at
 * xhdpi): without this, every screen there is drawn at half its size, with twice the cards to
 * compose.
 */
object TvLayout {
    /** Width of the screen of `UI.md`, in dp: the sizes of the design system are thought on it. */
    const val WIDTH_DP = 960f

    /** Width of a 1080p screen, in pixels: the most images are asked at ([imageScale]). */
    const val IMAGE_WIDTH_PX = 1920f

    /** The density that lays a window [widthPx] wide out on [layoutWidthDp]. */
    fun density(
        widthPx: Int,
        fontScale: Float,
        layoutWidthDp: Float = WIDTH_DP,
    ): Density = Density(widthPx / layoutWidthDp, fontScale)

    /**
     * What an image's size in pixels is multiplied by before it is asked of the server, for a window
     * [widthPx] wide: 1 up to a 1080p screen, 0.5 in 4K, whatever the size of the interface. A poster
     * asked at 4K would weigh four times the memory of the 1080p one for a difference nobody sees from
     * the sofa, and evict the others from Coil's cache.
     */
    fun imageScale(widthPx: Int): Float = if (widthPx > 0) (IMAGE_WIDTH_PX / widthPx).coerceAtMost(1f) else 1f
}

/** The [TvLayout.imageScale] of the window the content is drawn in. */
val LocalImageScale = staticCompositionLocalOf { 1f }

/**
 * Lays [content] out on the screen of the design, [size] wide: the density follows the width of the
 * window, so that a 4K screen shows exactly what a 1080p one does, twice as sharp. It follows the
 * window when it changes (a mode of the screen set while the app runs), which a density fixed when
 * the activity starts would not. Everything Compose draws follows it (dialogs included, they are
 * drawn in the same window); the views inside (the video, its subtitles) size themselves in fractions
 * of the picture.
 */
@Composable
fun TvLayoutDensity(
    size: LayoutSize = LayoutSize.Default,
    content: @Composable () -> Unit,
) {
    val width = LocalWindowInfo.current.containerSize.width
    val screen = LocalDensity.current
    val density =
        remember(width, screen, size) {
            if (width > 0) TvLayout.density(width, screen.fontScale, size.widthDp) else screen
        }
    CompositionLocalProvider(
        LocalDensity provides density,
        LocalImageScale provides TvLayout.imageScale(width),
        content = content,
    )
}

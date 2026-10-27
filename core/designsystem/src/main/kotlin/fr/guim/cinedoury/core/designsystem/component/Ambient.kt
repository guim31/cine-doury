package fr.guim.cinedoury.core.designsystem.component

import android.graphics.Bitmap
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import fr.guim.cinedoury.core.designsystem.image.BlurHash
import fr.guim.cinedoury.core.designsystem.theme.CineAmbient
import fr.guim.cinedoury.core.designsystem.theme.CineMotion
import fr.guim.cinedoury.core.designsystem.theme.CineTheme
import kotlinx.coroutines.delay

/** What the ambient backdrop is drawn from: an image's BlurHash, or a generated card's gradient. */
@Immutable
sealed interface Ambience {
    data class Blurred(
        val blurHash: String,
    ) : Ambience

    data class Gradient(
        val top: Color,
        val bottom: Color,
    ) : Ambience

    companion object {
        /** The backdrop of an element: its image's BlurHash, or the gradient of its generated card. */
        fun of(
            title: String,
            blurHash: String?,
        ): Ambience = blurHash?.let(::Blurred) ?: generated(title)

        fun generated(title: String): Ambience = artworkColors(title).let { (top, bottom) -> Gradient(top, bottom) }
    }
}

/**
 * The full-screen ambient backdrop of the navigation screens (`UI.md` § 3, § 4 and § 8): a very
 * blurred image of the focused element, dimmed and under the readability veils. It follows the
 * focus once it has rested for 250 ms, with a 300 ms crossfade, so that fast scrolling does not
 * flicker. No RenderEffect (API 31): the BlurHash is decoded at 32 × 18 and stretched with
 * bilinear filtering, which costs nothing and works on Android 11.
 */
@Composable
fun AmbientBackdrop(
    ambience: Ambience?,
    modifier: Modifier = Modifier,
) {
    val colors = CineTheme.colors
    val settled by settledAmbience(ambience)
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(colors.background),
    ) {
        Crossfade(
            targetState = settled,
            animationSpec = CineMotion.fade<Float>(CineMotion.BACKDROP_FADE_MILLIS),
            label = "ambience",
        ) { target ->
            Box(Modifier.fillMaxSize().readabilityScrims(colors.background)) {
                AmbienceLayer(target, colors.background)
            }
        }
    }
}

/** [target], once it has stayed the same for [CineMotion.BACKDROP_DELAY_MILLIS]; the first value at once. */
@Composable
private fun settledAmbience(target: Ambience?): State<Ambience?> {
    val settled = remember { mutableStateOf(target) }
    LaunchedEffect(target) {
        if (settled.value != target) {
            delay(CineMotion.BACKDROP_DELAY_MILLIS.toLong())
            settled.value = target
        }
    }
    return settled
}

/**
 * The ambience, already dimmed by [dim] at [CineAmbient.DIM_ALPHA]: the 32 × 18 pixels (or the two
 * colours of the gradient) are blended once, instead of a full-screen veil drawn over them at each
 * frame, the same picture for one layer less to fill (`SPEC.md` § 4.20).
 */
@Composable
private fun AmbienceLayer(
    ambience: Ambience?,
    dim: Color,
) {
    when (ambience) {
        is Ambience.Blurred -> {
            val bitmap =
                remember(ambience.blurHash, dim) {
                    BlurHash
                        .decode(ambience.blurHash, CineAmbient.DECODE_WIDTH, CineAmbient.DECODE_HEIGHT)
                        ?.let { pixels ->
                            for (i in pixels.indices) pixels[i] = dimmed(Color(pixels[i]), dim).toArgb()
                            Bitmap
                                .createBitmap(
                                    pixels,
                                    CineAmbient.DECODE_WIDTH,
                                    CineAmbient.DECODE_HEIGHT,
                                    Bitmap.Config.ARGB_8888,
                                ).asImageBitmap()
                        }
                }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds,
                    filterQuality = FilterQuality.Low,
                )
            }
        }

        null -> {}

        is Ambience.Gradient -> {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(listOf(dimmed(ambience.top, dim), dimmed(ambience.bottom, dim)))),
            )
        }
    }
}

/** [color] under a veil of [dim] at [CineAmbient.DIM_ALPHA], blended as the GPU does (sRGB, source over). */
private fun dimmed(
    color: Color,
    dim: Color,
): Color {
    val alpha = CineAmbient.DIM_ALPHA
    return Color(
        red = color.red * (1f - alpha) + dim.red * alpha,
        green = color.green * (1f - alpha) + dim.green * alpha,
        blue = color.blue * (1f - alpha) + dim.blue * alpha,
    )
}

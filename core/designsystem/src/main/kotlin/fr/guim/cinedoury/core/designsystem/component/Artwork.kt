package fr.guim.cinedoury.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.tv.material3.Text
import fr.guim.cinedoury.core.designsystem.theme.CineSizes
import fr.guim.cinedoury.core.designsystem.theme.CineSpacing
import fr.guim.cinedoury.core.designsystem.theme.CineTheme

private const val HUE_TURN = 360f
private const val HUE_SHIFT = 38f
private const val TOP_SATURATION = 0.42f
private const val TOP_VALUE = 0.46f
private const val BOTTOM_SATURATION = 0.58f
private const val BOTTOM_VALUE = 0.17f
private const val HASH_SPREAD = 31
private const val HASH_SEED = 7

/**
 * Two colours derived from a title, always the same for the same title. They stay dark and muted
 * so that the generated card sits calmly among real posters.
 */
fun artworkColors(title: String): Pair<Color, Color> {
    val hash = title.fold(HASH_SEED) { acc, char -> acc * HASH_SPREAD + char.code }
    val hue = Math.floorMod(hash, HUE_TURN.toInt()).toFloat()
    val top = Color.hsv(hue, TOP_SATURATION, TOP_VALUE)
    val bottom = Color.hsv((hue + HUE_SHIFT) % HUE_TURN, BOTTOM_SATURATION, BOTTOM_VALUE)
    return top to bottom
}

/**
 * Card shown when an item has no image (`UI.md` § 6): the title set in type on a gradient taken
 * from the name. Never a « broken image » icon.
 */
@Composable
fun GeneratedArtwork(
    title: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val (top, bottom) = remember(title) { artworkColors(title) }
    val typography = CineTheme.typography
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(Brush.linearGradient(listOf(top, bottom))),
        contentAlignment = Alignment.BottomStart,
    ) {
        Text(
            text = title,
            style = if (compact) typography.caption else typography.title2,
            color = CineTheme.colors.text,
            maxLines = if (compact) 3 else 4,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(if (compact) CineSpacing.xs else CineSpacing.s),
        )
    }
}

/** Bar of `UI.md` § 6: 4 dp, accent on a white track at 25 %, at the bottom of a card. */
@Composable
fun CardProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val colors = CineTheme.colors
    Canvas(
        modifier =
            modifier
                .fillMaxWidth()
                .height(CineSizes.progressHeight),
    ) {
        drawRect(colors.progressTrack)
        drawRect(colors.accent, size = size.copy(width = size.width * progress.coerceIn(0f, 1f)))
    }
}

/** Small accent triangle in the top right corner of an unwatched item (`UI.md` § 6). */
@Composable
fun UnwatchedTriangle(
    modifier: Modifier = Modifier,
    size: Dp = CineSizes.unwatchedTriangle,
) {
    val accent = CineTheme.colors.accent
    Canvas(modifier = modifier.size(size)) {
        val path =
            Path().apply {
                moveTo(0f, 0f)
                lineTo(this@Canvas.size.width, 0f)
                lineTo(this@Canvas.size.width, this@Canvas.size.height)
                close()
            }
        drawPath(path, accent)
    }
}

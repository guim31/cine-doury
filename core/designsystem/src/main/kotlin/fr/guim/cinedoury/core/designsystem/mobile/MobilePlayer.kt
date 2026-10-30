package fr.guim.cinedoury.core.designsystem.mobile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import fr.guim.cinedoury.core.designsystem.theme.CineSpacing
import fr.guim.cinedoury.core.designsystem.theme.CineTheme
import fr.guim.cinedoury.core.designsystem.theme.MobileSizes

/**
 * The bar of the touch player (M2, `SPEC.md` § 4.27): what has played in yellow, then the margin in
 * light grey (from the position to the end of what is loaded, memory and disk alike), then the rest of
 * the film. A touch or a slide moves it ([onSeek] while the finger is down, [onSeekEnd] when it lifts);
 * 48 dp high for the finger.
 *
 * @param progress, [margin] between 0 and 1 of the film; [margin] is where the loaded part ends.
 */
@Composable
fun MobileSeekBar(
    progress: Float,
    margin: Float,
    onSeek: (Float) -> Unit,
    onSeekEnd: () -> Unit,
    modifier: Modifier = Modifier,
    dragging: Boolean = false,
) {
    val colors = CineTheme.colors
    val seek by rememberUpdatedState(onSeek)
    val end by rememberUpdatedState(onSeekEnd)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(MobileSizes.seekBarTouch)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(progress.coerceIn(0f, 1f), 0f..1f)
                setProgress { value ->
                    seek(value)
                    end()
                    true
                }
            }.pointerInput(Unit) {
                detectTapGestures { offset ->
                    seek((offset.x / size.width).coerceIn(0f, 1f))
                    end()
                }
            }.pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { seek((it.x / size.width).coerceIn(0f, 1f)) },
                    onDragEnd = { end() },
                    onDragCancel = { end() },
                ) { change, _ ->
                    change.consume()
                    seek((change.position.x / size.width).coerceIn(0f, 1f))
                }
            },
    ) {
        val track = (if (dragging) MobileSizes.seekTrackActive else MobileSizes.seekTrack).toPx()
        val top = (size.height - track) / 2
        val radius = CornerRadius(track / 2)
        val played = size.width * progress.coerceIn(0f, 1f)
        val loaded = size.width * margin.coerceIn(progress.coerceIn(0f, 1f), 1f)
        drawRoundRect(colors.progressTrack, Offset(0f, top), Size(size.width, track), radius)
        drawRoundRect(colors.progressBuffer, Offset(0f, top), Size(loaded, track), radius)
        drawRoundRect(colors.accent, Offset(0f, top), Size(played, track), radius)
        drawCircle(colors.accent, MobileSizes.seekThumb.toPx() / 2, Offset(played, size.height / 2))
    }
}

/**
 * What a gesture of the player changes, while the finger moves: an icon, a bar from 0 to [level]
 * (brightness, volume), or a text (a time, « −30 s »).
 */
@Composable
fun MobileGestureIndicator(
    icon: ImageVector?,
    modifier: Modifier = Modifier,
    level: Float? = null,
    text: String? = null,
) {
    val colors = CineTheme.colors
    Row(
        modifier
            .clip(RoundedCornerShape(MobileSizes.gestureIndicator.height / 2))
            .background(colors.playerPanel)
            .padding(horizontal = CineSpacing.s, vertical = CineSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CineSpacing.xs),
    ) {
        icon?.let {
            Icon(
                it,
                contentDescription = null,
                tint = colors.text,
                modifier = Modifier.size(MobileSizes.icon),
            )
        }
        level?.let {
            Box(
                Modifier
                    .size(MobileSizes.gestureBar)
                    .clip(CircleShape)
                    .background(colors.progressTrack),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(it.coerceIn(0f, 1f))
                        .height(MobileSizes.gestureBar.height)
                        .background(colors.accent),
                )
            }
        }
        text?.let { Text(it, style = CineTheme.typography.label, color = colors.text) }
    }
}

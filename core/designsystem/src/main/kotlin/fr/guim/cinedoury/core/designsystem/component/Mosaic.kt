package fr.guim.cinedoury.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.tv.material3.Text
import fr.guim.cinedoury.core.designsystem.theme.CineScale
import fr.guim.cinedoury.core.designsystem.theme.CineSizes
import fr.guim.cinedoury.core.designsystem.theme.CineSpacing
import fr.guim.cinedoury.core.designsystem.theme.CineTheme

/**
 * A tile of the mosaic « Ce soir » at the top of the home (essai réel 5, `UI.md` § 8): a landscape
 * picture (a backdrop, the frame of an episode) filling the tile, a veil under the text, the reason it
 * is there ([eyebrow]: « REPRENDRE · RESTE 14 MIN », « NOUVEL ÉPISODE »), the title (or [logo] on the
 * [large] tile), a line of information, and the progress of a playback started. The size is the
 * caller's: the mosaic lays its tiles out on its grid.
 *
 * @param image the picture; without it, a [GeneratedArtwork] stands in.
 */
@Composable
fun MosaicTile(
    title: String,
    eyebrow: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    line: String? = null,
    large: Boolean = false,
    progress: Float? = null,
    focusRequester: FocusRequester? = null,
    focus: CineFocus = rememberCineFocus(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    logo: (@Composable () -> Unit)? = null,
    image: (@Composable () -> Unit)? = null,
) {
    val colors = CineTheme.colors
    val typography = CineTheme.typography
    Box(
        modifier =
            modifier
                .withFocusRequester(focusRequester)
                .cineFocusable(
                    focus,
                    CardShape,
                    if (large) CineScale.TILE_LARGE else CineScale.TILE,
                    onClick,
                    CardOutlineShape,
                    interactionSource,
                ).semantics { contentDescription = listOfNotNull(eyebrow, title, line).joinToString(", ") }
                .background(colors.surface1),
    ) {
        if (image != null) image() else GeneratedArtwork(title, compact = !large)
        // The text stands on a veil, darker towards the bottom.
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.Transparent, colors.shadow), startY = 0f)),
        )
        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(
                        start = if (large) CineSpacing.m else CineSpacing.s,
                        end = if (large) CineSpacing.m else CineSpacing.s,
                        bottom = (if (large) CineSpacing.m else CineSpacing.s) + CineSizes.progressHeight,
                    ),
            verticalArrangement = Arrangement.spacedBy(CineSpacing.xs / 2),
        ) {
            Eyebrow(eyebrow, large)
            if (large && logo != null) {
                Box(
                    Modifier.height(CineSizes.mosaicLogoHeight).fillMaxWidth(),
                    contentAlignment = Alignment.BottomStart,
                ) {
                    logo()
                }
            } else {
                Text(
                    text = title,
                    style = if (large) typography.title1 else typography.label,
                    color = colors.text,
                    maxLines = if (large) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (line != null) {
                Text(
                    text = line,
                    style = if (large) typography.body else typography.caption,
                    color = colors.text2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (progress != null) CardProgressBar(progress, Modifier.align(Alignment.BottomStart))
    }
}

/**
 * The room a clipping container leaves above a mosaic [height] high: the outline and halo of a focused
 * tile ([CineSizes.focusRoom]) and how much the large tile, the tallest, grows with the focus. Without
 * it, the top of a focused tile went out of the list (1.0.0, Shield in 4K).
 */
fun mosaicFocusRoom(height: Dp): Dp = CineSizes.focusRoom + height * ((CineScale.TILE_LARGE - 1f) / 2f)

/** Why the tile is there: a small pill, in the accent for the large tile. */
@Composable
private fun Eyebrow(
    text: String,
    large: Boolean,
) {
    val colors = CineTheme.colors
    Text(
        text = text,
        style = CineTheme.typography.minimum,
        color = if (large) colors.onAccent else colors.text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier =
            Modifier
                .background(if (large) colors.accent else colors.surface3, MosaicPillShape)
                .padding(horizontal = CineSpacing.xs, vertical = CineSpacing.xs / 4),
    )
}

private val MosaicPillShape = RoundedCornerShape(percent = 50)

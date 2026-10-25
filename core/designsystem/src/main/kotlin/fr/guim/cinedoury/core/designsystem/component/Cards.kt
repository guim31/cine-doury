package fr.guim.cinedoury.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.tv.material3.Text
import fr.guim.cinedoury.core.designsystem.theme.CineMotion
import fr.guim.cinedoury.core.designsystem.theme.CineScale
import fr.guim.cinedoury.core.designsystem.theme.CineSizes
import fr.guim.cinedoury.core.designsystem.theme.CineSpacing
import fr.guim.cinedoury.core.designsystem.theme.CineTheme
import fr.guim.cinedoury.core.designsystem.theme.tabular

/** Poster sizes on TV (`UI.md` § 6). */
enum class PosterSize(val dp: DpSize) {
    Small(CineSizes.posterSmall),
    Normal(CineSizes.posterNormal),
    Large(CineSizes.posterLarge),
}

internal val CardShape = RoundedCornerShape(CineSizes.cardCorner)

/** Outline corner concentric with [CardShape], once pushed out by the gap and half the stroke. */
internal val CardOutlineShape =
    RoundedCornerShape(CineSizes.cardCorner + CineSizes.focusOutlineGap + CineSizes.focusOutlineWidth / 2)

/**
 * A 2:3 poster (`UI.md` § 3 and § 6). Its title and year show under it only while it has the
 * focus, unless [alwaysShowTitle]; the space is reserved so that nothing moves. A generated card
 * already shows the title: under it, only the [subtitle] appears, so that the title is never
 * written twice.
 *
 * @param image the poster image; without it, a [GeneratedArtwork] stands in.
 * @param progress playback progress between 0 and 1, or null when not started.
 * @param unwatchedCount for a series, the number of unwatched episodes, shown instead of the triangle.
 */
@Composable
fun PosterCard(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    size: PosterSize = PosterSize.Normal,
    progress: Float? = null,
    unwatched: Boolean = false,
    unwatchedCount: Int? = null,
    alwaysShowTitle: Boolean = false,
    focusRequester: FocusRequester? = null,
    focus: CineFocus = rememberCineFocus(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    image: (@Composable () -> Unit)? = null,
) {
    Column(modifier = modifier.width(size.dp.width)) {
        Box(
            modifier =
                Modifier
                    .size(size.dp)
                    .withFocusRequester(focusRequester)
                    .cineFocusable(focus, CardShape, CineScale.POSTER, onClick, CardOutlineShape, interactionSource)
                    .background(CineTheme.colors.surface1),
        ) {
            if (image != null) image() else GeneratedArtwork(title, compact = size == PosterSize.Small)
            UnwatchedMark(unwatched, unwatchedCount)
            if (progress != null) CardProgressBar(progress, Modifier.align(Alignment.BottomStart))
        }
        CardCaption(
            title = title.takeIf { image != null },
            subtitle = subtitle,
            visible = alwaysShowTitle || focus.isFocused,
            modifier = Modifier.padding(top = CineSpacing.s),
        )
    }
}

/**
 * A 16:9 thumbnail (`UI.md` § 6), for « Reprendre », episodes and chapters. Its caption stays
 * visible: a still frame alone does not say what it is. A generated card already shows the title:
 * under it, only the [subtitle] appears (episode, « Reprendre à … »).
 */
@Composable
fun ThumbnailCard(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    progress: Float? = null,
    unwatched: Boolean = false,
    unwatchedCount: Int? = null,
    focusRequester: FocusRequester? = null,
    focus: CineFocus = rememberCineFocus(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    image: (@Composable () -> Unit)? = null,
) {
    Column(modifier = modifier.width(CineSizes.thumbnail.width)) {
        Box(
            modifier =
                Modifier
                    .size(CineSizes.thumbnail)
                    .withFocusRequester(focusRequester)
                    .cineFocusable(
                        focus,
                        CardShape,
                        CineScale.THUMBNAIL,
                        onClick,
                        CardOutlineShape,
                        interactionSource,
                    ).background(CineTheme.colors.surface1),
        ) {
            if (image != null) image() else GeneratedArtwork(title)
            UnwatchedMark(unwatched, unwatchedCount)
            if (progress != null) CardProgressBar(progress, Modifier.align(Alignment.BottomStart))
        }
        CardCaption(
            title = title.takeIf { image != null },
            subtitle = subtitle,
            visible = true,
            modifier = Modifier.padding(top = CineSpacing.s),
        )
    }
}

/** The « non vu » mark of a card: the number of unwatched episodes, else the triangle. */
@Composable
internal fun BoxScope.UnwatchedMark(
    unwatched: Boolean,
    unwatchedCount: Int?,
) {
    when {
        unwatchedCount != null && unwatchedCount > 0 -> {
            UnwatchedCountBadge(
                count = unwatchedCount,
                modifier = Modifier.align(Alignment.TopEnd).padding(CineSpacing.xs),
            )
        }

        unwatched -> {
            UnwatchedTriangle(Modifier.align(Alignment.TopEnd))
        }
    }
}

/**
 * Two lines under a card: the title, then the secondary line. Without a [title] (generated card),
 * the secondary line moves up; both lines stay reserved so that a row keeps its height.
 */
@Composable
private fun CardCaption(
    title: String?,
    subtitle: String?,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = CineMotion.fade(if (visible) CineMotion.FOCUS_IN_MILLIS else CineMotion.FOCUS_OUT_MILLIS),
        label = "caption",
    )
    val typography = CineTheme.typography
    val colors = CineTheme.colors
    Column(modifier = modifier.graphicsLayer { this.alpha = alpha }) {
        if (title != null) {
            Text(
                text = title,
                style = typography.caption,
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = subtitle.orEmpty(),
            style = typography.caption.tabular(),
            color = colors.text2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (title == null) {
            // Keeps the height of the missing line.
            Text(text = "", style = typography.caption, maxLines = 1)
        }
    }
}

private val BadgeMinSize = CineSizes.icon + CineSpacing.xs / 2

/** Number of unwatched episodes of a series, in an accent pill (`UI.md` § 6). */
@Composable
fun UnwatchedCountBadge(
    count: Int,
    modifier: Modifier = Modifier,
) {
    val colors = CineTheme.colors
    Box(
        modifier =
            modifier
                .defaultMinSize(minWidth = BadgeMinSize, minHeight = BadgeMinSize)
                .background(colors.accent, RoundedCornerShape(percent = 50))
                .padding(horizontal = CineSpacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = count.toString(),
            style = CineTheme.typography.minimum.tabular(),
            color = colors.onAccent,
            textAlign = TextAlign.Center,
        )
    }
}

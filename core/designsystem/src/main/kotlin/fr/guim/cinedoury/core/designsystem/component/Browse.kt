package fr.guim.cinedoury.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.tv.material3.Text
import fr.guim.cinedoury.core.designsystem.theme.CineMotion
import fr.guim.cinedoury.core.designsystem.theme.CineScale
import fr.guim.cinedoury.core.designsystem.theme.CineSizes
import fr.guim.cinedoury.core.designsystem.theme.CineSpacing
import fr.guim.cinedoury.core.designsystem.theme.CineTheme
import fr.guim.cinedoury.core.designsystem.theme.tabular

private val PillShape = RoundedCornerShape(percent = 50)
private val BlockShape = RoundedCornerShape(CineSizes.cardCorner)
private val BlockOutlineShape =
    RoundedCornerShape(CineSizes.cardCorner + CineSizes.focusOutlineGap + CineSizes.focusOutlineWidth / 2)

/**
 * A placeholder of the exact shape of a card, while its page is read (`UI.md` § 1, principle 2:
 * never a spinner). Still, without animation: a list of skeletons stays calm.
 */
@Composable
fun SkeletonCard(
    size: DpSize,
    modifier: Modifier = Modifier,
    captionLines: Int = 2,
) {
    Column(modifier = modifier.width(size.width)) {
        Box(Modifier.size(size).background(CineTheme.colors.surface1, BlockShape))
        // The caption keeps its place, so that nothing moves when the card arrives.
        Column(Modifier.padding(top = CineSpacing.s), verticalArrangement = Arrangement.spacedBy(CineSpacing.xs / 2)) {
            repeat(captionLines) {
                SkeletonLine(
                    width =
                        size.width *
                            if (it ==
                                0
                            ) {
                                SKELETON_TITLE
                            } else {
                                SKELETON_SUBTITLE
                            },
                )
            }
        }
    }
}

/** One line of text not read yet. */
@Composable
fun SkeletonLine(
    width: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .width(width)
            .height(CineSizes.skeletonLine)
            .background(CineTheme.colors.surface1, PillShape),
    )
}

private const val SKELETON_TITLE = 0.8f
private const val SKELETON_SUBTITLE = 0.5f

/** Title of a row of the home or of a library: « Titre 2 », 24 dp above the cards (`UI.md` § 8). */
@Composable
fun RowTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = CineTheme.typography.title2,
        color = CineTheme.colors.text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/**
 * A tab of a library (Tout, Collections, Genres, À suivre). The selected one is written in the
 * accent on `surface-2`; the focused one turns light like the other pills.
 */
@Composable
fun CineTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    focus: CineFocus = rememberCineFocus(),
) {
    val colors = CineTheme.colors
    val focused = focus.isFocused
    Box(
        modifier =
            modifier
                .height(CineSizes.tabHeight)
                .withFocusRequester(focusRequester)
                .cineFocusable(focus, PillShape, CineScale.BUTTON, onClick)
                .background(
                    when {
                        focused -> colors.text
                        selected -> colors.surface2
                        else -> Color.Transparent
                    },
                ).padding(horizontal = CineSpacing.m),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = CineTheme.typography.label,
            color =
                when {
                    focused -> colors.background
                    selected -> colors.accent
                    else -> colors.text2
                },
            maxLines = 1,
        )
    }
}

/**
 * The A–Z jump on the right of a library (`FONCTIONNALITES.md` § 3): « # » then the letters. The
 * letter of the focused item is in the accent; a letter jumps on OK.
 *
 * @param letterRequester attached to the [current] letter, to enter the bar on it. The preview tag of
 * a letter is `alpha-<letter>`.
 */
@Composable
fun AlphabetBar(
    letters: List<String>,
    current: String?,
    onLetter: (String) -> Unit,
    modifier: Modifier = Modifier,
    letterRequester: FocusRequester? = null,
) {
    Column(
        modifier = modifier.width(CineSizes.alphabetWidth).focusGroup(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        letters.forEach { letter ->
            val focus = rememberCineFocus("alpha-$letter")
            val colors = CineTheme.colors
            Box(
                modifier =
                    Modifier
                        .size(CineSizes.alphabetKey)
                        .withFocusRequester(if (letter == current) letterRequester else null)
                        .cineFocusable(focus, PillShape, CineScale.POSTER, { onLetter(letter) })
                        .background(if (focus.isFocused) colors.text else Color.Transparent),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = letter,
                    style = CineTheme.typography.minimum,
                    textAlign = TextAlign.Center,
                    color =
                        when {
                            focus.isFocused -> colors.background
                            letter == current -> colors.accent
                            else -> colors.text3
                        },
                )
            }
        }
    }
}

/**
 * A panel sliding in from the right over the screen, which stays visible behind (`UI.md` § 7:
 * 200 ms, slide and fade). For the filters and the sort of a library.
 */
@Composable
fun SidePanel(
    visible: Boolean,
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val reduced = CineTheme.reducedMotion
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(CineMotion.fade(CineMotion.PANEL_MILLIS)),
            exit = fadeOut(CineMotion.fade(CineMotion.PANEL_MILLIS)),
        ) {
            Box(Modifier.fillMaxSize().background(CineTheme.colors.dialogScrim))
        }
        AnimatedVisibility(
            visible = visible,
            enter =
                fadeIn(CineMotion.fade(CineMotion.PANEL_MILLIS)) +
                    if (reduced) {
                        fadeIn()
                    } else {
                        slideInHorizontally(CineMotion.fade<IntOffset>(CineMotion.PANEL_MILLIS)) {
                            it /
                                PANEL_SLIDE
                        }
                    },
            exit =
                fadeOut(CineMotion.fade(CineMotion.PANEL_MILLIS)) +
                    if (reduced) {
                        fadeOut()
                    } else {
                        slideOutHorizontally(CineMotion.fade<IntOffset>(CineMotion.PANEL_MILLIS)) {
                            it /
                                PANEL_SLIDE
                        }
                    },
        ) {
            Column(
                modifier =
                    Modifier
                        .width(CineSizes.panelWidth)
                        .fillMaxHeight()
                        .background(CineTheme.colors.surface2)
                        .padding(
                            horizontal = CineSpacing.m,
                            vertical = CineSpacing.safeVertical + CineSpacing.s,
                        ).focusGroup(),
                verticalArrangement = Arrangement.spacedBy(CineSpacing.s),
            ) {
                Text(text = title, style = CineTheme.typography.title2, color = CineTheme.colors.text)
                content()
            }
        }
    }
}

private const val PANEL_SLIDE = 3

/**
 * An item of the detailed list of a library: a small poster, the title, a line of metadata and
 * the first lines of the synopsis, with the progress and the « non vu » mark.
 */
@Composable
fun MediaListRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    metadata: String? = null,
    overview: String? = null,
    progress: Float? = null,
    unwatched: Boolean = false,
    unwatchedCount: Int? = null,
    focusRequester: FocusRequester? = null,
    focus: CineFocus = rememberCineFocus(),
    image: (@Composable () -> Unit)? = null,
) {
    val colors = CineTheme.colors
    val typography = CineTheme.typography
    val focused = focus.isFocused
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(CineSizes.listRowHeight)
                .withFocusRequester(focusRequester)
                .cineFocusable(focus, BlockShape, CineScale.FIELD, onClick, BlockOutlineShape)
                .background(if (focused) colors.surface3 else colors.surface1)
                .padding(CineSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(CineSpacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(CineSizes.listRowPoster).background(colors.surface2, BlockShape)) {
            if (image != null) image() else GeneratedArtwork(title, compact = true)
            UnwatchedMark(unwatched, unwatchedCount)
            if (progress != null) CardProgressBar(progress, Modifier.align(Alignment.BottomStart))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(CineSpacing.xs / 2)) {
            Text(
                text = title,
                style = typography.label,
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (metadata != null) {
                Text(
                    text = metadata,
                    style = typography.caption.tabular(),
                    color = colors.text2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (overview != null) {
                Text(
                    text = overview,
                    style = typography.caption,
                    color = colors.text3,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(CineSpacing.xs))
    }
}

/** A skeleton of a [MediaListRow]. */
@Composable
fun SkeletonListRow(modifier: Modifier = Modifier) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(CineSizes.listRowHeight)
                .background(CineTheme.colors.surface1.copy(alpha = SKELETON_ROW_ALPHA), BlockShape)
                .padding(CineSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(CineSpacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(CineSizes.listRowPoster).background(CineTheme.colors.surface2, BlockShape))
        Column(verticalArrangement = Arrangement.spacedBy(CineSpacing.xs)) {
            SkeletonLine(CineSizes.panelWidth / 2)
            SkeletonLine(CineSizes.panelWidth / 3)
        }
    }
}

private const val SKELETON_ROW_ALPHA = 0.6f

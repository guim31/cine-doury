package fr.guim.cinedoury.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import fr.guim.cinedoury.core.designsystem.theme.CineScale
import fr.guim.cinedoury.core.designsystem.theme.CineSizes
import fr.guim.cinedoury.core.designsystem.theme.CineSpacing
import fr.guim.cinedoury.core.designsystem.theme.CineTheme
import fr.guim.cinedoury.core.designsystem.theme.tabular

private val BadgeShape = RoundedCornerShape(CineSizes.cardCorner / 2)
private val BlockShape = RoundedCornerShape(CineSizes.cardCorner)
private val BlockOutlineShape =
    RoundedCornerShape(CineSizes.cardCorner + CineSizes.focusOutlineGap + CineSizes.focusOutlineWidth / 2)

/**
 * The technical badges of a details screen (`UI.md` § 8): sober labels in « Légende » on
 * `surface-2` (« 4K », « Dolby Vision », « Atmos », « VF »), never focusable.
 */
@Composable
fun TechBadges(
    labels: List<String>,
    modifier: Modifier = Modifier,
) {
    val colors = CineTheme.colors
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(CineSpacing.xs)) {
        labels.forEach { label ->
            Box(
                modifier =
                    Modifier
                        .height(CineSizes.badgeHeight)
                        .background(colors.surface2, BadgeShape)
                        .padding(horizontal = CineSpacing.xs),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = label, style = CineTheme.typography.caption, color = colors.text2, maxLines = 1)
            }
        }
    }
}

/**
 * A synopsis on [collapsedLines] lines that unfolds with OK (`UI.md` § 8, « Fiche »). It takes the
 * focus only when there is more to read: a short synopsis is plain text.
 */
@Composable
fun ExpandableText(
    text: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    collapsedLines: Int = 3,
    focus: CineFocus = rememberCineFocus(),
    focusRequester: FocusRequester? = null,
) {
    val colors = CineTheme.colors
    var overflows by remember(text) { mutableStateOf(false) }
    val foldable = overflows || expanded
    val focused = focus.isFocused
    val content =
        @Composable {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(CineSpacing.xs)) {
                Text(
                    text = text,
                    style = CineTheme.typography.body,
                    color = colors.text,
                    maxLines = if (expanded) Int.MAX_VALUE else collapsedLines,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow },
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (foldable) {
                    Icon(
                        imageVector = if (expanded) CineIcons.Up else CineIcons.Expand,
                        contentDescription = null,
                        tint = if (focused) colors.text else colors.text2,
                        modifier = Modifier.size(CineSizes.icon),
                    )
                }
            }
        }
    if (foldable) {
        Box(
            modifier =
                modifier
                    .withFocusRequester(focusRequester)
                    .cineFocusable(focus, BlockShape, CineScale.FIELD, onToggle, BlockOutlineShape)
                    .background(if (focused) colors.surface2 else colors.surface2.copy(alpha = 0f))
                    .padding(CineSpacing.xs),
        ) { content() }
    } else {
        Box(modifier.padding(CineSpacing.xs)) { content() }
    }
}

/**
 * An episode in the list of a series (`FONCTIONNALITES.md` § 4): its frame with the progress and the
 * « non vu » mark, its number and title, a line of metadata (duration, air date), its synopsis on
 * two lines; on its right, a round button marks it watched or not.
 *
 * @param rowModifier applied to the focusable row itself (tags, focus listeners), [modifier] to the
 * whole line with its button.
 */
@Composable
fun EpisodeRow(
    title: String,
    onClick: () -> Unit,
    watched: Boolean,
    watchedLabel: String,
    onToggleWatched: () -> Unit,
    modifier: Modifier = Modifier,
    rowModifier: Modifier = Modifier,
    metadata: String? = null,
    overview: String? = null,
    progress: Float? = null,
    thumbnail: DpSize = CineSizes.thumbnail,
    focusRequester: FocusRequester? = null,
    focus: CineFocus = rememberCineFocus(),
    watchedFocus: CineFocus = rememberCineFocus(),
    image: (@Composable () -> Unit)? = null,
) {
    val colors = CineTheme.colors
    val typography = CineTheme.typography
    val focused = focus.isFocused
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CineSpacing.m),
    ) {
        Row(
            modifier =
                rowModifier
                    .weight(1f)
                    .height(CineSizes.episodeRowHeight)
                    .withFocusRequester(focusRequester)
                    .cineFocusable(focus, BlockShape, CineScale.FIELD, onClick, BlockOutlineShape)
                    .background(if (focused) colors.surface3 else colors.surface1)
                    .padding(CineSpacing.s),
            horizontalArrangement = Arrangement.spacedBy(CineSpacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(thumbnail).background(colors.surface2, BlockShape)) {
                if (image != null) image() else GeneratedArtwork(title, compact = true)
                UnwatchedMark(unwatched = !watched && progress == null, unwatchedCount = null)
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
        }
        RoundIconButton(
            icon = if (watched) CineIcons.Unwatched else CineIcons.Watched,
            label = watchedLabel,
            onClick = onToggleWatched,
            selected = watched,
            focus = watchedFocus,
        )
    }
}

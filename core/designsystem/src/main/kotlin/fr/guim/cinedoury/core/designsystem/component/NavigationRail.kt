package fr.guim.cinedoury.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.lerp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import fr.guim.cinedoury.core.designsystem.theme.CineMotion
import fr.guim.cinedoury.core.designsystem.theme.CineScale
import fr.guim.cinedoury.core.designsystem.theme.CineSizes
import fr.guim.cinedoury.core.designsystem.theme.CineSpacing
import fr.guim.cinedoury.core.designsystem.theme.CineTheme

/**
 * An entry of the rail. [avatar] stands in for the icon (the profile picture), drawn at the size of
 * an icon.
 */
@Immutable
data class RailDestination(
    val icon: ImageVector,
    val label: String,
    val avatar: (@Composable () -> Unit)? = null,
)

private val RailItemShape = RoundedCornerShape(percent = 50)
private const val RAIL_SCRIM_ALPHA = 0.72f

/**
 * The side rail of `UI.md` § 2. Closed, it shows the icons only (72 dp); as soon as it holds the
 * focus it opens to 240 dp with the labels, over the content, which it dims. Lay the content out
 * with a start padding of [CineSizes.railCollapsed] and the rail on top of it.
 *
 * @param bottomDestinations pinned to the bottom (« Réglages »); their indices follow [destinations].
 * @param expanded forces a state, for specimens; null lets the focus decide.
 * @param previewFocusedIndex draws that destination as focused and the rail open, for previews
 * and screenshots ([CineFocus.preview]).
 * @param focusRequester attached to the selected destination: the screen gives the rail the focus
 * through it (« Retour » on the home).
 */
@Composable
fun NavigationRail(
    destinations: List<RailDestination>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    bottomDestinations: List<RailDestination> = emptyList(),
    expanded: Boolean? = null,
    previewFocusedIndex: Int? = null,
    focusRequester: FocusRequester? = null,
) {
    var hasFocus by remember { mutableStateOf(false) }
    // Entering the rail lands on the entry focused last, else on the selected one.
    val selected = focusRequester ?: remember { FocusRequester() }
    val open = expanded ?: (hasFocus || previewFocusedIndex != null)
    val expansion by animateFloatAsState(
        targetValue = if (open) 1f else 0f,
        animationSpec = CineMotion.panel(reduced = CineTheme.reducedMotion),
        label = "rail",
    )
    val colors = CineTheme.colors
    val scrimWidth = CineSizes.railExpanded - CineSizes.railCollapsed
    Column(
        modifier =
            modifier
                .fillMaxHeight()
                .width(lerp(CineSizes.railCollapsed, CineSizes.railExpanded, expansion))
                .drawBehind {
                    if (expansion > 0f) {
                        // Opaque: the open rail covers the content (`UI.md` § 2).
                        drawRect(colors.surface1.copy(alpha = expansion))
                        val scrim = scrimWidth.toPx()
                        drawRect(
                            brush =
                                Brush.horizontalGradient(
                                    colors =
                                        listOf(
                                            colors.background.copy(alpha = RAIL_SCRIM_ALPHA * expansion),
                                            Color.Transparent,
                                        ),
                                    startX = size.width,
                                    endX = size.width + scrim,
                                ),
                            topLeft = Offset(size.width, 0f),
                            size = Size(scrim, size.height),
                        )
                    }
                }.onFocusChanged { hasFocus = it.hasFocus }
                .focusRestorer(selected)
                .focusGroup()
                .padding(vertical = CineSpacing.safeVertical + CineSpacing.m, horizontal = CineSpacing.xs),
        verticalArrangement = Arrangement.spacedBy(CineSpacing.xs),
    ) {
        // Many libraries: the top of the rail scrolls, « Profil » and « Réglages » stay at the bottom;
        // what goes on below fades out instead of being cut.
        val scroll = rememberScrollState()
        Column(
            modifier = Modifier.weight(1f).fadeBottom(scroll.canScrollForward).verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(CineSpacing.xs),
        ) {
            destinations.forEachIndexed { index, destination ->
                RailItem(
                    destination,
                    selected = index == selectedIndex,
                    labelAlpha = expansion,
                    onClick = { onSelect(index) },
                    preview = index == previewFocusedIndex,
                    focusRequester = selected.takeIf { index == selectedIndex },
                )
            }
        }
        bottomDestinations.forEachIndexed { offset, destination ->
            val index = destinations.size + offset
            RailItem(
                destination,
                selected = index == selectedIndex,
                labelAlpha = expansion,
                onClick = { onSelect(index) },
                preview = index == previewFocusedIndex,
                focusRequester = selected.takeIf { index == selectedIndex },
            )
        }
    }
}

@Composable
private fun RailItem(
    destination: RailDestination,
    selected: Boolean,
    labelAlpha: Float,
    onClick: () -> Unit,
    preview: Boolean,
    focusRequester: FocusRequester?,
) {
    val colors = CineTheme.colors
    val focus = if (preview) remember { CineFocus.preview() } else rememberCineFocus()
    val focused = focus.isFocused
    val content =
        when {
            focused -> colors.background
            selected -> colors.accent
            else -> colors.text2
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(CineSizes.railItemHeight)
                .withFocusRequester(focusRequester)
                .cineFocusable(focus, RailItemShape, CineScale.BUTTON, onClick)
                .background(if (focused) colors.text else Color.Transparent)
                .padding(horizontal = CineSpacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CineSpacing.s),
    ) {
        val avatar = destination.avatar
        if (avatar != null) {
            Box(Modifier.size(CineSizes.icon), contentAlignment = Alignment.Center) { avatar() }
        } else {
            Icon(
                imageVector = destination.icon,
                contentDescription = destination.label,
                tint = content,
                modifier = Modifier.size(CineSizes.icon),
            )
        }
        Box(Modifier.graphicsLayer { alpha = labelAlpha }) {
            Text(
                text = destination.label,
                style = CineTheme.typography.label,
                color =
                    if (focused) {
                        colors.background
                    } else if (selected) {
                        colors.text
                    } else {
                        colors.text2
                    },
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
            )
        }
    }
}

/** Fades out the bottom of a list that goes on below, over [CineSpacing.l]. */
private fun Modifier.fadeBottom(enabled: Boolean): Modifier =
    if (!enabled) {
        this
    } else {
        graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val fade = CineSpacing.l.toPx() / size.height
                drawRect(
                    Brush.verticalGradient(0f to Color.Black, (1f - fade) to Color.Black, 1f to Color.Transparent),
                    blendMode = BlendMode.DstIn,
                )
            }
    }

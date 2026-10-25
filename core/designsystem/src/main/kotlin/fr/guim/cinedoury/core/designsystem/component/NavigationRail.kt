package fr.guim.cinedoury.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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

@Immutable
data class RailDestination(
    val icon: ImageVector,
    val label: String,
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
 */
@Composable
fun NavigationRail(
    destinations: List<RailDestination>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    bottomDestinations: List<RailDestination> = emptyList(),
    expanded: Boolean? = null,
) {
    var hasFocus by remember { mutableStateOf(false) }
    val open = expanded ?: hasFocus
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
                .focusGroup()
                .padding(vertical = CineSpacing.safeVertical + CineSpacing.m, horizontal = CineSpacing.xs),
        verticalArrangement = Arrangement.spacedBy(CineSpacing.xs),
    ) {
        destinations.forEachIndexed { index, destination ->
            RailItem(
                destination,
                selected = index == selectedIndex,
                labelAlpha = expansion,
                onClick = { onSelect(index) },
            )
        }
        Spacer(Modifier.weight(1f))
        bottomDestinations.forEachIndexed { offset, destination ->
            val index = destinations.size + offset
            RailItem(
                destination,
                selected = index == selectedIndex,
                labelAlpha = expansion,
                onClick = { onSelect(index) },
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
) {
    val colors = CineTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
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
                .cineFocusable(interactionSource, RailItemShape, CineScale.BUTTON, onClick)
                .background(if (focused) colors.text else Color.Transparent)
                .padding(horizontal = CineSpacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CineSpacing.s),
    ) {
        Icon(
            imageVector = destination.icon,
            contentDescription = destination.label,
            tint = content,
            modifier = Modifier.size(CineSizes.icon),
        )
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

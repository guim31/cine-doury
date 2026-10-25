package fr.guim.cinedoury.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import fr.guim.cinedoury.core.designsystem.theme.CineMotion
import fr.guim.cinedoury.core.designsystem.theme.CineSizes
import fr.guim.cinedoury.core.designsystem.theme.CineTheme

/**
 * Progress of the focus animation, from 0 (not focused) to 1 (focused): 150 ms on the way in,
 * 120 ms on the way out (`UI.md` § 3).
 */
@Composable
fun focusProgress(interactionSource: MutableInteractionSource): State<Float> {
    val focused by interactionSource.collectIsFocusedAsState()
    return animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = CineMotion.focus(gained = focused, reduced = CineTheme.reducedMotion),
        label = "focus",
    )
}

/**
 * The focus treatment of `UI.md` § 3 on a clickable element: it grows to [focusedScale], gets a
 * 3 dp outline in the `focus` colour 2 dp away from its edge, and a soft drop shadow. The outline
 * and the scale make the focus visible without relying on colour.
 *
 * @param outlineShape shape of the outline, drawn [CineSizes.focusOutlineGap] outside [shape]:
 * pass a shape with a larger corner radius for concentric corners.
 */
@Composable
fun Modifier.cineFocusable(
    interactionSource: MutableInteractionSource,
    shape: Shape,
    focusedScale: Float,
    onClick: () -> Unit,
    outlineShape: Shape = shape,
): Modifier {
    // Read only in the layer and draw phases: the animation never recomposes the element.
    val progress by focusProgress(interactionSource)
    val colors = CineTheme.colors
    return this
        .graphicsLayer {
            val scale = 1f + (focusedScale - 1f) * progress
            scaleX = scale
            scaleY = scale
            shadowElevation = CineSizes.focusElevation.toPx() * progress
            this.shape = shape
            clip = false
            ambientShadowColor = colors.shadow
            spotShadowColor = colors.shadow
        }.drawWithContent {
            drawContent()
            if (progress > 0f) {
                val stroke = CineSizes.focusOutlineWidth.toPx()
                val inset = CineSizes.focusOutlineGap.toPx() + stroke / 2f
                val outline =
                    outlineShape.createOutline(
                        Size(size.width + inset * 2f, size.height + inset * 2f),
                        layoutDirection,
                        this,
                    )
                translate(left = -inset, top = -inset) {
                    drawOutline(outline, colors.focus, alpha = progress, style = Stroke(width = stroke))
                }
            }
        }.clip(shape)
        .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
}

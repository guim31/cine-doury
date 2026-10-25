package fr.guim.cinedoury.core.designsystem.component

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

private const val SIDE_SCRIM_ALPHA = 0.92f
private const val SIDE_SCRIM_EXTENT = 0.60f
private const val BOTTOM_SCRIM_EXTENT = 0.45f

/**
 * The readability veils of `UI.md` § 4 over a full-screen backdrop: [color] at 92 % fading to 0
 * from the left over 60 % of the width, and at 100 % fading to 0 from the bottom over 45 % of the
 * height. Text laid over them must still reach 4.5:1.
 */
fun Modifier.readabilityScrims(color: Color): Modifier =
    drawWithCache {
        val sideWidth = size.width * SIDE_SCRIM_EXTENT
        val bottomHeight = size.height * BOTTOM_SCRIM_EXTENT
        val side =
            Brush.horizontalGradient(
                colors = listOf(color.copy(alpha = SIDE_SCRIM_ALPHA), Color.Transparent),
                startX = 0f,
                endX = sideWidth,
            )
        val bottom =
            Brush.verticalGradient(
                colors = listOf(Color.Transparent, color),
                startY = size.height - bottomHeight,
                endY = size.height,
            )
        onDrawWithContent {
            drawContent()
            drawRect(side, size = Size(sideWidth, size.height))
            drawRect(bottom, topLeft = Offset(0f, size.height - bottomHeight), size = Size(size.width, bottomHeight))
        }
    }

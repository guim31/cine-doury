package fr.guim.cinedoury.core.designsystem.component

import androidx.compose.ui.graphics.vector.PathNode

/**
 * The paths of the icons, read the same on every Android.
 *
 * After a close (`z`), SVG starts a relative move (`m`) from the start of the closed subpath. Compose
 * hands the move to `android.graphics.Path.rMoveTo`, and the Skia of Android 11 (the Shield) starts it
 * from the last point drawn instead: « M6 6h2v12H6zm3.5 6… » put the triangle of « Épisode précédent »
 * 12 units too low, out of the grid. Every relative move is therefore made absolute here, where the
 * current point is known, so that no `rMoveTo` ever reaches the platform.
 */
internal object IconPaths {
    /** [nodes] with each [PathNode.RelativeMoveTo] replaced by the [PathNode.MoveTo] SVG means. */
    @Suppress("CyclomaticComplexMethod") // One branch per kind of node.
    fun absoluteMoves(nodes: List<PathNode>): List<PathNode> {
        var x = 0f
        var y = 0f
        var startX = 0f
        var startY = 0f
        return nodes.map { node ->
            val out = if (node is PathNode.RelativeMoveTo) PathNode.MoveTo(x + node.dx, y + node.dy) else node
            when (out) {
                is PathNode.MoveTo -> {
                    x = out.x
                    y = out.y
                    startX = x
                    startY = y
                }

                PathNode.Close -> {
                    x = startX
                    y = startY
                }

                is PathNode.RelativeMoveTo -> {
                    error("Made absolute above")
                }

                is PathNode.LineTo -> {
                    x = out.x
                    y = out.y
                }

                is PathNode.RelativeLineTo -> {
                    x += out.dx
                    y += out.dy
                }

                is PathNode.HorizontalTo -> {
                    x = out.x
                }

                is PathNode.RelativeHorizontalTo -> {
                    x += out.dx
                }

                is PathNode.VerticalTo -> {
                    y = out.y
                }

                is PathNode.RelativeVerticalTo -> {
                    y += out.dy
                }

                is PathNode.CurveTo -> {
                    x = out.x3
                    y = out.y3
                }

                is PathNode.RelativeCurveTo -> {
                    x += out.dx3
                    y += out.dy3
                }

                is PathNode.ReflectiveCurveTo -> {
                    x = out.x2
                    y = out.y2
                }

                is PathNode.RelativeReflectiveCurveTo -> {
                    x += out.dx2
                    y += out.dy2
                }

                is PathNode.QuadTo -> {
                    x = out.x2
                    y = out.y2
                }

                is PathNode.RelativeQuadTo -> {
                    x += out.dx2
                    y += out.dy2
                }

                is PathNode.ReflectiveQuadTo -> {
                    x = out.x
                    y = out.y
                }

                is PathNode.RelativeReflectiveQuadTo -> {
                    x += out.dx
                    y += out.dy
                }

                is PathNode.ArcTo -> {
                    x = out.arcStartX
                    y = out.arcStartY
                }

                is PathNode.RelativeArcTo -> {
                    x += out.arcStartDx
                    y += out.arcStartDy
                }
            }
            out
        }
    }
}

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
    fun absoluteMoves(nodes: List<PathNode>): List<PathNode> {
        var at = Point(0f, 0f)
        var start = at
        return nodes.map { node ->
            val out = if (node is PathNode.RelativeMoveTo) PathNode.MoveTo(at.x + node.dx, at.y + node.dy) else node
            at = end(out, at, start)
            if (out is PathNode.MoveTo) start = at
            out
        }
    }

    private data class Point(
        val x: Float,
        val y: Float,
    )

    /** Where [node] leaves the pen, from [at], in a subpath begun at [start]. */
    @Suppress("CyclomaticComplexMethod") // One branch per kind of node.
    private fun end(
        node: PathNode,
        at: Point,
        start: Point,
    ): Point =
        when (node) {
            PathNode.Close -> start
            is PathNode.MoveTo -> Point(node.x, node.y)
            is PathNode.RelativeMoveTo -> Point(at.x + node.dx, at.y + node.dy)
            is PathNode.LineTo -> Point(node.x, node.y)
            is PathNode.RelativeLineTo -> Point(at.x + node.dx, at.y + node.dy)
            is PathNode.HorizontalTo -> Point(node.x, at.y)
            is PathNode.RelativeHorizontalTo -> Point(at.x + node.dx, at.y)
            is PathNode.VerticalTo -> Point(at.x, node.y)
            is PathNode.RelativeVerticalTo -> Point(at.x, at.y + node.dy)
            is PathNode.CurveTo -> Point(node.x3, node.y3)
            is PathNode.RelativeCurveTo -> Point(at.x + node.dx3, at.y + node.dy3)
            is PathNode.ReflectiveCurveTo -> Point(node.x2, node.y2)
            is PathNode.RelativeReflectiveCurveTo -> Point(at.x + node.dx2, at.y + node.dy2)
            is PathNode.QuadTo -> Point(node.x2, node.y2)
            is PathNode.RelativeQuadTo -> Point(at.x + node.dx2, at.y + node.dy2)
            is PathNode.ReflectiveQuadTo -> Point(node.x, node.y)
            is PathNode.RelativeReflectiveQuadTo -> Point(at.x + node.dx, at.y + node.dy)
            is PathNode.ArcTo -> Point(node.arcStartX, node.arcStartY)
            is PathNode.RelativeArcTo -> Point(at.x + node.arcStartDx, at.y + node.arcStartDy)
        }
}

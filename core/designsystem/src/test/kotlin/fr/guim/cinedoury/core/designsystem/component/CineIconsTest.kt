package fr.guim.cinedoury.core.designsystem.component

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorNode
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.addPathNodes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Every icon draws the same on every Android (« Épisode précédent » on the Shield, Android 11): no
 * relative move after a close that the Skia of Android 11 would start from the last point drawn
 * instead of the start of the closed subpath ([IconPaths]).
 */
class CineIconsTest {
    /** Every icon of [CineIcons], by name: ours (lazy) and those of Material. */
    private val icons: Map<String, ImageVector> =
        CineIcons::class.java.declaredMethods
            .filter { it.name.startsWith("get") && it.parameterCount == 0 && it.returnType == ImageVector::class.java }
            .associate { it.name.removePrefix("get") to it.invoke(CineIcons) as ImageVector }

    /** The icons drawn from a path of ours ([CineIcons] `by lazy`), not those of Material. */
    private val ours: Set<String> =
        CineIcons::class.java.declaredFields
            .map { it.name }
            .filter { it.endsWith("\$delegate") }
            .map { it.removeSuffix("\$delegate") }
            .toSet()

    @Test
    fun everyIconIsFound() {
        assertTrue(icons.size > 60, "${icons.size} icons found")
        assertTrue("SkipPrevious" in ours && "SkipPrevious" in icons)
    }

    @Test
    fun ourIconsHaveNoRelativeMove() {
        val failures =
            icons.filterKeys { it in ours }.flatMap { (name, icon) ->
                paths(icon.root).filter { path -> path.any { it is PathNode.RelativeMoveTo } }.map { name }
            }
        if (failures.isNotEmpty()) fail("Relative moves left in ${failures.distinct()}")
    }

    @Test
    fun noRelativeMoveAfterACloseDependsOnTheReading() {
        val failures =
            icons.flatMap { (name, icon) ->
                paths(icon.root).flatMap { nodes -> ambiguousMoves(nodes).map { "$name: $it" } }
            }
        if (failures.isNotEmpty()) fail(failures.joinToString("\n"))
    }

    @Test
    fun theTrapIsCaught() {
        // The path of « Épisode précédent » up to the 1.0.5: the two readings differ by 12 units.
        val trap = addPathNodes("M6 6h2v12H6zm3.5 6l8.5 6V6z")
        assertEquals(listOf("move at 5: (9.5, 12.0) or (9.5, 24.0)"), ambiguousMoves(trap))
        assertEquals(PathNode.MoveTo(9.5f, 12f), IconPaths.absoluteMoves(trap)[5])
    }

    @Test
    fun episodePreviousStaysInItsGrid() {
        val nodes = paths(CineIcons.SkipPrevious.root).single()
        assertEquals(PathNode.MoveTo(9.5f, 12f), nodes[5])
        val points = IconPaths.absoluteMoves(nodes).filterIsInstance<PathNode.MoveTo>()
        assertTrue(points.all { it.x in 0f..24f && it.y in 0f..24f }, "$points")
    }

    @Test
    fun relativeMovesBecomeAbsoluteAsSvgReadsThem() {
        val nodes = IconPaths.absoluteMoves(addPathNodes("m2 3h4v4zm1 1l1 1zM10 10m2 2c1 1 2 2 3 3m1 1"))
        assertEquals(
            listOf(
                PathNode.MoveTo(2f, 3f),
                PathNode.MoveTo(3f, 4f),
                PathNode.MoveTo(12f, 12f),
                PathNode.MoveTo(16f, 16f),
            ),
            nodes.filterIsInstance<PathNode.MoveTo>().filterNot { it == PathNode.MoveTo(10f, 10f) },
        )
        assertTrue(nodes.none { it is PathNode.RelativeMoveTo })
    }

    /**
     * The relative moves after a close where SVG (from the start of the closed subpath) and the Skia of
     * Android 11 (from the last point drawn) disagree.
     */
    private fun ambiguousMoves(nodes: List<PathNode>): List<String> =
        nodes.indices
            .filter { i -> i > 0 && nodes[i] is PathNode.RelativeMoveTo && nodes[i - 1] == PathNode.Close }
            .mapNotNull { i ->
                val svg = IconPaths.absoluteMoves(nodes.take(i + 1)).last() as PathNode.MoveTo
                // Without the close, the move starts from the last point drawn, as on Android 11.
                val skia = IconPaths.absoluteMoves(nodes.take(i - 1) + nodes[i]).last() as PathNode.MoveTo
                "move at $i: (${svg.x}, ${svg.y}) or (${skia.x}, ${skia.y})".takeIf { svg != skia }
            }

    private fun paths(node: VectorNode): List<List<PathNode>> =
        when (node) {
            is VectorPath -> listOf(node.pathData)
            is VectorGroup -> node.flatMap { paths(it) }
        }
}

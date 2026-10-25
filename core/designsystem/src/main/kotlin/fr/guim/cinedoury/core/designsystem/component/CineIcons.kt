package fr.guim.cinedoury.core.designsystem.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** The icons of the app, in one place. Material icons (Apache 2.0) on a 24 × 24 grid. */
object CineIcons {
    val Home: ImageVector get() = Icons.Filled.Home
    val Search: ImageVector get() = Icons.Filled.Search
    val Settings: ImageVector get() = Icons.Filled.Settings
    val Profile: ImageVector get() = Icons.Filled.Person
    val Play: ImageVector get() = Icons.Filled.PlayArrow
    val Watched: ImageVector get() = Icons.Filled.Check
    val Favorite: ImageVector get() = Icons.Filled.Favorite
    val NotFavorite: ImageVector get() = Icons.Filled.FavoriteBorder
    val More: ImageVector get() = Icons.Filled.MoreVert

    val Movies: ImageVector by lazy {
        icon(
            "Movies",
            "M18,4l2,4h-3l-2,-4h-2l2,4h-3l-2,-4H8l2,4H7L5,4H4c-1.1,0 -1.99,0.9 -1.99,2L2,18c0,1.1 0.9,2 2,2h16" +
                "c1.1,0 2,-0.9 2,-2V4h-4z",
        )
    }

    val Shows: ImageVector by lazy {
        icon(
            "Shows",
            "M21,3H3c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h5v2h8v-2h5c1.1,0 1.99,-0.9 1.99,-2L23,5" +
                "c0,-1.1 -0.9,-2 -2,-2zM21,17H3V5h18v12z",
        )
    }

    /** « Depuis le début ». */
    val Restart: ImageVector by lazy {
        icon(
            "Restart",
            "M12,5V1L7,6l5,5V7c3.31,0 6,2.69 6,6s-2.69,6 -6,6 -6,-2.69 -6,-6H4c0,4.42 3.58,8 8,8s8,-3.58 8,-8" +
                " -3.58,-8 -8,-8z",
        )
    }

    private fun icon(name: String, path: String): ImageVector =
        ImageVector
            .Builder(
                name = name,
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).addPath(pathData = addPathNodes(path), fill = SolidColor(Color.Black))
            .build()
}

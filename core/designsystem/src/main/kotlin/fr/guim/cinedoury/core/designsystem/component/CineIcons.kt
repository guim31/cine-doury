package fr.guim.cinedoury.core.designsystem.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
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
    val Add: ImageVector get() = Icons.Filled.Add
    val Lock: ImageVector get() = Icons.Filled.Lock
    val Retry: ImageVector get() = Icons.Filled.Refresh
    val Info: ImageVector get() = Icons.Filled.Info
    val Warning: ImageVector get() = Icons.Filled.Warning
    val Close: ImageVector get() = Icons.Filled.Close
    val ListView: ImageVector get() = Icons.AutoMirrored.Filled.List
    val Up: ImageVector get() = Icons.Filled.KeyboardArrowUp
    val Down: ImageVector get() = Icons.Filled.KeyboardArrowDown
    val Star: ImageVector get() = Icons.Filled.Star
    val Edit: ImageVector get() = Icons.Filled.Edit
    val Exit: ImageVector get() = Icons.AutoMirrored.Filled.ExitToApp

    val Filter: ImageVector by lazy { icon("Filter", "M10,18h4v-2h-4v2zM3,6v2h18V6H3zm3,7h12v-2H6v2z") }

    val Sort: ImageVector by lazy { icon("Sort", "M3,18h6v-2H3v2zM3,6v2h18V6H3zm0,7h12v-2H3v2z") }

    /** A grid of posters. */
    val GridView: ImageVector by lazy {
        icon(
            "GridView",
            "M4,8h4V4H4v4zm6,12h4v-4h-4v4zm-6,0h4v-4H4v4zm0,-6h4v-4H4v4zm6,0h4v-4h-4v4zm6,-10v4h4V4h-4z" +
                "m-6,4h4V4h-4v4zm6,6h4v-4h-4v4zm0,6h4v-4h-4v4z",
        )
    }

    /** Landscape thumbnails. */
    val ThumbnailView: ImageVector by lazy { icon("ThumbnailView", "M4,18h17v-6H4v6zM4,5v6h17V5H4z") }

    /** A collection of films. */
    val Collections: ImageVector by lazy {
        icon(
            "Collections",
            "M4,6H2v14c0,1.1 0.9,2 2,2h14v-2H4V6zm16,-4H8c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h12" +
                "c1.1,0 2,-0.9 2,-2V4c0,-1.1 -0.9,-2 -2,-2zm0,14H8V4h12v12z",
        )
    }

    val Show: ImageVector by lazy {
        icon(
            "Show",
            "M12,4.5C7,4.5 2.73,7.61 1,12c1.73,4.39 6,7.5 11,7.5s9.27,-3.11 11,-7.5c-1.73,-4.39 -6,-7.5 -11,-7.5z" +
                "M12,17c-2.76,0 -5,-2.24 -5,-5s2.24,-5 5,-5 5,2.24 5,5 -2.24,5 -5,5zm0,-8c-1.66,0 -3,1.34 -3,3" +
                "s1.34,3 3,3 3,-1.34 3,-3 -1.34,-3 -3,-3z",
        )
    }

    val Hide: ImageVector by lazy {
        icon(
            "Hide",
            "M12,7c2.76,0 5,2.24 5,5 0,0.65 -0.13,1.26 -0.36,1.83l2.92,2.92c1.51,-1.26 2.7,-2.89 3.43,-4.75" +
                " -1.73,-4.39 -6,-7.5 -11,-7.5 -1.4,0 -2.74,0.25 -3.98,0.7l2.16,2.16C10.74,7.13 11.35,7 12,7z" +
                "M2,4.27l2.28,2.28 0.46,0.46C3.08,8.3 1.78,10.02 1,12c1.73,4.39 6,7.5 11,7.5 1.55,0 3.03,-0.3" +
                " 4.38,-0.84l0.42,0.42L19.73,22 21,20.73 3.27,3 2,4.27zM7.53,9.8l1.55,1.55c-0.05,0.21 -0.08,0.43" +
                " -0.08,0.65 0,1.66 1.34,3 3,3 0.22,0 0.44,-0.03 0.65,-0.08l1.55,1.55c-0.67,0.33 -1.41,0.53" +
                " -2.2,0.53 -2.76,0 -5,-2.24 -5,-5 0,-0.79 0.2,-1.53 0.53,-2.2zM11.84,9.02l3.15,3.15 0.02,-0.16" +
                "c0,-1.66 -1.34,-3 -3,-3l-0.17,0.01z",
        )
    }

    /** Erases the last digit. */
    val Backspace: ImageVector by lazy {
        icon(
            "Backspace",
            "M22,3H7c-0.69,0 -1.23,0.35 -1.59,0.88L0,12l5.41,8.11c0.36,0.53 0.9,0.89 1.59,0.89h15c1.1,0 2,-0.9" +
                " 2,-2V5c0,-1.1 -0.9,-2 -2,-2zM19,15.59L17.59,17 14,13.41 10.41,17 9,15.59 12.59,12 9,8.41" +
                " 10.41,7 14,10.59 17.59,7 19,8.41 15.41,12 19,15.59z",
        )
    }

    /** A server. */
    val Server: ImageVector by lazy {
        icon(
            "Server",
            "M20,13H4c-0.55,0 -1,0.45 -1,1v6c0,0.55 0.45,1 1,1h16c0.55,0 1,-0.45 1,-1v-6c0,-0.55 -0.45,-1 -1,-1z" +
                "M7,19c-1.1,0 -2,-0.9 -2,-2s0.9,-2 2,-2 2,0.9 2,2 -0.9,2 -2,2zM20,3H4c-0.55,0 -1,0.45 -1,1v6" +
                "c0,0.55 0.45,1 1,1h16c0.55,0 1,-0.45 1,-1V4c0,-0.55 -0.45,-1 -1,-1zM7,9c-1.1,0 -2,-0.9 -2,-2" +
                "s0.9,-2 2,-2 2,0.9 2,2 -0.9,2 -2,2z",
        )
    }

    /** Remote connection, through the public address. */
    val Remote: ImageVector by lazy {
        icon(
            "Remote",
            "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM11,19.93c-3.95,-0.49 -7,-3.85" +
                " -7,-7.93 0,-0.62 0.08,-1.21 0.21,-1.79L9,15v1c0,1.1 0.9,2 2,2v1.93zM17.9,17.39c-0.26,-0.81" +
                " -1,-1.39 -1.9,-1.39h-1v-3c0,-0.55 -0.45,-1 -1,-1L8,12v-2h2c0.55,0 1,-0.45 1,-1L11,7h2" +
                "c1.1,0 2,-0.9 2,-2v-0.41c2.93,1.19 5,4.06 5,7.41 0,2.08 -0.8,3.97 -2.1,5.39z",
        )
    }

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

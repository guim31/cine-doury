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

/**
 * The icons of the app, in one place. Material icons (Apache 2.0) on a 24 × 24 grid; the paths of
 * those not in `material-icons-core` come from `@material-design-icons/svg` 0.14.15 (filled).
 */
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

    /** A library of cartoons (« Dessins animés »). */
    val Animation: ImageVector by lazy {
        icon(
            "Animation",
            "M15 2c-2.71 0-5.05 1.54-6.22 3.78a7.062 7.062 0 0 0-3 3A7.014 7.014 0 0 0 2 15c0 3.87 " +
                "3.13 7 7 7 2.71 0 5.05-1.54 6.22-3.78a7.062 7.062 0 0 0 3-3A7.014 7.014 0 0 0 22 " +
                "9c0-3.87-3.13-7-7-7zM9 20a5.002 5.002 0 0 1-4-8c0 3.87 3.13 7 7 7-.84.63-1.88 1-3 " +
                "1zm3-3a5.002 5.002 0 0 1-4-8c0 3.86 3.13 6.99 7 7-.84.63-1.88 1-3 " +
                "1zm4.7-3.3c-.53.19-1.1.3-1.7.3-2.76 0-5-2.24-5-5 0-.6.11-1.17.3-1.7.53-.19 1.1-.3 1.7-.3 " +
                "2.76 0 5 2.24 5 5 0 .6-.11 1.17-.3 1.7zM19 12c0-3.86-3.13-6.99-7-7a5.002 5.002 0 0 1 7 " +
                "7z",
        )
    }

    /** A library of Japanese animation (« Animes »). */
    val Anime: ImageVector by lazy {
        icon(
            "Anime",
            "m19 9 1.25-2.75L23 5l-2.75-1.25L19 1l-1.25 2.75L15 5l2.75 1.25L19 9zm-7.5.5L9 4 6.5 9.5 " +
                "1 12l5.5 2.5L9 20l2.5-5.5L17 12l-5.5-2.5zM19 15l-1.25 2.75L15 19l2.75 1.25L19 " +
                "23l1.25-2.75L23 19l-2.75-1.25L19 15z",
        )
    }

    /** A library of documentaries. */
    val Documentaries: ImageVector by lazy {
        icon("Documentaries", "m14 6-3.75 5 2.85 3.8-1.6 1.2C9.81 13.75 7 10 7 10l-6 8h22L14 6z")
    }

    /** A library of shows: stage, concerts, stand-up (« Spectacles »). */
    val Stage: ImageVector by lazy {
        icon(
            "Stage",
            "M2 16.5C2 19.54 4.46 22 7.5 22s5.5-2.46 5.5-5.5V10H2v6.5zm5.5 2C6.12 18.5 5 17.83 5 " +
                "17h5c0 .83-1.12 1.5-2.5 1.5zM10 13c.55 0 1 .45 1 1s-.45 1-1 1-1-.45-1-1 .45-1 1-1zm-5 " +
                "0c.55 0 1 .45 1 1s-.45 1-1 1-1-.45-1-1 .45-1 1-1z M11 3v6h3v2.5c0-.83 1.12-1.5 " +
                "2.5-1.5s2.5.67 2.5 1.5h-5v2.89c.75.38 1.6.61 2.5.61 3.04 0 5.5-2.46 5.5-5.5V3H11zm3 " +
                "5.08c-.55 0-1-.45-1-1s.45-1 1-1 1 .45 1 1c0 .56-.45 1-1 1zm5 0c-.55 0-1-.45-1-1s.45-1 " +
                "1-1 1 .45 1 1c0 .56-.45 1-1 1z",
        )
    }

    /** A library of sport (« MMA », « Sport »). */
    val Sport: ImageVector by lazy {
        icon(
            "Sport",
            "M19 5h-2V3H7v2H5c-1.1 0-2 .9-2 2v1c0 2.55 1.92 4.63 4.39 4.94A5.01 5.01 0 0 0 11 " +
                "15.9V19H7v2h10v-2h-4v-3.1a5.01 5.01 0 0 0 3.61-2.96C19.08 12.63 21 10.55 21 " +
                "8V7c0-1.1-.9-2-2-2zM5 8V7h2v3.82C5.84 10.4 5 9.3 5 8zm14 0c0 1.3-.84 2.4-2 2.82V7h2v1z",
        )
    }

    /** A library of online videos (« YouTube »). */
    val OnlineVideo: ImageVector by lazy {
        icon(
            "OnlineVideo",
            "M20 4H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zM9.5 " +
                "16.5v-9l7 4.5-7 4.5z",
        )
    }

    /** A library for the family and the children. */
    val Kids: ImageVector by lazy {
        icon(
            "Kids",
            "M16 4c0-1.11.89-2 2-2s2 .89 2 2-.89 2-2 2-2-.89-2-2zm4 18v-6h2.5l-2.54-7.63A2.01 2.01 0 " +
                "0 0 18.06 7h-.12a2 2 0 0 0-1.9 1.37l-.86 2.58c1.08.6 1.82 1.73 1.82 " +
                "3.05v8h3zm-7.5-10.5c.83 0 1.5-.67 1.5-1.5s-.67-1.5-1.5-1.5S11 9.17 11 10s.67 1.5 1.5 " +
                "1.5zM5.5 6c1.11 0 2-.89 2-2s-.89-2-2-2-2 .89-2 2 .89 2 2 2zm2 " +
                "16v-7H9V9c0-1.1-.9-2-2-2H4c-1.1 0-2 .9-2 2v6h1.5v7h4zm6.5 " +
                "0v-4h1v-4c0-.82-.68-1.5-1.5-1.5h-2c-.82 0-1.5.68-1.5 1.5v4h1v4h3z",
        )
    }

    /** A library of folders, or of anything. */
    val Folder: ImageVector by lazy {
        icon(
            "Folder",
            "M10 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 " +
                "2-2V8c0-1.1-.9-2-2-2h-8l-2-2z",
        )
    }

    /** « Bande-annonce ». */
    val Trailer: ImageVector by lazy {
        icon(
            "Trailer",
            "M18 3v2h-2V3H8v2H6V3H4v18h2v-2h2v2h8v-2h2v2h2V3h-2zM8 " +
                "17H6v-2h2v2zm0-4H6v-2h2v2zm0-4H6V7h2v2zm10 8h-2v-2h2v2zm0-4h-2v-2h2v2zm0-4h-2V7h2v2z",
        )
    }

    /** « Informations sur le fichier ». */
    val FileInfo: ImageVector by lazy {
        icon(
            "FileInfo",
            "M14 2H6c-1.1 0-1.99.9-1.99 2L4 20c0 1.1.89 2 1.99 2H18c1.1 0 2-.9 2-2V8l-6-6zm2 " +
                "16H8v-2h8v2zm0-4H8v-2h8v2zm-3-5V3.5L18.5 9H13z",
        )
    }

    /** « Marquer comme non vu ». */
    val Unwatched: ImageVector by lazy {
        icon(
            "Unwatched",
            "m1.79 12 5.58 5.59L5.96 19 .37 13.41 1.79 12zm.45-7.78L12.9 14.89l-1.28 1.28L7.44 " +
                "12l-1.41 1.41L11.62 19l2.69-2.69 4.89 4.89 1.41-1.41L3.65 2.81 2.24 4.22zm14.9 " +
                "9.27L23.62 7 22.2 5.59l-6.48 6.48 1.42 1.42zM17.96 7l-1.41-1.41-3.65 3.66 1.41 " +
                "1.41L17.96 7z",
        )
    }

    /** A season or a series marked as watched. */
    val WatchedAll: ImageVector by lazy {
        icon(
            "WatchedAll",
            "m18 7-1.41-1.41-6.34 6.34 1.41 1.41L18 7zm4.24-1.41L11.66 16.17 7.48 12l-1.41 1.41L11.66 " +
                "19l12-12-1.42-1.41zM.41 13.41 6 19l1.41-1.41L1.83 12 .41 13.41z",
        )
    }

    /** « Audio et sous-titres ». */
    val Tracks: ImageVector by lazy {
        icon(
            "Tracks",
            "M20 4H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zM4 " +
                "12h4v2H4v-2zm10 6H4v-2h10v2zm6 0h-4v-2h4v2zm0-4H10v-2h10v2z",
        )
    }

    /** An audio track. */
    val Audio: ImageVector by lazy {
        icon(
            "Audio",
            "M12 3v9.28a4.39 4.39 0 0 0-1.5-.28C8.01 12 6 14.01 6 16.5S8.01 21 10.5 21c2.31 0 " +
                "4.2-1.75 4.45-4H15V6h4V3h-7z",
        )
    }

    // The player (J4).
    val Pause: ImageVector by lazy { icon("Pause", "M6 19h4V5H6v14zm8-14v14h4V5h-4z") }
    val SkipNext: ImageVector by lazy { icon("SkipNext", "M6 18l8.5-6L6 6v12zM16 6v12h2V6h-2z") }
    val FastForward: ImageVector by lazy { icon("FastForward", "M4 18l8.5-6L4 6v12zm9-12v12l8.5-6L13 6z") }
    val FastRewind: ImageVector by lazy { icon("FastRewind", "M11 18V6l-8.5 6 8.5 6zm.5-6l8.5 6V6l-8.5 6z") }
    val Chapters: ImageVector by lazy {
        icon("Chapters", "M3 13h2v-2H3v2zm0 4h2v-2H3v2zm0-8h2V7H3v2zm4 4h14v-2H7v2zm0 4h14v-2H7v2zM7 7v2h14V7H7z")
    }
    val Quality: ImageVector by lazy {
        icon(
            "Quality",
            "M19 4H5c-1.11 0-2 .9-2 2v12c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm-8 11H9.5v-2h-2v2H6V9h" +
                "1.5v2.5h2V9H11v6zm7-1c0 .55-.45 1-1 1h-.75v1.5h-1.5V15H14c-.55 0-1-.45-1-1v-4c0-.55.45-1 1-1h3c.55 " +
                "0 1 .45 1 1v4zm-3.5-.5h2v-3h-2v3z",
        )
    }

    /** A synopsis that unfolds. */
    val Expand: ImageVector by lazy { icon("Expand", "M16.59 8.59 12 13.17 7.41 8.59 6 10l6 6 6-6z") }

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

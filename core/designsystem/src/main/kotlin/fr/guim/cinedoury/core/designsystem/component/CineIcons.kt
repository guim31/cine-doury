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

    /** A library of home videos (« Vidéos de famille »). */
    val HomeVideos: ImageVector by lazy {
        icon(
            "HomeVideos",
            "M18 10.48V6c0-1.1-.9-2-2-2H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2v-4.48l" +
                "4 3.98v-11l-4 3.98zM5 16l2.38-3.17L9 15l2.62-3.5L15 16H5z",
        )
    }

    /** A library of 4K films. */
    val Uhd: ImageVector by lazy {
        icon(
            "Uhd",
            "M19 3H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm-7 10.5h-" +
                "1V15H9.5v-1.5h-3V9H8v3h1.5V9H11v3h1v1.5zm6 1.5h-1.75l-1.75-2.25V15H13V9h1.5v2.25L16.25" +
                " 9H18l-2.25 3L18 15z",
        )
    }

    /** A library of classics. */
    val Classics: ImageVector by lazy {
        icon(
            "Classics",
            "M14 5c0-1.1-.9-2-2-2h-1V2c0-.55-.45-1-1-1H6c-.55 0-1 .45-1 1v1H4c-1.1 0-2 .9-2 2v15c0 " +
                "1.1.9 2 2 2h8c1.1 0 2-.9 2-2h8V5h-8zm-2 13h-2v-2h2v2zm0-9h-2V7h2v2zm4 9h-2v-2h2v2zm0-9" +
                "h-2V7h2v2zm4 9h-2v-2h2v2zm0-9h-2V7h2v2z",
        )
    }

    /** A library of comedy, stand-up (« Humour »). */
    val Comedy: ImageVector by lazy {
        icon(
            "Comedy",
            "M11.99 2C6.47 2 2 6.48 2 12s4.47 10 9.99 10C17.52 22 22 17.52 22 12S17.52 2 11.99 2zM1" +
                "2 20c-4.42 0-8-3.58-8-8s3.58-8 8-8 8 3.58 8 8-3.58 8-8 8zm3.5-9c.83 0 1.5-.67 1.5-1.5S" +
                "16.33 8 15.5 8 14 8.67 14 9.5s.67 1.5 1.5 1.5zm-7 0c.83 0 1.5-.67 1.5-1.5S9.33 8 8.5 8" +
                " 7 8.67 7 9.5 7.67 11 8.5 11zm3.5 6.5c2.33 0 4.31-1.46 5.11-3.5H6.89c.8 2.04 2.78 3.5 " +
                "5.11 3.5z",
        )
    }

    /** A library of music videos (« Clips »). */
    val MusicVideos: ImageVector by lazy {
        icon(
            "MusicVideos",
            "M21 3H3c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h18c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm0 16H3V5h" +
                "18v14zM8 15c0-1.66 1.34-3 3-3 .35 0 .69.07 1 .18V6h5v2h-3v7.03A3.003 3.003 0 0 1 11 18" +
                "c-1.66 0-3-1.34-3-3z",
        )
    }

    /** A library of science fiction. */
    val SciFi: ImageVector by lazy {
        icon(
            "SciFi",
            "M9.19 6.35c-2.04 2.29-3.44 5.58-3.57 5.89L2 10.69l4.05-4.05c.47-.47 1.15-.68 1.81-.55l" +
                "1.33.26zM11.17 17s3.74-1.55 5.89-3.7c5.4-5.4 4.5-9.62 4.21-10.57-.95-.3-5.17-1.19-10.5" +
                "7 4.21C8.55 9.09 7 12.83 7 12.83L11.17 17zm6.48-2.19c-2.29 2.04-5.58 3.44-5.89 3.57L13" +
                ".31 22l4.05-4.05c.47-.47.68-1.15.55-1.81l-.26-1.33zM9 18c0 .83-.34 1.58-.88 2.12C6.94 " +
                "21.3 2 22 2 22s.7-4.94 1.88-6.12A2.996 2.996 0 0 1 9 18zm4-9c0-1.1.9-2 2-2s2 .9 2 2-.9" +
                " 2-2 2-2-.9-2-2z",
        )
    }

    /** A library of horror (« Épouvante »). */
    val Horror: ImageVector by lazy {
        icon(
            "Horror",
            "M11.1 12.08c-2.33-4.51-.5-8.48.53-10.07C6.27 2.2 1.98 6.59 1.98 12c0 .14.02.28.02.42.6" +
                "2-.27 1.29-.42 2-.42 1.66 0 3.18.83 4.1 2.15A4.01 4.01 0 0 1 11 18c0 1.52-.87 2.83-2.1" +
                "2 3.51.98.32 2.03.5 3.11.5 3.5 0 6.58-1.8 8.37-4.52-2.36.23-6.98-.97-9.26-5.41z M7 16h" +
                "-.18C6.4 14.84 5.3 14 4 14c-1.66 0-3 1.34-3 3s1.34 3 3 3h3c1.1 0 2-.9 2-2s-.9-2-2-2z",
        )
    }

    /** A library of action and adventure. */
    val Action: ImageVector by lazy {
        icon(
            "Action",
            "m12 12.9-2.13 2.09c-.56.56-.87 1.29-.87 2.07C9 18.68 10.35 20 12 20s3-1.32 3-2.94c0-.7" +
                "8-.31-1.52-.87-2.07L12 12.9z m16 6-.44.55C14.38 8.02 12 7.19 12 5.3V2S4 6 4 13c0 2.92 " +
                "1.56 5.47 3.89 6.86-.56-.79-.89-1.76-.89-2.8 0-1.32.52-2.56 1.47-3.5L12 10.1l3.53 3.47" +
                "c.95.93 1.47 2.17 1.47 3.5 0 1.02-.31 1.96-.85 2.75 1.89-1.15 3.29-3.06 3.71-5.3.66-3." +
                "55-1.07-6.9-3.86-8.52z",
        )
    }

    /** A library for the holidays (« Noël »). */
    val Holidays: ImageVector by lazy {
        icon(
            "Holidays",
            "m2 22 14-5-9-9zm12.53-9.47 5.59-5.59a1.25 1.25 0 0 1 1.77 0l.59.59 1.06-1.06-.59-.59a2" +
                ".758 2.758 0 0 0-3.89 0l-5.59 5.59 1.06 1.06zm-4.47-5.65-.59.59 1.06 1.06.59-.59a2.758" +
                " 2.758 0 0 0 0-3.89l-.59-.59-1.06 1.07.59.59c.48.48.48 1.28 0 1.76zm7 5-1.59 1.59 1.06" +
                " 1.06 1.59-1.59a1.25 1.25 0 0 1 1.77 0l1.61 1.61 1.06-1.06-1.61-1.61a2.758 2.758 0 0 0" +
                "-3.89 0zm-2-6-3.59 3.59 1.06 1.06 3.59-3.59a2.758 2.758 0 0 0 0-3.89l-1.59-1.59-1.06 1" +
                ".06 1.59 1.59c.48.49.48 1.29 0 1.77z",
        )
    }

    /** A library of courses and talks (« Tutos », « Conférences »). */
    val Learning: ImageVector by lazy {
        icon(
            "Learning",
            "M5 13.18v4L12 21l7-3.82v-4L12 17l-7-3.82zM12 3 1 9l11 6 9-4.91V17h2V9L12 3z",
        )
    }

    /** A library of cooking. */
    val Cooking: ImageVector by lazy {
        icon(
            "Cooking",
            "M11 9H9V2H7v7H5V2H3v7c0 2.12 1.66 3.84 3.75 3.97V22h2.5v-9.03C11.34 12.84 13 11.12 13 " +
                "9V2h-2v7zm5-3v8h2.5v8H21V2c-2.76 0-5 2.24-5 4z",
        )
    }

    /** A library of nature and animals. */
    val Nature: ImageVector by lazy {
        icon(
            "Nature",
            "M16 12 9 2 2 12h1.86L0 18h7v4h4v-4h7l-3.86-6z M20.14 12H22L15 2l-2.39 3.41L17.92 13h-1" +
                ".95l3.22 5H24zM13 19h4v3h-4z",
        )
    }

    /** A library of world cinema (« VOST »). */
    val World: ImageVector by lazy {
        icon(
            "World",
            "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-1 17.93c-3.95-.49-" +
                "7-3.85-7-7.93 0-.62.08-1.21.21-1.79L9 15v1c0 1.1.9 2 2 2v1.93zm6.9-2.54c-.26-.81-1-1.3" +
                "9-1.9-1.39h-1v-3c0-.55-.45-1-1-1H8v-2h2c.55 0 1-.45 1-1V7h2c1.1 0 2-.9 2-2v-.41c2.93 1" +
                ".19 5 4.06 5 7.41 0 2.08-.8 3.97-2.1 5.39z",
        )
    }

    /** A library of TV programmes (« Émissions », « Replay »). */
    val TvPrograms: ImageVector by lazy {
        icon(
            "TvPrograms",
            "M21 6h-7.59l3.29-3.29L16 2l-4 4-4-4-.71.71L10.59 6H3a2 2 0 0 0-2 2v12c0 1.1.9 2 2 2h18" +
                "c1.1 0 2-.9 2-2V8a2 2 0 0 0-2-2zm0 14H3V8h18v12zM9 10v8l7-4z",
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
    val SkipPrevious: ImageVector by lazy { icon("SkipPrevious", "M6 6h2v12H6zm3.5 6l8.5 6V6z") }
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

    val Remove: ImageVector by lazy { icon("Remove", "M19 13H5v-2h14v2z") }

    /** The size of the subtitles. */
    val TextSize: ImageVector by lazy { icon("TextSize", "M9 4v3h5v12h3V7h5V4H9zm-6 8h3v7h3v-7h3V9H3v3z") }

    /** A synopsis that unfolds. */
    val Expand: ImageVector by lazy { icon("Expand", "M16.59 8.59 12 13.17 7.41 8.59 6 10l6 6 6-6z") }

    // Management (J6).
    val Delete: ImageVector by lazy {
        icon("Delete", "M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z")
    }
    val Image: ImageVector by lazy {
        icon(
            "Image",
            "M21 19V5c0-1.1-.9-2-2-2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2zM8.5 13.5l2.5 " +
                "3.01L14.5 12l4.5 6H5l3.5-4.5z",
        )
    }
    val Checked: ImageVector by lazy {
        icon(
            "Checked",
            "M19 3H5c-1.11 0-2 .9-2 2v14c0 1.1.89 2 2 2h14c1.11 0 2-.9 2-2V5c0-1.1-.89-2-2-2zm-9 14l-5-5 " +
                "1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z",
        )
    }
    val Unchecked: ImageVector by lazy {
        icon(
            "Unchecked",
            "M19 5v14H5V5h14m0-2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2z",
        )
    }

    // Search (J5).

    /** Voice search. */
    val Mic: ImageVector by lazy {
        icon(
            "Mic",
            "M12,14c1.66,0 2.99,-1.34 2.99,-3L15,5c0,-1.66 -1.34,-3 -3,-3S9,3.34 9,5v6c0,1.66 1.34,3 3,3zM17.3,11" +
                "c0,3 -2.54,5.1 -5.3,5.1S6.7,14 6.7,11L5,11c0,3.41 2.72,6.23 6,6.72L11,21h2v-3.28c3.28,-0.48 6,-3.3" +
                " 6,-6.72h-1.7z",
        )
    }

    /** The keyboard of the system. */
    val Keyboard: ImageVector by lazy {
        icon(
            "Keyboard",
            "M20,5H4c-1.1,0 -1.99,0.9 -1.99,2L2,17c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V7c0,-1.1 -0.9,-2 -2,-2z" +
                "M11,8h2v2h-2V8zM11,11h2v2h-2v-2zM8,8h2v2H8V8zM8,11h2v2H8v-2zM7,13H5v-2h2v2zM7,10H5V8h2v2zM16,17H8" +
                "v-2h8v2zM16,13h-2v-2h2v2zM16,10h-2V8h2v2zM19,13h-2v-2h2v2zM19,10h-2V8h2v2z",
        )
    }

    /** The space bar of the on-screen keyboard. */
    val Space: ImageVector by lazy { icon("Space", "M18,9v4H6V9H4v6h16V9z") }

    /** A recent search. */
    val History: ImageVector by lazy {
        icon(
            "History",
            "M13,3c-4.97,0 -9,4.03 -9,9H1l3.89,3.89 0.07,0.14L9,12H6c0,-3.87 3.13,-7 7,-7s7,3.13 7,7 -3.13,7 -7,7" +
                "c-1.93,0 -3.68,-0.79 -4.94,-2.06l-1.42,1.42C8.27,19.99 10.51,21 13,21c4.97,0 9,-4.03 9,-9s-4.03,-9" +
                " -9,-9zM12,8v5l4.28,2.54 0.72,-1.21 -3.5,-2.08V8H12z",
        )
    }

    /** « Demander » (Seerr). */
    val Request: ImageVector by lazy {
        icon("Request", "M19,9h-4V3H9v6H5l7,7 7,-7zM5,18v2h14v-2H5z")
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

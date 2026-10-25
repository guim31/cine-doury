package fr.guim.cinedoury.core.designsystem.theme

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/** The 8 dp grid and the TV safe area (`UI.md` § 2). */
object CineSpacing {
    val xs = 8.dp
    val s = 16.dp
    val m = 24.dp
    val l = 32.dp
    val xl = 48.dp
    val xxl = 64.dp

    /** 5 % of a 1080p screen, left and right. */
    val safeHorizontal = 48.dp

    /** 5 % of a 1080p screen, top and bottom. */
    val safeVertical = 27.dp

    /** Between a row title and its cards (`UI.md` § 8). */
    val rowTitleGap = 24.dp

    val all = listOf(xs, s, m, l, xl, xxl)
}

/** Sizes of `UI.md` § 2, § 3 and § 6. */
object CineSizes {
    val posterSmall = DpSize(120.dp, 180.dp)
    val posterNormal = DpSize(150.dp, 225.dp)
    val posterLarge = DpSize(190.dp, 285.dp)
    val thumbnail = DpSize(300.dp, 169.dp)
    val cardCorner = 8.dp
    val hairline = 1.dp
    val swatch = DpSize(96.dp, 64.dp)
    val progressHeight = 4.dp
    val unwatchedTriangle = 22.dp

    val focusOutlineWidth = 3.dp
    val focusOutlineGap = 2.dp
    val focusElevation = 16.dp

    val buttonHeight = 52.dp
    val roundButton = 52.dp
    val icon = 24.dp

    val railCollapsed = 72.dp
    val railExpanded = 240.dp
    val railItemHeight = 52.dp

    val logoMax = DpSize(360.dp, 140.dp)
    val trickplayWidth = 320.dp
}

/** Scale of a focused element (`UI.md` § 3). */
object CineScale {
    const val POSTER = 1.08f
    const val BUTTON = 1.05f
    const val THUMBNAIL = 1.05f
}

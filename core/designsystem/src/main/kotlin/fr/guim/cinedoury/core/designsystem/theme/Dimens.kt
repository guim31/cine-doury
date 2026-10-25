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
    /**
     * 16:9, for « Reprendre », episodes and chapters: four whole thumbnails and the start of a fifth
     * in a row of a 1080p screen (`UI.md` § 6, retouched on 25/10/2026).
     */
    val thumbnail = DpSize(176.dp, 99.dp)
    val cardCorner = 8.dp
    val hairline = 1.dp
    val swatch = DpSize(104.dp, 64.dp)
    val progressHeight = 4.dp
    val unwatchedTriangle = 22.dp

    val focusOutlineWidth = 3.dp
    val focusOutlineGap = 2.dp
    val focusElevation = 16.dp

    /** Blur radius of the focus halo, drawn from just outside the outline (`UI.md` § 3). */
    val focusHalo = 14.dp

    val buttonHeight = 52.dp
    val roundButton = 52.dp
    val icon = 24.dp

    val railCollapsed = 72.dp
    val railExpanded = 240.dp
    val railItemHeight = 52.dp

    // Sign-in, profiles and settings (J1). The TV screen is 960 × 540 dp.
    val avatarLarge = 112.dp
    val avatarMedium = 72.dp
    val avatarSmall = 32.dp
    val textFieldHeight = 52.dp
    val textFieldWidth = 400.dp
    val keypadKey = 52.dp
    val pinDot = 16.dp
    val formWidth = 480.dp
    val settingsWidth = 560.dp
    val settingsRowHeight = 56.dp
    val dialogWidth = 480.dp

    val logoMax = DpSize(360.dp, 140.dp)
    val trickplayWidth = 320.dp

    // Home and libraries (J2).

    /** Width of the text column of the featured banner. */
    val heroTextWidth = 520.dp
    val tabHeight = 40.dp
    val alphabetKey = 18.dp
    val alphabetWidth = 32.dp
    val panelWidth = 400.dp
    val listRowPoster = DpSize(64.dp, 96.dp)
    val listRowHeight = 112.dp
    val skeletonLine = 16.dp

    /** The featured banner takes 60 % of the screen height (`UI.md` § 8). */
    const val HERO_HEIGHT_FRACTION = 0.6f
}

/** Scale of a focused element (`UI.md` § 3). */
object CineScale {
    const val POSTER = 1.08f
    const val BUTTON = 1.05f
    const val THUMBNAIL = 1.05f

    /** Wide elements (text fields, list rows): a light growth, so that they stay in their column. */
    const val FIELD = 1.02f
}

/**
 * The ambient backdrop of the navigation screens (`UI.md` § 3, § 4 and § 8): a very blurred
 * full-screen image taken from the focused element, decoded from its BlurHash at a tiny size and
 * stretched, then dimmed under the readability veils.
 */
object CineAmbient {
    /** Size the BlurHash is decoded at before being stretched over the screen (16:9). */
    const val DECODE_WIDTH = 32
    const val DECODE_HEIGHT = 18

    /**
     * Opacity of the `fond` layer over the ambient image. At 60 %, `texte` keeps 4.6:1 over the
     * brightest possible image (pure white); `texte-2` and smaller text sit on the veils.
     */
    const val DIM_ALPHA = 0.6f

    /** Size a BlurHash placeholder is decoded at under a loading image. */
    const val PLACEHOLDER_SIZE = 24
}

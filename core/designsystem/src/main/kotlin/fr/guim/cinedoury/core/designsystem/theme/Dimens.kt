package fr.guim.cinedoury.core.designsystem.theme

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/** The 8 dp grid and the TV safe area (`UI.md` § 2). */
object CineSpacing {
    val none = 0.dp
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

    /**
     * Room a container that clips (a scrolling column, a lazy list) leaves around its focusable
     * elements, so that their outline and halo are never cut (J8: « Utiliser un mot de passe » on
     * Quick Connect lost the bottom of its outline on the Shield).
     */
    val focusRoom = focusOutlineGap + focusOutlineWidth + focusHalo

    val buttonHeight = 52.dp
    val roundButton = 52.dp
    val icon = 24.dp

    val railCollapsed = 72.dp
    val railExpanded = 240.dp
    val railItemHeight = 52.dp

    /**
     * Between the edges of the rail and its entries, on every side: the outline of the focused entry
     * (5 dp) and its halo ([railFocusHalo]) stay inside the rail, open or closed (perf/fluidite).
     */
    val railInset = 16.dp

    /** The halo of a focused entry of the rail: smaller than [focusHalo], to fit in [railInset]. */
    val railFocusHalo = 6.dp

    // Sign-in, profiles and settings (J1). The TV screen is 960 × 540 dp.
    val avatarLarge = 112.dp
    val avatarMedium = 72.dp
    val avatarSmall = 32.dp
    val textFieldHeight = 52.dp
    val textFieldWidth = 400.dp

    /** A field for a year (« Corriger l'identification », J6). */
    val yearFieldWidth = 160.dp

    /** The list of « Éléments à problème » (J6): the poster, the title and the path of the file. */
    val problemsListWidth = 720.dp
    val keypadKey = 52.dp
    val pinDot = 16.dp
    val formWidth = 480.dp
    val settingsWidth = 560.dp
    val settingsRowHeight = 56.dp

    /** The changelog of an update: what is longer scrolls (J7). */
    val changelogHeight = 160.dp
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

    // Details screens (J3).

    /** The text column of a details screen: 45 % of the screen (`UI.md` § 8, « Fiche »). */
    val detailsTextWidth = 432.dp
    val badgeHeight = 28.dp
    val episodeRowHeight = 131.dp

    /** The frame of an episode on its details screen, in place of a poster. */
    val episodePicture = DpSize(320.dp, 180.dp)

    /** The side panels with long lines: tracks, file information. */
    val widePanelWidth = 480.dp

    // Player (J4).

    /** Above and below the text of a pill (« par défaut »). */
    val pillPadding = 2.dp

    /** The progress bar of the player, and its knob with the focus. */
    val progressBar = 6.dp
    val progressBarFocused = 10.dp
    val progressKnob = 20.dp
    val chapterGap = 3.dp

    /** « Épisode suivant », its countdown ring. */
    val nextCardWidth = 480.dp
    val countdownRing = 48.dp
    val countdownStroke = 4.dp

    /** The information screen of the player: wide enough for a codec string. */
    val infoSheetWidth = 640.dp
    val infoLabelWidth = 200.dp

    /** The two columns of the audio and subtitle panel of the player. */
    val tracksPanelWidth = 720.dp

    /** The value between − and + of a stepper (« 140 % »). */
    val stepperValueWidth = 64.dp

    /** The bar of the subtitles over the top of the picture, while their size is set. */
    val subtitleBarWidth = 840.dp

    /** The still picture under the subtitles of « Apparence des sous-titres ». */
    val subtitlePreview = DpSize(320.dp, 180.dp)

    /** The subtitles as they will look, under the title of their page of settings (J7). */
    val subtitlePreviewSmall = DpSize(224.dp, 126.dp)
    val subtitlePreviewTop = 120.dp

    // Search (J5).

    /** A key of the on-screen keyboard: seven in a row fill the left column of the search. */
    val keyboardKey = 40.dp

    /** The line of the query, above the keyboard. */
    val searchFieldHeight = 52.dp

    /** A chip: a recent search, a genre. */
    val chipHeight = 40.dp

    /** The microphone of the voice search, and the ring that follows the voice around it. */
    val voiceMic = 96.dp
    val voiceRing = 160.dp

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

    /**
     * The entries of the rail: no growth. The focused entry fills in white, and its outline and halo
     * stay inside the rail ([CineSizes.railInset]).
     */
    const val RAIL = 1f
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

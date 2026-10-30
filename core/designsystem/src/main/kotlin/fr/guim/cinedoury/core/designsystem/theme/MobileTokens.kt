package fr.guim.cinedoury.core.designsystem.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The type scale of the phone and the tablet (M1, `UI.md` § 9): the same roles and the same font as
 * [TvCineTypography], at the size of a screen held in the hand. The colours, the grid of
 * [CineSpacing], the corners and the durations are the TV's: one set of tokens for both apps.
 */
val MobileCineTypography =
    CineTypography(
        display = mobileInter(size = 28, lineHeight = 34, weight = FontWeight.Bold, tracking = -0.5f),
        title1 = mobileInter(size = 22, lineHeight = 28, weight = FontWeight.SemiBold, tracking = -0.3f),
        title2 = mobileInter(size = 18, lineHeight = 24, weight = FontWeight.SemiBold, tracking = -0.1f),
        body = mobileInter(size = 15, lineHeight = 22, weight = FontWeight.Normal),
        label = mobileInter(size = 15, lineHeight = 20, weight = FontWeight.SemiBold),
        caption = mobileInter(size = 13, lineHeight = 18, weight = FontWeight.Medium),
        minimum = mobileInter(size = 11, lineHeight = 16, weight = FontWeight.Medium),
    )

private fun mobileInter(
    size: Int,
    lineHeight: Int,
    weight: FontWeight,
    tracking: Float = 0f,
) = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = tracking.sp,
)

/** Sizes of the phone and the tablet (M1, `UI.md` § 9), on the 8 dp grid of [CineSpacing]. */
object MobileSizes {
    /** The smallest a target of the finger is, on every side (`UI.md` § 9). */
    val touchTarget = 48.dp

    /** Left and right of the content, on a phone. */
    val gutter = 16.dp

    /** Left and right of the content, wider than [compactWidth] (a tablet, a phone in landscape). */
    val gutterWide = 24.dp

    /** Up to this width, a phone held upright (Material's « compact »). */
    val compactWidth = 600.dp

    /** From this width, a tablet in landscape: two panes come with M3 (`UI.md` § 9). */
    val expandedWidth = 840.dp

    val posterSmall = DpSize(96.dp, 144.dp)
    val posterNormal = DpSize(112.dp, 168.dp)
    val posterLarge = DpSize(136.dp, 204.dp)

    /** 16:9, for « Reprendre », episodes and the small tiles of « Ce soir ». */
    val thumbnail = DpSize(208.dp, 117.dp)

    /** The smallest a poster of a library grid gets: the grid fits as many as the width allows. */
    val gridPosterMin = 104.dp

    val cardCorner = CineSizes.cardCorner
    val sheetCorner = 16.dp
    val buttonHeight = 48.dp
    val textFieldWidth = 480.dp
    val formWidth = 480.dp
    val icon = 24.dp
    val avatarLarge = 88.dp
    val avatarMedium = 56.dp
    val avatarSmall = 32.dp
    val keypadKey = 72.dp
    val pinDot = 14.dp
    val listRowPoster = DpSize(48.dp, 72.dp)
    val episodeThumbnail = DpSize(144.dp, 81.dp)
    val badgeHeight = 24.dp
    val logoMax = DpSize(240.dp, 88.dp)
    val chipHeight = 32.dp

    /** The backdrop at the top of a details screen, and of the large tile of « Ce soir ». */
    const val BACKDROP_RATIO = 16f / 9f

    /** The A–Z bar along a library grid: a letter for each finger slot. */
    val alphabetWidth = 28.dp

    /** The player (M1): the round buttons over the picture, the large one in the middle. */
    val playerButton = 56.dp
    val playerButtonLarge = 72.dp
    val playerIcon = 28.dp
    val playerIconLarge = 40.dp
    val seekBarTouch = 48.dp

    /** The touch player (M2): the bar with its margin, the indicators of the gestures, the preview. */
    val seekTrack = 4.dp
    val seekTrackActive = 6.dp
    val seekThumb = 16.dp
    val playerSmallButton = 48.dp
    val gestureIndicator = DpSize(160.dp, 48.dp)
    val gestureBar = DpSize(120.dp, 4.dp)
    val seekPreview = DpSize(192.dp, 108.dp)
    val jumpBubble = 112.dp
    val pipButton = 40.dp
}

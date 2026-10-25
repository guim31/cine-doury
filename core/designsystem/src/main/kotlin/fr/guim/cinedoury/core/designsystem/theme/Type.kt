package fr.guim.cinedoury.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import fr.guim.cinedoury.core.designsystem.R

/** Inter 4.1, embedded (SIL Open Font License, see `INTER-LICENSE.txt`). */
val InterFontFamily =
    FontFamily(
        Font(R.font.inter_regular, FontWeight.Normal),
        Font(R.font.inter_medium, FontWeight.Medium),
        Font(R.font.inter_semibold, FontWeight.SemiBold),
        Font(R.font.inter_bold, FontWeight.Bold),
    )

/** TV type scale of `UI.md` § 5, for a 1080p screen seen from 3 m. */
@Immutable
data class CineTypography(
    /** « Affiche », 48 sp, 700: title without a logo on a detail screen. */
    val display: TextStyle,
    /** « Titre 1 », 34 sp, 600: screen titles. */
    val title1: TextStyle,
    /** « Titre 2 », 26 sp, 600: row titles. */
    val title2: TextStyle,
    /** « Corps », 20 sp, 400: synopsis, lists. */
    val body: TextStyle,
    /** « Corps » at 600, for button and menu labels. */
    val label: TextStyle,
    /** « Légende », 16 sp, 500: metadata, badges. */
    val caption: TextStyle,
    /** « Minimum absolu », 14 sp, 500: nothing smaller on a TV. */
    val minimum: TextStyle,
)

private fun inter(size: Int, lineHeight: Int, weight: FontWeight, tracking: Float = 0f) =
    TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = lineHeight.sp,
        letterSpacing = tracking.sp,
    )

val TvCineTypography =
    CineTypography(
        display = inter(size = 48, lineHeight = 56, weight = FontWeight.Bold, tracking = -0.8f),
        title1 = inter(size = 34, lineHeight = 42, weight = FontWeight.SemiBold, tracking = -0.4f),
        title2 = inter(size = 26, lineHeight = 32, weight = FontWeight.SemiBold, tracking = -0.2f),
        body = inter(size = 20, lineHeight = 28, weight = FontWeight.Normal),
        label = inter(size = 20, lineHeight = 28, weight = FontWeight.SemiBold),
        caption = inter(size = 16, lineHeight = 22, weight = FontWeight.Medium),
        minimum = inter(size = 14, lineHeight = 20, weight = FontWeight.Medium),
    )

/** Tabular figures, for durations and times (« 1 h 52 », « 22 h 47 »). */
fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")

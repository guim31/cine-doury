package fr.guim.cinedoury.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** Colour tokens of `UI.md` § 4. Screens read them through [CineTheme.colors], never as literals. */
@Immutable
data class CineColors(
    /** `fond`: application background. */
    val background: Color,
    /** `surface-1`: cards, panels. */
    val surface1: Color,
    /** `surface-2`: menus, dialogs. */
    val surface2: Color,
    /** `surface-3`: hover, disabled elements. */
    val surface3: Color,
    /** `texte`: main text. */
    val text: Color,
    /** `texte-2`: secondary text. */
    val text2: Color,
    /** `texte-3`: discreet metadata. */
    val text3: Color,
    /** `focus`: focus outline. */
    val focus: Color,
    /** `accent`: « jaune projecteur », main button, progress, selection. */
    val accent: Color,
    /** Text on the accent. */
    val onAccent: Color,
    /** Accent while pressed. */
    val accentPressed: Color,
    /** Track behind a progress bar. */
    val progressTrack: Color,
    /** `danger`: deletion. */
    val danger: Color,
    /** `succès`: confirmations. */
    val success: Color,
    /** Veil behind a dialog. */
    val dialogScrim: Color,
    /** Drop shadow of a focused element. */
    val shadow: Color,
    /**
     * Halo around a focused element: a soft light glow that shows the focus on a pure black
     * background, where a shadow cannot (`UI.md` § 3).
     */
    val focusHalo: Color,
)

enum class ThemeVariant {
    /** The default dark theme. */
    Dark,

    /** Pure black background, for OLED TVs. */
    OledBlack,
}

private val Ink = Color(0xFF0B0C0F)

val DarkCineColors =
    CineColors(
        background = Ink,
        surface1 = Color(0xFF15171C),
        surface2 = Color(0xFF1E2128),
        surface3 = Color(0xFF2A2E37),
        text = Color(0xFFF2F3F5),
        text2 = Color(0xFFA9AEB8),
        text3 = Color(0xFF6E7480),
        focus = Color.White.copy(alpha = 0.9f),
        accent = Color(0xFFF2C14E),
        onAccent = Color(0xFF14120B),
        accentPressed = Color(0xFFD9A93A),
        progressTrack = Color.White.copy(alpha = 0.25f),
        danger = Color(0xFFE5484D),
        success = Color(0xFF46A758),
        dialogScrim = Ink.copy(alpha = 0.82f),
        shadow = Color.Black,
        focusHalo = Color.White.copy(alpha = 0.4f),
    )

val OledCineColors = DarkCineColors.copy(background = Color.Black, dialogScrim = Color.Black.copy(alpha = 0.82f))

fun ThemeVariant.colors(): CineColors =
    when (this) {
        ThemeVariant.Dark -> DarkCineColors
        ThemeVariant.OledBlack -> OledCineColors
    }

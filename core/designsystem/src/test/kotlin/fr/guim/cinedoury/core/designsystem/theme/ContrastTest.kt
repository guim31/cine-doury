package fr.guim.cinedoury.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import kotlin.test.Test
import kotlin.test.fail

/**
 * Every pair of text and background the tokens make reaches the contrast of WCAG 2.2 level AA
 * (`FONCTIONNALITES.md` § 13): 4.5:1 for text, 3:1 for large text (24 sp, or 18.7 sp at 600 and
 * more) and for what shows a state without text (the focus outline). In both themes. Text over a
 * picture is checked on the screenshots (`UI.md` § 4); a disabled element is exempt.
 */
class ContrastTest {
    @Test
    fun theTokensReachAa() {
        val failures =
            ThemeVariant.entries.flatMap { variant ->
                pairs(variant.colors()).mapNotNull { pair ->
                    val ratio = contrast(pair.foreground, pair.background)
                    "${variant.name}: ${pair.name} = ${"%.2f".format(ratio)}:1, under ${pair.minimum}:1"
                        .takeIf { ratio < pair.minimum }
                }
            }
        if (failures.isNotEmpty()) fail(failures.joinToString("\n"))
    }

    private class Pair(
        val name: String,
        foreground: Color,
        val background: Color,
        val minimum: Double,
    ) {
        /** A see-through colour as drawn over its background. */
        val foreground = foreground.compositeOver(background)
    }

    private fun pairs(colors: CineColors): List<Pair> {
        val surfaces =
            listOf(
                "fond" to colors.background,
                "surface-1" to colors.surface1,
                "surface-2" to colors.surface2,
            )
        return buildList {
            for ((surface, background) in surfaces) {
                add(Pair("texte on $surface", colors.text, background, TEXT))
                add(Pair("texte-2 on $surface", colors.text2, background, TEXT))
                add(Pair("texte-3 on $surface", colors.text3, background, TEXT))
                // Yellow text: a chosen tab, the selected value of a list.
                add(Pair("accent on $surface", colors.accent, background, TEXT))
                add(Pair("focus outline on $surface", colors.focus, background, NON_TEXT))
            }
            // An error message on the screen, a destructive line of a list.
            add(Pair("danger on fond", colors.danger, colors.background, TEXT))
            add(Pair("danger on surface-1", colors.danger, colors.surface1, TEXT))
            add(Pair("succès on fond", colors.success, colors.background, TEXT))
            // The main button, normal and pressed; a destructive button.
            add(Pair("text on the accent", colors.onAccent, colors.accent, TEXT))
            add(Pair("text on the pressed accent", colors.onAccent, colors.accentPressed, TEXT))
            add(Pair("texte on danger (destructive button)", colors.text, colors.dangerButton, TEXT))
            // A focused line or button turns light, its text dark.
            add(Pair("fond on texte (focused line)", colors.background, colors.text, TEXT))
            // The player's panels and veils, over a white picture at worst.
            val panel = colors.playerPanel.compositeOver(Color.White)
            add(Pair("texte on a panel of the player", colors.text, panel, TEXT))
            add(Pair("texte-2 on a panel of the player", colors.text2, panel, TEXT))
            val veil = colors.playerVeil.compositeOver(Color.White)
            add(Pair("texte on the veil of the player", colors.text, veil, TEXT))
            // The ambient backdrop dims the brightest picture by `fond` at 60 %.
            val ambient = colors.background.copy(alpha = CineAmbient.DIM_ALPHA).compositeOver(Color.White)
            add(Pair("texte on the ambient backdrop", colors.text, ambient, TEXT))
        }
    }

    /** The contrast ratio of WCAG 2.2, from the relative luminance of each colour. */
    private fun contrast(
        a: Color,
        b: Color,
    ): Double {
        val la = a.luminance().toDouble()
        val lb = b.luminance().toDouble()
        return (maxOf(la, lb) + OFFSET) / (minOf(la, lb) + OFFSET)
    }

    private companion object {
        const val TEXT = 4.5
        const val NON_TEXT = 3.0
        const val OFFSET = 0.05
    }
}

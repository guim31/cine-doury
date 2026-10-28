package fr.guim.cinedoury.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.LocalTextStyle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

private val LocalCineColors = staticCompositionLocalOf { DarkCineColors }
private val LocalCineTypography = staticCompositionLocalOf { TvCineTypography }
private val LocalReducedMotion = staticCompositionLocalOf { false }
private val LocalCardPreferences = staticCompositionLocalOf { CardPreferences() }

/**
 * What the cards show, for every screen at once (J7): [alwaysShowTitles], the title and year under
 * every poster (not only the focused one, `UI.md` § 6); [unwatchedMarks], the « non vu » triangle and
 * the count of unwatched episodes.
 */
@Immutable
data class CardPreferences(
    val alwaysShowTitles: Boolean = false,
    val unwatchedMarks: Boolean = true,
)

/**
 * The Ciné Doury theme. Also feeds the Compose for TV [MaterialTheme], so its components follow
 * the same tokens.
 *
 * @param reducedMotion fades only (`UI.md` § 7): set from the settings or from the system.
 * @param textScale the size of the text against the scale of `UI.md` § 5 (« Taille du texte », J7).
 * @param cards what every card shows (« Accueil et navigation », J7).
 * @param tvLayout lays the content out on the screen of the design, whatever the screen
 * ([TvLayoutDensity]).
 * @param layoutSize the width of that screen (« Taille de l'interface », essai réel 3).
 */
@Composable
fun CineTheme(
    variant: ThemeVariant = ThemeVariant.Dark,
    reducedMotion: Boolean = false,
    textScale: Float = 1f,
    cards: CardPreferences = CardPreferences(),
    tvLayout: Boolean = true,
    layoutSize: LayoutSize = LayoutSize.Default,
    content: @Composable () -> Unit,
) {
    if (tvLayout) {
        // A very compact interface keeps its text a little larger than the rest (LayoutSize.textScale).
        TvLayoutDensity(layoutSize) {
            CineThemeContent(variant, reducedMotion, textScale * layoutSize.textScale, cards, content)
        }
    } else {
        CineThemeContent(variant, reducedMotion, textScale, cards, content)
    }
}

@Composable
private fun CineThemeContent(
    variant: ThemeVariant,
    reducedMotion: Boolean,
    textScale: Float,
    cards: CardPreferences,
    content: @Composable () -> Unit,
) {
    val colors = variant.colors()
    val typography = if (textScale == 1f) TvCineTypography else TvCineTypography.scaled(textScale)
    val scheme =
        darkColorScheme(
            primary = colors.accent,
            onPrimary = colors.onAccent,
            background = colors.background,
            onBackground = colors.text,
            surface = colors.surface1,
            onSurface = colors.text,
            surfaceVariant = colors.surface2,
            onSurfaceVariant = colors.text2,
            error = colors.danger,
            border = colors.focus,
        )
    CompositionLocalProvider(
        LocalCineColors provides colors,
        LocalCineTypography provides typography,
        LocalReducedMotion provides reducedMotion,
        LocalCardPreferences provides cards,
    ) {
        MaterialTheme(colorScheme = scheme) {
            CompositionLocalProvider(
                LocalContentColor provides colors.text,
                LocalTextStyle provides typography.body,
                content = content,
            )
        }
    }
}

object CineTheme {
    val colors: CineColors
        @Composable @ReadOnlyComposable
        get() = LocalCineColors.current

    val typography: CineTypography
        @Composable @ReadOnlyComposable
        get() = LocalCineTypography.current

    val reducedMotion: Boolean
        @Composable @ReadOnlyComposable
        get() = LocalReducedMotion.current

    val cards: CardPreferences
        @Composable @ReadOnlyComposable
        get() = LocalCardPreferences.current
}

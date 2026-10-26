package fr.guim.cinedoury.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.LocalTextStyle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

private val LocalCineColors = staticCompositionLocalOf { DarkCineColors }
private val LocalCineTypography = staticCompositionLocalOf { TvCineTypography }
private val LocalReducedMotion = staticCompositionLocalOf { false }

/**
 * The Ciné Doury theme. Also feeds the Compose for TV [MaterialTheme], so its components follow
 * the same tokens.
 *
 * @param reducedMotion fades only (`UI.md` § 7): set from the settings or from the system.
 * @param tvLayout lays the content out on the 960 × 540 dp of the design, whatever the screen
 * ([TvLayoutDensity]).
 */
@Composable
fun CineTheme(
    variant: ThemeVariant = ThemeVariant.Dark,
    reducedMotion: Boolean = false,
    tvLayout: Boolean = true,
    content: @Composable () -> Unit,
) {
    if (tvLayout) {
        TvLayoutDensity { CineThemeContent(variant, reducedMotion, content) }
    } else {
        CineThemeContent(variant, reducedMotion, content)
    }
}

@Composable
private fun CineThemeContent(
    variant: ThemeVariant,
    reducedMotion: Boolean,
    content: @Composable () -> Unit,
) {
    val colors = variant.colors()
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
        LocalCineTypography provides TvCineTypography,
        LocalReducedMotion provides reducedMotion,
    ) {
        MaterialTheme(colorScheme = scheme) {
            CompositionLocalProvider(
                LocalContentColor provides colors.text,
                LocalTextStyle provides TvCineTypography.body,
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
}

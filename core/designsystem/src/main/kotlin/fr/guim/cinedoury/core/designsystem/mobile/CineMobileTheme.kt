package fr.guim.cinedoury.core.designsystem.mobile

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import fr.guim.cinedoury.core.designsystem.theme.CardPreferences
import fr.guim.cinedoury.core.designsystem.theme.CineColors
import fr.guim.cinedoury.core.designsystem.theme.CineTypography
import fr.guim.cinedoury.core.designsystem.theme.MobileCineTypography
import fr.guim.cinedoury.core.designsystem.theme.ProvideCineTokens
import fr.guim.cinedoury.core.designsystem.theme.ThemeVariant
import fr.guim.cinedoury.core.designsystem.theme.colors
import fr.guim.cinedoury.core.designsystem.theme.scaled

/**
 * The Ciné Doury theme of the phone and the tablet (M1, `SPEC.md` § 4.26): the tokens of the TV
 * ([fr.guim.cinedoury.core.designsystem.theme.CineTheme] reads the same colours, motion and card
 * preferences), the type scale of the hand ([MobileCineTypography]), and the Material 3 theme of
 * `androidx.compose.material3` built from them, so that its components wear the identity of the app.
 * Never the density of the TV's layout: a phone lays its screens out on its own dp.
 */
@Composable
fun CineMobileTheme(
    variant: ThemeVariant = ThemeVariant.Dark,
    reducedMotion: Boolean = false,
    textScale: Float = 1f,
    cards: CardPreferences = CardPreferences(alwaysShowTitles = true),
    content: @Composable () -> Unit,
) {
    val colors = variant.colors()
    val typography = if (textScale == 1f) MobileCineTypography else MobileCineTypography.scaled(textScale)
    val scheme = remember(colors) { colors.materialScheme() }
    val material = remember(typography) { typography.material() }
    ProvideCineTokens(colors, typography, reducedMotion, cards) {
        MaterialTheme(colorScheme = scheme, typography = material) {
            CompositionLocalProvider(
                LocalContentColor provides colors.text,
                LocalTextStyle provides typography.body,
                content = content,
            )
        }
    }
}

/** The Material colours from the tokens: the accent for what is primary, the surfaces for the rest. */
internal fun CineColors.materialScheme() =
    darkColorScheme(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = surface3,
        onPrimaryContainer = text,
        secondary = text2,
        onSecondary = background,
        secondaryContainer = surface3,
        onSecondaryContainer = text,
        background = background,
        onBackground = text,
        surface = background,
        onSurface = text,
        surfaceVariant = surface2,
        onSurfaceVariant = text2,
        surfaceContainerLowest = background,
        surfaceContainerLow = surface1,
        surfaceContainer = surface1,
        surfaceContainerHigh = surface2,
        surfaceContainerHighest = surface3,
        inverseSurface = text,
        inverseOnSurface = background,
        outline = text3,
        outlineVariant = surface3,
        error = danger,
        onError = text,
        scrim = dialogScrim,
    )

/** The Material type roles from the scale of the app. */
internal fun CineTypography.material() =
    Typography(
        displayLarge = display,
        displayMedium = display,
        displaySmall = display,
        headlineLarge = display,
        headlineMedium = title1,
        headlineSmall = title1,
        titleLarge = title1,
        titleMedium = title2,
        titleSmall = label,
        bodyLarge = body,
        bodyMedium = body,
        bodySmall = caption,
        labelLarge = label,
        labelMedium = caption,
        labelSmall = minimum,
    )

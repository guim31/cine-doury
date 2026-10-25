package fr.guim.cinedoury.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import fr.guim.cinedoury.core.designsystem.theme.CineScale
import fr.guim.cinedoury.core.designsystem.theme.CineSizes
import fr.guim.cinedoury.core.designsystem.theme.CineSpacing
import fr.guim.cinedoury.core.designsystem.theme.CineTheme
import fr.guim.cinedoury.core.designsystem.theme.tabular

/** One key of the on-screen PIN pad: a digit, or an icon (erase). */
@Composable
fun KeypadKey(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    digit: Char? = null,
    icon: ImageVector? = null,
    contentDescription: String? = null,
    focusRequester: FocusRequester? = null,
    focus: CineFocus = rememberCineFocus(),
) {
    val colors = CineTheme.colors
    val focused = focus.isFocused
    val content = if (focused) colors.background else colors.text
    Box(
        modifier =
            modifier
                .size(CineSizes.keypadKey)
                .withFocusRequester(focusRequester)
                .cineFocusable(focus, CircleShape, CineScale.BUTTON, onClick)
                .background(if (focused) colors.text else colors.surface2),
        contentAlignment = Alignment.Center,
    ) {
        when {
            digit != null -> {
                Text(text = digit.toString(), style = CineTheme.typography.title2.tabular(), color = content)
            }

            icon != null -> {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = content,
                    modifier = Modifier.size(CineSizes.icon),
                )
            }
        }
    }
}

/** The [length] places of a PIN, the first [filled] of them filled. Digits are never shown. */
@Composable
fun PinDots(
    filled: Int,
    modifier: Modifier = Modifier,
    length: Int = PIN_LENGTH,
    error: Boolean = false,
) {
    val colors = CineTheme.colors
    val tint = if (error) colors.danger else colors.text
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(CineSpacing.m)) {
        repeat(length) { index ->
            val dot = Modifier.size(CineSizes.pinDot)
            Box(
                if (index < filled) {
                    dot.background(tint, CircleShape)
                } else {
                    dot.border(CineSizes.focusOutlineGap, if (error) colors.danger else colors.text3, CircleShape)
                },
            )
        }
    }
}

/** Length of a profile PIN (`FONCTIONNALITES.md` § 1). */
const val PIN_LENGTH = 4

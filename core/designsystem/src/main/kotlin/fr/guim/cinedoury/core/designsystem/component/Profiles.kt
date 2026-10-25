package fr.guim.cinedoury.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import fr.guim.cinedoury.core.designsystem.theme.CineScale
import fr.guim.cinedoury.core.designsystem.theme.CineSizes
import fr.guim.cinedoury.core.designsystem.theme.CineSpacing
import fr.guim.cinedoury.core.designsystem.theme.CineTheme

/**
 * A profile picture: the user's image, or the initial of [name] on a disc coloured from the name
 * (always the same colour for the same name).
 */
@Composable
fun ProfileAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = CineSizes.avatarLarge,
    image: (@Composable () -> Unit)? = null,
) {
    val (top, bottom) = remember(name) { artworkColors(name) }
    Box(
        modifier =
            modifier
                .size(size)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(top, bottom))),
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) {
            image()
        } else {
            val typography = CineTheme.typography
            Text(
                text = name.trim().take(1).uppercase(),
                style = if (size >= CineSizes.avatarMedium) typography.display else typography.label,
                color = CineTheme.colors.text,
            )
        }
    }
}

/**
 * A profile in the profile picker: a round avatar that takes the poster focus, the name under it,
 * and a padlock when the profile has a PIN.
 *
 * @param icon replaces the avatar (« Autre compte »).
 */
@Composable
fun ProfileTile(
    name: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    locked: Boolean = false,
    caption: String? = null,
    icon: ImageVector? = null,
    focusRequester: FocusRequester? = null,
    focus: CineFocus = rememberCineFocus(),
    image: (@Composable () -> Unit)? = null,
) {
    val colors = CineTheme.colors
    val typography = CineTheme.typography
    Column(
        modifier = modifier.width(CineSizes.avatarLarge + CineSpacing.l),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier =
                Modifier
                    .size(CineSizes.avatarLarge)
                    .withFocusRequester(focusRequester)
                    .cineFocusable(focus, CircleShape, CineScale.POSTER, onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) {
                Box(
                    Modifier
                        .size(CineSizes.avatarLarge)
                        .background(if (focus.isFocused) colors.text else colors.surface2),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (focus.isFocused) colors.background else colors.text,
                        modifier = Modifier.size(CineSizes.icon * 2),
                    )
                }
            } else {
                ProfileAvatar(name = name, image = image)
            }
        }
        Row(
            modifier = Modifier.padding(top = CineSpacing.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CineSpacing.xs),
        ) {
            if (locked) {
                Icon(
                    imageVector = CineIcons.Lock,
                    contentDescription = null,
                    tint = colors.text2,
                    modifier = Modifier.size(CineSizes.icon * 3 / 4),
                )
            }
            Text(
                text = name,
                style = typography.label,
                color = if (focus.isFocused) colors.text else colors.text2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
        Text(
            text = caption.orEmpty(),
            style = typography.caption,
            color = colors.text3,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

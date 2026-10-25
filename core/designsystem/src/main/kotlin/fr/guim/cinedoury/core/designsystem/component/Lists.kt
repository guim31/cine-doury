package fr.guim.cinedoury.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import fr.guim.cinedoury.core.designsystem.theme.CineScale
import fr.guim.cinedoury.core.designsystem.theme.CineSizes
import fr.guim.cinedoury.core.designsystem.theme.CineSpacing
import fr.guim.cinedoury.core.designsystem.theme.CineTheme

private val RowShape = RoundedCornerShape(CineSizes.cardCorner)
private val RowOutlineShape =
    RoundedCornerShape(CineSizes.cardCorner + CineSizes.focusOutlineGap + CineSizes.focusOutlineWidth / 2)

/**
 * A row of a list (servers found, settings): a title, a line of explanation under it, a value at
 * the end. It turns light with the focus. [destructive] writes the title in `danger`.
 */
@Composable
fun ListRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    value: String? = null,
    icon: ImageVector? = null,
    destructive: Boolean = false,
    trailingIcon: ImageVector? = null,
    focusRequester: FocusRequester? = null,
    focus: CineFocus = rememberCineFocus(),
) {
    val colors = CineTheme.colors
    val typography = CineTheme.typography
    val focused = focus.isFocused
    val primary =
        when {
            focused -> colors.background
            destructive -> colors.danger
            else -> colors.text
        }
    val secondary = if (focused) colors.background else colors.text2
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = CineSizes.settingsRowHeight)
                .withFocusRequester(focusRequester)
                .cineFocusable(focus, RowShape, CineScale.FIELD, onClick, RowOutlineShape)
                .background(if (focused) colors.text else colors.surface1)
                .padding(horizontal = CineSpacing.m, vertical = CineSpacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CineSpacing.s),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = secondary,
                modifier = Modifier.size(CineSizes.icon),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = typography.label,
                color = primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (description != null) {
                Text(
                    text = description,
                    style = typography.caption,
                    color = secondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (value != null) {
            Text(
                text = value,
                style = typography.body,
                color = secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (trailingIcon != null) {
            Icon(
                imageVector = trailingIcon,
                contentDescription = null,
                tint = if (focused) colors.background else colors.accent,
                modifier = Modifier.size(CineSizes.icon),
            )
        }
    }
}

/** Title of a group of rows. */
@Composable
fun ListSectionTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = CineTheme.typography.title2,
        color = CineTheme.colors.text,
        modifier = modifier.padding(top = CineSpacing.m, bottom = CineSpacing.xs),
    )
}

/**
 * A confirmation over the screen (`UI.md` § 8, « Action destructrice »): a `surface-2` box that
 * says exactly what will happen, « Annuler » with the initial focus, the action in `danger`.
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    cancelLabel: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    cancelFocusRequester: FocusRequester? = null,
    cancelFocus: CineFocus = rememberCineFocus(),
    confirmFocus: CineFocus = rememberCineFocus(),
) {
    val colors = CineTheme.colors
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(colors.dialogScrim),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier =
                Modifier
                    .width(CineSizes.dialogWidth)
                    .background(colors.surface2, RowShape)
                    .padding(CineSpacing.l),
            verticalArrangement = Arrangement.spacedBy(CineSpacing.s),
        ) {
            Text(text = title, style = CineTheme.typography.title1, color = colors.text)
            Text(text = message, style = CineTheme.typography.body, color = colors.text2)
            Row(
                modifier = Modifier.padding(top = CineSpacing.m),
                horizontalArrangement = Arrangement.spacedBy(CineSpacing.m),
            ) {
                SecondaryButton(
                    text = cancelLabel,
                    onClick = onCancel,
                    focusRequester = cancelFocusRequester,
                    focus = cancelFocus,
                )
                PrimaryButton(text = confirmLabel, onClick = onConfirm, destructive = true, focus = confirmFocus)
            }
        }
    }
}

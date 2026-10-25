package fr.guim.cinedoury.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import fr.guim.cinedoury.core.designsystem.theme.CineSizes
import fr.guim.cinedoury.core.designsystem.theme.CineSpacing
import fr.guim.cinedoury.core.designsystem.theme.CineTheme

/**
 * A readable error (`UI.md` § 10): what happened ([title]), what to do ([message]), the action
 * that fixes it (« Réessayer ») with the focus, and the technical detail behind « Plus d'infos ».
 * Never a silent black screen.
 */
@Composable
fun MessagePanel(
    title: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = CineIcons.Warning,
    secondaryLabel: String? = null,
    onSecondary: () -> Unit = {},
    detailsLabel: String? = null,
    details: String? = null,
    showDetails: Boolean = false,
    actionFocusRequester: FocusRequester? = null,
    actionFocus: CineFocus = rememberCineFocus(),
) {
    val colors = CineTheme.colors
    var detailsShown by rememberSaveable { mutableStateOf(showDetails) }
    Column(
        modifier = modifier.width(CineSizes.formWidth),
        verticalArrangement = Arrangement.spacedBy(CineSpacing.s),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.text2,
            modifier =
                Modifier.size(CineSizes.icon * 2),
        )
        Text(text = title, style = CineTheme.typography.title1, color = colors.text)
        Text(text = message, style = CineTheme.typography.body, color = colors.text2)
        Row(
            modifier = Modifier.padding(top = CineSpacing.m),
            horizontalArrangement = Arrangement.spacedBy(CineSpacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PrimaryButton(
                text = actionLabel,
                onClick = onAction,
                icon = CineIcons.Retry,
                focusRequester = actionFocusRequester,
                focus = actionFocus,
            )
            if (secondaryLabel != null) SecondaryButton(text = secondaryLabel, onClick = onSecondary)
            if (detailsLabel != null && details != null) {
                SecondaryButton(text = detailsLabel, icon = CineIcons.Info, onClick = { detailsShown = !detailsShown })
            }
        }
        AnimatedVisibility(visible = detailsShown && details != null) {
            Text(
                text = details.orEmpty(),
                style = CineTheme.typography.caption,
                color = colors.text3,
                modifier =
                    Modifier
                        .padding(top = CineSpacing.s)
                        .background(colors.surface1, RoundedCornerShape(CineSizes.cardCorner))
                        .padding(CineSpacing.s),
            )
        }
    }
}

/** A discreet status in a pill (« À distance »), never focusable. */
@Composable
fun StatusPill(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val colors = CineTheme.colors
    Row(
        modifier =
            modifier
                .background(colors.surface2, RoundedCornerShape(percent = 50))
                .padding(horizontal = CineSpacing.s, vertical = CineSpacing.xs / 2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CineSpacing.xs),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.text2,
                modifier =
                    Modifier.size(CineSizes.icon * 3 / 4),
            )
        }
        Text(text = text, style = CineTheme.typography.caption, color = colors.text2, maxLines = 1)
    }
}

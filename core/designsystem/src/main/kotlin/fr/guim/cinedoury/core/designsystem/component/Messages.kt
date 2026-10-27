package fr.guim.cinedoury.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import fr.guim.cinedoury.core.designsystem.theme.CineScale
import fr.guim.cinedoury.core.designsystem.theme.CineSizes
import fr.guim.cinedoury.core.designsystem.theme.CineSpacing
import fr.guim.cinedoury.core.designsystem.theme.CineTheme

/**
 * A readable error (`UI.md` § 10): what happened ([title]), what to do ([message]), the action
 * that fixes it (« Réessayer ») with the focus, and the technical detail behind « Plus d'infos ».
 * Never a silent black screen.
 *
 * The detail opens under the buttons and takes the focus, which brings it into view in a scrolling
 * parent ([FormScreen] scrolls); the up key goes back to the buttons.
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
    detailsFocus: CineFocus = rememberCineFocus(),
) {
    val colors = CineTheme.colors
    var detailsShown by rememberSaveable { mutableStateOf(showDetails) }
    val detailsRequester = remember { FocusRequester() }
    // Opened by « Plus d'infos » (not when shown from the start): the detail takes the focus.
    var focusDetails by remember { mutableStateOf(false) }
    // Only the text keeps to the reading width: the buttons take what they need.
    val reading = Modifier.width(CineSizes.formWidth)
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(CineSpacing.s),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.text2,
            modifier =
                Modifier.size(CineSizes.icon * 2),
        )
        Text(text = title, style = CineTheme.typography.title1, color = colors.text, modifier = reading.asHeading())
        Text(text = message, style = CineTheme.typography.body, color = colors.text2, modifier = reading)
        Row(
            // Room for the scale and the halo of the focused button.
            modifier = Modifier.padding(top = CineSpacing.m, bottom = CineSpacing.xs),
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
                SecondaryButton(
                    text = detailsLabel,
                    icon = CineIcons.Info,
                    onClick = {
                        detailsShown = !detailsShown
                        focusDetails = detailsShown
                    },
                    modifier = Modifier.testTag(MESSAGE_DETAILS_BUTTON),
                )
            }
        }
        if (detailsShown && details != null) {
            MessageDetails(details, detailsRequester, detailsFocus, reading)
            if (focusDetails) {
                LaunchedEffect(Unit) {
                    withFrameNanos { }
                    // The focus brings the detail into view: the panel scrolls to it.
                    runCatching { detailsRequester.requestFocus() }
                    focusDetails = false
                }
            }
        }
    }
}

/** The technical detail of a [MessagePanel], focusable so that the D-pad can reach and scroll it. */
@Composable
private fun MessageDetails(
    details: String,
    requester: FocusRequester,
    focus: CineFocus,
    modifier: Modifier,
) {
    val colors = CineTheme.colors
    val shape = RoundedCornerShape(CineSizes.cardCorner)
    val inView = remember { BringIntoViewRequester() }
    // Opened under the fold of a scrolling parent: brought on screen at once, focus or not.
    LaunchedEffect(Unit) {
        withFrameNanos { }
        inView.bringIntoView()
    }
    Text(
        text = details,
        style = CineTheme.typography.caption,
        color = colors.text2,
        modifier =
            modifier
                .bringIntoViewRequester(inView)
                .padding(top = CineSpacing.s)
                .cineFocusDecoration(focus, shape, CineScale.FIELD)
                .background(if (focus.isFocused) colors.surface2 else colors.surface1)
                .padding(CineSpacing.s)
                .focusRequester(requester)
                .onFocusChanged { focus.update(it.isFocused) }
                .focusable()
                .testTag(MESSAGE_DETAILS),
    )
}

/** Test tag of the « Plus d'infos » button of a [MessagePanel]. */
const val MESSAGE_DETAILS_BUTTON = "message-details-button"

/** Test tag of the detail of a [MessagePanel]. */
const val MESSAGE_DETAILS = "message-details"

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

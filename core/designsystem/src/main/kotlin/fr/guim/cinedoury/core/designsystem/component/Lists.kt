package fr.guim.cinedoury.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import fr.guim.cinedoury.core.designsystem.theme.CineScale
import fr.guim.cinedoury.core.designsystem.theme.CineSizes
import fr.guim.cinedoury.core.designsystem.theme.CineSpacing
import fr.guim.cinedoury.core.designsystem.theme.CineTheme
import fr.guim.cinedoury.core.designsystem.theme.tabular

private val RowShape = RoundedCornerShape(CineSizes.cardCorner)
private val RowOutlineShape =
    RoundedCornerShape(CineSizes.cardCorner + CineSizes.focusOutlineGap + CineSizes.focusOutlineWidth / 2)

/**
 * A row of a list (servers found, settings): a title, a line of explanation under it, a value at
 * the end. It turns light with the focus. [destructive] writes the title in `danger`.
 *
 * @param pills short attributes after the title (« par défaut », « forcés », « SDH »).
 * @param wrapTitle the title goes on as many lines as it needs, never cut (the tracks).
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
    pills: List<String> = emptyList(),
    wrapTitle: Boolean = false,
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
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(CineSpacing.xs),
                verticalArrangement = Arrangement.spacedBy(CineSpacing.xs / 2),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = typography.label,
                    color = primary,
                    maxLines = if (wrapTitle) Int.MAX_VALUE else 1,
                    overflow = if (wrapTitle) TextOverflow.Clip else TextOverflow.Ellipsis,
                )
                pills.forEach { TrackPill(it, inverted = focused) }
            }
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

/**
 * A row whose value goes down with ← and up with → (− and + at its sides, dimmed at the ends), at
 * one step per press and a few per second while the key is held; OK does nothing more. The size of
 * the subtitles (`UI.md` § 8).
 */
@Composable
fun StepperRow(
    title: String,
    value: String,
    onStep: (forward: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    canDecrease: Boolean = true,
    canIncrease: Boolean = true,
    icon: ImageVector? = null,
    focusRequester: FocusRequester? = null,
    focus: CineFocus = rememberCineFocus(),
) {
    val colors = CineTheme.colors
    val focused = focus.isFocused
    val primary = if (focused) colors.background else colors.text
    val secondary = if (focused) colors.background else colors.text2
    val dimmed = if (focused) colors.text2 else colors.text3
    Row(
        modifier =
            modifier
                .heightIn(min = CineSizes.settingsRowHeight)
                .withFocusRequester(focusRequester)
                .onPreviewKeyEvent { event ->
                    val forward =
                        when (event.key) {
                            Key.DirectionLeft -> false
                            Key.DirectionRight -> true
                            else -> return@onPreviewKeyEvent false
                        }
                    if (event.type == KeyEventType.KeyDown &&
                        event.nativeKeyEvent.repeatCount % STEP_EVERY_REPEAT == 0
                    ) {
                        onStep(forward)
                    }
                    true
                }.cineFocusable(focus, RowShape, CineScale.FIELD, {}, RowOutlineShape)
                .background(if (focused) colors.text else colors.surface1)
                .padding(horizontal = CineSpacing.m, vertical = CineSpacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CineSpacing.s),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = secondary, modifier = Modifier.size(CineSizes.icon))
        }
        Text(text = title, style = CineTheme.typography.label, color = primary, maxLines = 1)
        Icon(
            CineIcons.Remove,
            contentDescription = null,
            tint = if (canDecrease) secondary else dimmed,
            modifier = Modifier.size(CineSizes.icon),
        )
        Text(
            text = value,
            style = CineTheme.typography.body.tabular(),
            color = primary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.widthIn(min = CineSizes.stepperValueWidth),
        )
        Icon(
            CineIcons.Add,
            contentDescription = null,
            tint = if (canIncrease) secondary else dimmed,
            modifier = Modifier.size(CineSizes.icon),
        )
    }
}

/** A key held down repeats about twenty times a second: one step out of four of them. */
private const val STEP_EVERY_REPEAT = 4

/**
 * A short attribute in a pill (« par défaut », « forcés », « SDH »), after the title of a row: on
 * `surface-2`, and dark on a focused (light) row.
 */
@Composable
fun TrackPill(
    text: String,
    modifier: Modifier = Modifier,
    inverted: Boolean = false,
) {
    val colors = CineTheme.colors
    Text(
        text = text,
        style = CineTheme.typography.minimum,
        color = if (inverted) colors.text else colors.text2,
        maxLines = 1,
        modifier =
            modifier
                .background(if (inverted) colors.background else colors.surface2, PillShape)
                .padding(horizontal = CineSpacing.xs, vertical = CineSizes.pillPadding),
    )
}

private val PillShape = RoundedCornerShape(percent = 50)

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
 * A message over the screen, with one button that closes it (a failure in two sentences): the same
 * `surface-2` box as [ConfirmDialog].
 */
@Composable
fun MessageDialog(
    title: String,
    message: String,
    buttonLabel: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    buttonFocusRequester: FocusRequester? = null,
    buttonFocus: CineFocus = rememberCineFocus(),
) {
    val colors = CineTheme.colors
    // While a button closes the box, the focus may leave it: the caller gives the focus back to the
    // element the box came from before the box goes away.
    val closing = remember { BooleanArray(1) }
    val close: (() -> Unit) -> Unit = { action ->
        closing[0] = true
        try {
            action()
        } finally {
            closing[0] = false
        }
    }
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
            Row(modifier = Modifier.padding(top = CineSpacing.m)) {
                PrimaryButton(
                    text = buttonLabel,
                    onClick = onDismiss,
                    focusRequester = buttonFocusRequester,
                    focus = buttonFocus,
                )
            }
        }
    }
}

/**
 * A confirmation over the screen (`UI.md` § 8, « Action destructrice »): a `surface-2` box that
 * says exactly what will happen, « Annuler » with the initial focus, the action in `danger` (in the
 * accent when it destroys nothing: « Quitter »).
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
    destructive: Boolean = true,
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
                    // The focus stays in the box: the screen behind it is out of reach.
                    .focusProperties { onExit = { if (!closing[0]) cancelFocusChange() } }
                    .focusGroup()
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
                    onClick = { close(onCancel) },
                    focusRequester = cancelFocusRequester,
                    focus = cancelFocus,
                )
                PrimaryButton(
                    text = confirmLabel,
                    onClick = { close(onConfirm) },
                    destructive = destructive,
                    focus = confirmFocus,
                )
            }
        }
    }
}

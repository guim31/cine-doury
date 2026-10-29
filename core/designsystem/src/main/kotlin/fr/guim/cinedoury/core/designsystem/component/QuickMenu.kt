package fr.guim.cinedoury.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Text
import fr.guim.cinedoury.core.designsystem.theme.CineSizes
import fr.guim.cinedoury.core.designsystem.theme.CineSpacing
import fr.guim.cinedoury.core.designsystem.theme.CineTheme

/** A line of [QuickMenu]: what it says, its icon, its test tag, and what it does. */
@Immutable
data class QuickMenuLine(
    val label: String,
    val icon: ImageVector?,
    val tag: String,
    val onClick: () -> Unit,
)

/**
 * The menu of a card, opened by OK held (`UI.md` § 2, essai réel 5): a panel on the right of the
 * screen, which stays visible behind its veil, with the title of the card and its options. The focus
 * stays in it until an option or « Retour » closes it (the caller gives the focus back to the card,
 * [QuickMenuLine.onClick] and [onDismiss] run while it may leave). OK still held from the press that
 * opened it is not taken: only a new press chooses an option.
 *
 * @param previewFocusedIndex draws that line as focused, for screenshots ([CineFocus.preview]).
 */
@Composable
fun QuickMenu(
    title: String,
    subtitle: String?,
    lines: List<QuickMenuLine>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    previewFocusedIndex: Int? = null,
) {
    val colors = CineTheme.colors
    val first = remember { FocusRequester() }
    // While an option closes the menu, the focus leaves it for the card.
    val closing = remember { BooleanArray(1) }
    // OK held since the card: its repeats and its release belong to the long press.
    val armed = remember { BooleanArray(1) }
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
                .background(colors.dialogScrim)
                .testTag(QUICK_MENU),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Column(
            modifier =
                Modifier
                    .width(CineSizes.panelWidth)
                    .fillMaxHeight()
                    .background(colors.surface2)
                    .onPreviewKeyEvent { event ->
                        when {
                            // « Retour » closes the menu, and goes no further (the screen would leave).
                            event.key == Key.Back -> {
                                if (event.type == KeyEventType.KeyUp) close(onDismiss)
                                true
                            }

                            event.key !in MENU_SELECT_KEYS || armed[0] -> {
                                false
                            }

                            event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0 -> {
                                armed[0] = true
                                false
                            }

                            else -> {
                                true
                            }
                        }
                    }
                    // The focus stays in the menu: the screen behind it is out of reach.
                    .focusProperties { onExit = { if (!closing[0]) cancelFocusChange() } }
                    .focusGroup()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = CineSpacing.m, vertical = CineSpacing.safeVertical + CineSpacing.s),
            verticalArrangement = Arrangement.spacedBy(CineSpacing.xs),
        ) {
            Text(
                text = title,
                style = CineTheme.typography.title2,
                color = colors.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.asHeading(),
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = CineTheme.typography.caption,
                    color = colors.text2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(Modifier.padding(top = CineSpacing.s))
            lines.forEachIndexed { index, line ->
                ListRow(
                    title = line.label,
                    onClick = { close(line.onClick) },
                    icon = line.icon,
                    focusRequester = if (index == 0) first else null,
                    focus = if (index == previewFocusedIndex) remember { CineFocus.preview() } else rememberCineFocus(),
                    modifier = Modifier.testTag(line.tag),
                )
            }
        }
    }
    InitialFocus(first)
}

/** The test tag of the menu of a card. */
const val QUICK_MENU = "quick-menu"

private val MENU_SELECT_KEYS = setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)

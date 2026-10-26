package fr.guim.cinedoury.core.designsystem.component

import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicSecureTextField
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.tv.material3.Text
import fr.guim.cinedoury.core.designsystem.theme.CineScale
import fr.guim.cinedoury.core.designsystem.theme.CineSizes
import fr.guim.cinedoury.core.designsystem.theme.CineSpacing
import fr.guim.cinedoury.core.designsystem.theme.CineTheme

private val FieldShape = RoundedCornerShape(CineSizes.cardCorner)
private val FieldOutlineShape =
    RoundedCornerShape(CineSizes.cardCorner + CineSizes.focusOutlineGap + CineSizes.focusOutlineWidth / 2)

/**
 * A one-line text field for the remote: its label above, the value on `surface-1`, the focus
 * treatment of `UI.md` § 3 when it holds the focus.
 *
 * Made for the D-pad, with no focus trap (`UI.md` § 2; CLAUDE.md, « Pièges »). The focus first
 * lands on the frame of the field, where the keys of the remote move on as anywhere else; OK, on
 * its release, starts the typing (the keyboard opens, and never receives that release, which would
 * type the key under its own focus). While typing:
 * - up and down leave the field, left and right leave it at either end of the text;
 * - the keyboard action (« Suivant », « OK ») runs [onImeAction], or by default moves on to the
 *   field below (`ImeAction.Next`) or ends the typing; Enter on a keyboard does the same.
 *
 * @param secret masks the value (a password), unless the caller shows it.
 */
@Composable
fun CineTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    secret: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: (() -> Unit)? = null,
    width: Dp = CineSizes.textFieldWidth,
    focusRequester: FocusRequester? = null,
    focus: CineFocus = rememberCineFocus(),
) {
    val colors = CineTheme.colors
    val typography = CineTheme.typography
    val state = rememberTextFieldState(value)
    val currentValue by rememberUpdatedState(value)
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    // The caller owns the value: its changes come in (a refused password is emptied)...
    LaunchedEffect(value) {
        if (state.text.toString() != value) state.setTextAndPlaceCursorAtEnd(value)
    }
    // ...and what is typed goes out.
    LaunchedEffect(state) {
        snapshotFlow { state.text.toString() }.collect { if (it != currentValue) currentOnValueChange(it) }
    }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val view = LocalView.current
    // The keyboard closes with the typing: our keyboard action replaces Compose's, which hid it
    // for « Terminé ». Android 14 closes it when the focus leaves the field; Android 11 (the
    // Shield) leaves it open over the next screen, where it takes the OK of the remote, and may
    // ignore a hide through the window insets just after the keyboard opened: the input method
    // manager is asked too, in order with the show (essai réel 2).
    val hideKeyboard = {
        keyboard?.hide()
        view.context
            .getSystemService(InputMethodManager::class.java)
            ?.hideSoftInputFromWindow(view.windowToken, 0)
    }
    val frame = remember { FocusRequester() }
    val input = remember { FocusRequester() }
    var typing by remember { mutableStateOf(false) }
    LaunchedEffect(typing) {
        if (typing) {
            // The input becomes focusable at the next frame; its focus opens the keyboard.
            withFrameNanos { }
            runCatching { input.requestFocus() }
        }
    }
    val currentOnImeAction by rememberUpdatedState(onImeAction)
    val runImeAction: () -> Unit = {
        // Before the focus moves on, while the field is still the one the keyboard serves.
        hideKeyboard()
        val action = currentOnImeAction
        when {
            action != null -> {
                runCatching { frame.requestFocus() }
                action()
            }

            imeAction == ImeAction.Next -> {
                focusManager.moveFocus(FocusDirection.Down)
            }

            else -> {
                runCatching { frame.requestFocus() }
            }
        }
    }
    val options =
        KeyboardOptions(
            keyboardType = if (secret) KeyboardType.Password else keyboardType,
            imeAction = imeAction,
            autoCorrectEnabled = false,
        )
    val inputModifier =
        Modifier
            .fillMaxWidth()
            .focusRequester(input)
            .focusProperties { canFocus = typing }
            .onFocusChanged {
                if (!it.isFocused && typing) {
                    typing = false
                    hideKeyboard()
                }
            }.onPreviewKeyEvent { event -> typingKey(event, state, focusManager, onEnter = runImeAction) }
    Column(modifier = modifier.width(width)) {
        Text(
            text = label,
            style = typography.caption,
            color = colors.text2,
            modifier = Modifier.padding(bottom = CineSpacing.xs),
        )
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(CineSizes.textFieldHeight)
                    .cineFocusDecoration(focus, FieldShape, CineScale.FIELD, FieldOutlineShape)
                    .background(if (focus.isFocused) colors.surface2 else colors.surface1)
                    .withFocusRequester(focusRequester)
                    .focusRequester(frame)
                    .onFocusChanged { focus.update(it.hasFocus) }
                    .onPreviewKeyEvent { event ->
                        // Keys typed in the input reach here first: only the frame's own keys count.
                        !typing && frameKey(event) { typing = true }
                    }.focusable()
                    .padding(horizontal = CineSpacing.m),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (value.isEmpty() && placeholder != null) {
                Text(text = placeholder, style = typography.body, color = colors.text3, maxLines = 1)
            }
            val textStyle = typography.body.copy(color = colors.text)
            val cursor = SolidColor(colors.accent)
            val handler = KeyboardActionHandler { runImeAction() }
            if (secret) {
                BasicSecureTextField(
                    state = state,
                    textStyle = textStyle,
                    cursorBrush = cursor,
                    keyboardOptions = options,
                    onKeyboardAction = handler,
                    textObfuscationMode = TextObfuscationMode.Hidden,
                    modifier = inputModifier,
                )
            } else {
                BasicTextField(
                    state = state,
                    textStyle = textStyle,
                    cursorBrush = cursor,
                    keyboardOptions = options,
                    onKeyboardAction = handler,
                    lineLimits = TextFieldLineLimits.SingleLine,
                    modifier = inputModifier,
                )
            }
        }
    }
}

/** OK (or Enter) on the frame starts the typing, on its release; the press is consumed too. */
private fun frameKey(
    event: KeyEvent,
    startTyping: () -> Unit,
): Boolean =
    when (event.key) {
        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> consume(event.type == KeyEventType.KeyUp, startTyping)
        else -> false
    }

/**
 * The keys of the remote while typing, whatever device sends them: Compose lets the D-pad out of
 * a field only for devices that declare a D-pad source, which a Bluetooth remote may not do.
 */
private fun typingKey(
    event: KeyEvent,
    state: TextFieldState,
    focusManager: FocusManager,
    onEnter: () -> Unit,
): Boolean {
    val down = event.type == KeyEventType.KeyDown
    val selection = state.selection
    val atStart = selection.collapsed && selection.start == 0
    val atEnd = selection.collapsed && selection.end == state.text.length
    return when (event.key) {
        Key.DirectionUp -> consume(down) { focusManager.moveFocus(FocusDirection.Up) }

        Key.DirectionDown -> consume(down) { focusManager.moveFocus(FocusDirection.Down) }

        Key.DirectionLeft -> atStart && consume(down) { focusManager.moveFocus(FocusDirection.Left) }

        Key.DirectionRight -> atEnd && consume(down) { focusManager.moveFocus(FocusDirection.Right) }

        // Typing already: OK does nothing more (the keyboard is open, or Back closed it on purpose).
        Key.DirectionCenter -> true

        Key.Enter, Key.NumPadEnter -> consume(event.type == KeyEventType.KeyUp, onEnter)

        else -> false
    }
}

private inline fun consume(
    act: Boolean,
    action: () -> Unit,
): Boolean {
    if (act) action()
    return true
}

package fr.guim.cinedoury.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
 * treatment of `UI.md` § 3 when it holds the focus (OK then opens the system keyboard).
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
    onImeAction: () -> Unit = {},
    width: Dp = CineSizes.textFieldWidth,
    focusRequester: FocusRequester? = null,
    focus: CineFocus = rememberCineFocus(),
) {
    val colors = CineTheme.colors
    val typography = CineTheme.typography
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
                    .padding(horizontal = CineSpacing.m),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (value.isEmpty() && placeholder != null) {
                Text(text = placeholder, style = typography.body, color = colors.text3, maxLines = 1)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = typography.body.copy(color = colors.text),
                cursorBrush = SolidColor(colors.accent),
                visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = if (secret) KeyboardType.Password else keyboardType,
                        imeAction = imeAction,
                        autoCorrectEnabled = false,
                    ),
                keyboardActions = KeyboardActions(onAny = { onImeAction() }),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .withFocusRequester(focusRequester)
                        .onFocusChanged { focus.update(it.isFocused) },
            )
        }
    }
}

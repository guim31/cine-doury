package fr.guim.cinedoury.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import fr.guim.cinedoury.core.designsystem.component.CineIcons
import fr.guim.cinedoury.core.designsystem.component.CineTextField
import fr.guim.cinedoury.core.designsystem.component.FormScreen
import fr.guim.cinedoury.core.designsystem.component.InitialFocus
import fr.guim.cinedoury.core.designsystem.component.MessagePanel
import fr.guim.cinedoury.core.designsystem.component.PrimaryButton
import fr.guim.cinedoury.core.designsystem.component.RoundIconButton
import fr.guim.cinedoury.core.designsystem.component.SecondaryButton
import fr.guim.cinedoury.core.designsystem.component.rememberCineFocus
import fr.guim.cinedoury.core.designsystem.theme.CineSpacing
import fr.guim.cinedoury.core.designsystem.theme.CineTheme
import fr.guim.cinedoury.core.designsystem.theme.tabular
import fr.guim.cinedoury.core.ui.CollectEvents
import fr.guim.cinedoury.core.ui.PREVIEW_RETRY
import fr.guim.cinedoury.core.ui.SourceErrorPanel
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import fr.guim.cinedoury.core.ui.R as UiR

@Composable
fun QuickConnectRoute(
    username: String?,
    onSignedIn: () -> Unit,
    onUsePassword: (instead: Boolean) -> Unit,
    viewModel: QuickConnectViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectEvents(viewModel.events) { event ->
        when (event) {
            SignInEvent.SignedIn -> onSignedIn()

            // Quick Connect is off: the password screen replaces this one.
            SignInEvent.UsePassword -> onUsePassword(true)
        }
    }
    QuickConnectScreen(
        state = state,
        username = username,
        onNewCode = viewModel::start,
        onUsePassword = { onUsePassword(false) },
    )
}

/**
 * Quick Connect, offered first (`FONCTIONNALITES.md` § 1): the 6-digit code in large type, one
 * line to say where to type it, and the TV waits without blocking. No password typed with the
 * remote.
 */
@Composable
fun QuickConnectScreen(
    state: QuickConnectState,
    username: String?,
    onNewCode: () -> Unit,
    onUsePassword: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val usePassword = remember { FocusRequester() }
    val panel = state is QuickConnectState.Expired || state is QuickConnectState.Failed
    FormScreen(
        brand = stringResource(UiR.string.brand_name),
        title = stringResource(R.string.sign_in_title),
        subtitle = username?.let { stringResource(R.string.sign_in_for, it) },
        modifier = modifier,
    ) {
        when (state) {
            is QuickConnectState.Expired -> {
                MessagePanel(
                    title = stringResource(UiR.string.error_code_expired_title),
                    message = stringResource(UiR.string.error_code_expired_message),
                    actionLabel = stringResource(R.string.quick_connect_new_code),
                    onAction = onNewCode,
                    secondaryLabel = stringResource(R.string.use_password),
                    onSecondary = onUsePassword,
                    actionFocus = rememberCineFocus(PREVIEW_RETRY),
                )
            }

            is QuickConnectState.Failed -> {
                SourceErrorPanel(
                    error = state.error,
                    onRetry = onNewCode,
                    secondaryLabel = stringResource(R.string.use_password),
                    onSecondary = onUsePassword,
                )
            }

            else -> {
                QuickConnectCode(state, usePassword, onUsePassword)
            }
        }
    }
    InitialFocus(usePassword, key = panel, enabled = !panel)
}

@Composable
private fun QuickConnectCode(
    state: QuickConnectState,
    usePassword: FocusRequester,
    onUsePassword: () -> Unit,
) {
    val colors = CineTheme.colors
    val typography = CineTheme.typography
    val code = (state as? QuickConnectState.Waiting)?.code
    Column(verticalArrangement = Arrangement.spacedBy(CineSpacing.s)) {
        Text(
            text = stringResource(R.string.quick_connect_instruction),
            style = typography.body,
            color = colors.text2,
        )
        val description = code?.let { stringResource(R.string.quick_connect_code, it) }.orEmpty()
        Text(
            // Two groups of three digits are easier to read and to type.
            text = code?.chunked(CODE_GROUP)?.joinToString(CODE_SPACE) ?: CODE_PLACEHOLDER,
            style = typography.display.tabular(),
            color = if (code == null) colors.text3 else colors.text,
            modifier =
                Modifier
                    .padding(vertical = CineSpacing.s)
                    .semantics { contentDescription = description }
                    .testTag("quick-connect-code"),
        )
        Text(
            text =
                stringResource(
                    if (code == null) R.string.quick_connect_loading else R.string.quick_connect_waiting,
                ),
            style = typography.caption,
            color = colors.text3,
        )
        SecondaryButton(
            text = stringResource(R.string.use_password),
            onClick = onUsePassword,
            focusRequester = usePassword,
            focus = rememberCineFocus(AuthPreview.USE_PASSWORD),
            modifier = Modifier.padding(top = CineSpacing.m).testTag(AuthPreview.USE_PASSWORD),
        )
    }
}

private const val CODE_GROUP = 3
private const val CODE_SPACE = "  "
private const val CODE_PLACEHOLDER = "––– –––"

@Composable
fun PasswordRoute(
    username: String?,
    onSignedIn: () -> Unit,
    viewModel: PasswordViewModel = koinViewModel(key = "password-$username") { parametersOf(PasswordArgs(username)) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectEvents(viewModel.signedIn) { onSignedIn() }
    PasswordScreen(
        state = state,
        onUsername = viewModel::editUsername,
        onPassword = viewModel::editPassword,
        onToggleShow = viewModel::toggleShowPassword,
        onSubmit = viewModel::submit,
        onDismissError = viewModel::dismissError,
    )
}

/** For Koin, which does not pass a null parameter. */
data class PasswordArgs(
    val username: String?,
)

/** Username and password, the password masked unless shown (`FONCTIONNALITES.md` § 1). */
@Composable
fun PasswordScreen(
    state: PasswordState,
    onUsername: (String) -> Unit,
    onPassword: (String) -> Unit,
    onToggleShow: () -> Unit,
    onSubmit: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val usernameField = remember { FocusRequester() }
    val passwordField = remember { FocusRequester() }
    val error = state.error
    FormScreen(
        brand = stringResource(UiR.string.brand_name),
        title = stringResource(R.string.sign_in_title),
        subtitle = stringResource(R.string.password_subtitle),
        modifier = modifier,
    ) {
        if (error != null) {
            SourceErrorPanel(
                error = error,
                onRetry = onSubmit,
                secondaryLabel = stringResource(UiR.string.action_back),
                onSecondary = onDismissError,
            )
        } else {
            PasswordForm(state, onUsername, onPassword, onToggleShow, onSubmit, usernameField, passwordField)
        }
    }
    // The password first when the profile is known; back to it after a refusal.
    val target = if (state.username.isBlank()) usernameField else passwordField
    InitialFocus(target, key = error == null to state.refused, enabled = error == null)
}

@Composable
private fun PasswordForm(
    state: PasswordState,
    onUsername: (String) -> Unit,
    onPassword: (String) -> Unit,
    onToggleShow: () -> Unit,
    onSubmit: () -> Unit,
    usernameField: FocusRequester,
    passwordField: FocusRequester,
) {
    Column(verticalArrangement = Arrangement.spacedBy(CineSpacing.m)) {
        CineTextField(
            value = state.username,
            onValueChange = onUsername,
            label = stringResource(R.string.password_username),
            focusRequester = usernameField,
            modifier = Modifier.testTag("username-field"),
        )
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(CineSpacing.m)) {
            CineTextField(
                value = state.password,
                onValueChange = onPassword,
                label = stringResource(R.string.password_password),
                secret = !state.showPassword,
                imeAction = ImeAction.Done,
                onImeAction = onSubmit,
                focusRequester = passwordField,
                focus = rememberCineFocus(AuthPreview.PASSWORD_FIELD),
                modifier = Modifier.testTag(AuthPreview.PASSWORD_FIELD),
            )
            RoundIconButton(
                icon = if (state.showPassword) CineIcons.Hide else CineIcons.Show,
                label = stringResource(if (state.showPassword) R.string.password_hide else R.string.password_show),
                onClick = onToggleShow,
                modifier = Modifier.testTag("show-password"),
            )
        }
        if (state.refused) {
            Text(
                text =
                    stringResource(UiR.string.error_credentials_title) + " " +
                        stringResource(UiR.string.error_credentials_message),
                style = CineTheme.typography.caption,
                color = CineTheme.colors.danger,
                modifier = Modifier.testTag("refused"),
            )
        }
        PrimaryButton(
            text = stringResource(R.string.password_sign_in),
            onClick = onSubmit,
            focus = rememberCineFocus(AuthPreview.SIGN_IN),
            modifier = Modifier.padding(top = CineSpacing.s).testTag(AuthPreview.SIGN_IN),
        )
    }
}

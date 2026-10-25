package fr.guim.cinedoury.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import fr.guim.cinedoury.core.designsystem.component.CineIcons
import fr.guim.cinedoury.core.designsystem.component.CineTextField
import fr.guim.cinedoury.core.designsystem.component.FormScreen
import fr.guim.cinedoury.core.designsystem.component.InitialFocus
import fr.guim.cinedoury.core.designsystem.component.ListRow
import fr.guim.cinedoury.core.designsystem.component.PrimaryButton
import fr.guim.cinedoury.core.designsystem.component.SecondaryButton
import fr.guim.cinedoury.core.designsystem.component.rememberCineFocus
import fr.guim.cinedoury.core.designsystem.theme.CineSizes
import fr.guim.cinedoury.core.designsystem.theme.CineSpacing
import fr.guim.cinedoury.core.designsystem.theme.CineTheme
import fr.guim.cinedoury.core.source.ServerAddress
import fr.guim.cinedoury.core.ui.CollectEvents
import fr.guim.cinedoury.core.ui.SourceErrorPanel
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import fr.guim.cinedoury.core.ui.R as UiR

@Composable
fun ServerSearchRoute(
    onConnected: () -> Unit,
    onEnterAddress: () -> Unit,
    viewModel: ServerSearchViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectEvents(viewModel.connected) { onConnected() }
    ServerSearchScreen(
        state = state,
        onChoose = viewModel::choose,
        onEnterAddress = onEnterAddress,
        onSearchAgain = viewModel::search,
        onRetry = viewModel::retry,
        onDismissError = viewModel::dismissError,
    )
}

/**
 * The servers of the local network, listed as they answer, and the way to type an address. The
 * focus starts on « Saisir une adresse »; the first server found takes it as soon as it appears,
 * unless a key was pressed in the meantime (the focus never jumps under the user's hand).
 */
@Composable
fun ServerSearchScreen(
    state: ServerSearchState,
    onChoose: (ServerAddress) -> Unit,
    onEnterAddress: () -> Unit,
    onSearchAgain: () -> Unit,
    onRetry: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val enterAddress = remember { FocusRequester() }
    val firstServer = remember { FocusRequester() }
    var touched by rememberSaveable { mutableStateOf(false) }
    val error = state.error
    val subtitle =
        when {
            error != null -> null
            state.searching -> stringResource(R.string.servers_searching)
            state.servers.isEmpty() -> stringResource(R.string.servers_none)
            else -> stringResource(R.string.servers_found)
        }
    FormScreen(
        brand = stringResource(UiR.string.brand_name),
        title = stringResource(R.string.servers_title),
        subtitle = subtitle,
        modifier =
            modifier.onPreviewKeyEvent {
                touched = true
                false
            },
    ) {
        if (error != null) {
            SourceErrorPanel(
                error = error,
                onRetry = onRetry,
                secondaryLabel = stringResource(UiR.string.action_back),
                onSecondary = onDismissError,
            )
        } else {
            ServerList(state, onChoose, onEnterAddress, onSearchAgain, enterAddress, firstServer)
        }
    }
    val found = state.servers.isNotEmpty()
    // On arrival and back from an error: the first server if there is one.
    InitialFocus(if (found) firstServer else enterAddress, key = error == null, enabled = error == null)
    // A server appears while the user has not touched anything: it takes the focus.
    InitialFocus(firstServer, key = found, enabled = found && !touched && error == null)
}

@Composable
private fun ServerList(
    state: ServerSearchState,
    onChoose: (ServerAddress) -> Unit,
    onEnterAddress: () -> Unit,
    onSearchAgain: () -> Unit,
    enterAddress: FocusRequester,
    firstServer: FocusRequester,
) {
    Column(verticalArrangement = Arrangement.spacedBy(CineSpacing.m)) {
        Column(
            modifier = Modifier.width(CineSizes.settingsWidth),
            verticalArrangement = Arrangement.spacedBy(CineSpacing.xs),
        ) {
            state.servers.forEachIndexed { index, server ->
                ListRow(
                    title = server.name,
                    description = server.address.url,
                    value = stringResource(R.string.servers_checking).takeIf { state.checking == server.address },
                    icon = CineIcons.Server,
                    onClick = { onChoose(server.address) },
                    focusRequester = if (index == 0) firstServer else null,
                    focus = rememberCineFocus("server-$index"),
                    modifier = Modifier.testTag("server-$index"),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(CineSpacing.m)) {
            SecondaryButton(
                text = stringResource(R.string.servers_enter_address),
                onClick = onEnterAddress,
                focusRequester = enterAddress,
                focus = rememberCineFocus(AuthPreview.ENTER_ADDRESS),
                modifier = Modifier.testTag(AuthPreview.ENTER_ADDRESS),
            )
            if (!state.searching) {
                SecondaryButton(
                    text = stringResource(R.string.servers_search_again),
                    icon = CineIcons.Retry,
                    onClick = onSearchAgain,
                    modifier = Modifier.testTag("search-again"),
                )
            }
        }
    }
}

@Composable
fun AddressRoute(
    mode: AddressMode,
    onDone: () -> Unit,
    viewModel: AddressViewModel = koinViewModel(key = "address-$mode") { parametersOf(mode) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectEvents(viewModel.done) { onDone() }
    AddressScreen(
        state = state,
        onEdit = viewModel::edit,
        onSubmit = viewModel::submit,
        onDismissError = viewModel::dismissError,
    )
}

/** An address typed with the remote: `http://` and port 8096 assumed (`SPEC.md` § 4.7). */
@Composable
fun AddressScreen(
    state: AddressState,
    onEdit: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val field = remember { FocusRequester() }
    val (title, subtitle) =
        when (state.mode) {
            AddressMode.Add -> R.string.address_title to R.string.address_subtitle
            AddressMode.EditLocal -> R.string.address_local_title to R.string.address_local_subtitle
            AddressMode.EditRemote -> R.string.address_remote_title to R.string.address_remote_subtitle
        }
    val error = state.error
    FormScreen(
        brand = stringResource(UiR.string.brand_name),
        title = stringResource(title),
        // The error panel takes the room of the explanation.
        subtitle = stringResource(subtitle).takeIf { error == null },
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
            AddressForm(state, onEdit, onSubmit, field)
        }
    }
    InitialFocus(field, key = error == null, enabled = error == null)
}

@Composable
private fun AddressForm(
    state: AddressState,
    onEdit: (String) -> Unit,
    onSubmit: () -> Unit,
    field: FocusRequester,
) {
    Column(verticalArrangement = Arrangement.spacedBy(CineSpacing.m)) {
        CineTextField(
            value = state.text,
            onValueChange = onEdit,
            label = stringResource(R.string.address_label),
            placeholder =
                stringResource(
                    if (state.mode ==
                        AddressMode.EditRemote
                    ) {
                        R.string.address_remote_placeholder
                    } else {
                        R.string.address_placeholder
                    },
                ),
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Go,
            onImeAction = onSubmit,
            focusRequester = field,
            focus = rememberCineFocus(AuthPreview.ADDRESS_FIELD),
            modifier = Modifier.testTag(AuthPreview.ADDRESS_FIELD),
        )
        if (state.invalid) {
            Text(
                text = stringResource(R.string.address_invalid),
                style = CineTheme.typography.caption,
                color = CineTheme.colors.danger,
            )
        }
        PrimaryButton(
            text =
                stringResource(
                    if (state.mode ==
                        AddressMode.Add
                    ) {
                        R.string.address_continue
                    } else {
                        R.string.address_save
                    },
                ),
            onClick = onSubmit,
            modifier = Modifier.testTag("address-submit"),
        )
    }
}

package fr.guim.cinedoury.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.guim.cinedoury.core.designsystem.component.CineTextField
import fr.guim.cinedoury.core.designsystem.component.FormScreen
import fr.guim.cinedoury.core.designsystem.component.InitialFocus
import fr.guim.cinedoury.core.designsystem.component.PrimaryButton
import fr.guim.cinedoury.core.designsystem.component.SecondaryButton
import fr.guim.cinedoury.core.designsystem.component.rememberCineFocus
import fr.guim.cinedoury.core.designsystem.theme.CineSpacing
import org.koin.androidx.compose.koinViewModel
import fr.guim.cinedoury.core.ui.R as UiR

@Composable
fun DeviceNameRoute(
    onDone: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler(onBack = onDone)
    DeviceNameScreen(
        current = state.deviceName,
        tvName = state.tvName,
        onSave = { name ->
            viewModel.setDeviceName(name)
            onDone()
        },
    )
}

/**
 * « Nom de l'appareil » (`SPEC.md` § 4.17): the name the server shows for this TV, from the next
 * request on. Empty, or « Nom de la télévision », goes back to the name of the TV.
 */
@Composable
fun DeviceNameScreen(
    current: String?,
    tvName: String,
    onSave: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by rememberSaveable { mutableStateOf(current ?: tvName) }
    val field = remember { FocusRequester() }
    FormScreen(
        brand = stringResource(UiR.string.brand_name),
        title = stringResource(R.string.device_name_title),
        subtitle = stringResource(R.string.device_name_subtitle, tvName),
        modifier = modifier.testTag("device-name"),
    ) {
        CineTextField(
            value = text,
            onValueChange = { text = it },
            label = stringResource(R.string.device_name_field),
            imeAction = ImeAction.Done,
            onImeAction = { onSave(text.takeIf { it.isNotBlank() && it.trim() != tvName }) },
            focusRequester = field,
            focus = rememberCineFocus("device-name-field"),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(CineSpacing.m)) {
            PrimaryButton(
                text = stringResource(R.string.device_name_save),
                onClick = { onSave(text.takeIf { it.isNotBlank() && it.trim() != tvName }) },
                modifier = Modifier.testTag("device-name-save"),
            )
            SecondaryButton(
                text = stringResource(R.string.device_name_reset),
                onClick = { onSave(null) },
                modifier = Modifier.testTag("device-name-reset"),
            )
        }
    }
    InitialFocus(field)
}

package fr.guim.cinedoury.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Text
import fr.guim.cinedoury.core.data.SessionManager
import fr.guim.cinedoury.core.data.search.RequestServer
import fr.guim.cinedoury.core.data.search.RequestSettings
import fr.guim.cinedoury.core.designsystem.component.CineIcons
import fr.guim.cinedoury.core.designsystem.component.CineTextField
import fr.guim.cinedoury.core.designsystem.component.FormScreen
import fr.guim.cinedoury.core.designsystem.component.InitialFocus
import fr.guim.cinedoury.core.designsystem.component.PrimaryButton
import fr.guim.cinedoury.core.designsystem.component.SecondaryButton
import fr.guim.cinedoury.core.designsystem.component.rememberCineFocus
import fr.guim.cinedoury.core.designsystem.theme.CineSpacing
import fr.guim.cinedoury.core.designsystem.theme.CineTheme
import fr.guim.cinedoury.core.source.RequestConnector
import fr.guim.cinedoury.core.source.SourceException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/** Why the settings of Seerr were not saved. */
enum class SeerrProblem {
    Empty,
    Unreachable,
    Key,
    NotSeerr,
    Other,
}

/**
 * The settings of Seerr (`SPEC.md` § 4.12).
 *
 * @param version the version of the server once checked: it is saved.
 */
@Immutable
data class SeerrSettingsState(
    val address: String = "",
    val apiKey: String = "",
    val configured: Boolean = false,
    val checking: Boolean = false,
    val version: String? = null,
    val problem: SeerrProblem? = null,
)

/** Checks the address and the key before saving them; the key is kept encrypted (`RequestSettings`). */
class SeerrSettingsViewModel(
    sessions: SessionManager,
    private val settings: RequestSettings,
    private val connector: RequestConnector,
) : ViewModel() {
    private val serverId =
        sessions.active.value
            ?.profile
            ?.serverId
    private val local = MutableStateFlow(SeerrSettingsState())
    val state: StateFlow<SeerrSettingsState> = local.asStateFlow()

    init {
        viewModelScope.launch {
            val saved = serverId?.let { settings.server(it).first() } ?: return@launch
            local.update { it.copy(address = saved.address, apiKey = saved.apiKey, configured = true) }
        }
    }

    fun setAddress(address: String) = local.update { it.copy(address = address, problem = null, version = null) }

    fun setKey(key: String) = local.update { it.copy(apiKey = key.trim(), problem = null, version = null) }

    fun save() {
        val serverId = serverId ?: return
        val state = local.value
        if (state.checking) return
        if (state.address.isBlank() || state.apiKey.isBlank()) {
            local.update { it.copy(problem = SeerrProblem.Empty) }
            return
        }
        local.update { it.copy(checking = true, problem = null) }
        viewModelScope.launch {
            try {
                val version = connector.open(state.address.trim(), state.apiKey, null).check()
                settings.save(serverId, RequestServer(state.address.trim(), state.apiKey))
                local.update { it.copy(checking = false, configured = true, version = version) }
            } catch (failure: SourceException) {
                local.update { it.copy(checking = false, problem = failure.problem()) }
            }
        }
    }

    fun remove() {
        val serverId = serverId ?: return
        viewModelScope.launch {
            settings.clear(serverId)
            local.value = SeerrSettingsState()
        }
    }

    private fun SourceException.problem(): SeerrProblem =
        when (this) {
            is SourceException.Unreachable -> SeerrProblem.Unreachable
            is SourceException.Unauthorized, is SourceException.Forbidden -> SeerrProblem.Key
            is SourceException.NotAServer -> SeerrProblem.NotSeerr
            else -> SeerrProblem.Other
        }
}

@Composable
fun SeerrSettingsRoute(viewModel: SeerrSettingsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SeerrSettingsScreen(
        state = state,
        onAddress = viewModel::setAddress,
        onKey = viewModel::setKey,
        onSave = viewModel::save,
        onRemove = viewModel::remove,
    )
}

/** Tags of the settings of Seerr, for tests and screenshots. */
object SeerrPreview {
    const val ADDRESS = "seerr-address"
    const val KEY = "seerr-key"
    const val SAVE = "seerr-save"
    const val REMOVE = "seerr-remove"
}

/**
 * « Demandes (Seerr) »: the address and the API key of a Seerr server (or Jellyseerr, Overseerr),
 * checked before they are kept. Without them, nothing of Seerr shows in the search.
 */
@Composable
fun SeerrSettingsScreen(
    state: SeerrSettingsState,
    onAddress: (String) -> Unit,
    onKey: (String) -> Unit,
    onSave: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val first = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    // ↑ and ↓ leave a text field (it would keep the focus on ↓: a trap for the remote).
    val leaveVertically =
        Modifier.onPreviewKeyEvent { event ->
            when {
                event.type != KeyEventType.KeyDown -> false
                event.key == Key.DirectionDown -> focusManager.moveFocus(FocusDirection.Down)
                event.key == Key.DirectionUp -> focusManager.moveFocus(FocusDirection.Up)
                else -> false
            }
        }
    FormScreen(
        brand = stringResource(fr.guim.cinedoury.core.ui.R.string.brand_name),
        title = stringResource(R.string.seerr_title),
        subtitle = stringResource(R.string.seerr_subtitle),
        modifier = modifier,
    ) {
        CineTextField(
            value = state.address,
            onValueChange = onAddress,
            label = stringResource(R.string.seerr_address),
            placeholder = stringResource(R.string.seerr_address_placeholder),
            keyboardType = KeyboardType.Uri,
            focusRequester = first,
            focus = rememberCineFocus(SeerrPreview.ADDRESS),
            modifier = leaveVertically.testTag(SeerrPreview.ADDRESS),
        )
        CineTextField(
            value = state.apiKey,
            onValueChange = onKey,
            label = stringResource(R.string.seerr_key),
            placeholder = stringResource(R.string.seerr_key_placeholder),
            secret = true,
            imeAction = ImeAction.Done,
            onImeAction = onSave,
            focus = rememberCineFocus(SeerrPreview.KEY),
            modifier = leaveVertically.testTag(SeerrPreview.KEY),
        )
        val message =
            when {
                state.checking -> stringResource(R.string.seerr_checking)
                state.problem != null -> stringResource(state.problem.message)
                state.version != null -> stringResource(R.string.seerr_saved, state.version)
                !state.configured -> stringResource(R.string.seerr_not_set)
                else -> null
            }
        if (message != null) {
            Text(
                text = message,
                style = CineTheme.typography.body,
                color = if (state.problem != null) CineTheme.colors.danger else CineTheme.colors.text2,
                modifier = Modifier.testTag("seerr-message"),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(CineSpacing.s)) {
            PrimaryButton(
                text = stringResource(R.string.seerr_save),
                icon = CineIcons.Watched,
                onClick = onSave,
                focus = rememberCineFocus(SeerrPreview.SAVE),
                modifier = Modifier.testTag(SeerrPreview.SAVE),
            )
            if (state.configured) {
                SecondaryButton(
                    text = stringResource(R.string.seerr_remove),
                    icon = CineIcons.Remove,
                    onClick = onRemove,
                    focus = rememberCineFocus(SeerrPreview.REMOVE),
                    modifier = Modifier.testTag(SeerrPreview.REMOVE),
                )
            }
        }
    }
    InitialFocus(first)
}

private val SeerrProblem.message: Int
    get() =
        when (this) {
            SeerrProblem.Empty -> R.string.seerr_error_empty
            SeerrProblem.Unreachable -> R.string.seerr_error_unreachable
            SeerrProblem.Key -> R.string.seerr_error_key
            SeerrProblem.NotSeerr -> R.string.seerr_error_not_seerr
            SeerrProblem.Other -> R.string.seerr_error_other
        }

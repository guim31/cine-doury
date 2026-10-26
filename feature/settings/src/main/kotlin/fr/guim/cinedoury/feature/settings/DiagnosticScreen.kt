package fr.guim.cinedoury.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.guim.cinedoury.core.designsystem.component.CineIcons
import fr.guim.cinedoury.feature.settings.page.SettingItem
import fr.guim.cinedoury.feature.settings.page.SettingsPageScreen
import org.koin.androidx.compose.koinViewModel

@Composable
fun DiagnosticRoute(viewModel: DiagnosticViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    DiagnosticScreen(state, onSend = viewModel::send)
}

/** Tags of the report, for tests and screenshots. */
object DiagnosticPreview {
    const val SEND = "send"
}

/**
 * « Rapport de diagnostic » (`SPEC.md` § 4.18): « Envoyer au serveur » first, then the report, a line
 * per fact, which ↓ goes through. Once sent, the name of the file the server made.
 */
@Composable
fun DiagnosticScreen(
    state: DiagnosticState,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sendDescription =
        when {
            !state.connected -> stringResource(R.string.report_no_server)
            state.sending -> stringResource(R.string.report_sending)
            state.sentAs != null -> stringResource(R.string.report_sent, state.sentAs)
            state.failed -> stringResource(R.string.report_failed)
            else -> null
        }
    val lines =
        state.report
            ?.lines()
            .orEmpty()
            .filter { it.isNotBlank() }
    val items =
        buildList {
            add(
                SettingItem.Action(
                    key = DiagnosticPreview.SEND,
                    title = stringResource(R.string.report_send),
                    description = sendDescription,
                    icon = CineIcons.Server,
                    onClick = { if (state.connected) onSend() },
                ),
            )
            lines.forEachIndexed { index, line ->
                if (line.startsWith("## ")) {
                    add(SettingItem.Section(key = "line-$index", title = line.removePrefix("## ")))
                } else {
                    add(SettingItem.Action(key = "line-$index", title = line, wrap = true, onClick = {}))
                }
            }
        }
    SettingsPageScreen(
        title = stringResource(R.string.report_title),
        subtitle = stringResource(R.string.report_subtitle),
        items = items,
        modifier = modifier.testTag("report"),
    )
}

package fr.guim.cinedoury.feature.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import fr.guim.cinedoury.core.designsystem.component.FormScreen
import fr.guim.cinedoury.core.designsystem.component.InitialFocus
import fr.guim.cinedoury.core.source.SourceException
import fr.guim.cinedoury.core.ui.SourceErrorPanel
import fr.guim.cinedoury.core.ui.R as UiR

/**
 * At start, the saved server did not answer at its local or its remote address: what happened,
 * « Réessayer » with the focus, or another server. Never a silent black screen.
 */
@Composable
fun UnreachableScreen(
    serverName: String,
    error: SourceException,
    onRetry: () -> Unit,
    onChangeServer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val retry = remember { FocusRequester() }
    FormScreen(brand = stringResource(UiR.string.brand_name), title = serverName, modifier = modifier) {
        SourceErrorPanel(
            error = error,
            onRetry = onRetry,
            secondaryLabel = stringResource(R.string.change_server),
            onSecondary = onChangeServer,
            retryFocusRequester = retry,
        )
    }
    InitialFocus(retry)
}

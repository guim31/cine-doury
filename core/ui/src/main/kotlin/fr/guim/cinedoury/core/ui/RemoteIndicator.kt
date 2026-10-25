package fr.guim.cinedoury.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import fr.guim.cinedoury.core.designsystem.component.CineIcons
import fr.guim.cinedoury.core.designsystem.component.StatusPill

/** The discreet sign that the server is reached through its remote address (`SPEC.md` § 4.7). */
@Composable
fun RemoteIndicator(modifier: Modifier = Modifier) {
    StatusPill(text = stringResource(R.string.remote_connection), icon = CineIcons.Remote, modifier = modifier)
}

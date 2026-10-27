package fr.guim.cinedoury.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.Text
import fr.guim.cinedoury.core.designsystem.component.InitialFocus
import fr.guim.cinedoury.core.designsystem.component.SecondaryButton
import fr.guim.cinedoury.core.designsystem.component.asHeading
import fr.guim.cinedoury.core.designsystem.theme.CineSizes
import fr.guim.cinedoury.core.designsystem.theme.CineSpacing
import fr.guim.cinedoury.core.designsystem.theme.CineTheme

/**
 * A screen that a later milestone fills (search, details, player): its title, one line, and
 * « Retour », which has the focus. « Retour » on the remote does the same.
 */
@Composable
fun ComingSoonScreen(
    title: String,
    message: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val back = remember { FocusRequester() }
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(CineTheme.colors.background)
                .padding(start = CineSizes.railCollapsed + CineSpacing.xl),
        contentAlignment = Alignment.CenterStart,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(CineSpacing.s)) {
            Text(
                text = title,
                style = CineTheme.typography.title1,
                color = CineTheme.colors.text,
                modifier = Modifier.asHeading(),
            )
            Text(
                text = message,
                style = CineTheme.typography.body,
                color = CineTheme.colors.text2,
                modifier = Modifier.width(CineSizes.formWidth),
            )
            SecondaryButton(
                text = stringResource(R.string.action_back),
                onClick = onBack,
                focusRequester = back,
                modifier = Modifier.padding(top = CineSpacing.m),
            )
        }
    }
    InitialFocus(back)
}

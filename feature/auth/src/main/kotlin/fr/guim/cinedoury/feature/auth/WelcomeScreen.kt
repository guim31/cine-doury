package fr.guim.cinedoury.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.Text
import fr.guim.cinedoury.core.designsystem.component.InitialFocus
import fr.guim.cinedoury.core.designsystem.component.PrimaryButton
import fr.guim.cinedoury.core.designsystem.component.Wordmark
import fr.guim.cinedoury.core.designsystem.component.rememberCineFocus
import fr.guim.cinedoury.core.designsystem.theme.CineSpacing
import fr.guim.cinedoury.core.designsystem.theme.CineTheme
import fr.guim.cinedoury.core.ui.R as UiR

/** First launch: the name of the app, one line, one button (`UI.md` § 1, principle 5). */
@Composable
fun WelcomeScreen(
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val start = remember { FocusRequester() }
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(CineTheme.colors.background),
        contentAlignment = Alignment.CenterStart,
    ) {
        Column(
            modifier = Modifier.padding(start = CineSpacing.safeHorizontal * 2),
            verticalArrangement = Arrangement.spacedBy(CineSpacing.s),
        ) {
            Wordmark(stringResource(UiR.string.brand_name), large = true)
            Text(
                text = stringResource(R.string.welcome_tagline),
                style = CineTheme.typography.body,
                color = CineTheme.colors.text2,
            )
            PrimaryButton(
                text = stringResource(R.string.welcome_start),
                onClick = onStart,
                focusRequester = start,
                focus = rememberCineFocus(AuthPreview.START),
                modifier = Modifier.padding(top = CineSpacing.l).testTag(AuthPreview.START),
            )
        }
    }
    InitialFocus(start)
}

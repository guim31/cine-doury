package fr.guim.cinedoury.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.tv.material3.Text
import fr.guim.cinedoury.core.designsystem.theme.CineSizes
import fr.guim.cinedoury.core.designsystem.theme.CineSpacing
import fr.guim.cinedoury.core.designsystem.theme.CineTheme

/** The name of the app set in type: there is no logo image. */
@Composable
fun Wordmark(
    text: String,
    modifier: Modifier = Modifier,
    large: Boolean = false,
) {
    val typography = CineTheme.typography
    Text(
        text = text,
        style = if (large) typography.display else typography.title2,
        color = if (large) CineTheme.colors.text else CineTheme.colors.text2,
        modifier = modifier,
    )
}

/**
 * A screen of the sign-in path or of the settings: the `fond` background, the safe area, the name
 * of the app at the top, a reading column on the left with a title and an optional line under it.
 *
 * @param topEnd a discreet status at the top right (« À distance »).
 */
@Composable
fun FormScreen(
    brand: String,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    topEnd: (@Composable () -> Unit)? = null,
    overlay: (@Composable BoxScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = CineTheme.colors
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(colors.background),
    ) {
        Row(
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .padding(
                        horizontal = CineSpacing.safeHorizontal * 2,
                        vertical =
                            CineSpacing.safeVertical + CineSpacing.m,
                    ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CineSpacing.m),
        ) {
            Wordmark(brand)
        }
        if (topEnd != null) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(
                        horizontal = CineSpacing.safeHorizontal,
                        vertical =
                            CineSpacing.safeVertical + CineSpacing.m,
                    ),
            ) { topEnd() }
        }
        Column(
            modifier =
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = CineSpacing.safeHorizontal * 2, end = CineSpacing.safeHorizontal),
            verticalArrangement = Arrangement.spacedBy(CineSpacing.s),
        ) {
            Text(text = title, style = CineTheme.typography.title1, color = colors.text)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = CineTheme.typography.body,
                    color = colors.text2,
                    modifier = Modifier.width(CineSizes.settingsWidth),
                )
            }
            Column(
                modifier = Modifier.padding(top = CineSpacing.l),
                verticalArrangement = Arrangement.spacedBy(CineSpacing.m),
                content = content,
            )
        }
        overlay?.invoke(this)
    }
}

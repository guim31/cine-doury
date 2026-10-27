package fr.guim.cinedoury.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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
        modifier = modifier.asHeading(),
    )
}

/** A title of a screen or of a row: TalkBack reads it as a heading and can jump from one to the next. */
fun Modifier.asHeading(): Modifier = semantics { heading() }

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
        // The brand on top, the reading column centred in the rest: they never overlap.
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        start = CineSpacing.safeHorizontal * 2,
                        end = CineSpacing.safeHorizontal,
                        top = CineSpacing.safeVertical + CineSpacing.m,
                        bottom = CineSpacing.safeVertical,
                    ),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Wordmark(brand, Modifier.weight(1f))
                topEnd?.invoke()
            }
            // A gap under the brand: what scrolls away is cut there, never against the brand.
            Box(
                Modifier.weight(1f).fillMaxWidth().padding(top = CineSpacing.m),
                contentAlignment = Alignment.CenterStart,
            ) {
                // Scrolls when the content outgrows the screen (the detail of an error): the focused
                // element is always brought into view.
                KeepFocusRoom {
                    Column(
                        // Room under the last element inside the scroll, which clips: its focus shows
                        // whole (the title on top is never focused).
                        modifier = Modifier.verticalScroll(rememberScrollState()).padding(bottom = CineSizes.focusRoom),
                        verticalArrangement = Arrangement.spacedBy(CineSpacing.s),
                    ) {
                        Text(
                            text = title,
                            style = CineTheme.typography.title1,
                            color = colors.text,
                            modifier = Modifier.asHeading(),
                        )
                        if (subtitle != null) {
                            Text(
                                text = subtitle,
                                style = CineTheme.typography.body,
                                color = colors.text2,
                                modifier = Modifier.width(CineSizes.settingsWidth),
                            )
                        }
                        Column(
                            modifier = Modifier.padding(top = CineSpacing.m),
                            verticalArrangement = Arrangement.spacedBy(CineSpacing.m),
                            content = content,
                        )
                    }
                }
            }
        }
        overlay?.invoke(this)
    }
}

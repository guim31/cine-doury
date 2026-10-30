package fr.guim.cinedoury.core.ui.mobile

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import fr.guim.cinedoury.core.designsystem.component.CineIcons
import fr.guim.cinedoury.core.designsystem.mobile.MobileMessage
import fr.guim.cinedoury.core.designsystem.mobile.MobilePrimaryButton
import fr.guim.cinedoury.core.designsystem.mobile.MobileTextButton
import fr.guim.cinedoury.core.designsystem.theme.CineSpacing
import fr.guim.cinedoury.core.designsystem.theme.CineTheme
import fr.guim.cinedoury.core.source.SourceException
import fr.guim.cinedoury.core.ui.R
import fr.guim.cinedoury.core.ui.toErrorText

/**
 * A failure of the source on the phone (`UI.md` § 10): two sentences, « Réessayer », and the detail
 * under « Plus d'infos ».
 */
@Composable
fun MobileSourceError(
    error: SourceException,
    onRetry: (() -> Unit)?,
    modifier: Modifier = Modifier,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    val text = error.toErrorText()
    var details by rememberSaveable { mutableStateOf(false) }
    MobileMessage(
        title = text.title,
        message = text.message,
        icon = CineIcons.Warning,
        modifier = modifier.testTag(MobileTags.ERROR),
    ) {
        onRetry?.let { MobilePrimaryButton(stringResource(R.string.action_retry), it, icon = CineIcons.Retry) }
        if (secondaryLabel != null && onSecondary != null) MobileTextButton(secondaryLabel, onSecondary)
        MobileTextButton(stringResource(R.string.action_more_info), { details = !details })
        if (details) {
            Text(
                text.details,
                style = CineTheme.typography.minimum,
                color = CineTheme.colors.text3,
                modifier = Modifier.padding(top = CineSpacing.xs),
            )
        }
    }
}

/** Tags of the shared pieces of the phone, for tests. */
object MobileTags {
    const val ERROR = "mobile-error"
}

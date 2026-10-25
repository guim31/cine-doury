package fr.guim.cinedoury.feature.details

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.Text
import fr.guim.cinedoury.core.designsystem.component.InitialFocus
import fr.guim.cinedoury.core.designsystem.component.ListRow
import fr.guim.cinedoury.core.designsystem.component.SecondaryButton
import fr.guim.cinedoury.core.designsystem.theme.CineSizes
import fr.guim.cinedoury.core.designsystem.theme.CineSpacing
import fr.guim.cinedoury.core.designsystem.theme.CineTheme
import fr.guim.cinedoury.core.model.PlaybackRequest
import fr.guim.cinedoury.core.ui.cardTitle
import fr.guim.cinedoury.core.ui.describe
import fr.guim.cinedoury.core.ui.toDurationText
import fr.guim.cinedoury.core.ui.R as UiR

/**
 * Stands for the player until J4: what would be played, as the details screen asks it (version,
 * audio, subtitles, from where), and « Retour ».
 */
@Composable
fun PlaybackPreviewScreen(
    request: PlaybackRequest,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val item = request.item
    val source = item.mediaSources.firstOrNull { it.id == request.selection?.sourceId }
    val audio = source?.audio?.firstOrNull { it.index == request.selection?.audioIndex }
    val subtitles = source?.subtitles?.firstOrNull { it.index == request.selection?.subtitleIndex }
    val back = remember { FocusRequester() }
    Box(
        modifier =
            modifier.fillMaxSize().background(CineTheme.colors.background).padding(
                start =
                    CineSizes.railCollapsed + CineSpacing.xl,
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Column(Modifier.width(CineSizes.settingsWidth), verticalArrangement = Arrangement.spacedBy(CineSpacing.s)) {
            Text(
                text = stringResource(UiR.string.soon_title_player),
                style = CineTheme.typography.title1,
                color = CineTheme.colors.text,
            )
            Text(
                text = stringResource(UiR.string.soon_message_player, item.cardTitle),
                style = CineTheme.typography.body,
                color = CineTheme.colors.text2,
            )
            val position = item.userData?.playbackPosition?.takeIf { request.resume && item.progress != null }
            val lines =
                listOfNotNull(
                    source?.let { stringResource(R.string.playback_version) to it.describe() },
                    stringResource(R.string.panel_audio) to
                        (audio?.describe() ?: stringResource(R.string.playback_player_choice)),
                    stringResource(R.string.panel_subtitles) to
                        when {
                            request.selection == null -> stringResource(R.string.playback_player_choice)
                            subtitles == null -> stringResource(R.string.subtitles_none)
                            else -> subtitles.describe()
                        },
                    stringResource(R.string.playback_start) to
                        (
                            position?.let { stringResource(R.string.playback_resume_at, it.toDurationText()) }
                                ?: stringResource(R.string.playback_from_start)
                        ),
                )
            lines.forEachIndexed { index, (title, value) ->
                ListRow(
                    title = title,
                    description = value,
                    onClick = {},
                    modifier = Modifier.testTag("playback-$index"),
                )
            }
            SecondaryButton(
                text = stringResource(UiR.string.action_back),
                onClick = onBack,
                focusRequester = back,
                modifier = Modifier.padding(top = CineSpacing.m).testTag("playback-back"),
            )
        }
    }
    InitialFocus(back)
}

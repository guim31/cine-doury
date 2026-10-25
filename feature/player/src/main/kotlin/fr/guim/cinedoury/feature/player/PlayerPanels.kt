package fr.guim.cinedoury.feature.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import fr.guim.cinedoury.core.designsystem.component.CineIcons
import fr.guim.cinedoury.core.designsystem.component.InitialFocus
import fr.guim.cinedoury.core.designsystem.component.ListRow
import fr.guim.cinedoury.core.designsystem.component.ListSectionTitle
import fr.guim.cinedoury.core.designsystem.component.SidePanel
import fr.guim.cinedoury.core.designsystem.component.SubtitlePreview
import fr.guim.cinedoury.core.designsystem.component.rememberCineFocus
import fr.guim.cinedoury.core.designsystem.theme.CineSizes
import fr.guim.cinedoury.core.designsystem.theme.CineSpacing
import fr.guim.cinedoury.core.model.MediaStream
import fr.guim.cinedoury.core.model.Qualities
import fr.guim.cinedoury.core.model.SubtitleEdge
import fr.guim.cinedoury.core.model.SubtitleStyle
import fr.guim.cinedoury.core.ui.attributes
import fr.guim.cinedoury.core.ui.label

/** Preview tag of the row [index] of column [column] of a panel of the player. */
fun playerPanelLine(
    column: Int,
    index: Int,
) = "player-panel-$column-$index"

/**
 * The panels of the player, over the right of the picture, which plays on behind (`UI.md` § 8):
 * audio and subtitles side by side, the quality, the settings (format, intro, look of the
 * subtitles over the paused picture), the chapters.
 */
@Composable
fun PlayerPanels(
    panel: PlayerPanel?,
    state: PlayerState,
    actions: PlayerActions,
    onPanel: (PlayerPanel?) -> Unit,
) {
    var last by remember { mutableStateOf(panel) }
    SideEffect { if (panel != null) last = panel }
    val shown = panel ?: last
    if (shown == PlayerPanel.Settings && panel != null) SubtitleSample(state.settings.subtitles)
    val title =
        stringResource(
            when (shown) {
                PlayerPanel.Tracks, null -> R.string.player_tracks
                PlayerPanel.Quality -> R.string.player_quality
                PlayerPanel.Settings -> R.string.player_settings
                PlayerPanel.Chapters -> R.string.player_chapters
            },
        )
    SidePanel(
        visible = panel != null,
        title = title,
        width = if (shown == PlayerPanel.Tracks) CineSizes.tracksPanelWidth else CineSizes.widePanelWidth,
        scrim = false,
        modifier = Modifier.testTag("player-panel"),
    ) {
        when (shown) {
            PlayerPanel.Tracks -> {
                TracksPanel(state, actions, enabled = panel != null)
            }

            PlayerPanel.Quality -> {
                QualityPanel(state, actions, enabled = panel != null) { onPanel(null) }
            }

            PlayerPanel.Settings -> {
                SettingsPanel(state, actions, enabled = panel != null)
            }

            PlayerPanel.Chapters -> {
                ChaptersPanel(state, actions, enabled = panel != null) { onPanel(null) }
            }

            null -> {}
        }
    }
}

/** One choice of a column: its title, its pills, checked when chosen. */
private data class Choice(
    val key: String,
    val title: String,
    val pills: List<String>,
    val checked: Boolean,
    val onClick: () -> Unit,
    val description: String? = null,
    val value: String? = null,
)

@Composable
private fun ChoiceColumn(
    column: Int,
    title: String?,
    choices: List<Choice>,
    modifier: Modifier = Modifier,
    initialFocus: FocusRequester? = null,
) {
    val initial = choices.indexOfFirst { it.checked }.coerceAtLeast(0)
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(CineSpacing.xs),
    ) {
        if (title != null) item(key = "title") { ListSectionTitle(title) }
        itemsIndexed(choices, key = { _, choice -> choice.key }) { index, choice ->
            ListRow(
                title = choice.title,
                description = choice.description,
                value = choice.value,
                onClick = choice.onClick,
                pills = choice.pills,
                wrapTitle = true,
                trailingIcon = if (choice.checked) CineIcons.Watched else null,
                focusRequester = if (index == initial) initialFocus else null,
                focus = rememberCineFocus(playerPanelLine(column, index)),
                modifier = Modifier.testTag(playerPanelLine(column, index)),
            )
        }
    }
}

/** The tracks of the version played: audio at the left, subtitles at the right. */
@Composable
private fun TracksPanel(
    state: PlayerState,
    actions: PlayerActions,
    enabled: Boolean,
) {
    val source = state.stream?.source ?: state.item.mediaSources.firstOrNull()
    val audio =
        source?.audio.orEmpty().map {
            it.choice(
                state.audioIndex == it.index,
            ) { actions.selectAudio(it.index) }
        }
    val subtitles =
        listOf(
            Choice(
                "none",
                stringResource(R.string.player_subtitles_none),
                emptyList(),
                state.subtitleIndex == null,
                { actions.selectSubtitles(null) },
            ),
        ) +
            source?.subtitles.orEmpty().map {
                it.choice(
                    state.subtitleIndex == it.index,
                ) { actions.selectSubtitles(it.index) }
            }
    val first = remember { FocusRequester() }
    Row(horizontalArrangement = Arrangement.spacedBy(CineSpacing.m)) {
        ChoiceColumn(0, stringResource(R.string.player_audio), audio, Modifier.weight(1f), first)
        ChoiceColumn(1, stringResource(R.string.player_subtitles), subtitles, Modifier.weight(1f))
    }
    InitialFocus(first, key = enabled, enabled = enabled)
}

@Composable
private fun MediaStream.choice(
    checked: Boolean,
    onClick: () -> Unit,
) = Choice("track-$index", label(), attributes(), checked, onClick)

/** « Automatique » (direct play when possible), then lower bitrates: the server transcodes. */
@Composable
private fun QualityPanel(
    state: PlayerState,
    actions: PlayerActions,
    enabled: Boolean,
    onDone: () -> Unit,
) {
    val choices =
        listOf(
            Choice(
                key = "auto",
                title = stringResource(R.string.player_quality_auto),
                pills = emptyList(),
                checked = state.maxBitrate == null,
                onClick = {
                    actions.selectQuality(null)
                    onDone()
                },
                description = stringResource(R.string.player_quality_auto_detail),
            ),
        ) +
            Qualities.BITRATES.map { bitrate ->
                Choice(
                    key = "bitrate-$bitrate",
                    title = stringResource(R.string.player_quality_bitrate, bitrate.megabits()),
                    pills = emptyList(),
                    checked = state.maxBitrate == bitrate,
                    onClick = {
                        actions.selectQuality(bitrate)
                        onDone()
                    },
                )
            }
    val first = remember { FocusRequester() }
    ChoiceColumn(0, null, choices, initialFocus = first)
    InitialFocus(first, key = enabled, enabled = enabled)
}

/** Each row goes to the next value with OK; the subtitles show over the paused picture. */
@Composable
private fun SettingsPanel(
    state: PlayerState,
    actions: PlayerActions,
    enabled: Boolean,
) {
    val settings = state.settings
    val style = settings.subtitles

    fun subtitles(change: (SubtitleStyle) -> SubtitleStyle) =
        actions.updateSettings {
            it.copy(subtitles = change(it.subtitles))
        }
    val choices =
        listOf(
            Choice("aspect", stringResource(R.string.player_aspect), emptyList(), false, {
                actions.updateSettings { it.copy(aspect = it.aspect.next()) }
            }, value = settings.aspect.label()),
            Choice("segments", stringResource(R.string.player_segments), emptyList(), false, {
                actions.updateSettings { it.copy(segments = it.segments.next()) }
            }, value = settings.segments.label()),
            Choice("size", stringResource(R.string.player_subtitle_size), emptyList(), false, {
                subtitles { it.copy(size = it.size.next()) }
            }, value = style.size.label()),
            Choice("color", stringResource(R.string.player_subtitle_color), emptyList(), false, {
                subtitles { it.copy(color = it.color.next()) }
            }, value = style.color.label()),
            Choice("edge", stringResource(R.string.player_subtitle_edge), emptyList(), false, {
                subtitles { it.copy(edge = it.edge.next()) }
            }, value = style.edge.label()),
            Choice("background", stringResource(R.string.player_subtitle_background), emptyList(), false, {
                subtitles { it.copy(background = it.background.next()) }
            }, value = style.background.label()),
            Choice("position", stringResource(R.string.player_subtitle_position), emptyList(), false, {
                subtitles { it.copy(position = it.position.next()) }
            }, value = style.position.label()),
        )
    val first = remember { FocusRequester() }
    ChoiceColumn(0, null, choices, initialFocus = first)
    InitialFocus(first, key = enabled, enabled = enabled)
}

/** The next value of a setting, back to the first after the last. */
inline fun <reified T : Enum<T>> T.next(): T = enumValues<T>().let { it[(ordinal + 1) % it.size] }

/**
 * The subtitles as they will look, over the picture at the left of the panel ([beside]), or over
 * the whole of a picture.
 */
@Composable
fun SubtitleSample(
    style: SubtitleStyle,
    beside: Boolean = true,
) {
    Box(Modifier.fillMaxSize().padding(end = if (beside) CineSizes.widePanelWidth else CineSpacing.none)) {
        SubtitlePreview(
            text = stringResource(R.string.player_subtitle_sample),
            color = Color(style.color.argb),
            background = Color(style.background.argb),
            outline = style.edge == SubtitleEdge.Outline,
            shadow = style.edge == SubtitleEdge.Shadow,
            sizeFraction = style.size.fraction,
            bottomFraction = style.position.bottomFraction,
        )
    }
}

@Composable
private fun ChaptersPanel(
    state: PlayerState,
    actions: PlayerActions,
    enabled: Boolean,
    onDone: () -> Unit,
) {
    val current = state.chapterIndex
    val choices =
        state.chapters.mapIndexed { index, chapter ->
            Choice(
                key = "chapter-$index",
                title = chapter.name ?: (index + 1).toString(),
                pills = emptyList(),
                checked = index == current,
                onClick = {
                    actions.seekTo(chapter.start)
                    onDone()
                },
                value = chapter.start.toClockText(),
            )
        }
    val first = remember { FocusRequester() }
    ChoiceColumn(0, null, choices, initialFocus = first)
    InitialFocus(first, key = enabled, enabled = enabled)
}

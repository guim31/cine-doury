package fr.guim.cinedoury.feature.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import fr.guim.cinedoury.core.designsystem.component.CineIcons
import fr.guim.cinedoury.core.designsystem.component.InitialFocus
import fr.guim.cinedoury.core.designsystem.component.ListRow
import fr.guim.cinedoury.core.designsystem.component.ListSectionTitle
import fr.guim.cinedoury.core.designsystem.component.RowState
import fr.guim.cinedoury.core.designsystem.component.SidePanel
import fr.guim.cinedoury.core.designsystem.component.StepperRow
import fr.guim.cinedoury.core.designsystem.component.SubtitleBar
import fr.guim.cinedoury.core.designsystem.component.SubtitlePreview
import fr.guim.cinedoury.core.designsystem.component.rememberCineFocus
import fr.guim.cinedoury.core.designsystem.theme.CineMotion
import fr.guim.cinedoury.core.designsystem.theme.CineSizes
import fr.guim.cinedoury.core.designsystem.theme.CineSpacing
import fr.guim.cinedoury.core.model.MediaStream
import fr.guim.cinedoury.core.model.Qualities
import fr.guim.cinedoury.core.model.SubtitleBackground
import fr.guim.cinedoury.core.model.SubtitleEdge
import fr.guim.cinedoury.core.model.SubtitleScale
import fr.guim.cinedoury.core.model.SubtitleStyle
import fr.guim.cinedoury.core.source.GestureLog
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
    AnimatedVisibility(
        visible = panel == PlayerPanel.SubtitleSize,
        enter = fadeIn(CineMotion.fade(CineMotion.OSD_FADE_MILLIS)),
        exit = fadeOut(CineMotion.fade(CineMotion.OSD_FADE_MILLIS)),
    ) {
        SubtitleSizeBar(state, actions, enabled = panel == PlayerPanel.SubtitleSize)
    }
    if (shown == PlayerPanel.SubtitleSize) return
    val title =
        stringResource(
            when (shown) {
                PlayerPanel.Tracks, PlayerPanel.SubtitleSize, null -> R.string.player_tracks
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
                TracksPanel(state, actions, enabled = panel != null, onPanel)
            }

            PlayerPanel.Quality -> {
                QualityPanel(state, actions, enabled = panel != null) { onPanel(null) }
            }

            PlayerPanel.Settings -> {
                SettingsPanel(state, actions, enabled = panel != null, onPanel)
            }

            PlayerPanel.Chapters -> {
                ChaptersPanel(state, actions, enabled = panel != null) { onPanel(null) }
            }

            PlayerPanel.SubtitleSize, null -> {}
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
    // What `ci/couch-gestures.sh` needs to choose a line and to put the choice back (`GestureLog`).
    LaunchedEffect(column) { GestureLog.mark("column $column lines=${choices.size} checked=$initial") }
    LazyColumn(
        modifier = modifier,
        // A lazy list clips: room for the focus of its first and last lines.
        contentPadding = PaddingValues(vertical = CineSizes.focusRoom),
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
                modifier = Modifier.testTag(playerPanelLine(column, index)).logFocus(playerPanelLine(column, index)),
                state = RowState.Option(choice.checked),
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
    onPanel: (PlayerPanel?) -> Unit,
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
            } +
            subtitleLook(state) { onPanel(PlayerPanel.SubtitleSize) }
    val first = remember { FocusRequester() }
    Row(horizontalArrangement = Arrangement.spacedBy(CineSpacing.m)) {
        ChoiceColumn(0, stringResource(R.string.player_audio), audio, Modifier.weight(1f), first)
        ChoiceColumn(1, stringResource(R.string.player_subtitles), subtitles, Modifier.weight(1f))
    }
    InitialFocus(first, key = enabled, enabled = enabled)
}

/** « Taille, position et fond », with the size: the bar over the picture. */
@Composable
private fun subtitleLook(
    state: PlayerState,
    onClick: () -> Unit,
) = Choice(
    key = "look",
    title = stringResource(R.string.player_subtitle_look),
    pills = emptyList(),
    checked = false,
    onClick = onClick,
    description =
        stringResource(
            if (state.subtitlesBurnedIn) R.string.player_subtitle_fixed else R.string.player_subtitle_hint,
        ),
    value = if (state.subtitlesBurnedIn) null else state.settings.subtitles.scaleLabel(),
)

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
    onPanel: (PlayerPanel?) -> Unit,
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
            subtitleLook(state) { onPanel(PlayerPanel.SubtitleSize) },
            Choice("color", stringResource(R.string.player_subtitle_color), emptyList(), false, {
                subtitles { it.copy(color = it.color.next()) }
            }, value = style.color.label()),
            Choice("edge", stringResource(R.string.player_subtitle_edge), emptyList(), false, {
                subtitles { it.copy(edge = it.edge.next()) }
            }, value = style.edge.label()),
        )
    val first = remember { FocusRequester() }
    ChoiceColumn(0, null, choices, initialFocus = first)
    InitialFocus(first, key = enabled, enabled = enabled)
}

/**
 * The size, the height and the background of the subtitles, over the top of the picture so that they
 * stay in sight (`UI.md` § 8): three settings side by side. ← and → go from one to the next, ↑ and ↓
 * change the one focused, at once (essai réel 3: ← and → changed the size, and nothing reached the two
 * others). A line shows while no subtitle is on screen. Subtitles burned into the picture cannot
 * change: the bar says so.
 */
@Composable
private fun SubtitleSizeBar(
    state: PlayerState,
    actions: PlayerActions,
    enabled: Boolean,
) {
    val style = state.settings.subtitles
    val fixed = state.subtitlesBurnedIn

    fun subtitles(change: (SubtitleStyle) -> SubtitleStyle) =
        actions.updateSettings {
            it.copy(subtitles = change(it.subtitles))
        }
    val first = remember { FocusRequester() }
    Box(Modifier.fillMaxSize().padding(top = CineSpacing.safeVertical), contentAlignment = Alignment.TopCenter) {
        SubtitleBar(
            note = stringResource(if (fixed) R.string.player_subtitle_fixed else R.string.player_subtitle_bar_hint),
            modifier = Modifier.testTag(PlayerPreview.SUBTITLE_BAR),
        ) {
            StepperRow(
                title = stringResource(R.string.player_subtitle_size_short),
                value = style.scaleLabel(),
                onStep = { forward -> if (!fixed) subtitles { it.step(forward) } },
                canDecrease = !fixed && style.scale > SubtitleScale.MIN,
                canIncrease = !fixed && style.scale < SubtitleScale.MAX,
                icon = CineIcons.TextSize,
                focusRequester = first,
                focus = rememberCineFocus(playerPanelLine(0, 0)),
                vertical = true,
                modifier = Modifier.weight(1.2f).testTag(playerPanelLine(0, 0)),
            )
            if (fixed) {
                // The size alone keeps its width: the rest of the bar holds nothing to set.
                Spacer(Modifier.weight(2f))
            } else {
                StepperRow(
                    title = stringResource(R.string.player_subtitle_position),
                    value = style.position.label(),
                    onStep = { forward -> subtitles { it.copy(position = it.position.stepped(forward)) } },
                    canDecrease = style.position.canStep(forward = false),
                    canIncrease = style.position.canStep(forward = true),
                    focus = rememberCineFocus(playerPanelLine(0, 1)),
                    vertical = true,
                    modifier = Modifier.weight(1f).testTag(playerPanelLine(0, 1)),
                )
                StepperRow(
                    title = stringResource(R.string.player_subtitle_background),
                    value = style.background.label(),
                    onStep = { forward -> subtitles { it.copy(background = it.background.stepped(forward)) } },
                    canDecrease = style.background.canStep(forward = false),
                    canIncrease = style.background.canStep(forward = true),
                    focus = rememberCineFocus(playerPanelLine(0, 2)),
                    vertical = true,
                    modifier = Modifier.weight(1f).testTag(playerPanelLine(0, 2)),
                )
            }
        }
    }
    InitialFocus(first, key = enabled, enabled = enabled)
}

/** The value one step further ([forward]) or back, stopping at the ends. */
inline fun <reified T : Enum<T>> T.stepped(forward: Boolean): T =
    enumValues<T>().let { it[(ordinal + if (forward) 1 else -1).coerceIn(0, it.lastIndex)] }

/** Whether there is a value one step further ([forward]) or back. */
inline fun <reified T : Enum<T>> T.canStep(forward: Boolean): Boolean =
    if (forward) ordinal < enumValues<T>().lastIndex else ordinal > 0

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
            sizeFraction = style.textFraction,
            bottomFraction = style.position.bottomFraction,
            band = style.background == SubtitleBackground.Band,
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

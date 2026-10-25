package fr.guim.cinedoury.feature.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import fr.guim.cinedoury.core.designsystem.component.CineIcons
import fr.guim.cinedoury.core.designsystem.component.InitialFocus
import fr.guim.cinedoury.core.designsystem.component.ListRow
import fr.guim.cinedoury.core.designsystem.component.ListSectionTitle
import fr.guim.cinedoury.core.designsystem.component.SidePanel
import fr.guim.cinedoury.core.designsystem.component.previewFirstVisible
import fr.guim.cinedoury.core.designsystem.component.rememberCineFocus
import fr.guim.cinedoury.core.designsystem.theme.CineSizes
import fr.guim.cinedoury.core.designsystem.theme.CineSpacing
import fr.guim.cinedoury.core.model.ItemKind
import fr.guim.cinedoury.core.model.MediaSource
import fr.guim.cinedoury.core.model.StreamType
import fr.guim.cinedoury.core.ui.SEPARATOR
import fr.guim.cinedoury.core.ui.describe
import fr.guim.cinedoury.core.ui.sizeText

/** A line of a panel: a section title, or a row that may be chosen. */
@Immutable
private sealed interface PanelLine {
    val key: String

    data class Section(
        override val key: String,
        val title: String,
    ) : PanelLine

    data class Choice(
        override val key: String,
        val title: String,
        val onClick: () -> Unit,
        val description: String? = null,
        val checked: Boolean = false,
        val icon: ImageVector? = null,
        /** Where the focus lands when the panel opens. */
        val initial: Boolean = false,
    ) : PanelLine
}

/** Preview tag of the line of a panel at [index]. */
fun panelLine(index: Int) = "$PANEL_LINE$index"

private const val PANEL_LINE = "details-panel-"

/**
 * The panels of a details screen, over its right side (`UI.md` § 7): the version and the tracks
 * before playback, « ⋯ », the information on the file.
 */
@Composable
internal fun DetailsPanels(
    panel: DetailsPanel?,
    state: DetailsState,
    actions: DetailsActions,
    onPanel: (DetailsPanel?) -> Unit,
    onPlay: () -> Unit,
) {
    // While the panel fades out, it keeps its lines: none of them leaves with the focus on it.
    var last by remember { mutableStateOf(panel) }
    SideEffect { if (panel != null) last = panel }
    val shown = panel ?: last
    val title =
        when (shown) {
            DetailsPanel.Tracks -> stringResource(R.string.panel_tracks)
            DetailsPanel.More -> state.item.title
            DetailsPanel.FileInfo -> stringResource(R.string.panel_file)
            null -> ""
        }
    val lines =
        when (shown) {
            DetailsPanel.Tracks -> trackLines(state, actions, onPlay)
            DetailsPanel.More -> moreLines(state, actions, onPanel)
            DetailsPanel.FileInfo -> fileLines(state.item.mediaSources)
            null -> emptyList()
        }
    val wide = shown != DetailsPanel.More
    SidePanel(
        visible = panel != null,
        title = title,
        width = if (wide) CineSizes.widePanelWidth else CineSizes.panelWidth,
        modifier = Modifier.testTag("details-panel"),
    ) {
        val first = remember(shown) { FocusRequester() }
        val initial =
            lines.indexOfFirst { it is PanelLine.Choice && it.initial }.takeIf { it >= 0 }
                ?: lines.indexOfFirst { it is PanelLine.Choice }
        LazyColumn(
            state = rememberLazyListState(initialFirstVisibleItemIndex = previewFirstVisible(PANEL_LINE)),
            contentPadding = PaddingValues(vertical = CineSpacing.xs, horizontal = CineSpacing.xs),
            verticalArrangement = Arrangement.spacedBy(CineSpacing.xs),
        ) {
            itemsIndexed(lines, key = { _, line -> line.key }) { index, line ->
                when (line) {
                    is PanelLine.Section -> {
                        ListSectionTitle(line.title)
                    }

                    is PanelLine.Choice -> {
                        ListRow(
                            title = line.title,
                            description = line.description,
                            icon = line.icon,
                            trailingIcon = if (line.checked) CineIcons.Watched else null,
                            onClick = line.onClick,
                            focusRequester = if (index == initial) first else null,
                            focus = rememberCineFocus(panelLine(index)),
                            modifier = Modifier.testTag(panelLine(index)),
                        )
                    }
                }
            }
        }
        InitialFocus(first, key = panel, enabled = panel != null && initial >= 0)
    }
}

/**
 * The version (when there are several), the audio tracks, the subtitles (or none), each described in
 * words, the choice of the profile checked; « Lire » at the top plays with them.
 */
@Composable
private fun trackLines(
    state: DetailsState,
    actions: DetailsActions,
    onPlay: () -> Unit,
): List<PanelLine> {
    val selection = state.selection
    val source = state.source ?: return emptyList()
    return buildList {
        add(PanelLine.Choice("play", stringResource(R.string.action_play_with_tracks), onPlay, icon = CineIcons.Play))
        if (state.item.mediaSources.size > 1) {
            add(PanelLine.Section("versions", stringResource(R.string.panel_version)))
            state.item.mediaSources.forEach { version ->
                val chosen = version.id == source.id
                add(
                    PanelLine.Choice(
                        key = "version-${version.id}",
                        title = version.describe(),
                        onClick = { actions.selectVersion(version.id) },
                        description = version.name,
                        checked = chosen,
                        initial = chosen,
                    ),
                )
            }
        }
        add(PanelLine.Section("audio", stringResource(R.string.panel_audio)))
        source.audio.forEach { track ->
            val chosen = selection?.audioIndex == track.index
            add(
                PanelLine.Choice(
                    "audio-${track.index}",
                    track.describe(),
                    { actions.selectAudio(track.index) },
                    checked = chosen,
                    initial = chosen && state.item.mediaSources.size <= 1,
                ),
            )
        }
        add(PanelLine.Section("subtitles", stringResource(R.string.panel_subtitles)))
        add(
            PanelLine.Choice(
                "subtitles-none",
                stringResource(R.string.subtitles_none),
                { actions.selectSubtitles(null) },
                checked = selection?.subtitleIndex == null,
            ),
        )
        source.subtitles.forEach { track ->
            add(
                PanelLine.Choice(
                    "subtitles-${track.index}",
                    track.describe(),
                    { actions.selectSubtitles(track.index) },
                    checked = selection?.subtitleIndex == track.index,
                ),
            )
        }
    }
}

/** « ⋯ »: no management before J6, only « Marquer comme non vu » and the file. */
@Composable
private fun moreLines(
    state: DetailsState,
    actions: DetailsActions,
    onPanel: (DetailsPanel?) -> Unit,
): List<PanelLine> {
    val item = state.item
    val watched = item.userData?.played == true
    return buildList {
        add(
            PanelLine.Choice(
                "watched",
                stringResource(if (watched) R.string.action_unwatched else R.string.action_watched),
                {
                    actions.toggleWatched(item)
                    onPanel(null)
                },
                icon = if (watched) CineIcons.Unwatched else CineIcons.Watched,
            ),
        )
        if (item.kind != ItemKind.Series && item.mediaSources.isNotEmpty()) {
            add(
                PanelLine.Choice(
                    "file",
                    stringResource(R.string.action_file_info),
                    { onPanel(DetailsPanel.FileInfo) },
                    icon = CineIcons.FileInfo,
                ),
            )
        }
    }
}

/** For each version: its path, container, size and bitrate, then each stream as the server describes it. */
@Composable
private fun fileLines(sources: List<MediaSource>): List<PanelLine> =
    buildList {
        sources.forEach { source ->
            if (sources.size > 1) add(PanelLine.Section("source-${source.id}", source.describe()))
            val facts =
                listOfNotNull(
                    source.container?.uppercase(),
                    source.sizeBytes?.sizeText(),
                    source.bitrate?.let { stringResource(R.string.bitrate, it / KILO / KILO) },
                ).joinToString(SEPARATOR)
            add(
                PanelLine.Choice("path-${source.id}", source.path ?: source.name.orEmpty(), {
                }, description = facts, icon = CineIcons.FileInfo),
            )
            source.streams.filter { it.type != StreamType.Other }.forEach { stream ->
                add(
                    PanelLine.Choice(
                        key = "stream-${source.id}-${stream.index}",
                        title = stream.describe(),
                        onClick = {},
                        description = stream.displayTitle,
                        icon =
                            when (stream.type) {
                                StreamType.Video -> CineIcons.Movies
                                StreamType.Audio -> CineIcons.Audio
                                else -> CineIcons.Tracks
                            },
                    ),
                )
            }
        }
    }

private const val KILO = 1_000

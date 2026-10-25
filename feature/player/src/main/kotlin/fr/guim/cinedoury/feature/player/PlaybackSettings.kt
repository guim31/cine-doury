package fr.guim.cinedoury.feature.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Text
import fr.guim.cinedoury.core.designsystem.component.GeneratedArtwork
import fr.guim.cinedoury.core.designsystem.component.InitialFocus
import fr.guim.cinedoury.core.designsystem.component.ListRow
import fr.guim.cinedoury.core.designsystem.component.ListSectionTitle
import fr.guim.cinedoury.core.designsystem.component.StepperRow
import fr.guim.cinedoury.core.designsystem.component.rememberCineFocus
import fr.guim.cinedoury.core.designsystem.theme.CineSizes
import fr.guim.cinedoury.core.designsystem.theme.CineSpacing
import fr.guim.cinedoury.core.designsystem.theme.CineTheme
import fr.guim.cinedoury.core.model.DeviceCapabilities
import fr.guim.cinedoury.core.model.PassthroughFormat
import fr.guim.cinedoury.core.model.PlaybackProfile
import fr.guim.cinedoury.core.model.PlaybackProfiles
import fr.guim.cinedoury.core.model.PlayerSettings
import fr.guim.cinedoury.core.model.SubtitleStyle
import fr.guim.cinedoury.core.playback.DeviceCapabilitiesReader
import fr.guim.cinedoury.core.playback.PlayerSettingsStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/** The settings of the player on this device, with what the device can do. */
@Immutable
data class PlaybackSettingsState(
    val settings: PlayerSettings = PlayerSettings(),
    val capabilities: DeviceCapabilities? = null,
) {
    val profile: PlaybackProfile? get() = capabilities?.let { PlaybackProfiles.build(it, settings.profileOptions()) }
}

/**
 * The settings of the player (`FONCTIONNALITES.md` § 12, a simple page until J7) and the profile of
 * the device (debug builds). The capabilities are read again each time a page opens: an amplifier
 * may have been turned on.
 */
class PlaybackSettingsViewModel(
    private val store: PlayerSettingsStore,
    reader: DeviceCapabilitiesReader,
) : ViewModel() {
    private val capabilities = reader.read()

    val state: StateFlow<PlaybackSettingsState> =
        store.settings
            .map { PlaybackSettingsState(it, capabilities) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, PlaybackSettingsState(capabilities = capabilities))

    fun update(change: (PlayerSettings) -> PlayerSettings) {
        viewModelScope.launch { store.update(change) }
    }
}

/** Preview tags of the settings of the player. */
object PlaybackSettingsPreview {
    fun row(index: Int) = "playback-settings-$index"
}

/** A row of a page of settings: a title, its explanation, its value; OK changes it. */
private data class SettingRow(
    val key: String,
    val title: String,
    val description: String?,
    val value: String?,
    val onClick: () -> Unit,
    /** ← and → change the value (a size), instead of OK. */
    val onStep: ((forward: Boolean) -> Unit)? = null,
)

private sealed interface SettingLine {
    data class Section(
        val title: String,
    ) : SettingLine

    data class Row(
        val row: SettingRow,
    ) : SettingLine
}

@Composable
fun PlaybackSettingsRoute(
    onBack: () -> Unit,
    viewModel: PlaybackSettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)
    PlaybackSettingsScreen(state, viewModel::update)
}

/**
 * « Lecture et son »: what goes to the amplifier, format by format (« La sortie ne le prend pas »
 * when the HDMI output does not offer it), then the frame rate, the intro, the next episode,
 * « toujours là », the threshold of « vu », the jumps and the ASS subtitles.
 */
@Composable
fun PlaybackSettingsScreen(
    state: PlaybackSettingsState,
    onChange: ((PlayerSettings) -> PlayerSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings = state.settings
    val output = state.capabilities?.passthrough.orEmpty()
    val on = stringResource(R.string.setting_on)
    val off = stringResource(R.string.setting_off)
    val lines =
        buildList {
            add(SettingLine.Section(stringResource(R.string.settings_sound)))
            PassthroughFormat.entries.forEach { format ->
                val enabled = format in settings.passthrough
                val available = format in output
                add(
                    SettingLine.Row(
                        SettingRow(
                            key = "passthrough-${format.name}",
                            title = format.label(),
                            description =
                                stringResource(
                                    if (available) R.string.passthrough_available else R.string.passthrough_unavailable,
                                ),
                            value = if (enabled) on else off,
                            onClick = {
                                onChange {
                                    it.copy(
                                        passthrough =
                                            if (format in
                                                it.passthrough
                                            ) {
                                                it.passthrough - format
                                            } else {
                                                it.passthrough + format
                                            },
                                    )
                                }
                            },
                        ),
                    ),
                )
            }
            add(SettingLine.Section(stringResource(R.string.settings_playback)))
            add(
                row(
                    "frame-rate",
                    R.string.setting_frame_rate,
                    R.string.setting_frame_rate_description,
                    if (settings.matchFrameRate) on else off,
                ) {
                    onChange { it.copy(matchFrameRate = !it.matchFrameRate) }
                },
            )
            add(
                row(
                    "segments",
                    R.string.player_segments,
                    R.string.setting_segments_description,
                    settings.segments.label(),
                ) {
                    onChange { it.copy(segments = it.segments.next()) }
                },
            )
            add(
                row(
                    "autoplay",
                    R.string.setting_autoplay,
                    R.string.setting_autoplay_description,
                    if (settings.autoplayNext) on else off,
                ) {
                    onChange { it.copy(autoplayNext = !it.autoplayNext) }
                },
            )
            add(
                row(
                    "still",
                    R.string.setting_still_watching,
                    R.string.setting_still_watching_description,
                    if (settings.stillWatchingAfter == 0) {
                        stringResource(R.string.setting_never)
                    } else {
                        stringResource(R.string.setting_episodes, settings.stillWatchingAfter)
                    },
                ) {
                    onChange {
                        it.copy(
                            stillWatchingAfter = it.stillWatchingAfter.nextOf(PlayerSettings.STILL_WATCHING_STEPS),
                        )
                    }
                },
            )
            add(
                row(
                    "watched",
                    R.string.setting_watched,
                    R.string.setting_watched_description,
                    stringResource(R.string.setting_percent, (settings.watchedThreshold * PERCENT).toInt()),
                ) {
                    onChange {
                        it.copy(
                            watchedThreshold = it.watchedThreshold.nextOf(PlayerSettings.WATCHED_THRESHOLDS),
                        )
                    }
                },
            )
            add(
                row(
                    "back",
                    R.string.setting_seek_back,
                    null,
                    stringResource(R.string.setting_seconds, settings.seekBackSeconds),
                ) {
                    onChange { it.copy(seekBackSeconds = it.seekBackSeconds.nextOf(PlayerSettings.SEEK_STEPS)) }
                },
            )
            add(
                row(
                    "forward",
                    R.string.setting_seek_forward,
                    null,
                    stringResource(R.string.setting_seconds, settings.seekForwardSeconds),
                ) {
                    onChange { it.copy(seekForwardSeconds = it.seekForwardSeconds.nextOf(PlayerSettings.SEEK_STEPS)) }
                },
            )
            add(
                row(
                    "ass",
                    R.string.setting_burn_in,
                    R.string.setting_burn_in_description,
                    if (settings.burnInAss) on else off,
                ) {
                    onChange { it.copy(burnInAss = !it.burnInAss) }
                },
            )
        }
    SettingsPage(stringResource(R.string.settings_playback_title), lines, modifier)
}

@Composable
private fun row(
    key: String,
    title: Int,
    description: Int?,
    value: String,
    onClick: () -> Unit,
) = SettingLine.Row(SettingRow(key, stringResource(title), description?.let { stringResource(it) }, value, onClick))

private fun <T> T.nextOf(steps: List<T>): T = steps[(steps.indexOf(this) + 1) % steps.size]

private const val PERCENT = 100

@Composable
fun PassthroughFormat.label(): String =
    stringResource(
        when (this) {
            PassthroughFormat.Ac3 -> R.string.passthrough_ac3
            PassthroughFormat.Eac3 -> R.string.passthrough_eac3
            PassthroughFormat.Eac3Atmos -> R.string.passthrough_eac3_atmos
            PassthroughFormat.Dts -> R.string.passthrough_dts
            PassthroughFormat.DtsHd -> R.string.passthrough_dts_hd
            PassthroughFormat.TrueHd -> R.string.passthrough_truehd
        },
    )

@Composable
fun SubtitleSettingsRoute(
    onBack: () -> Unit,
    viewModel: PlaybackSettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)
    SubtitleSettingsScreen(
        style = state.settings.subtitles,
        onChange = { change -> viewModel.update { it.copy(subtitles = change(it.subtitles)) } },
    )
}

/** « Apparence des sous-titres », with the subtitles over a still picture at the right. */
@Composable
fun SubtitleSettingsScreen(
    style: SubtitleStyle,
    onChange: ((SubtitleStyle) -> SubtitleStyle) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lines =
        listOf(
            SettingLine.Row(
                SettingRow(
                    key = "size",
                    title = stringResource(R.string.player_subtitle_size),
                    description = null,
                    value = style.scaleLabel(),
                    onClick = {},
                    onStep = { forward -> onChange { it.step(forward) } },
                ),
            ),
            row("color", R.string.player_subtitle_color, null, style.color.label()) {
                onChange { it.copy(color = it.color.next()) }
            },
            row(
                "edge",
                R.string.player_subtitle_edge,
                null,
                style.edge.label(),
            ) { onChange { it.copy(edge = it.edge.next()) } },
            row("background", R.string.player_subtitle_background, null, style.background.label()) {
                onChange { it.copy(background = it.background.next()) }
            },
            row("position", R.string.player_subtitle_position, null, style.position.label()) {
                onChange { it.copy(position = it.position.next()) }
            },
        )
    Box(modifier.fillMaxSize()) {
        SettingsPage(stringResource(R.string.settings_subtitles_title), lines, Modifier)
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(end = CineSpacing.safeHorizontal, top = CineSizes.subtitlePreviewTop)
                .size(CineSizes.subtitlePreview)
                .clip(PreviewShape),
        ) {
            GeneratedArtwork(stringResource(R.string.settings_subtitles_title))
            SubtitleSample(style, beside = false)
        }
    }
}

private val PreviewShape = RoundedCornerShape(CineSizes.cardCorner)

@Composable
private fun SettingsPage(
    title: String,
    lines: List<SettingLine>,
    modifier: Modifier,
) {
    val first = remember { FocusRequester() }
    Row(
        modifier
            .fillMaxSize()
            .background(CineTheme.colors.background)
            .padding(start = CineSpacing.safeHorizontal * 2, end = CineSpacing.safeHorizontal),
    ) {
        Text(
            text = title,
            style = CineTheme.typography.title1,
            color = CineTheme.colors.text,
            modifier =
                Modifier
                    .padding(
                        top = CineSpacing.safeVertical + CineSpacing.m,
                        end = CineSpacing.xl,
                    ).width(CineSizes.avatarLarge * 2),
        )
        LazyColumn(
            state = rememberLazyListState(),
            modifier = Modifier.width(CineSizes.settingsWidth).fillMaxHeight().testTag("playback-settings"),
            contentPadding =
                PaddingValues(
                    top = CineSpacing.safeVertical + CineSpacing.m,
                    bottom =
                        CineSpacing.safeVertical + CineSpacing.xxl,
                ),
            verticalArrangement = Arrangement.spacedBy(CineSpacing.xs),
        ) {
            settingLines(lines, first)
        }
    }
    InitialFocus(first)
}

private fun LazyListScope.settingLines(
    lines: List<SettingLine>,
    first: FocusRequester,
) {
    val firstRow = lines.indexOfFirst { it is SettingLine.Row }
    lines.forEachIndexed { index, line ->
        when (line) {
            is SettingLine.Section -> {
                item(key = "section-$index") { ListSectionTitle(line.title) }
            }

            is SettingLine.Row -> {
                item(key = line.row.key) {
                    val onStep = line.row.onStep
                    if (onStep != null) {
                        StepperRow(
                            title = line.row.title,
                            value = line.row.value.orEmpty(),
                            onStep = onStep,
                            focusRequester = if (index == firstRow) first else null,
                            focus = rememberCineFocus(PlaybackSettingsPreview.row(index)),
                            modifier = Modifier.fillMaxWidth().testTag(PlaybackSettingsPreview.row(index)),
                        )
                        return@item
                    }
                    ListRow(
                        title = line.row.title,
                        description = line.row.description,
                        value = line.row.value,
                        onClick = line.row.onClick,
                        focusRequester = if (index == firstRow) first else null,
                        focus = rememberCineFocus(PlaybackSettingsPreview.row(index)),
                        modifier = Modifier.testTag(PlaybackSettingsPreview.row(index)),
                    )
                }
            }
        }
    }
}

@Composable
fun DeviceProfileRoute(
    onBack: () -> Unit,
    viewModel: PlaybackSettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)
    DeviceProfileScreen(state)
}

/**
 * The profile of the device (debug builds, `SPEC.md` § 4.11): what was read on the device and what
 * the server is told, in full, to diagnose a device from a photo of the screen.
 */
@Composable
fun DeviceProfileScreen(
    state: PlaybackSettingsState,
    modifier: Modifier = Modifier,
) {
    val device = state.capabilities ?: return
    val profile = state.profile ?: return
    val lines = deviceProfileLines(device, profile)
    val first = remember { FocusRequester() }
    Column(
        modifier
            .fillMaxSize()
            .background(CineTheme.colors.background)
            .padding(horizontal = CineSpacing.safeHorizontal, vertical = CineSpacing.safeVertical),
        verticalArrangement = Arrangement.spacedBy(CineSpacing.xs),
    ) {
        Text(
            text = stringResource(R.string.device_profile_title),
            style = CineTheme.typography.title1,
            color = CineTheme.colors.text,
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("device-profile"),
            verticalArrangement = Arrangement.spacedBy(CineSpacing.xs / 2),
        ) {
            lines.forEachIndexed { index, (label, value) ->
                item(key = "line-$index") {
                    ListRow(
                        title = label,
                        description = value,
                        onClick = {},
                        wrapTitle = true,
                        focusRequester = if (index == 0) first else null,
                        focus = rememberCineFocus("device-profile-$index"),
                    )
                }
            }
        }
    }
    InitialFocus(first)
}

/** One line per fact: the device, the screen, each decoder, the audio, then the profile sent. */
@Composable
private fun deviceProfileLines(
    device: DeviceCapabilities,
    profile: PlaybackProfile,
): List<Pair<String, String>> =
    buildList {
        add(stringResource(R.string.info_device) to "${device.device} · Android ${device.system}")
        val display = device.display
        add(
            stringResource(R.string.info_screen) to
                "${display.width} × ${display.height} · ${display.hdr.joinToString(
                    ", ",
                ) { it.name }.ifEmpty { "SDR" }} · " +
                display.refreshRates.joinToString(", ") { "%.3f".format(java.util.Locale.ROOT, it) },
        )
        device.videoDecoders.forEach { decoder ->
            add(
                "${decoder.codec.name} · ${decoder.name}" to
                    listOfNotNull(
                        stringResource(if (decoder.hardware) R.string.device_hardware else R.string.device_software),
                        decoder.profiles
                            .sorted()
                            .joinToString(", ")
                            .ifEmpty { null },
                        decoder.maxLevel?.let { stringResource(R.string.device_level, it) },
                        "${decoder.maxWidth} × ${decoder.maxHeight}",
                        stringResource(R.string.device_bits, decoder.maxBitDepth),
                    ).joinToString(" · "),
            )
        }
        add(
            stringResource(R.string.device_dolby_vision) to
                device.dolbyVisionProfiles
                    .sorted()
                    .joinToString(", ")
                    .ifEmpty { "—" },
        )
        add(stringResource(R.string.device_audio_decoders) to device.audioDecoders.joinToString(", ") { it.codecName })
        add(
            stringResource(R.string.device_passthrough) to
                device.passthrough.joinToString(", ") { it.name }.ifEmpty { "—" },
        )
        add(stringResource(R.string.device_channels) to device.maxPcmChannels.toString())
        add(stringResource(R.string.device_subtitles) to device.subtitleFormats.joinToString(", ") { it.codecName })
        add(stringResource(R.string.info_profile) to profile.name)
        profile.video.forEach { rule ->
            add(
                stringResource(R.string.device_direct_video, rule.codec) to
                    listOfNotNull(
                        rule.profiles.joinToString(", ").ifEmpty { null },
                        rule.maxLevel?.let { stringResource(R.string.device_level, it) },
                        "≤ ${rule.maxWidth} × ${rule.maxHeight}",
                        stringResource(R.string.device_bits, rule.maxBitDepth),
                        rule.ranges.joinToString(", ") { it.serverName },
                    ).joinToString(" · "),
            )
        }
        add(
            stringResource(R.string.device_direct_audio) to
                "${profile.audioCodecs.joinToString(", ")} · ≤ ${profile.maxAudioChannels}",
        )
        add(
            stringResource(R.string.device_transcode) to
                "${profile.transcodeContainer} (HLS) · ${profile.transcodeVideoCodecs.joinToString(", ")} · " +
                "${profile.transcodeAudioCodecs.joinToString(", ")} · ${profile.maxBitrate / MEGABIT} Mb/s",
        )
        add(
            stringResource(R.string.device_subtitle_rules) to
                profile.subtitles.joinToString(", ") { "${it.format} ${it.method.name.lowercase()}" },
        )
    }

private const val MEGABIT = 1_000_000

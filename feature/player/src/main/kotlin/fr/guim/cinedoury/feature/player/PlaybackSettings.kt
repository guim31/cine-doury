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
import fr.guim.cinedoury.core.designsystem.component.asHeading
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
 * The profile of the device (debug builds); the settings of the player are in the pages of
 * `:feature:settings` since J7. The capabilities are read again each time a page opens: an amplifier
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
            modifier = Modifier.asHeading(),
            color = CineTheme.colors.text,
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("device-profile"),
            contentPadding = PaddingValues(vertical = CineSizes.focusRoom),
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
        val ignored = display.announcedHdr - display.hdr
        if (ignored.isNotEmpty()) {
            add(stringResource(R.string.info_screen_ignored_hdr) to ignored.joinToString(", ") { it.name })
        }
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

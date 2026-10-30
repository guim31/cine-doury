package fr.guim.cinedoury.feature.player.mobile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.guim.cinedoury.core.designsystem.component.CineIcons
import fr.guim.cinedoury.core.designsystem.mobile.MobileListRow
import fr.guim.cinedoury.core.designsystem.mobile.MobileSheet
import fr.guim.cinedoury.core.designsystem.mobile.MobileSheetLine
import fr.guim.cinedoury.core.designsystem.mobile.MobileSheetLines
import fr.guim.cinedoury.core.designsystem.theme.CineSpacing
import fr.guim.cinedoury.core.designsystem.theme.CineTheme
import fr.guim.cinedoury.core.designsystem.theme.MobileSizes
import fr.guim.cinedoury.core.model.MarginPlan
import fr.guim.cinedoury.core.model.PlayerSettings
import fr.guim.cinedoury.core.model.Qualities
import fr.guim.cinedoury.feature.player.PlaybackSettingsViewModel
import fr.guim.cinedoury.feature.player.R
import fr.guim.cinedoury.feature.player.hourlyEstimate
import fr.guim.cinedoury.feature.player.megabits
import org.koin.androidx.compose.koinViewModel

/** Tags of the playback settings of the phone, for tests and screenshots. */
object MobilePlaybackTags {
    const val MARGIN = "setting-margin"
    const val WIFI = "setting-quality-wifi"
    const val MOBILE = "setting-quality-mobile"
}

/** Which choice the screenshots open. */
enum class MobilePlaybackSheet { Margin, Wifi, Mobile }

@Composable
fun MobilePlaybackSettingsRoute(viewModel: PlaybackSettingsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MobilePlaybackSettings(state.settings, viewModel::update)
}

/**
 * The playback settings of this phone (M2, `SPEC.md` § 4.27), in the profile tab: « Marge de lecture »
 * (5, 15 or 30 min), and the default quality on Wi-Fi (Automatique) and on the mobile network
 * (4 Mb/s), each with what an hour weighs. They belong to the device, not to the profile.
 */
@Composable
fun MobilePlaybackSettings(
    settings: PlayerSettings,
    onChange: ((PlayerSettings) -> PlayerSettings) -> Unit,
    modifier: Modifier = Modifier,
    previewSheet: MobilePlaybackSheet? = null,
) {
    var sheet by remember { mutableStateOf(previewSheet) }
    val automatic = stringResource(R.string.player_quality_auto)
    Column(modifier) {
        Text(
            stringResource(R.string.mobile_settings_title),
            style = CineTheme.typography.label,
            color = CineTheme.colors.text2,
            modifier = Modifier.padding(horizontal = MobileSizes.gutter, vertical = CineSpacing.xs),
        )
        MobileListRow(
            title = stringResource(R.string.mobile_settings_margin),
            subtitle = stringResource(R.string.mobile_settings_margin_value, settings.marginMinutes),
            icon = CineIcons.Down,
            onClick = { sheet = MobilePlaybackSheet.Margin },
            modifier = Modifier.testTag(MobilePlaybackTags.MARGIN),
        )
        MobileListRow(
            title = stringResource(R.string.mobile_settings_wifi),
            subtitle =
                settings.localQuality?.let { stringResource(R.string.player_quality_bitrate, it.megabits()) }
                    ?: automatic,
            icon = CineIcons.Quality,
            onClick = { sheet = MobilePlaybackSheet.Wifi },
            modifier = Modifier.testTag(MobilePlaybackTags.WIFI),
        )
        MobileListRow(
            title = stringResource(R.string.mobile_settings_mobile),
            subtitle =
                settings.mobileQuality?.let { stringResource(R.string.player_quality_bitrate, it.megabits()) }
                    ?: automatic,
            icon = CineIcons.Quality,
            onClick = { sheet = MobilePlaybackSheet.Mobile },
            modifier = Modifier.testTag(MobilePlaybackTags.MOBILE),
        )
    }
    val close = { sheet = null }
    when (sheet) {
        MobilePlaybackSheet.Margin -> {
            MobileSheet(
                stringResource(R.string.mobile_settings_margin),
                close,
                subtitle = stringResource(R.string.mobile_settings_margin_detail),
            ) {
                MobileSheetLines(
                    MarginPlan.MINUTES.map { minutes ->
                        MobileSheetLine(
                            stringResource(R.string.mobile_settings_margin_value, minutes),
                            null,
                            "margin-$minutes",
                        ) {
                            onChange { it.copy(marginMinutes = minutes) }
                            close()
                        }
                    },
                    selected = "margin-${settings.marginMinutes}",
                )
            }
        }

        MobilePlaybackSheet.Wifi -> {
            QualityChoice(stringResource(R.string.mobile_settings_wifi), settings.localQuality, close) { quality ->
                onChange { it.copy(localQuality = quality) }
            }
        }

        MobilePlaybackSheet.Mobile -> {
            QualityChoice(stringResource(R.string.mobile_settings_mobile), settings.mobileQuality, close) { quality ->
                onChange { it.copy(mobileQuality = quality) }
            }
        }

        null -> {
            Unit
        }
    }
}

@Composable
private fun QualityChoice(
    title: String,
    selected: Int?,
    onDismiss: () -> Unit,
    onChoose: (Int?) -> Unit,
) {
    MobileSheet(title, onDismiss, subtitle = stringResource(R.string.mobile_settings_quality_detail)) {
        MobileSheetLines(
            listOf(
                MobileSheetLine(stringResource(R.string.player_quality_auto), null, "quality-auto") {
                    onChoose(null)
                    onDismiss()
                },
            ) +
                Qualities.BITRATES.map { bitrate ->
                    MobileSheetLine(
                        stringResource(R.string.player_quality_bitrate, bitrate.megabits()),
                        null,
                        "quality-$bitrate",
                        detail = hourlyEstimate(bitrate),
                    ) {
                        onChoose(bitrate)
                        onDismiss()
                    }
                },
            selected = selected?.let { "quality-$it" } ?: "quality-auto",
        )
    }
}

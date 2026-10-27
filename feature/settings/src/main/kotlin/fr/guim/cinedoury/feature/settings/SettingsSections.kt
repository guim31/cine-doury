package fr.guim.cinedoury.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import fr.guim.cinedoury.core.designsystem.component.CineIcons

/**
 * The themes of the settings (`FONCTIONNALITES.md` § 12), one page each. « Recherche » opens the
 * settings of Seerr directly: it has nothing else.
 */
enum class SettingsSection {
    Playback,
    Picture,
    Sound,
    Subtitles,
    Segments,
    Browsing,
    Libraries,
    Appearance,
    Profile,
    Network,
    Device,
    Advanced,
}

internal val SettingsSection.icon: ImageVector
    get() =
        when (this) {
            SettingsSection.Playback -> CineIcons.Play
            SettingsSection.Picture -> CineIcons.Image
            SettingsSection.Sound -> CineIcons.Audio
            SettingsSection.Subtitles -> CineIcons.Tracks
            SettingsSection.Segments -> CineIcons.SkipNext
            SettingsSection.Browsing -> CineIcons.Home
            SettingsSection.Libraries -> CineIcons.Movies
            SettingsSection.Appearance -> CineIcons.TextSize
            SettingsSection.Profile -> CineIcons.Profile
            SettingsSection.Network -> CineIcons.Server
            SettingsSection.Device -> CineIcons.Info
            SettingsSection.Advanced -> CineIcons.FileInfo
        }

@Composable
internal fun SettingsSection.title(): String =
    stringResource(
        when (this) {
            SettingsSection.Playback -> R.string.theme_playback
            SettingsSection.Picture -> R.string.theme_picture
            SettingsSection.Sound -> R.string.theme_sound
            SettingsSection.Subtitles -> R.string.theme_subtitles
            SettingsSection.Segments -> R.string.theme_segments
            SettingsSection.Browsing -> R.string.theme_browsing
            SettingsSection.Libraries -> R.string.theme_libraries
            SettingsSection.Appearance -> R.string.theme_appearance
            SettingsSection.Profile -> R.string.theme_profile
            SettingsSection.Network -> R.string.theme_network
            SettingsSection.Device -> R.string.theme_device
            SettingsSection.Advanced -> R.string.theme_advanced
        },
    )

@Composable
internal fun SettingsSection.description(): String =
    stringResource(
        when (this) {
            SettingsSection.Playback -> R.string.theme_playback_description
            SettingsSection.Picture -> R.string.theme_picture_description
            SettingsSection.Sound -> R.string.theme_sound_description
            SettingsSection.Subtitles -> R.string.theme_subtitles_description
            SettingsSection.Segments -> R.string.theme_segments_description
            SettingsSection.Browsing -> R.string.theme_browsing_description
            SettingsSection.Libraries -> R.string.theme_libraries_description
            SettingsSection.Appearance -> R.string.theme_appearance_description
            SettingsSection.Profile -> R.string.theme_profile_description
            SettingsSection.Network -> R.string.theme_network_description
            SettingsSection.Device -> R.string.theme_device_description
            SettingsSection.Advanced -> R.string.theme_advanced_description
        },
    )

package fr.guim.cinedoury.core.playback

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import fr.guim.cinedoury.core.model.PassthroughFormat
import fr.guim.cinedoury.core.model.PlayerSettings
import fr.guim.cinedoury.core.model.SubtitleBackground
import fr.guim.cinedoury.core.model.SubtitleColor
import fr.guim.cinedoury.core.model.SubtitleEdge
import fr.guim.cinedoury.core.model.SubtitlePosition
import fr.guim.cinedoury.core.model.SubtitleSize
import fr.guim.cinedoury.core.model.SubtitleStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * The settings of the player, kept on the device (DataStore `player`): they belong to the TV and its
 * amplifier, not to a profile (`FONCTIONNALITES.md` § 12).
 */
class PlayerSettingsStore(
    private val store: DataStore<Preferences>,
) {
    val settings: Flow<PlayerSettings> = store.data.map { it.toSettings() }.distinctUntilChanged()

    suspend fun current(): PlayerSettings = settings.first()

    suspend fun update(change: (PlayerSettings) -> PlayerSettings) {
        store.edit { prefs ->
            val next = change(prefs.toSettings())
            prefs[PASSTHROUGH] = next.passthrough.map { it.name }.toSet()
            prefs[FRAME_RATE] = next.matchFrameRate
            prefs[SEGMENTS] = next.segments.name
            prefs[AUTOPLAY] = next.autoplayNext
            prefs[STILL_WATCHING] = next.stillWatchingAfter
            prefs[WATCHED] = next.watchedThreshold
            prefs[SEEK_BACK] = next.seekBackSeconds
            prefs[SEEK_FORWARD] = next.seekForwardSeconds
            prefs[BURN_IN_ASS] = next.burnInAss
            val bitrate = next.maxBitrate
            if (bitrate == null) prefs.remove(MAX_BITRATE) else prefs[MAX_BITRATE] = bitrate
            prefs[ASPECT] = next.aspect.name
            prefs[SUBTITLE_SIZE] = next.subtitles.size.name
            prefs[SUBTITLE_COLOR] = next.subtitles.color.name
            prefs[SUBTITLE_EDGE] = next.subtitles.edge.name
            prefs[SUBTITLE_BACKGROUND] = next.subtitles.background.name
            prefs[SUBTITLE_POSITION] = next.subtitles.position.name
        }
    }

    private fun Preferences.toSettings(): PlayerSettings {
        val defaults = PlayerSettings()
        return PlayerSettings(
            passthrough =
                this[PASSTHROUGH]
                    ?.mapNotNull { name ->
                        PassthroughFormat.entries.firstOrNull { it.name == name }
                    }?.toSet()
                    ?: defaults.passthrough,
            matchFrameRate = this[FRAME_RATE] ?: defaults.matchFrameRate,
            segments = enumOf(this[SEGMENTS], defaults.segments),
            autoplayNext = this[AUTOPLAY] ?: defaults.autoplayNext,
            stillWatchingAfter = this[STILL_WATCHING] ?: defaults.stillWatchingAfter,
            watchedThreshold = this[WATCHED] ?: defaults.watchedThreshold,
            seekBackSeconds = this[SEEK_BACK] ?: defaults.seekBackSeconds,
            seekForwardSeconds = this[SEEK_FORWARD] ?: defaults.seekForwardSeconds,
            burnInAss = this[BURN_IN_ASS] ?: defaults.burnInAss,
            maxBitrate = this[MAX_BITRATE],
            aspect = enumOf(this[ASPECT], defaults.aspect),
            subtitles =
                SubtitleStyle(
                    size = enumOf(this[SUBTITLE_SIZE], SubtitleSize.Normal),
                    color = enumOf(this[SUBTITLE_COLOR], SubtitleColor.White),
                    edge = enumOf(this[SUBTITLE_EDGE], SubtitleEdge.Outline),
                    background = enumOf(this[SUBTITLE_BACKGROUND], SubtitleBackground.None),
                    position = enumOf(this[SUBTITLE_POSITION], SubtitlePosition.Bottom),
                ),
        )
    }

    private inline fun <reified T : Enum<T>> enumOf(
        name: String?,
        default: T,
    ): T = enumValues<T>().firstOrNull { it.name == name } ?: default

    companion object {
        const val FILE = "player"
        private val PASSTHROUGH = stringSetPreferencesKey("passthrough")
        private val FRAME_RATE = booleanPreferencesKey("match_frame_rate")
        private val SEGMENTS = stringPreferencesKey("segments")
        private val AUTOPLAY = booleanPreferencesKey("autoplay_next")
        private val STILL_WATCHING = intPreferencesKey("still_watching_after")
        private val WATCHED = floatPreferencesKey("watched_threshold")
        private val SEEK_BACK = intPreferencesKey("seek_back")
        private val SEEK_FORWARD = intPreferencesKey("seek_forward")
        private val BURN_IN_ASS = booleanPreferencesKey("burn_in_ass")
        private val MAX_BITRATE = intPreferencesKey("max_bitrate")
        private val ASPECT = stringPreferencesKey("aspect")
        private val SUBTITLE_SIZE = stringPreferencesKey("subtitle_size")
        private val SUBTITLE_COLOR = stringPreferencesKey("subtitle_color")
        private val SUBTITLE_EDGE = stringPreferencesKey("subtitle_edge")
        private val SUBTITLE_BACKGROUND = stringPreferencesKey("subtitle_background")
        private val SUBTITLE_POSITION = stringPreferencesKey("subtitle_position")
    }
}

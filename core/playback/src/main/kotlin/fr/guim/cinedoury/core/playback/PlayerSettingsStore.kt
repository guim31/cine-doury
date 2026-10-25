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
import fr.guim.cinedoury.core.model.SubtitleScale
import fr.guim.cinedoury.core.model.SubtitleStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * The settings of the player, kept on the device (DataStore `player`): they belong to the TV and its
 * amplifier, not to a profile (`FONCTIONNALITES.md` § 12). The look of the subtitles is the
 * exception: it is kept per profile of this device ([profile], the open one), since one reads them
 * from the sofa and another from close by; a profile that never set it starts from the device's.
 */
class PlayerSettingsStore(
    private val store: DataStore<Preferences>,
    private val profile: () -> String? = { null },
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
            val keys = SubtitleKeys.of(profile())
            prefs[keys.scale] = next.subtitles.scale
            prefs[keys.color] = next.subtitles.color.name
            prefs[keys.edge] = next.subtitles.edge.name
            prefs[keys.background] = next.subtitles.background.name
            prefs[keys.position] = next.subtitles.position.name
        }
    }

    /** The keys of the look of the subtitles: those of a profile, or of the device ([profile] null). */
    private class SubtitleKeys(
        suffix: String,
    ) {
        val scale = floatPreferencesKey("subtitle_scale$suffix")
        val color = stringPreferencesKey("subtitle_color$suffix")
        val edge = stringPreferencesKey("subtitle_edge$suffix")
        val background = stringPreferencesKey("subtitle_background$suffix")
        val position = stringPreferencesKey("subtitle_position$suffix")

        companion object {
            val DEVICE = SubtitleKeys("")

            fun of(profile: String?): SubtitleKeys = if (profile == null) DEVICE else SubtitleKeys("@$profile")
        }
    }

    private fun Preferences.subtitles(): SubtitleStyle {
        val own = SubtitleKeys.of(profile())
        val device = SubtitleKeys.DEVICE
        val defaults = SubtitleStyle()
        return SubtitleStyle(
            scale =
                (this[own.scale] ?: this[device.scale] ?: defaults.scale).coerceIn(
                    SubtitleScale.MIN,
                    SubtitleScale.MAX,
                ),
            color = enumOf(this[own.color] ?: this[device.color], defaults.color),
            edge = enumOf(this[own.edge] ?: this[device.edge], defaults.edge),
            background = enumOf(this[own.background] ?: this[device.background], defaults.background),
            position = enumOf(this[own.position] ?: this[device.position], defaults.position),
        )
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
            subtitles = subtitles(),
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
    }
}

package fr.guim.cinedoury.core.playback

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
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
 * The settings of the player, in the DataStore `player` (`SPEC.md` § 4.17). Those of the device (the
 * TV, its amplifier, its network: passthrough, stereo, audio offset, picture, quality, direct play,
 * ASS, format) are the same for every profile. Those of the profile (intro, next episode, countdown,
 * « toujours là », « vu », jumps, look of the subtitles) are kept per profile of this device
 * ([profile], the open one): a profile that never set one starts from the device's, the value kept
 * before J7 (or set before any profile opened).
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
            prefs[STEREO] = next.forceStereo
            prefs[AUDIO_OFFSET] = next.audioOffsetMillis
            prefs[FRAME_RATE] = next.matchFrameRate
            prefs[RESOLUTION] = next.matchResolution
            prefs[HDR] = next.allowHdr
            prefs[DOLBY_VISION] = next.allowDolbyVision
            prefs.putOrRemove(LOCAL_QUALITY, next.localQuality)
            prefs.putOrRemove(REMOTE_QUALITY, next.remoteQuality ?: ORIGINAL)
            prefs[CEILING] = next.bitrateCeiling
            prefs[DIRECT_PLAY] = next.directPlay.name
            prefs[BURN_IN_ASS] = next.burnInAss
            prefs[ASPECT] = next.aspect.name
            val keys = ProfileKeys.of(profile())
            prefs[keys.segments] = next.segments.name
            prefs[keys.autoplay] = next.autoplayNext
            prefs[keys.countdown] = next.nextCountdownSeconds
            prefs[keys.stillWatching] = next.stillWatchingAfter
            prefs[keys.watched] = next.watchedThreshold
            prefs[keys.seekBack] = next.seekBackSeconds
            prefs[keys.seekForward] = next.seekForwardSeconds
            prefs[keys.scale] = next.subtitles.scale
            prefs[keys.color] = next.subtitles.color.name
            prefs[keys.edge] = next.subtitles.edge.name
            prefs[keys.background] = next.subtitles.background.name
            prefs[keys.position] = next.subtitles.position.name
        }
    }

    private fun MutablePreferences.putOrRemove(
        key: Preferences.Key<Int>,
        value: Int?,
    ) {
        if (value == null) remove(key) else this[key] = value
    }

    /**
     * The keys of the settings of a profile, or of the device ([profile] null). The names are those of
     * J4, where they were the device's: what was set then is the default of every profile.
     */
    private class ProfileKeys(
        suffix: String,
    ) {
        val segments = stringPreferencesKey("segments$suffix")
        val autoplay = booleanPreferencesKey("autoplay_next$suffix")
        val countdown = intPreferencesKey("next_countdown$suffix")
        val stillWatching = intPreferencesKey("still_watching_after$suffix")
        val watched = floatPreferencesKey("watched_threshold$suffix")
        val seekBack = intPreferencesKey("seek_back$suffix")
        val seekForward = intPreferencesKey("seek_forward$suffix")
        val scale = floatPreferencesKey("subtitle_scale$suffix")
        val color = stringPreferencesKey("subtitle_color$suffix")
        val edge = stringPreferencesKey("subtitle_edge$suffix")
        val background = stringPreferencesKey("subtitle_background$suffix")
        val position = stringPreferencesKey("subtitle_position$suffix")

        companion object {
            val DEVICE = ProfileKeys("")

            fun of(profile: String?): ProfileKeys = if (profile == null) DEVICE else ProfileKeys("@$profile")
        }
    }

    /** The value of the profile, else that of the device. */
    private fun <T> Preferences.own(key: (ProfileKeys) -> Preferences.Key<T>): T? =
        this[key(ProfileKeys.of(profile()))] ?: this[key(ProfileKeys.DEVICE)]

    private fun Preferences.subtitles(): SubtitleStyle {
        val defaults = SubtitleStyle()
        return SubtitleStyle(
            scale = (own { it.scale } ?: defaults.scale).coerceIn(SubtitleScale.MIN, SubtitleScale.MAX),
            color = enumOf(own { it.color }, defaults.color),
            edge = enumOf(own { it.edge }, defaults.edge),
            background = enumOf(own { it.background }, defaults.background),
            position = enumOf(own { it.position }, defaults.position),
        )
    }

    private fun Preferences.toSettings(): PlayerSettings = ofProfile(ofDevice())

    /** The settings of the device, those of the profile at their defaults. */
    private fun Preferences.ofDevice(): PlayerSettings {
        val defaults = PlayerSettings()
        return PlayerSettings(
            passthrough =
                this[PASSTHROUGH]
                    ?.mapNotNull { name ->
                        PassthroughFormat.entries.firstOrNull { it.name == name }
                    }?.toSet()
                    ?: defaults.passthrough,
            forceStereo = this[STEREO] ?: defaults.forceStereo,
            audioOffsetMillis =
                (this[AUDIO_OFFSET] ?: defaults.audioOffsetMillis).coerceIn(
                    -PlayerSettings.MAX_AUDIO_OFFSET,
                    PlayerSettings.MAX_AUDIO_OFFSET,
                ),
            matchFrameRate = this[FRAME_RATE] ?: defaults.matchFrameRate,
            matchResolution = this[RESOLUTION] ?: defaults.matchResolution,
            allowHdr = this[HDR] ?: defaults.allowHdr,
            allowDolbyVision = this[DOLBY_VISION] ?: defaults.allowDolbyVision,
            localQuality = this[LOCAL_QUALITY],
            remoteQuality =
                when (val remote = this[REMOTE_QUALITY]) {
                    null -> defaults.remoteQuality
                    ORIGINAL -> null
                    else -> remote
                },
            bitrateCeiling = this[CEILING] ?: defaults.bitrateCeiling,
            directPlay = enumOf(this[DIRECT_PLAY], defaults.directPlay),
            burnInAss = this[BURN_IN_ASS] ?: defaults.burnInAss,
            aspect = enumOf(this[ASPECT], defaults.aspect),
        )
    }

    /** [device] with the settings of the profile. */
    private fun Preferences.ofProfile(device: PlayerSettings): PlayerSettings {
        val defaults = PlayerSettings()
        return device.copy(
            segments = enumOf(own { it.segments }, defaults.segments),
            autoplayNext = own { it.autoplay } ?: defaults.autoplayNext,
            nextCountdownSeconds = own { it.countdown } ?: defaults.nextCountdownSeconds,
            stillWatchingAfter = own { it.stillWatching } ?: defaults.stillWatchingAfter,
            watchedThreshold = own { it.watched } ?: defaults.watchedThreshold,
            seekBackSeconds = own { it.seekBack } ?: defaults.seekBackSeconds,
            seekForwardSeconds = own { it.seekForward } ?: defaults.seekForwardSeconds,
            subtitles = subtitles(),
        )
    }

    private inline fun <reified T : Enum<T>> enumOf(
        name: String?,
        default: T,
    ): T = enumValues<T>().firstOrNull { it.name == name } ?: default

    companion object {
        const val FILE = "player"

        /** A remote quality of « Originale », told apart from « never set » (the default, 8 Mb/s). */
        private const val ORIGINAL = 0
        private val PASSTHROUGH = stringSetPreferencesKey("passthrough")
        private val STEREO = booleanPreferencesKey("force_stereo")
        private val AUDIO_OFFSET = intPreferencesKey("audio_offset_ms")
        private val FRAME_RATE = booleanPreferencesKey("match_frame_rate")
        private val RESOLUTION = booleanPreferencesKey("match_resolution")
        private val HDR = booleanPreferencesKey("allow_hdr")
        private val DOLBY_VISION = booleanPreferencesKey("allow_dolby_vision")

        // « max_bitrate » was the quality of J4, never offered as a setting: the local quality now.
        private val LOCAL_QUALITY = intPreferencesKey("max_bitrate")
        private val REMOTE_QUALITY = intPreferencesKey("remote_quality")
        private val CEILING = intPreferencesKey("bitrate_ceiling")
        private val DIRECT_PLAY = stringPreferencesKey("direct_play")
        private val BURN_IN_ASS = booleanPreferencesKey("burn_in_ass")
        private val ASPECT = stringPreferencesKey("aspect")
    }
}

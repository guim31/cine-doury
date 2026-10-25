package fr.guim.cinedoury.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TrackPreselectionTest {
    /** « Nuit polaire » of the test server: three audio tracks and four subtitle tracks. */
    private val source =
        MediaSource(
            id = "nuit",
            name = null,
            container = "mkv",
            sizeBytes = null,
            streams =
                listOf(
                    MediaStream(0, StreamType.Video, "hevc"),
                    MediaStream(1, StreamType.Audio, "eac3", language = "fra", isDefault = true, channels = 6),
                    MediaStream(2, StreamType.Audio, "ac3", language = "eng", channels = 6),
                    MediaStream(3, StreamType.Audio, "aac", language = "jpn", channels = 2),
                    MediaStream(4, StreamType.Subtitle, "subrip", language = "fra", isForced = true),
                    MediaStream(5, StreamType.Subtitle, "subrip", language = "fra"),
                    MediaStream(6, StreamType.Subtitle, "ass", language = "eng"),
                    MediaStream(7, StreamType.Subtitle, "pgssub", language = "fra"),
                ),
        )

    private fun choose(preferences: PlaybackPreferences) = TrackPreselection.choose(source, preferences)

    @Test
    fun `the default audio track without a preferred language`() {
        assertEquals(1, choose(PlaybackPreferences()).audioIndex)
        assertEquals(1, choose(PlaybackPreferences(playDefaultAudio = false)).audioIndex)
    }

    @Test
    fun `the preferred audio language, bibliographic codes included`() {
        assertEquals(2, choose(PlaybackPreferences(audioLanguage = "eng")).audioIndex)
        assertEquals(1, choose(PlaybackPreferences(audioLanguage = "fre")).audioIndex)
        // A language the file does not have: the default track.
        assertEquals(1, choose(PlaybackPreferences(audioLanguage = "deu")).audioIndex)
    }

    @Test
    fun `default mode takes the forced subtitles of the audio language`() {
        assertEquals(4, choose(PlaybackPreferences()).subtitleIndex)
        assertNull(choose(PlaybackPreferences(audioLanguage = "jpn")).subtitleIndex)
    }

    @Test
    fun `always takes a full track of the preferred language`() {
        val selection =
            choose(
                PlaybackPreferences(
                    audioLanguage = "eng",
                    subtitleLanguage = "fra",
                    subtitleMode = SubtitleMode.Always,
                ),
            )
        assertEquals(2, selection.audioIndex)
        assertEquals(5, selection.subtitleIndex)
    }

    @Test
    fun `smart shows subtitles only when the audio is foreign`() {
        val foreign =
            choose(
                PlaybackPreferences(audioLanguage = "jpn", subtitleLanguage = "fra", subtitleMode = SubtitleMode.Smart),
            )
        assertEquals(5, foreign.subtitleIndex)
        val local =
            choose(
                PlaybackPreferences(audioLanguage = "fra", subtitleLanguage = "fra", subtitleMode = SubtitleMode.Smart),
            )
        assertEquals(4, local.subtitleIndex)
    }

    @Test
    fun `none and only forced`() {
        assertNull(choose(PlaybackPreferences(subtitleMode = SubtitleMode.None)).subtitleIndex)
        assertEquals(4, choose(PlaybackPreferences(subtitleMode = SubtitleMode.OnlyForced)).subtitleIndex)
        assertNull(
            choose(PlaybackPreferences(audioLanguage = "eng", subtitleMode = SubtitleMode.OnlyForced)).subtitleIndex,
        )
    }

    @Test
    fun `a file without tracks`() {
        val empty = TrackPreselection.choose(source.copy(streams = emptyList()), PlaybackPreferences())
        assertEquals(TrackSelection("nuit", null, null), empty)
    }
}

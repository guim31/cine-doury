package fr.guim.cinedoury.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The tracks chosen for a series, found on the next episode by language and nature (W). */
class SeriesTracksTest {
    private fun source(vararg streams: MediaStream) =
        MediaSource(id = "e", name = null, container = "mkv", sizeBytes = null, streams = streams.toList())

    /** Episode 1: French dub first, original English second, French forced then full subtitles. */
    private val first =
        source(
            MediaStream(0, StreamType.Video, "h264"),
            MediaStream(1, StreamType.Audio, "eac3", language = "fra", isDefault = true),
            MediaStream(2, StreamType.Audio, "eac3", language = "eng"),
            MediaStream(3, StreamType.Subtitle, "subrip", language = "fre", isForced = true),
            MediaStream(4, StreamType.Subtitle, "subrip", language = "fre"),
            MediaStream(5, StreamType.Subtitle, "subrip", language = "fre", isHearingImpaired = true),
        )

    /** Episode 2: the same tracks at other indices, and the SDH track before the plain one. */
    private val second =
        source(
            MediaStream(0, StreamType.Video, "h264"),
            MediaStream(1, StreamType.Audio, "aac", language = "eng"),
            MediaStream(2, StreamType.Audio, "aac", language = "fra", isDefault = true),
            MediaStream(3, StreamType.Audio, "aac", language = "deu"),
            MediaStream(4, StreamType.Subtitle, "subrip", language = "fra", isHearingImpaired = true),
            MediaStream(5, StreamType.Subtitle, "subrip", language = "fra"),
            MediaStream(6, StreamType.Subtitle, "subrip", language = "fra", isForced = true),
        )

    /** Preferences of the profile: French audio, French forced subtitles. */
    private val preferences = PlaybackPreferences(audioLanguage = "fra", subtitleMode = SubtitleMode.OnlyForced)

    /** What the user chose on episode 1: original audio, full French subtitles. */
    private val chosen =
        SeriesTracks()
            .withAudio(TrackTraits.of(first.audio.single { it.index == 2 }))
            .withSubtitles(TrackTraits.of(first.subtitles.single { it.index == 4 }))

    @Test
    fun `the choice of an episode is found on the next by language and nature, not by index`() {
        val selection = TrackPreselection.choose(second, preferences, chosen)
        assertEquals(1, selection.audioIndex)
        assertEquals(5, selection.subtitleIndex)
    }

    @Test
    fun `without a choice the preferences of the profile decide`() {
        val selection = TrackPreselection.choose(second, preferences, null)
        assertEquals(2, selection.audioIndex)
        assertEquals(6, selection.subtitleIndex)
        assertEquals(
            TrackPreselection.choose(second, preferences),
            TrackPreselection.choose(second, preferences, SeriesTracks()),
        )
    }

    @Test
    fun `no subtitles is a choice too`() {
        val off = SeriesTracks().withSubtitles(null)
        assertNull(TrackPreselection.choose(second, preferences, off).subtitleIndex)
        // Only the subtitles were chosen: the audio stays that of the profile.
        assertEquals(2, TrackPreselection.choose(second, preferences, off).audioIndex)
    }

    @Test
    fun `forced or SDH as chosen`() {
        val forced = SeriesTracks().withSubtitles(TrackTraits("fra", forced = true))
        assertEquals(6, TrackPreselection.choose(second, PlaybackPreferences(), forced).subtitleIndex)
        val sdh = SeriesTracks().withSubtitles(TrackTraits("fra", hearingImpaired = true))
        assertEquals(4, TrackPreselection.choose(second, PlaybackPreferences(), sdh).subtitleIndex)
    }

    @Test
    fun `a language the episode lacks falls back on the profile, without error`() {
        val japanese = SeriesTracks().withAudio(TrackTraits("jpn")).withSubtitles(TrackTraits("jpn"))
        val selection = TrackPreselection.choose(second, preferences, japanese)
        assertEquals(2, selection.audioIndex)
        assertEquals(6, selection.subtitleIndex)
    }

    @Test
    fun `a full track is not replaced by a forced one of the same language`() {
        val onlyForced =
            source(
                MediaStream(1, StreamType.Audio, "aac", language = "eng"),
                MediaStream(2, StreamType.Subtitle, "subrip", language = "fra", isForced = true),
            )
        val full = SeriesTracks().withSubtitles(TrackTraits("fra"))
        // The profile then decides: no preferred language, « Aucun ».
        assertNull(
            TrackPreselection
                .choose(
                    onlyForced,
                    PlaybackPreferences(subtitleMode = SubtitleMode.None),
                    full,
                ).subtitleIndex,
        )
    }
}

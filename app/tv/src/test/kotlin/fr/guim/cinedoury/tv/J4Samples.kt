package fr.guim.cinedoury.tv

import fr.guim.cinedoury.core.model.Chapter
import fr.guim.cinedoury.core.model.EpisodeInfo
import fr.guim.cinedoury.core.model.ImageType
import fr.guim.cinedoury.core.model.ItemId
import fr.guim.cinedoury.core.model.MediaItem
import fr.guim.cinedoury.core.model.MediaSegment
import fr.guim.cinedoury.core.model.PassthroughFormat
import fr.guim.cinedoury.core.model.PlayMethod
import fr.guim.cinedoury.core.model.PlaybackProfiles
import fr.guim.cinedoury.core.model.PlaybackStream
import fr.guim.cinedoury.core.model.PlayerSettings
import fr.guim.cinedoury.core.model.SegmentType
import fr.guim.cinedoury.core.model.StreamType
import fr.guim.cinedoury.core.model.SubtitleDelivery
import fr.guim.cinedoury.core.model.SubtitleMethod
import fr.guim.cinedoury.core.model.TranscodeReason
import fr.guim.cinedoury.core.model.TrickplayInfo
import fr.guim.cinedoury.core.playback.AudioOutput
import fr.guim.cinedoury.core.playback.PlayerSnapshot
import fr.guim.cinedoury.core.playback.VideoOutput
import fr.guim.cinedoury.core.source.fake.SampleDevices
import fr.guim.cinedoury.feature.player.PlaybackSettingsState
import fr.guim.cinedoury.feature.player.PlayerActions
import fr.guim.cinedoury.feature.player.PlayerState
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Sample playback of the J4 screenshots: « Le Phare » in 4K Dolby Vision on a Shield, played as it
 * is or transcoded, and an episode of « Les Marées » at its intro and at its credits. The picture
 * is a generated still ([SampleArt]).
 */
internal object J4Samples {
    private val trickplay = TrickplayInfo(320, 180, 10, 10, 660, 10.seconds)

    val film: MediaItem =
        J3Samples.phare.copy(
            chapters =
                listOf(
                    Chapter(Duration.ZERO, "Ouverture"),
                    Chapter(18.minutes, "La tempête"),
                    Chapter(52.minutes, "Le naufrage"),
                    Chapter(97.minutes, "Générique"),
                ),
            mediaSources = J3Samples.phare.mediaSources.map { it.copy(trickplay = listOf(trickplay)) },
        )

    private val source = film.mediaSources.first()

    val directStream =
        PlaybackStream(
            url = "http://192.0.2.10:8096/Videos/phare/stream?static=true",
            method = PlayMethod.DirectPlay,
            reasons = emptySet(),
            playSessionId = "session",
            source = source,
            container = "mkv",
            isHls = false,
            subtitles = source.subtitles.map { SubtitleDelivery(it.index, SubtitleMethod.Embed, codec = it.codec) },
            audioIndex = 2,
            subtitleIndex = 4,
            maxBitrate = null,
        )

    /** On a Chromecast: no Dolby Vision profile 5 decoder, no TrueHD; the server tone maps. */
    val transcodedStream =
        directStream.copy(
            url = "http://192.0.2.10:8096/videos/phare/master.m3u8",
            method = PlayMethod.Transcode,
            reasons = setOf(TranscodeReason.VideoRangeTypeNotSupported, TranscodeReason.AudioCodecNotSupported),
            container = "ts",
            isHls = true,
            subtitles =
                source.subtitles.map {
                    SubtitleDelivery(
                        it.index,
                        if (it.codec ==
                            "pgssub"
                        ) {
                            SubtitleMethod.Encode
                        } else {
                            SubtitleMethod.External
                        },
                        codec = it.codec,
                    )
                },
            maxBitrate = 20_000_000,
        )

    private val profile = PlaybackProfiles.build(SampleDevices.shield)

    val playing =
        PlayerState(
            item = film,
            loading = false,
            position = 40.minutes + 12.seconds,
            duration = film.runtime ?: Duration.ZERO,
            buffered = 48.seconds,
            playing = true,
            stream = directStream,
            audioIndex = 2,
            subtitleIndex = 4,
            capabilities = SampleDevices.shield,
            profile = profile,
        )

    val searching = playing.copy(scrub = 65.minutes + 30.seconds)

    val paused = playing.copy(playing = false)

    val directInfo =
        playing.copy(
            info =
                PlayerSnapshot(
                    position = playing.position,
                    duration = playing.duration,
                    buffered = 48.seconds,
                    playing = true,
                    video =
                        VideoOutput(
                            "video/dolby-vision · dvhe.08.06",
                            3840,
                            2160,
                            23.976f,
                            "Dolby Vision",
                            "OMX.Nvidia.h265.decode",
                        ),
                    audio = AudioOutput("audio/eac3", 6, 48_000, passthrough = true, decoder = null),
                    droppedFrames = 0,
                    bandwidth = 94_000_000,
                ),
        )

    val transcodedInfo =
        directInfo.copy(
            stream = transcodedStream,
            maxBitrate = 20_000_000,
            fallback = null,
            capabilities = SampleDevices.chromecast,
            info =
                directInfo.info?.copy(
                    video =
                        VideoOutput(
                            "video/hevc · hvc1.2.4.L153",
                            3840,
                            2160,
                            23.976f,
                            "HDR10",
                            "c2.amlogic.hevc.decoder",
                        ),
                    audio = AudioOutput("audio/ac3", 6, 48_000, passthrough = true, decoder = null),
                    buffered = 21.seconds,
                    droppedFrames = 3,
                    bandwidth = 38_000_000,
                ),
        )

    private val episodeItem = J3Samples.episode.item.copy(runtime = 42.minutes)

    private val next =
        episodeItem.copy(
            id = ItemId("marees-s2e1"),
            title = "Le retour",
            episode = EpisodeInfo(ItemId("Les Marées"), "Les Marées", ItemId("Les Marées-s2"), 2, 1),
            images = episodeItem.images.copy(primary = SampleArt.image("Le retour", ImageType.Backdrop)),
            userData = null,
        )

    private val episodeStream =
        directStream.copy(
            source = episodeItem.mediaSources.first(),
            subtitles = emptyList(),
            audioIndex = 1,
            subtitleIndex = null,
        )

    val intro =
        PlayerState(
            item = episodeItem,
            loading = false,
            position = 52.seconds,
            duration = 42.minutes,
            playing = true,
            stream = episodeStream,
            audioIndex = 1,
            segments =
                listOf(
                    MediaSegment(SegmentType.Intro, 35.seconds, 95.seconds),
                    MediaSegment(SegmentType.Outro, 40.minutes, 42.minutes),
                ),
            skip = MediaSegment(SegmentType.Intro, 35.seconds, 95.seconds),
            next = next,
            capabilities = SampleDevices.shield,
        )

    val credits =
        intro.copy(
            position = 40.minutes + 4.seconds,
            skip = null,
            nextShown = true,
            nextCountdown = 0.6f,
            nextSeconds = 6,
        )

    val stillWatching = credits.copy(nextShown = false, playing = false, stillWatching = true)

    /** The settings page, on a Shield whose amplifier takes everything but DTS-HD today. */
    val settings =
        PlaybackSettingsState(
            settings = PlayerSettings(passthrough = PassthroughFormat.entries.toSet() - PassthroughFormat.TrueHd),
            capabilities =
                SampleDevices.shield.copy(
                    passthrough =
                        SampleDevices.shield.passthrough - PassthroughFormat.DtsHd,
                ),
        )

    val subtitleStyle = PlayerSettings().subtitles

    /** No gesture: the screenshots only draw. */
    val noActions =
        object : PlayerActions {
            override fun interacted() = Unit

            override fun togglePlay() = Unit

            override fun play() = Unit

            override fun pause() = Unit

            override fun jump(forward: Boolean) = Unit

            override fun seekTo(position: Duration) = Unit

            override fun scrub(
                forward: Boolean,
                repeat: Int,
            ) = Unit

            override fun commitScrub() = Unit

            override fun cancelScrub() = Unit

            override fun chapter(forward: Boolean) = Unit

            override fun skipSegment() = Unit

            override fun playNext() = Unit

            override fun dismissNext() = Unit

            override fun keepWatching() = Unit

            override fun selectAudio(index: Int) = Unit

            override fun selectSubtitles(index: Int?) = Unit

            override fun selectQuality(maxBitrate: Int?) = Unit

            override fun updateSettings(change: (PlayerSettings) -> PlayerSettings) = Unit

            override fun toggleInfo() = Unit

            override fun retry() = Unit

            override fun trickplayUrl(tile: Int): String = "trickplay-$tile"

            override val streamHeaders: Map<String, String> = emptyMap()
        }

    /** The row of the audio track chosen in the panel: the column of audio lists the tracks in order. */
    val chosenAudioRow: Int = source.streams.filter { it.type == StreamType.Audio }.indexOfFirst { it.index == 2 }
}

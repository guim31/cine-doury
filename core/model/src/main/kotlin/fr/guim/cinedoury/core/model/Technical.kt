package fr.guim.cinedoury.core.model

/** The dynamic range of a video, as the HDR filter offers it. */
enum class DynamicRange {
    Sdr,
    Hdr10,
    Hdr10Plus,
    Hlg,
    DolbyVision,
}

/** A video codec, as the codec filter offers it. */
enum class VideoCodec {
    H264,
    Hevc,
    Av1,
    Vp9,
    Mpeg2,
    Vc1,
    Other,
}

/** What an audio track brings, for the badges and the audio filter. */
enum class AudioFeature {
    Stereo,
    Surround51,
    Surround71,
    Atmos,
    Dts,
    DtsHd,
    DtsX,
    TrueHd,
}

/** The values of the audio filter (`FONCTIONNALITES.md` § 3): each matches one or more [AudioFeature]. */
enum class AudioFormat(
    val features: Set<AudioFeature>,
) {
    Stereo(setOf(AudioFeature.Stereo)),
    Surround51(setOf(AudioFeature.Surround51)),
    Surround71(setOf(AudioFeature.Surround71)),
    Atmos(setOf(AudioFeature.Atmos)),

    /** DTS, DTS-HD and DTS:X. */
    Dts(setOf(AudioFeature.Dts, AudioFeature.DtsHd, AudioFeature.DtsX)),
    TrueHd(setOf(AudioFeature.TrueHd)),
}

/**
 * The technical facts of an item, every version together (a film in 4K Dolby Vision and in 1080p SDR
 * is both): what the badges of a details screen show and what the local index stores to filter on
 * what Jellyfin cannot (`SPEC.md` § 4.10).
 *
 * @param maxWidth the widest picture of the versions, which gives the resolution.
 * @param forcedSubtitleLanguages languages that have a forced subtitle track.
 */
data class TechnicalSummary(
    val maxWidth: Int? = null,
    val ranges: Set<DynamicRange> = emptySet(),
    val videoCodecs: Set<VideoCodec> = emptySet(),
    val audio: Set<AudioFeature> = emptySet(),
    val audioLanguages: Set<String> = emptySet(),
    val subtitleLanguages: Set<String> = emptySet(),
    val forcedSubtitleLanguages: Set<String> = emptySet(),
    val versions: Int = 0,
    val sizeBytes: Long? = null,
) {
    val resolution: Resolution? get() = maxWidth?.let(Resolution::of)

    /**
     * The badges of a details screen (`FONCTIONNALITES.md` § 4), sober and in a fixed order: the
     * resolution, the best dynamic range, the best audio, the channels, then the languages for
     * [language] (ISO 639-2), the language of the profile: « VF » when the audio exists in it,
     * « VOST » when another audio exists with subtitles in it, « VO » otherwise.
     */
    fun badges(language: String = FRENCH): List<TechBadge> =
        buildList {
            when (resolution) {
                Resolution.Uhd -> {
                    add(TechBadge.Uhd)
                }

                Resolution.FullHd -> {
                    add(TechBadge.FullHd)
                }

                Resolution.Hd -> {
                    add(TechBadge.Hd)
                }

                Resolution.Sd -> {
                    add(TechBadge.Sd)
                }

                null -> {}
            }
            when {
                DynamicRange.DolbyVision in ranges -> add(TechBadge.DolbyVision)
                DynamicRange.Hdr10Plus in ranges -> add(TechBadge.Hdr10Plus)
                DynamicRange.Hdr10 in ranges -> add(TechBadge.Hdr10)
                DynamicRange.Hlg in ranges -> add(TechBadge.Hlg)
            }
            when {
                AudioFeature.Atmos in audio -> add(TechBadge.Atmos)
                AudioFeature.DtsX in audio -> add(TechBadge.DtsX)
                AudioFeature.TrueHd in audio -> add(TechBadge.TrueHd)
                AudioFeature.DtsHd in audio -> add(TechBadge.DtsHd)
                AudioFeature.Dts in audio -> add(TechBadge.Dts)
            }
            when {
                AudioFeature.Surround71 in audio -> add(TechBadge.Surround71)
                AudioFeature.Surround51 in audio -> add(TechBadge.Surround51)
            }
            val local = audioLanguages.any { sameLanguage(it, language) }
            val foreign = audioLanguages.any { !sameLanguage(it, language) && it != UNDETERMINED }
            if (local) add(TechBadge.LocalAudio)
            if (foreign) {
                add(
                    if (subtitleLanguages.any {
                            sameLanguage(
                                it,
                                language,
                            )
                        }
                    ) {
                        TechBadge.Subtitled
                    } else {
                        TechBadge.Original
                    },
                )
            }
        }

    companion object {
        const val FRENCH = "fra"
        private const val UNDETERMINED = "und"

        fun of(sources: List<MediaSource>): TechnicalSummary {
            val streams = sources.flatMap { it.streams }
            val videos = streams.filter { it.type == StreamType.Video }
            val audios = streams.filter { it.type == StreamType.Audio }
            val subtitles = streams.filter { it.type == StreamType.Subtitle }
            return TechnicalSummary(
                maxWidth = videos.mapNotNull { it.width }.maxOrNull(),
                ranges = videos.mapNotNull { it.dynamicRange }.toSet(),
                videoCodecs = videos.map { videoCodecOf(it.codec) }.toSet(),
                audio = audios.flatMap { it.audioFeatures }.toSet(),
                audioLanguages = audios.mapNotNull { it.language?.lowercase() }.toSet(),
                subtitleLanguages = subtitles.mapNotNull { it.language?.lowercase() }.toSet(),
                forcedSubtitleLanguages =
                    subtitles.filter { it.isForced }.mapNotNull { it.language?.lowercase() }.toSet(),
                versions = sources.size,
                sizeBytes = sources.mapNotNull { it.sizeBytes }.takeIf { it.isNotEmpty() }?.sum(),
            )
        }

        /** Two ISO 639-2 codes for the same language: the bibliographic « fre » is « fra ». */
        fun sameLanguage(
            a: String,
            b: String,
        ): Boolean = normalize(a) == normalize(b)

        private fun normalize(code: String): String = code.lowercase().let { BIBLIOGRAPHIC[it] ?: it }

        private val BIBLIOGRAPHIC =
            mapOf(
                "fre" to "fra",
                "ger" to "deu",
                "chi" to "zho",
                "dut" to "nld",
                "gre" to "ell",
                "cze" to "ces",
                "rum" to "ron",
                "per" to "fas",
                "arm" to "hye",
                "baq" to "eus",
                "bur" to "mya",
                "geo" to "kat",
                "ice" to "isl",
                "mac" to "mkd",
                "mao" to "mri",
                "may" to "msa",
                "slo" to "slk",
                "tib" to "bod",
                "wel" to "cym",
                "alb" to "sqi",
            )

        fun videoCodecOf(codec: String?): VideoCodec =
            when (codec?.lowercase()) {
                "h264", "avc" -> VideoCodec.H264
                "hevc", "h265" -> VideoCodec.Hevc
                "av1" -> VideoCodec.Av1
                "vp9" -> VideoCodec.Vp9
                "mpeg2video", "mpeg2" -> VideoCodec.Mpeg2
                "vc1" -> VideoCodec.Vc1
                else -> VideoCodec.Other
            }
    }
}

/** A badge of a details screen, in display order. */
enum class TechBadge {
    Uhd,
    FullHd,
    Hd,
    Sd,
    DolbyVision,
    Hdr10Plus,
    Hdr10,
    Hlg,
    Atmos,
    DtsX,
    TrueHd,
    DtsHd,
    Dts,
    Surround71,
    Surround51,

    /** « VF »: audio in the language of the profile. */
    LocalAudio,

    /** « VOST »: audio in another language, with subtitles in the language of the profile. */
    Subtitled,

    /** « VO »: audio in another language, without subtitles in the language of the profile. */
    Original,
}

/** The dynamic range of a video stream; null for an audio or subtitle stream, or when unknown. */
val MediaStream.dynamicRange: DynamicRange?
    get() =
        when (videoRange) {
            VideoRange.Sdr -> DynamicRange.Sdr
            VideoRange.Hdr10 -> DynamicRange.Hdr10
            VideoRange.Hdr10Plus -> DynamicRange.Hdr10Plus
            VideoRange.Hlg -> DynamicRange.Hlg
            VideoRange.DolbyVision -> DynamicRange.DolbyVision
            VideoRange.Unknown, null -> null
        }

private const val STEREO_CHANNELS = 2
private const val SURROUND_51_CHANNELS = 6
private const val SURROUND_71_CHANNELS = 8

/** What an audio track brings: its channels, its codec family, Atmos or DTS:X. */
val MediaStream.audioFeatures: Set<AudioFeature>
    get() {
        if (type != StreamType.Audio) return emptySet()
        val codec = codec?.lowercase().orEmpty()
        val profile = profile?.lowercase().orEmpty()
        return buildSet {
            when (channels) {
                null -> {}

                in 1..STEREO_CHANNELS -> {
                    add(AudioFeature.Stereo)
                }

                in STEREO_CHANNELS + 1..SURROUND_51_CHANNELS -> {
                    add(AudioFeature.Surround51)
                }

                else -> {
                    if (channels >= SURROUND_71_CHANNELS - 1) add(AudioFeature.Surround71)
                }
            }
            if (spatialAudio == SpatialAudio.DolbyAtmos || "atmos" in profile) add(AudioFeature.Atmos)
            if (codec == "truehd" || "truehd" in profile) add(AudioFeature.TrueHd)
            if (codec == "dts" || codec.startsWith("dts")) {
                when {
                    spatialAudio == SpatialAudio.DtsX || "dts:x" in profile || "dts-x" in profile -> {
                        add(AudioFeature.DtsX)
                        add(AudioFeature.DtsHd)
                    }

                    "hd" in profile || "ma" in profile.split(' ', '-') -> {
                        add(AudioFeature.DtsHd)
                    }

                    else -> {
                        add(AudioFeature.Dts)
                    }
                }
            }
        }
    }

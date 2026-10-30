package fr.guim.cinedoury.core.model

/**
 * The settings of the player (`FONCTIONNALITES.md` § 12: « Lecture », « Image », « Son »,
 * « Sous-titres », « Intro et générique »), with their defaults. Some belong to the device (the TV,
 * its amplifier, its network), the others to the profile that watches (`SPEC.md` § 4.17).
 *
 * Device:
 * @param passthrough the formats sent to the amplifier as they are (all that the output takes).
 * @param forceStereo the device takes two channels only: nothing to the amplifier, the server mixes
 * the rest down.
 * @param audioOffsetMillis the sound later (positive) or earlier than the picture, for an amplifier
 * that delays one of them.
 * @param matchFrameRate the screen follows the frame rate of the film (24p), when it can.
 * @param matchResolution the screen takes the resolution of the film (a 1080p film in a 1080p mode).
 * @param allowHdr HDR10, HDR10+ and HLG shown as they are; off, the server tone maps them.
 * @param allowDolbyVision Dolby Vision shown as it is; off, its base layer or a tone mapping.
 * @param localQuality the bitrate asked for on the local network; null for the original.
 * @param remoteQuality the bitrate asked for through the remote address; null for the original.
 * @param mobileQuality the bitrate asked for on a mobile network (a phone, M2); null for the original.
 * @param marginMinutes how far ahead a phone loads the film, memory and disk (« Marge de lecture », M2).
 * @param bitrateCeiling the most the device asks for, the original included (its network).
 * @param directPlay how the file reaches the player.
 * @param burnInAss ASS subtitles drawn into the picture by the server, styles and all.
 *
 * Profile:
 * @param segments what to do with an intro or a recap.
 * @param autoplayNext the next episode starts at the end of its countdown.
 * @param nextCountdownSeconds the countdown of « Épisode suivant ».
 * @param stillWatchingAfter episodes in a row without a key before « Vous êtes toujours là ? »; 0
 * never.
 * @param watchedThreshold the part of the runtime that makes an item watched.
 * @param seekBackSeconds the jump of ←, [seekForwardSeconds] that of →.
 * @param subtitles the look of the subtitles.
 */
data class PlayerSettings(
    val passthrough: Set<PassthroughFormat> = PassthroughFormat.entries.toSet(),
    val forceStereo: Boolean = false,
    val audioOffsetMillis: Int = 0,
    val matchFrameRate: Boolean = true,
    val matchResolution: Boolean = false,
    val allowHdr: Boolean = true,
    val allowDolbyVision: Boolean = true,
    val localQuality: Int? = null,
    val remoteQuality: Int? = DEFAULT_REMOTE_QUALITY,
    val mobileQuality: Int? = QualityGovernor.MOBILE_DEFAULT,
    val marginMinutes: Int = MarginPlan.DEFAULT_MINUTES,
    val bitrateCeiling: Int = ProfileOptions.MAX_BITRATE,
    val directPlay: DirectPlayMode = DirectPlayMode.Auto,
    val burnInAss: Boolean = false,
    val aspect: AspectMode = AspectMode.Fit,
    val segments: SegmentAction = SegmentAction.Button,
    val autoplayNext: Boolean = true,
    val nextCountdownSeconds: Int = DEFAULT_COUNTDOWN,
    val stillWatchingAfter: Int = StillWatching.DEFAULT_LIMIT,
    val watchedThreshold: Float = PlaybackRules.DEFAULT_WATCHED_THRESHOLD,
    val seekBackSeconds: Int = DEFAULT_SEEK_BACK,
    val seekForwardSeconds: Int = DEFAULT_SEEK_FORWARD,
    val subtitles: SubtitleStyle = SubtitleStyle(),
) {
    /** The bitrate to ask for by default, on the local network or through the remote address. */
    fun quality(
        remote: Boolean,
        network: NetworkKind? = null,
    ): Int = minOf(defaultQuality(remote, network) ?: bitrateCeiling, bitrateCeiling)

    /**
     * The quality chosen for this network, null for the original: on a mobile network (a phone, M2)
     * [mobileQuality], else the local or the remote one.
     */
    fun defaultQuality(
        remote: Boolean,
        network: NetworkKind? = null,
    ): Int? =
        when {
            network == NetworkKind.Mobile -> mobileQuality
            remote -> remoteQuality
            else -> localQuality
        }

    /** What the profile of the device sent to the server allows ([PlaybackProfiles.build]). */
    fun profileOptions(
        remote: Boolean = false,
        network: NetworkKind? = null,
    ): ProfileOptions =
        ProfileOptions(
            passthrough = if (forceStereo) emptySet() else passthrough,
            maxBitrate = quality(remote, network),
            burnInAss = burnInAss,
            allowHdr = allowHdr,
            allowDolbyVision = allowDolbyVision,
            stereo = forceStereo,
        )

    companion object {
        const val DEFAULT_SEEK_BACK = 10
        const val DEFAULT_SEEK_FORWARD = 30
        const val DEFAULT_COUNTDOWN = 10

        /** 8 Mb/s: a 1080p stream that the upload of a home connection carries. */
        const val DEFAULT_REMOTE_QUALITY = 8_000_000
        const val MAX_AUDIO_OFFSET = 500
        const val AUDIO_OFFSET_STEP = 25
        val SEEK_STEPS = listOf(5, 10, 15, 30, 60)
        val STILL_WATCHING_STEPS = listOf(0, 2, 3, 4, 5)
        val WATCHED_THRESHOLDS = listOf(0.8f, 0.85f, 0.9f, 0.95f)
        val COUNTDOWN_STEPS = listOf(5, 10, 15, 20, 30)

        /** The most the device may ask for, the original included (Mb/s for the network of the device). */
        val CEILINGS = listOf(200_000_000, 120_000_000, 80_000_000, 60_000_000, 40_000_000, 20_000_000)
    }
}

/** How a file reaches the player (`FONCTIONNALITES.md` § 12, « lecture directe forcée ou non »). */
enum class DirectPlayMode {
    /** As it is when the profile says the device reads it, else remuxed, else transcoded. */
    Auto,

    /**
     * Always the file as it is, even when the profile says the device does not read it (a profile too
     * cautious); the fallback to transcoding still follows a failure.
     */
    Forced,

    /** Always through the server (remux or transcoding): to find what the device does wrong. */
    Never,
}

/** How the picture fills the screen (`FONCTIONNALITES.md` § 5, « Image »). */
enum class AspectMode {
    /** The whole picture, with bars. */
    Fit,

    /** The screen, the picture stretched. */
    Fill,

    /** The screen, the picture cut. */
    Zoom,
}

/** The qualities offered during playback, from the original down (bits per second). */
object Qualities {
    val BITRATES = listOf(40_000_000, 20_000_000, 10_000_000, 8_000_000, 4_000_000, 2_000_000, 1_000_000)
}

/**
 * The look of the subtitles drawn by the player (`FONCTIONNALITES.md` § 5): size, colour, edge,
 * background and height above the bottom of the picture. Every size is a part of the height of the
 * picture, never pixels: a setting looks the same in 1080p and in 4K.
 *
 * @param scale the size, from [SubtitleScale.MIN] to [SubtitleScale.MAX] of [SubtitleScale.BASE]:
 * the text of SRT and ASS (the sizes of the ASS styles keep their proportions) and the pictures of
 * PGS and VobSub grow alike.
 */
data class SubtitleStyle(
    val scale: Float = 1f,
    val color: SubtitleColor = SubtitleColor.White,
    val edge: SubtitleEdge = SubtitleEdge.Outline,
    val background: SubtitleBackground = SubtitleBackground.None,
    val position: SubtitlePosition = SubtitlePosition.Bottom,
) {
    /** The height of the text without a size of its own, as a part of the height of the picture. */
    val textFraction: Float get() = SubtitleScale.BASE * scale

    /** The size one step up ([forward]) or down, within the steps. */
    fun step(forward: Boolean): SubtitleStyle = copy(scale = SubtitleScale.step(scale, forward))
}

/** The steps of the size of the subtitles: 60 % to 200 %, by tenths up to 100 %, then by fifths. */
object SubtitleScale {
    /** The height of a line at 100 %: 5.33 % of the picture, the size of Android's captions. */
    const val BASE = 0.0533f
    const val MIN = 0.6f
    const val MAX = 2f
    val STEPS = listOf(0.6f, 0.7f, 0.8f, 0.9f, 1f, 1.2f, 1.4f, 1.6f, 1.8f, 2f)

    /** The next step from [scale], one way or the other; a value between two steps snaps to one. */
    fun step(
        scale: Float,
        forward: Boolean,
    ): Float =
        if (forward) {
            STEPS.firstOrNull { it > scale + EPSILON } ?: STEPS.last()
        } else {
            STEPS.lastOrNull { it < scale - EPSILON } ?: STEPS.first()
        }

    /** The size as a whole percentage (« 140 % »). */
    fun percent(scale: Float): Int = Math.round(scale * PERCENT)

    private const val EPSILON = 0.001f
    private const val PERCENT = 100
}

/** The colour of the text, in ARGB. */
enum class SubtitleColor(
    val argb: Long,
) {
    White(0xFFFFFFFF),
    Yellow(0xFFFFE14D),
    Cyan(0xFF7FE7F2),
    Green(0xFF8AE68A),
}

enum class SubtitleEdge {
    None,
    Outline,
    Shadow,
}

/**
 * What lies behind the text, in ARGB: nothing, a shade behind the words, or a band the width of
 * the picture.
 */
enum class SubtitleBackground(
    val argb: Long,
) {
    None(0x00000000),
    Shaded(0x99000000),
    Band(0xB3000000),
}

/** The space under the text, as a part of the height of the picture. */
enum class SubtitlePosition(
    val bottomFraction: Float,
) {
    Bottom(0.08f),
    Raised(0.15f),
    High(0.25f),
    ;

    /** How much higher than [Bottom] the subtitles sit (pictures move up as much). */
    val raise: Float get() = bottomFraction - Bottom.bottomFraction
}

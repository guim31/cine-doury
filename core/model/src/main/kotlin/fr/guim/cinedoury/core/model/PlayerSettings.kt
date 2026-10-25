package fr.guim.cinedoury.core.model

/**
 * The settings of the player on this device (`FONCTIONNALITES.md` § 12: « Lecture », « Image »,
 * « Son », « Sous-titres », « Intro et générique »), with their defaults. The full pages come with
 * J7; J4 has the passthrough and the look of the subtitles.
 *
 * @param passthrough the formats sent to the amplifier as they are (all that the output takes).
 * @param matchFrameRate the screen follows the frame rate of the film (24p), when it can.
 * @param segments what to do with an intro or a recap.
 * @param autoplayNext the next episode starts at the end of its countdown.
 * @param stillWatchingAfter episodes in a row without a key before « Vous êtes toujours là ? »; 0
 * never.
 * @param watchedThreshold the part of the runtime that makes an item watched.
 * @param seekBackSeconds the jump of ←, [seekForwardSeconds] that of →.
 * @param burnInAss ASS subtitles drawn into the picture by the server, styles and all.
 * @param maxBitrate the quality chosen: null for the best the device takes.
 */
data class PlayerSettings(
    val passthrough: Set<PassthroughFormat> = PassthroughFormat.entries.toSet(),
    val matchFrameRate: Boolean = true,
    val segments: SegmentAction = SegmentAction.Button,
    val autoplayNext: Boolean = true,
    val stillWatchingAfter: Int = StillWatching.DEFAULT_LIMIT,
    val watchedThreshold: Float = PlaybackRules.DEFAULT_WATCHED_THRESHOLD,
    val seekBackSeconds: Int = DEFAULT_SEEK_BACK,
    val seekForwardSeconds: Int = DEFAULT_SEEK_FORWARD,
    val burnInAss: Boolean = false,
    val maxBitrate: Int? = null,
    val aspect: AspectMode = AspectMode.Fit,
    val subtitles: SubtitleStyle = SubtitleStyle(),
) {
    fun profileOptions(): ProfileOptions =
        ProfileOptions(
            passthrough = passthrough,
            maxBitrate = maxBitrate ?: ProfileOptions.MAX_BITRATE,
            burnInAss = burnInAss,
        )

    companion object {
        const val DEFAULT_SEEK_BACK = 10
        const val DEFAULT_SEEK_FORWARD = 30
        val SEEK_STEPS = listOf(5, 10, 15, 30, 60)
        val STILL_WATCHING_STEPS = listOf(0, 2, 3, 4, 5)
        val WATCHED_THRESHOLDS = listOf(0.8f, 0.85f, 0.9f, 0.95f)
    }
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

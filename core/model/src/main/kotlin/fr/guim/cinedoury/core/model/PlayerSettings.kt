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
 * background and height above the bottom of the picture.
 */
data class SubtitleStyle(
    val size: SubtitleSize = SubtitleSize.Normal,
    val color: SubtitleColor = SubtitleColor.White,
    val edge: SubtitleEdge = SubtitleEdge.Outline,
    val background: SubtitleBackground = SubtitleBackground.None,
    val position: SubtitlePosition = SubtitlePosition.Bottom,
)

/** The height of the text, as a fraction of the height of the picture. */
enum class SubtitleSize(
    val fraction: Float,
) {
    Small(0.045f),
    Normal(0.0533f),
    Large(0.065f),
    Huge(0.08f),
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

/** The box behind the text, in ARGB. */
enum class SubtitleBackground(
    val argb: Long,
) {
    None(0x00000000),
    Translucent(0x99000000),
    Opaque(0xFF000000),
}

/** The space under the text, as a fraction of the height of the picture. */
enum class SubtitlePosition(
    val bottomFraction: Float,
) {
    Bottom(0.08f),
    Raised(0.15f),
    High(0.25f),
}

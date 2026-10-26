package fr.guim.cinedoury.core.playback

/**
 * « Décalage audio » (`SPEC.md` § 4.16): the sound later (positive) or earlier than the picture, for
 * an amplifier or a TV that delays one of them. The video renderer of Media3 shows each picture when
 * the clock of the sound reaches it: the clock moved by the offset moves the pictures the other way.
 */
object AudioOffset {
    /** What the audio sink reports to Media3 when it does not know its position yet. */
    private const val NOT_SET = Long.MIN_VALUE
    private const val MICROS_PER_MILLI = 1_000L

    /**
     * The clock the pictures follow: [positionUs] of the sound heard, plus [offsetMillis] (the sound
     * then comes [offsetMillis] after its picture). Never before the start.
     */
    fun clock(
        positionUs: Long,
        offsetMillis: Int,
    ): Long =
        if (positionUs == NOT_SET) {
            positionUs
        } else {
            (positionUs + offsetMillis * MICROS_PER_MILLI).coerceAtLeast(0L)
        }
}

package fr.guim.cinedoury.core.model

/**
 * The place of a subtitle picture (PGS, VobSub) on the picture, in parts of its width and height:
 * [left] and [top] of its corner, [width] and [height].
 */
data class SubtitleBox(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
) {
    /**
     * The box at [scale], grown around the middle of its bottom edge (where the eye reads), moved up
     * by [raise], and kept on the picture.
     */
    fun scaled(
        scale: Float,
        raise: Float = 0f,
    ): SubtitleBox {
        val newWidth = (width * scale).coerceAtMost(1f)
        val newHeight = (height * scale).coerceAtMost(1f)
        val center = left + width / 2
        val bottom = top + height - raise
        return SubtitleBox(
            left = (center - newWidth / 2).coerceIn(0f, 1f - newWidth),
            top = (bottom - newHeight).coerceIn(0f, 1f - newHeight),
            width = newWidth,
            height = newHeight,
        )
    }
}

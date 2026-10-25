package fr.guim.cinedoury.core.playback

import androidx.annotation.OptIn
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi
import fr.guim.cinedoury.core.model.SubtitleBox
import fr.guim.cinedoury.core.model.SubtitleStyle

/**
 * The subtitles at the size and height of the settings (`FONCTIONNALITES.md` § 5), before they are
 * drawn. Everything is a part of the picture, never pixels:
 * - a text without a size of its own (SRT) takes the size of the style ([SubtitleStyle.textFraction]),
 *   drawn by the `SubtitleView`;
 * - a text with one (the styles of ASS) is scaled, so the styles keep their proportions;
 * - a picture (PGS, VobSub) grows around the middle of its bottom edge and moves up with the
 *   position ([SubtitleBox]).
 *
 * Subtitles burned into the picture by the server never reach here: they cannot change.
 */
@OptIn(UnstableApi::class)
object SubtitleCues {
    fun scale(
        cues: List<Cue>,
        style: SubtitleStyle,
    ): List<Cue> {
        if (style.scale == 1f && style.position.raise == 0f) return cues
        return cues.map { it.scaled(style) }
    }

    private fun Cue.scaled(style: SubtitleStyle): Cue =
        when {
            bitmap != null -> {
                bitmapScaled(style)
            }

            textSize != Cue.DIMEN_UNSET && textSizeType != Cue.TYPE_UNSET -> {
                buildUpon().setTextSize(textSize * style.scale, textSizeType).build()
            }

            else -> {
                this
            }
        }

    private fun Cue.bitmapScaled(style: SubtitleStyle): Cue {
        val placed =
            lineType == Cue.LINE_TYPE_FRACTION &&
                line != Cue.DIMEN_UNSET &&
                position != Cue.DIMEN_UNSET &&
                size != Cue.DIMEN_UNSET &&
                bitmapHeight != Cue.DIMEN_UNSET
        if (!placed) {
            return if (size ==
                Cue.DIMEN_UNSET
            ) {
                this
            } else {
                buildUpon().setSize((size * style.scale).coerceAtMost(1f)).build()
            }
        }
        val box =
            SubtitleBox(
                left = position - size * share(positionAnchor),
                top = line - bitmapHeight * share(lineAnchor),
                width = size,
                height = bitmapHeight,
            ).scaled(style.scale, style.position.raise)
        return buildUpon()
            .setPosition(box.left)
            .setPositionAnchor(Cue.ANCHOR_TYPE_START)
            .setLine(box.top, Cue.LINE_TYPE_FRACTION)
            .setLineAnchor(Cue.ANCHOR_TYPE_START)
            .setSize(box.width)
            .setBitmapHeight(box.height)
            .build()
    }

    /** The part of the box before its anchor: none at the start, half in the middle, all at the end. */
    private fun share(anchor: Int): Float =
        when (anchor) {
            Cue.ANCHOR_TYPE_MIDDLE -> HALF
            Cue.ANCHOR_TYPE_END -> 1f
            else -> 0f
        }

    private const val HALF = 0.5f
}

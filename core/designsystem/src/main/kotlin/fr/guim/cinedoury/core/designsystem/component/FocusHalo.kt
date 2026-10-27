package fr.guim.cinedoury.core.designsystem.component

import android.graphics.BlurMaskFilter
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * The blurred halo of the focus (`UI.md` § 3), drawn once into a bitmap and then only copied: a
 * `BlurMaskFilter` drawn live is blurred again by the GPU at each frame of the focus animation, over
 * the whole card, which a 4K screen pays four times (`SPEC.md` § 4.20).
 *
 * A rounded rectangle, the outline of every component, is cut in nine parts like a 9-patch: the
 * corners are drawn as blurred, the straight edges, the same all along, are stretched. One small
 * bitmap serves every card of a size class (all the posters of a row, all the buttons), whatever
 * their length. Other outlines get a bitmap of their own, kept by the element.
 *
 * Drawn at the resolution of 1080p at most ([scale], `TvLayout.imageScale`): a blur that soft does
 * not need more, and a 4K bitmap would weigh four times the memory.
 */
internal class FocusHalo private constructor(
    private val image: ImageBitmap,
    /** Pixels of the bitmap per pixel of the screen. */
    private val scale: Float,
    /** Room around the outline, in pixels of the screen. */
    private val pad: Float,
    /** In pixels of the bitmap: where the stretchable column and row start, or -1 (not stretched). */
    private val stretchX: Int,
    private val stretchY: Int,
) {
    /**
     * Draws the halo of an outline [width] × [height] whose top left corner is at [topLeft], at
     * [alpha].
     */
    fun DrawScope.draw(
        topLeft: Offset,
        width: Float,
        height: Float,
        alpha: Float,
    ) {
        val left = topLeft.x - pad
        val top = topLeft.y - pad
        val columns = spans(stretchX, image.width, width + pad * 2f)
        val rows = spans(stretchY, image.height, height + pad * 2f)
        for (row in rows) {
            for (column in columns) {
                drawImage(
                    image = image,
                    srcOffset = IntOffset(column.source, row.source),
                    srcSize = IntSize(column.sourceSize, row.sourceSize),
                    dstOffset = IntOffset((left + column.target).roundToInt(), (top + row.target).roundToInt()),
                    dstSize =
                        IntSize(
                            (left + column.target + column.targetSize).roundToInt() -
                                (left + column.target).roundToInt(),
                            (top + row.target + row.targetSize).roundToInt() - (top + row.target).roundToInt(),
                        ),
                    alpha = alpha,
                    filterQuality = FilterQuality.Low,
                )
            }
        }
    }

    /** One column (or row) of the 9-patch: where it is in the bitmap, and where on the screen. */
    private class Span(
        val source: Int,
        val sourceSize: Int,
        val target: Float,
        val targetSize: Float,
    )

    private fun spans(
        stretch: Int,
        sourceSize: Int,
        targetSize: Float,
    ): List<Span> {
        if (stretch < 0) return listOf(Span(0, sourceSize, 0f, targetSize))
        val cap = stretch / scale
        val end = sourceSize - stretch - STRETCH
        return listOf(
            Span(0, stretch, 0f, cap),
            Span(stretch, STRETCH, cap, targetSize - cap * 2f),
            Span(stretch + STRETCH, end, targetSize - cap, cap),
        )
    }

    companion object {
        /** Width of the stretched column (and height of the stretched row), in pixels of the bitmap. */
        private const val STRETCH = 2

        /** The halos of rounded rectangles, by size class: a few per screen (posters, thumbnails, buttons). */
        private val shared =
            object : LinkedHashMap<Key, FocusHalo>(MAX_SHARED, LOAD_FACTOR, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, FocusHalo>?) = size > MAX_SHARED
            }

        private data class Key(
            val width: Int,
            val height: Int,
            val corner: Int,
            val blur: Int,
            val color: Int,
            /** The same bitmap is drawn at another size on another screen: 1080p and 4K differ. */
            val scale: Float,
        )

        /**
         * The halo of [outline], blurred by [blur] pixels of the screen, in [color] (opaque: the
         * alpha is given when drawn), at [scale] pixels of bitmap per pixel of the screen.
         */
        fun of(
            outline: Outline,
            blur: Float,
            color: Int,
            scale: Float,
        ): FocusHalo {
            val rect = (outline as? Outline.Rounded)?.roundRect
            val corner = rect?.uniformCorner()
            if (rect == null || corner == null) return generic(outline, blur, color, scale)
            val bitmapBlur = blur * scale
            val pad = padding(bitmapBlur)
            val radius = ceil(corner * scale).toInt()
            // A column (row) stretches when the outline is long enough to hold both caps.
            val cap = radius + pad * 2
            val width = ceil(rect.width * scale).toInt()
            val height = ceil(rect.height * scale).toInt()
            val stretchesX = width + pad * 2 >= cap * 2 + STRETCH
            val stretchesY = height + pad * 2 >= cap * 2 + STRETCH
            val key =
                Key(
                    width = if (stretchesX) -1 else width,
                    height = if (stretchesY) -1 else height,
                    corner = radius,
                    blur = (bitmapBlur * BLUR_STEPS).roundToInt(),
                    color = color,
                    scale = scale,
                )
            return synchronized(shared) {
                shared.getOrPut(key) {
                    // The straight edges of the source: the stretched pixels, and a blur's reach on each side.
                    val sourceWidth = if (stretchesX) cap * 2 + STRETCH - pad * 2 else width
                    val sourceHeight = if (stretchesY) cap * 2 + STRETCH - pad * 2 else height
                    val path =
                        Path().apply {
                            addRoundRect(
                                RoundRect(
                                    left = 0f,
                                    top = 0f,
                                    right = sourceWidth.toFloat(),
                                    bottom = sourceHeight.toFloat(),
                                    cornerRadius = CornerRadius(radius.toFloat()),
                                ),
                            )
                        }
                    FocusHalo(
                        image = blurred(path, sourceWidth, sourceHeight, pad, bitmapBlur, color),
                        scale = scale,
                        pad = pad / scale,
                        stretchX = if (stretchesX) cap else -1,
                        stretchY = if (stretchesY) cap else -1,
                    )
                }
            }
        }

        /** Any other outline: the whole halo, at its size, for the element alone. */
        private fun generic(
            outline: Outline,
            blur: Float,
            color: Int,
            scale: Float,
        ): FocusHalo {
            val bitmapBlur = blur * scale
            val pad = padding(bitmapBlur)
            val bounds = outline.bounds
            val width = ceil(bounds.width * scale).toInt().coerceAtLeast(1)
            val height = ceil(bounds.height * scale).toInt().coerceAtLeast(1)
            val path =
                Path().apply {
                    addOutline(outline)
                    transform(
                        androidx.compose.ui.graphics
                            .Matrix()
                            .apply { scale(scale, scale) },
                    )
                }
            return FocusHalo(blurred(path, width, height, pad, bitmapBlur, color), scale, pad / scale, -1, -1)
        }

        /** [path] blurred outwards by [blur] in [color], with [pad] pixels of room on each side. */
        private fun blurred(
            path: Path,
            width: Int,
            height: Int,
            pad: Int,
            blur: Float,
            color: Int,
        ): ImageBitmap {
            val bitmap =
                android.graphics.Bitmap.createBitmap(
                    width + pad * 2,
                    height + pad * 2,
                    android.graphics.Bitmap.Config.ARGB_8888,
                )
            val paint =
                android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = color
                    maskFilter = BlurMaskFilter(blur.coerceAtLeast(MIN_BLUR), BlurMaskFilter.Blur.OUTER)
                }
            android.graphics.Canvas(bitmap).apply {
                translate(pad.toFloat(), pad.toFloat())
                drawPath(path.asAndroidPath(), paint)
            }
            return bitmap.asImageBitmap()
        }

        /** How far a blur of [blur] reaches: three sigmas, as Skia turns a radius into a sigma. */
        private fun padding(blur: Float): Int = ceil(SIGMAS * (SIGMA_PER_RADIUS * blur + SIGMA_OFFSET)).toInt() + 1

        private fun RoundRect.uniformCorner(): Float? {
            val radii =
                listOf(topLeftCornerRadius, topRightCornerRadius, bottomRightCornerRadius, bottomLeftCornerRadius)
            val first = radii.first()
            return first.x.takeIf { radii.all { it.x == first.x && it.y == first.x } }
        }

        private const val MAX_SHARED = 24
        private const val LOAD_FACTOR = 0.75f
        private const val BLUR_STEPS = 4
        private const val MIN_BLUR = 0.5f
        private const val SIGMAS = 3f
        private const val SIGMA_PER_RADIUS = 0.57735f
        private const val SIGMA_OFFSET = 0.5f
    }
}

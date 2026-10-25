package fr.guim.cinedoury.core.designsystem.image

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.withSign

/**
 * Decoder for BlurHash (https://blurha.sh), the compact blurred preview Jellyfin gives for each
 * image. Decoding a few dozen pixels costs nothing, so the preview shows before the image arrives
 * and stretches into the ambient backdrop.
 */
object BlurHash {
    private const val ALPHABET =
        "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz#$%*+,-.:;=?@[]^_{|}~"
    private const val BASE = 83
    private const val SIZE_COMPONENTS = 9
    private const val AC_STEPS = 19
    private const val AC_HALF = 9f
    private const val MAX_AC_SCALE = 166f
    private const val CHANNEL = 255f
    private const val BYTE = 0xFF
    private const val OPAQUE = 0xFF shl 24

    /**
     * The [width] × [height] preview of [hash], as ARGB pixels row by row, or null if [hash] is not
     * a valid BlurHash. [punch] raises or lowers the contrast.
     */
    fun decode(
        hash: String,
        width: Int,
        height: Int,
        punch: Float = 1f,
    ): IntArray? {
        require(width > 0 && height > 0) { "The size must be positive" }
        val components =
            try {
                parse(hash, punch)
            } catch (_: IllegalArgumentException) {
                return null
            }
        return render(components, width, height)
    }

    private const val MIN_LENGTH = 6
    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8

    /** The colours of the cosine components, row by row. Throws on an invalid hash. */
    private class Components(
        val colors: Array<FloatArray>,
        val countX: Int,
        val countY: Int,
    )

    private fun parse(
        hash: String,
        punch: Float,
    ): Components {
        require(hash.length >= MIN_LENGTH)
        val sizeFlag = decode83(hash, 0, 1)
        val countY = sizeFlag / SIZE_COMPONENTS + 1
        val countX = sizeFlag % SIZE_COMPONENTS + 1
        require(hash.length == MIN_LENGTH + 2 * (countX * countY - 1))
        val maxAc = (decode83(hash, 1, 2) + 1) / MAX_AC_SCALE * punch
        val colors =
            Array(countX * countY) { index ->
                if (index == 0) {
                    decodeDc(decode83(hash, 2, MIN_LENGTH))
                } else {
                    val start = MIN_LENGTH + (index - 1) * 2
                    decodeAc(decode83(hash, start, start + 2), maxAc)
                }
            }
        return Components(colors, countX, countY)
    }

    private fun render(
        components: Components,
        width: Int,
        height: Int,
    ): IntArray {
        val cosX = Array(components.countX) { i -> FloatArray(width) { x -> cos(PI * x * i / width).toFloat() } }
        val cosY = Array(components.countY) { j -> FloatArray(height) { y -> cos(PI * y * j / height).toFloat() } }
        return IntArray(width * height) { pixel -> color(components, cosX, cosY, pixel % width, pixel / width) }
    }

    private fun color(
        components: Components,
        cosX: Array<FloatArray>,
        cosY: Array<FloatArray>,
        x: Int,
        y: Int,
    ): Int {
        var r = 0f
        var g = 0f
        var b = 0f
        for (j in 0 until components.countY) {
            for (i in 0 until components.countX) {
                val basis = cosX[i][x] * cosY[j][y]
                val color = components.colors[i + j * components.countX]
                r += color[0] * basis
                g += color[1] * basis
                b += color[2] * basis
            }
        }
        return OPAQUE or (linearToSrgb(r) shl RED_SHIFT) or (linearToSrgb(g) shl GREEN_SHIFT) or linearToSrgb(b)
    }

    /** Base 83 digits of [text] from [from] to [to]; throws on a character outside the alphabet. */
    private fun decode83(
        text: String,
        from: Int,
        to: Int,
    ): Int {
        var value = 0
        for (index in from until to) {
            val digit = ALPHABET.indexOf(text[index])
            require(digit >= 0)
            value = value * BASE + digit
        }
        return value
    }

    private fun decodeDc(value: Int): FloatArray =
        floatArrayOf(
            srgbToLinear(value shr RED_SHIFT and BYTE),
            srgbToLinear(value shr GREEN_SHIFT and BYTE),
            srgbToLinear(value and BYTE),
        )

    private fun decodeAc(
        value: Int,
        maxAc: Float,
    ): FloatArray {
        val r = value / (AC_STEPS * AC_STEPS)
        val g = value / AC_STEPS % AC_STEPS
        val b = value % AC_STEPS
        return floatArrayOf(acChannel(r, maxAc), acChannel(g, maxAc), acChannel(b, maxAc))
    }

    private fun acChannel(
        quantised: Int,
        maxAc: Float,
    ): Float {
        val value = (quantised - AC_HALF) / AC_HALF
        return (value * value).withSign(value) * maxAc
    }

    private fun srgbToLinear(value: Int): Float {
        val v = value / CHANNEL
        return if (v <= SRGB_KNEE) v / SRGB_SLOPE else ((v + SRGB_OFFSET) / (1 + SRGB_OFFSET)).pow(SRGB_GAMMA)
    }

    private fun linearToSrgb(value: Float): Int {
        val v = value.coerceIn(0f, 1f)
        val srgb =
            if (v <= LINEAR_KNEE) v * SRGB_SLOPE else (1 + SRGB_OFFSET) * v.pow(1 / SRGB_GAMMA) - SRGB_OFFSET
        return (srgb * CHANNEL + ROUNDING).toInt().coerceIn(0, BYTE)
    }

    private const val SRGB_KNEE = 0.04045f
    private const val LINEAR_KNEE = 0.0031308f
    private const val SRGB_SLOPE = 12.92f
    private const val SRGB_OFFSET = 0.055f
    private const val SRGB_GAMMA = 2.4f
    private const val ROUNDING = 0.5f
}

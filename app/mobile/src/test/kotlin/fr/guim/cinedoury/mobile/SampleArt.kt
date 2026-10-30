package fr.guim.cinedoury.mobile

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.core.content.res.ResourcesCompat
import coil3.annotation.ExperimentalCoilApi
import coil3.asImage
import coil3.compose.AsyncImagePainter
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler
import coil3.compose.asPainter
import coil3.request.NullRequestData
import coil3.request.SuccessResult
import coil3.request.crossfade
import fr.guim.cinedoury.core.designsystem.component.artworkColors
import fr.guim.cinedoury.core.designsystem.image.ImageSource
import fr.guim.cinedoury.core.designsystem.image.SizedImage
import fr.guim.cinedoury.core.model.ImageRef
import fr.guim.cinedoury.core.model.ImageType
import fr.guim.cinedoury.core.model.ItemId
import org.robolectric.RuntimeEnvironment
import java.net.URLDecoder
import java.net.URLEncoder
import fr.guim.cinedoury.core.designsystem.R as DesignR

/**
 * The artwork of the test server, drawn at the size asked as `ci/jellyfin/images.sh` draws it: a
 * gradient and the title in Inter for a poster, a gradient for a backdrop, the title alone for a
 * logo, initials for a photo. Never a real poster. The screenshots of J3 show it through Coil's
 * preview handler, synchronously.
 */
internal object SampleArt {
    /** An image of [title] as the server would describe it; its tag carries what to draw. */
    fun image(
        title: String,
        type: ImageType,
    ) = ImageRef(ItemId(title), type, tag = URLEncoder.encode(title, Charsets.UTF_8))

    val sources: (ImageRef) -> ImageSource = { ref ->
        ImageSource { width, height -> "$SCHEME:${ref.type.name}:${ref.tag}:$width:$height" }
    }

    @OptIn(ExperimentalCoilApi::class)
    private val handler =
        AsyncImagePreviewHandler { _, request ->
            // RemoteImage asks for a SizedImage, whose URL Coil makes once it knows the size.
            val data = request.data
            val url =
                (if (data is SizedImage) data.url(request.sizeResolver.size()) else data.toString())
                    // No URL (MobileSamples.noImages): Coil's null data, drawn as before RemoteImage asked for a
                    // SizedImage, so that the references stay the same.
                    ?: NullRequestData.toString()
            val parts = url.split(":")
            // An image that is not one of ours (the samples of J2 have none): a backdrop of its name.
            val image =
                if (parts.size > HEIGHT && parts[0] == SCHEME) {
                    draw(
                        ImageType.valueOf(parts[TYPE]),
                        URLDecoder.decode(parts[TAG], Charsets.UTF_8),
                        parts[WIDTH].toInt(),
                        parts[HEIGHT].toInt(),
                    )
                } else {
                    draw(ImageType.Backdrop, url, FALLBACK_SIZE, FALLBACK_SIZE)
                }.asImage()
            // Without a crossfade: Coil times it on the real clock, and a capture would catch it at its start.
            val shown = request.newBuilder().crossfade(false).build()
            AsyncImagePainter.State.Success(image.asPainter(request.context), SuccessResult(image, shown))
        }

    /** Lets [content] draw the sample artwork. */
    @OptIn(ExperimentalCoilApi::class)
    @Composable
    fun Provide(content: @Composable () -> Unit) {
        CompositionLocalProvider(
            LocalInspectionMode provides true,
            LocalAsyncImagePreviewHandler provides handler,
            content = content,
        )
    }

    private val bold: Typeface by lazy {
        ResourcesCompat.getFont(RuntimeEnvironment.getApplication(), DesignR.font.inter_bold) ?: Typeface.DEFAULT_BOLD
    }

    private fun draw(
        type: ImageType,
        title: String,
        width: Int,
        height: Int,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val (top, bottom) = artworkColors(title.reversed())
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = bold }
        if (type != ImageType.Logo) {
            val fill =
                Paint().apply {
                    shader =
                        LinearGradient(
                            0f,
                            0f,
                            width.toFloat(),
                            height.toFloat(),
                            brighter(top.toArgb()),
                            bottom.toArgb(),
                            Shader.TileMode.CLAMP,
                        )
                }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fill)
        }
        when (type) {
            ImageType.Primary -> {
                if (height > width) {
                    text.color = android.graphics.Color.WHITE
                    text.textSize = width / POSTER_TEXT
                    canvas.drawText(title, width / MARGIN, height - width / MARGIN, text)
                }
            }

            ImageType.Logo -> {
                text.color = android.graphics.Color.WHITE
                text.textSize = height * LOGO_TEXT
                while (text.measureText(title) > width && text.textSize > 1f) text.textSize *= SHRINK
                text.setShadowLayer(
                    height / SHADOW,
                    height / SHADOW,
                    height / SHADOW,
                    android.graphics.Color.argb(SHADOW_ALPHA, 0, 0, 0),
                )
                canvas.drawText(title, 0f, (height + text.textSize * CAP) / 2, text)
            }

            ImageType.Backdrop, ImageType.Thumb -> {}
        }
        return bitmap
    }

    /** The photo of a person: their initials. */
    fun photo(name: String) = image(name, ImageType.Primary)

    private fun brighter(color: Int): Int {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color, hsv)
        hsv[2] = (hsv[2] * BRIGHTER).coerceAtMost(1f)
        return android.graphics.Color.HSVToColor(hsv)
    }

    private const val SCHEME = "sample"
    private const val FALLBACK_SIZE = 64
    private const val TYPE = 1
    private const val TAG = 2
    private const val WIDTH = 3
    private const val HEIGHT = 4
    private const val POSTER_TEXT = 8f
    private const val MARGIN = 12f
    private const val LOGO_TEXT = 0.7f
    private const val SHRINK = 0.9f
    private const val SHADOW = 40f
    private const val SHADOW_ALPHA = 128
    private const val CAP = 0.7f
    private const val BRIGHTER = 1.6f
}

package fr.guim.cinedoury.core.designsystem.image

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import coil3.ComponentRegistry
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.map.Mapper
import coil3.request.ImageRequest
import coil3.request.Options
import coil3.request.crossfade
import coil3.size.Size
import coil3.size.pxOrElse
import fr.guim.cinedoury.core.designsystem.theme.CineAmbient
import fr.guim.cinedoury.core.designsystem.theme.CineMotion
import fr.guim.cinedoury.core.designsystem.theme.TvLayout
import kotlin.math.roundToInt

/**
 * Where an image comes from, at the size it is displayed: the server resizes it (`UI.md` § 6,
 * never a full-size image for a thumbnail). A null URL shows the preview only.
 */
@Immutable
fun interface ImageSource {
    fun url(
        widthPx: Int,
        heightPx: Int,
    ): String?
}

/**
 * What [RemoteImage] asks Coil for: its URL is made once Coil knows the size the image takes on
 * screen ([ImageSourceMapper]), from the layout, without measuring the card a second time.
 *
 * @param scale applied to that size before it is asked of the server ([TvLayout.imageScale]).
 */
@Immutable
data class SizedImage(
    val source: ImageSource,
    val scale: Float = 1f,
) {
    /** The URL at [size], as the server is asked it. */
    fun url(size: Size): String? {
        val width = size.width.pxOrElse { 0 }
        val height = size.height.pxOrElse { 0 }
        return source.url((width * scale).roundToInt(), (height * scale).roundToInt())
    }
}

/** Turns a [SizedImage] into its URL at the size Coil resolved. Register it in the image loader. */
object ImageSourceMapper : Mapper<SizedImage, String> {
    override fun map(
        data: SizedImage,
        options: Options,
    ): String? = data.url(options.size)
}

/** The components the image loader of the app needs for [RemoteImage] and [PrefetchImages]. */
fun ComponentRegistry.Builder.addCineImages(): ComponentRegistry.Builder = add(ImageSourceMapper)

/** Decoded previews: a card coming back on screen does not decode its BlurHash again. */
private val previews = LruCache<String, ImageBitmap>(PREVIEW_CACHE)

private const val PREVIEW_CACHE = 256

/**
 * [hash] decoded at [width] × [height], or null if it is not a valid BlurHash. Kept in a small cache:
 * decoding is done on the main thread, while a list composes its cards.
 */
fun blurHashBitmap(
    hash: String,
    width: Int,
    height: Int,
): ImageBitmap? {
    val key = "$width:$height:$hash"
    previews.get(key)?.let { return it }
    return BlurHash
        .decode(hash, width, height)
        ?.let { pixels -> Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888).asImageBitmap() }
        ?.also { previews.put(key, it) }
}

/**
 * An image of the server, loaded by Coil at the size it takes on screen (at most the size of a
 * 1080p screen, [TvLayout.imageScale]). Its BlurHash shows while it loads (and stays if it fails),
 * then the image fades in over 200 ms (`UI.md` § 6).
 */
@Composable
fun RemoteImage(
    source: ImageSource,
    blurHash: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val preview =
        remember(blurHash) {
            blurHash
                ?.let { blurHashBitmap(it, CineAmbient.PLACEHOLDER_SIZE, CineAmbient.PLACEHOLDER_SIZE) }
                ?.let(::BitmapPainter)
        }
    val context = LocalPlatformContext.current
    val scale = TvLayout.imageScale(LocalDensity.current.density)
    val request =
        remember(source, scale) {
            ImageRequest
                .Builder(context)
                .data(SizedImage(source, scale))
                .crossfade(CineMotion.IMAGE_FADE_MILLIS)
                .build()
        }
    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        modifier = modifier.fillMaxSize(),
        placeholder = preview,
        error = preview,
        contentScale = contentScale,
    )
}

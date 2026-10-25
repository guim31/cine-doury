package fr.guim.cinedoury.core.designsystem.image

import android.graphics.Bitmap
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import fr.guim.cinedoury.core.designsystem.theme.CineAmbient
import fr.guim.cinedoury.core.designsystem.theme.CineMotion

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

/** [hash] decoded at [width] × [height], or null if it is not a valid BlurHash. */
fun blurHashBitmap(
    hash: String,
    width: Int,
    height: Int,
): ImageBitmap? =
    BlurHash.decode(hash, width, height)?.let { pixels ->
        Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888).asImageBitmap()
    }

/**
 * An image of the server, loaded by Coil at the size it takes on screen. Its BlurHash shows while
 * it loads (and stays if it fails), then the image fades in over 200 ms (`UI.md` § 6).
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
    BoxWithConstraints(modifier) {
        val url = source.url(constraints.maxWidth, constraints.maxHeight)
        val request =
            remember(url) {
                url?.let {
                    ImageRequest
                        .Builder(context)
                        .data(it)
                        .crossfade(CineMotion.IMAGE_FADE_MILLIS)
                        .build()
                }
            }
        AsyncImage(
            model = request,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
            placeholder = preview,
            error = preview,
            contentScale = contentScale,
        )
    }
}

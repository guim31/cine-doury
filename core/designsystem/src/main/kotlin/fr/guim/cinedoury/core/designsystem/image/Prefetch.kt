package fr.guim.cinedoury.core.designsystem.image

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest

/**
 * Loads [sources] into Coil's caches at [size] before they are drawn: the first images of the rows
 * next to the screen (`UI.md` § 6). The URLs are those [RemoteImage] asks at the same size, so that
 * the card finds them in the cache.
 */
@Composable
fun PrefetchImages(
    sources: List<ImageSource>,
    size: DpSize,
) {
    val context = LocalPlatformContext.current
    val density = LocalDensity.current
    val width = with(density) { size.width.roundToPx() }
    val height = with(density) { size.height.roundToPx() }
    LaunchedEffect(sources, width, height) {
        val loader = SingletonImageLoader.get(context)
        sources.mapNotNull { it.url(width, height) }.forEach { url ->
            loader.enqueue(ImageRequest.Builder(context).data(url).build())
        }
    }
}

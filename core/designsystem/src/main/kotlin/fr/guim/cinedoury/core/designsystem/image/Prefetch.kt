package fr.guim.cinedoury.core.designsystem.image

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import fr.guim.cinedoury.core.designsystem.theme.TvLayout

/**
 * Loads [sources] into Coil's caches at [size] before they are drawn: the first images of the rows
 * next to the screen (`UI.md` § 6). The requests are those [RemoteImage] makes at the same size, so
 * that the card finds them in the cache.
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
    val scale = TvLayout.imageScale(density.density)
    LaunchedEffect(sources, width, height, scale) {
        val loader = SingletonImageLoader.get(context)
        sources.forEach { source ->
            loader.enqueue(
                ImageRequest
                    .Builder(context)
                    .data(SizedImage(source, scale))
                    .size(width, height)
                    .build(),
            )
        }
    }
}

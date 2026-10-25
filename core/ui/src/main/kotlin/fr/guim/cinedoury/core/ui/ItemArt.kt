package fr.guim.cinedoury.core.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import fr.guim.cinedoury.core.designsystem.component.Ambience
import fr.guim.cinedoury.core.designsystem.image.ImageSource
import fr.guim.cinedoury.core.designsystem.image.RemoteImage
import fr.guim.cinedoury.core.model.ImageRef
import fr.guim.cinedoury.core.model.ItemKind
import fr.guim.cinedoury.core.model.MediaItem

/** Where the images of the server in use come from, at the size they are drawn. */
typealias ImageSources = (ImageRef) -> ImageSource

/**
 * The image of a card slot for [image], remembered so that a card does not recompose for a new
 * lambda; null without image (the card draws its generated artwork).
 */
@Composable
fun rememberArt(
    image: ImageRef?,
    sources: ImageSources,
    contentScale: ContentScale = ContentScale.Crop,
): (@Composable () -> Unit)? =
    remember(image) {
        image?.let { ref ->
            @Composable {
                RemoteImage(sources(ref), ref.blurHash, contentDescription = null, Modifier.fillMaxSize(), contentScale)
            }
        }
    }

/** The 2:3 picture of an item: its poster, or the poster of the series of an episode. */
val MediaItem.posterImage: ImageRef? get() = images.poster

/** The 16:9 picture of an item: the frame of an episode, a thumb or a backdrop of a film or series. */
val MediaItem.landscapeImage: ImageRef?
    get() = if (kind == ItemKind.Episode) images.primary ?: images.landscape else images.landscape

/** The ambient backdrop of an item: its backdrop's BlurHash, else its poster's, else its gradient. */
val MediaItem.ambience: Ambience
    get() = Ambience.of(cardTitle, (images.backdrop ?: images.poster ?: images.primary)?.blurHash)

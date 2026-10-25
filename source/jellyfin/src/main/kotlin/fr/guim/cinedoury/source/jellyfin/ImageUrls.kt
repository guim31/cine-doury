package fr.guim.cinedoury.source.jellyfin

import fr.guim.cinedoury.core.model.ImageRef
import fr.guim.cinedoury.core.source.ServerAddress
import java.net.URLEncoder

/** JPEG quality asked for every image: the difference with 100 is invisible at 3 m. */
private const val IMAGE_QUALITY = 90

/**
 * `GET /Items/{id}/Images/{type}[/{index}]`, resized and cropped by the server to fill
 * [width] × [height] pixels. The tag makes the URL change when the image changes, so caches never
 * serve an old image. Images are readable without a token (`SPEC.md` § 4.6).
 */
internal fun imageUrlOf(
    server: ServerAddress,
    image: ImageRef,
    width: Int,
    height: Int,
): String {
    val index = if (image.index > 0) "/${image.index}" else ""
    val tag = URLEncoder.encode(image.tag, Charsets.UTF_8)
    return "${server.url}/Items/${image.itemId.value}/Images/${image.type.name}$index" +
        "?fillWidth=$width&fillHeight=$height&quality=$IMAGE_QUALITY&tag=$tag"
}

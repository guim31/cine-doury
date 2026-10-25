package fr.guim.cinedoury.core.model

enum class ImageType {
    Primary,
    Backdrop,
    Logo,
    Thumb,
}

/**
 * An image the server holds for an item. [tag] changes when the image changes, which makes it a
 * cache key; [blurHash] is the preview shown while the image loads.
 */
data class ImageRef(
    val itemId: ItemId,
    val type: ImageType,
    val tag: String,
    val index: Int = 0,
    val blurHash: String? = null,
)

/**
 * The images of an item. An episode or a season also carries the images of its series ([parentBackdrop],
 * [parentLogo], [parentThumb], [seriesPrimary]): the server sends their tags with the item.
 */
data class ItemImages(
    val primary: ImageRef? = null,
    val backdrops: List<ImageRef> = emptyList(),
    val logo: ImageRef? = null,
    val thumb: ImageRef? = null,
    val seriesPrimary: ImageRef? = null,
    val parentBackdrop: ImageRef? = null,
    val parentLogo: ImageRef? = null,
    val parentThumb: ImageRef? = null,
) {
    /** A full-screen picture: the item's backdrop, else its series'. */
    val backdrop: ImageRef? get() = backdrops.firstOrNull() ?: parentBackdrop

    /** The title as a picture (clearlogo): the item's, else its series'. */
    val anyLogo: ImageRef? get() = logo ?: parentLogo

    /** A 16:9 picture: a thumb, the backdrop, or the frame of an episode. */
    val landscape: ImageRef? get() = thumb ?: backdrop ?: parentThumb

    /** A 2:3 picture: the poster, or the series poster for an episode. */
    val poster: ImageRef? get() = seriesPrimary ?: primary
}

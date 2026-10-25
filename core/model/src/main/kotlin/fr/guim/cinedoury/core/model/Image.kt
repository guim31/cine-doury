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

data class ItemImages(
    val primary: ImageRef? = null,
    val backdrops: List<ImageRef> = emptyList(),
    val logo: ImageRef? = null,
    val thumb: ImageRef? = null,
)

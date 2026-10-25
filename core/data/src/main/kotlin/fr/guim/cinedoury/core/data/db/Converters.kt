package fr.guim.cinedoury.core.data.db

import androidx.room.TypeConverter
import fr.guim.cinedoury.core.model.ImageRef
import fr.guim.cinedoury.core.model.ImageType
import fr.guim.cinedoury.core.model.ItemId

/** Room converters. An image is one text column: id, type, tag, index and BlurHash. */
class Converters {
    @TypeConverter
    fun fromImage(image: ImageRef?): String? =
        image?.let {
            listOf(it.itemId.value, it.type.name, it.tag, it.index.toString(), it.blurHash.orEmpty())
                .joinToString(SEPARATOR)
        }

    @TypeConverter
    fun toImage(value: String?): ImageRef? {
        val parts = value?.split(SEPARATOR)?.takeIf { it.size == IMAGE_PARTS } ?: return null
        val type = ImageType.entries.firstOrNull { it.name == parts[1] } ?: return null
        return ImageRef(
            itemId = ItemId(parts[0]),
            type = type,
            tag = parts[2],
            index = parts[3].toIntOrNull() ?: 0,
            blurHash = parts[4].ifEmpty { null },
        )
    }

    companion object {
        /**
         * The ASCII unit separator: it appears in no id, tag, name or BlurHash (whose alphabet has
         * `|`, `,` and `;`).
         */
        const val SEPARATOR = "\u001F"
        private const val IMAGE_PARTS = 5
    }
}

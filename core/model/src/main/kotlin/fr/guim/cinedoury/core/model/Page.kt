package fr.guim.cinedoury.core.model

/** A slice of a longer list; [totalCount] is the length of the whole list. */
data class Page<T>(
    val items: List<T>,
    val startIndex: Int,
    val totalCount: Int,
) {
    val nextStartIndex: Int? get() = (startIndex + items.size).takeIf { items.isNotEmpty() && it < totalCount }
}

data class PageRequest(
    val startIndex: Int = 0,
    val limit: Int = DEFAULT_LIMIT,
) {
    init {
        require(startIndex >= 0) { "startIndex must not be negative" }
        require(limit > 0) { "limit must be positive" }
    }

    companion object {
        const val DEFAULT_LIMIT = 60
    }
}

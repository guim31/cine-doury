package fr.guim.cinedoury.core.model

@JvmInline
value class LibraryId(val value: String)

/** What a library holds; it decides the screens and the default queries. */
enum class LibraryKind {
    Movies,
    Shows,
    Collections,
    HomeVideos,
    Mixed,
    Other,
}

data class Library(
    val id: LibraryId,
    val name: String,
    val kind: LibraryKind,
    val image: ImageRef? = null,
)

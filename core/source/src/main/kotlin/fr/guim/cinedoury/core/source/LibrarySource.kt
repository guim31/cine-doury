package fr.guim.cinedoury.core.source

import fr.guim.cinedoury.core.model.ItemId
import fr.guim.cinedoury.core.model.Library
import fr.guim.cinedoury.core.model.LibraryId
import fr.guim.cinedoury.core.model.MediaItem
import fr.guim.cinedoury.core.model.Page
import fr.guim.cinedoury.core.model.PageRequest

/** The libraries of a signed-in user. Every failure is a [SourceException]. */
interface LibrarySource {
    val session: Session

    /** The user's libraries, in the order the server gives them. */
    suspend fun libraries(): List<Library>

    /**
     * The top-level items of a library, sorted by sort title. Films stay individual items even
     * when they belong to a collection.
     */
    suspend fun items(library: LibraryId, page: PageRequest = PageRequest()): Page<MediaItem>

    /** One item with everything the detail screen needs, media sources included. */
    suspend fun item(id: ItemId): MediaItem
}

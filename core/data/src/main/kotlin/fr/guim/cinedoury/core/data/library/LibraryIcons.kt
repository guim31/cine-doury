package fr.guim.cinedoury.core.data.library

import fr.guim.cinedoury.core.data.db.IndexDao
import fr.guim.cinedoury.core.data.db.LibraryIconEntity
import fr.guim.cinedoury.core.model.Library
import fr.guim.cinedoury.core.model.LibraryIcon
import fr.guim.cinedoury.core.model.LibraryId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * The icons of the libraries in the rail (`FONCTIONNALITES.md` § 3): one chosen from the name and the
 * kind ([LibraryIcon.defaultFor]), or the one the user picked in the settings, kept on the device per
 * server.
 */
class LibraryIconRepository(
    private val dao: IndexDao,
) {
    /** The icons the user picked, by library. */
    fun chosen(serverId: String): Flow<Map<LibraryId, LibraryIcon>> =
        dao
            .observeIcons(serverId)
            .map { rows ->
                rows
                    .mapNotNull { row ->
                        LibraryIcon.entries.firstOrNull { it.name == row.icon }?.let {
                            LibraryId(row.libraryId) to
                                it
                        }
                    }.toMap()
            }.distinctUntilChanged()

    /** [icon] for [library]; null goes back to the icon chosen from its name. */
    suspend fun set(
        serverId: String,
        library: LibraryId,
        icon: LibraryIcon?,
    ) {
        if (icon == null) {
            dao.resetIcon(serverId, library.value)
        } else {
            dao.setIcon(LibraryIconEntity(serverId, library.value, icon.name))
        }
    }
}

/** The icon of this library: the one the user picked, else the one its name and kind suggest. */
fun Library.icon(chosen: Map<LibraryId, LibraryIcon>): LibraryIcon = chosen[id] ?: LibraryIcon.defaultFor(kind, name)

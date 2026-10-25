package fr.guim.cinedoury.core.data.manage

import fr.guim.cinedoury.core.data.home.HomeRepository
import fr.guim.cinedoury.core.data.index.LibraryIndex
import fr.guim.cinedoury.core.model.DeletionPreview
import fr.guim.cinedoury.core.model.ItemId
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** What a management action changed on the server (`SPEC.md` § 4.12). */
sealed interface ItemChange {
    val item: ItemId

    /** [item] and its [children] (the episodes of a season or a series) are gone. */
    data class Deleted(
        override val item: ItemId,
        val children: Set<ItemId> = emptySet(),
    ) : ItemChange {
        /** Whether [id] went with the deletion. */
        operator fun contains(id: ItemId): Boolean = id == item || id in children
    }

    /** The metadata or the pictures of [item] changed ([kind] says how): whoever shows it reads it again. */
    data class Changed(
        override val item: ItemId,
        val kind: ChangeKind = ChangeKind.Refreshed,
    ) : ItemChange
}

/** What changed an item. */
enum class ChangeKind {
    /** A refresh was asked: the server does it in the background. */
    Refreshed,
    Identified,
    Image,
    Watched,
}

/**
 * The screens that show an item follow what management did to it, wherever it was done: a library
 * reads its page again, a details screen under the one that deleted an episode reads its episodes.
 */
class ManageUpdates {
    private val changes = MutableSharedFlow<ItemChange>(extraBufferCapacity = CAPACITY)

    val updates: SharedFlow<ItemChange> = changes.asSharedFlow()

    fun publish(change: ItemChange) {
        changes.tryEmit(change)
    }

    private companion object {
        const val CAPACITY = 16
    }
}

/**
 * What the app keeps of an item once management changed it (`SPEC.md` § 4.12): a deleted item
 * leaves the cache of the home (every profile of the server) and the local index at once, before
 * any refresh; then every screen hears of it through [updates].
 */
class ManageRepository(
    private val home: HomeRepository,
    private val index: LibraryIndex,
    val updates: ManageUpdates,
) {
    suspend fun deleted(
        serverId: String,
        preview: DeletionPreview,
    ) {
        val children = preview.episodes.orEmpty().toSet()
        val gone = children + preview.item.id
        home.forget(serverId, gone)
        index.forget(serverId, gone)
        updates.publish(ItemChange.Deleted(preview.item.id, children))
    }

    /** [item] was refreshed, identified again or given a new picture. */
    fun changed(
        item: ItemId,
        kind: ChangeKind = ChangeKind.Refreshed,
    ) = updates.publish(ItemChange.Changed(item, kind))
}

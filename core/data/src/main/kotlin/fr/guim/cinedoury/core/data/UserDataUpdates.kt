package fr.guim.cinedoury.core.data

import fr.guim.cinedoury.core.model.ItemId
import fr.guim.cinedoury.core.model.UserData
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * What changed on an item while another screen was in front (`SPEC.md` § 4.11): the player says
 * where it stopped and whether the item is watched; the details screens under it follow, without
 * asking the server again.
 */
class UserDataUpdates {
    private val changes = MutableSharedFlow<Pair<ItemId, UserData>>(extraBufferCapacity = CAPACITY)

    val updates: SharedFlow<Pair<ItemId, UserData>> = changes.asSharedFlow()

    fun publish(
        item: ItemId,
        data: UserData,
    ) {
        changes.tryEmit(item to data)
    }

    private companion object {
        const val CAPACITY = 16
    }
}

package fr.guim.cinedoury.core.data.quick

import fr.guim.cinedoury.core.data.SessionManager
import fr.guim.cinedoury.core.data.UserDataUpdates
import fr.guim.cinedoury.core.data.home.HomeRepository
import fr.guim.cinedoury.core.data.home.ProfileKey
import fr.guim.cinedoury.core.model.ItemId
import fr.guim.cinedoury.core.model.MediaItem
import fr.guim.cinedoury.core.model.QuickOption
import fr.guim.cinedoury.core.model.QuickOptions
import fr.guim.cinedoury.core.model.UserData
import fr.guim.cinedoury.core.source.FailureLog
import fr.guim.cinedoury.core.source.LibrarySource
import fr.guim.cinedoury.core.source.SourceException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.time.Duration

/**
 * The options of the menu of a card that change the item (essai réel 5, `SPEC.md` § 4.23): « vu »,
 * « Retirer de Continuer », favourites. One logic for every screen: the change shows at once (what
 * the server will answer), then the server's answer; the screens follow through [UserDataUpdates],
 * the home through its cache, without loading anything again. A failure puts the data back.
 *
 * @param scope outlives the screen: the menu closes and the screen may go before the server answers.
 */
class QuickActions(
    private val sessions: SessionManager,
    private val updates: UserDataUpdates,
    private val home: HomeRepository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    /** The latest data of the items changed here or by the player: the menu reads its labels from it. */
    private val known = mutableMapOf<ItemId, UserData>()

    init {
        scope.launch { updates.updates.collect { (id, data) -> synchronized(known) { known[id] = data } } }
    }

    /** [item] with the last data this session knows of it. */
    fun current(item: MediaItem): MediaItem =
        synchronized(known) { known[item.id] }?.let { item.copy(userData = it) } ?: item

    /**
     * Does [option] on [item]. [continueRow]: the row of the home the card is in, when it is
     * « Reprendre »: the item leaves it once removed or watched.
     */
    fun apply(
        item: MediaItem,
        option: QuickOption,
        continueRow: String? = null,
    ): Job? {
        val source = sessions.active.value?.library ?: return null
        val before = current(item).userData ?: UserData(false, Duration.ZERO, 0, false)
        val expected = QuickOptions.expected(option, before)
        if (expected == before && option !in CHANGES) return null
        return scope.launch {
            publish(item.id, expected, option, continueRow)
            try {
                publish(item.id, ask(source, item.id, option) ?: return@launch, option, continueRow)
            } catch (error: SourceException) {
                FailureLog.warn("Quick menu $option on ${item.id.value}", error)
                publish(item.id, before, option, continueRow = null)
            }
        }
    }

    private suspend fun ask(
        source: LibrarySource,
        item: ItemId,
        option: QuickOption,
    ): UserData? =
        when (option) {
            QuickOption.MarkWatched -> source.setPlayed(item, true)
            QuickOption.MarkUnwatched -> source.setPlayed(item, false)
            QuickOption.RemoveFromContinue -> source.clearProgress(item)
            QuickOption.AddFavorite -> source.setFavorite(item, true)
            QuickOption.RemoveFavorite -> source.setFavorite(item, false)
            else -> null
        }

    private suspend fun publish(
        item: ItemId,
        data: UserData,
        option: QuickOption,
        continueRow: String?,
    ) {
        synchronized(known) { known[item] = data }
        updates.publish(item, data)
        val active = sessions.active.value ?: return
        val profile = ProfileKey(active.profile.serverId, active.profile.user.id.value)
        home.updateUserData(profile, item, data)
        home.updateFavorite(profile, item, data.isFavorite)
        val leaves = option == QuickOption.RemoveFromContinue || option == QuickOption.MarkWatched
        if (continueRow != null && leaves) home.removeFromRow(profile, continueRow, item)
    }

    private companion object {
        val CHANGES =
            setOf(
                QuickOption.MarkWatched,
                QuickOption.MarkUnwatched,
                QuickOption.RemoveFromContinue,
                QuickOption.AddFavorite,
                QuickOption.RemoveFavorite,
            )
    }
}

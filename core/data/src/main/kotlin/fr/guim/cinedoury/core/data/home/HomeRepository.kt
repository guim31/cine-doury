package fr.guim.cinedoury.core.data.home

import fr.guim.cinedoury.core.data.db.Converters
import fr.guim.cinedoury.core.data.db.HomeCacheDao
import fr.guim.cinedoury.core.data.db.HomeRowConfigEntity
import fr.guim.cinedoury.core.data.db.PreferencesDao
import fr.guim.cinedoury.core.data.db.RailHiddenEntity
import fr.guim.cinedoury.core.model.ItemId
import fr.guim.cinedoury.core.model.LibraryId
import fr.guim.cinedoury.core.model.MediaItem
import fr.guim.cinedoury.core.model.UserData
import fr.guim.cinedoury.core.source.LibrarySource
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Clock

/**
 * The home of a profile, cache first (`UI.md` principle 2, `SPEC.md` § 4.9): the screens show what
 * Room holds at once, [refresh] asks the server and replaces the cache in one transaction, and Room
 * gives the screens the new rows.
 */
@Suppress("TooManyFunctions") // One function per use of the cache by the screens.
class HomeRepository(
    private val cache: HomeCacheDao,
    private val preferences: PreferencesDao,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    /** The rows as the user arranged them; the default home until the first change. */
    fun layout(profile: ProfileKey): Flow<List<HomeRowConfig>> =
        preferences
            .observeLayout(profile.serverId, profile.userId)
            .map { rows -> rows.mapNotNull { it.toModel() }.ifEmpty { HomeLayouts.DEFAULT } }
            .distinctUntilChanged()

    suspend fun saveLayout(
        profile: ProfileKey,
        rows: List<HomeRowConfig>,
    ) {
        preferences.replaceLayout(
            profile.serverId,
            profile.userId,
            rows.mapIndexed { position, row -> row.toEntity(profile, position) },
        )
    }

    /** Back to the default home. */
    suspend fun resetLayout(profile: ProfileKey) = preferences.deleteLayout(profile.serverId, profile.userId)

    /** The libraries of the user and their exclusions, from the cache. */
    fun libraries(profile: ProfileKey): Flow<UserLibraries> =
        cache
            .observeLibraries(profile.serverId, profile.userId)
            .map { it.toUserLibraries() }
            .distinctUntilChanged()

    /** The libraries the user took out of the rail. */
    fun railHidden(profile: ProfileKey): Flow<Set<LibraryId>> =
        preferences
            .observeRailHidden(profile.serverId, profile.userId)
            .map { ids -> ids.map(::LibraryId).toSet() }
            .distinctUntilChanged()

    suspend fun setShownInRail(
        profile: ProfileKey,
        library: LibraryId,
        shown: Boolean,
    ) {
        if (shown) {
            preferences.showInRail(profile.serverId, profile.userId, library.value)
        } else {
            preferences.hideInRail(RailHiddenEntity(profile.serverId, profile.userId, library.value))
        }
    }

    /** The cached items of each row, by [HomeRowSpec.key]. */
    fun rows(profile: ProfileKey): Flow<Map<String, List<MediaItem>>> =
        cache
            .observeRows(profile.serverId, profile.userId)
            .map { items -> items.groupBy({ it.rowKey }, { it.item.toModel() }) }
            .distinctUntilChanged()

    /**
     * Reads the libraries, then every row of the layout, and replaces the cache; returns the rows
     * written. Fails with a `SourceException` and leaves the cache as it was if the server cannot
     * answer.
     */
    suspend fun refresh(
        profile: ProfileKey,
        source: LibrarySource,
    ): Map<String, List<MediaItem>> {
        val libraries =
            coroutineScope {
                val list = async { source.libraries() }
                val exclusions = async { source.homeExclusions() }
                UserLibraries(list.await(), exclusions.await())
            }
        val specs = HomeRows.plan(layout(profile).first(), libraries.libraries, libraries.exclusions)
        val rows =
            coroutineScope {
                specs
                    .map { spec -> async { spec.key to HomeRows.fetch(spec, source, clock) } }
                    .awaitAll()
                    .toMap()
            }
        cache.replaceLibraries(profile.serverId, profile.userId, libraries.toEntities(profile))
        cache.replaceRows(
            profile.serverId,
            profile.userId,
            rows.mapValues { (_, items) -> items.map { it.toEntity(profile) } },
        )
        return rows
    }

    /**
     * What the user did with [item] (`SPEC.md` § 4.11): the position and « vu » at the end of
     * playback reach the cards of the home at once, before the next refresh.
     */
    suspend fun updateUserData(
        profile: ProfileKey,
        item: ItemId,
        data: UserData,
    ) {
        cache.updateUserData(
            serverId = profile.serverId,
            userId = profile.userId,
            itemId = item.value,
            played = data.played,
            positionMs = data.playbackPosition.inWholeMilliseconds,
            playCount = data.playCount,
            lastPlayed = data.lastPlayed?.toEpochMilli(),
        )
    }

    /** Forgets [items], deleted on the server, from the rows of every profile of the server (J6). */
    suspend fun forget(
        serverId: String,
        items: Collection<ItemId>,
    ) {
        items.forEach { cache.forgetItem(serverId, it.value) }
    }

    /** Forgets the cache of a profile (signed out), or of every profile of a server. */
    suspend fun clear(
        serverId: String,
        userId: String?,
    ) {
        cache.clear(serverId, userId)
        if (userId == null) preferences.clearServer(serverId)
    }
}

private fun HomeRowConfig.toEntity(
    profile: ProfileKey,
    position: Int,
) = HomeRowConfigEntity(
    serverId = profile.serverId,
    userId = profile.userId,
    rowId = id,
    position = position,
    type = type.name,
    libraries = libraries.joinToString(Converters.SEPARATOR) { it.value },
    style = style.name,
    windowDays = windowDays,
)

private fun HomeRowConfigEntity.toModel(): HomeRowConfig? {
    val type = HomeRowType.entries.firstOrNull { it.name == type } ?: return null
    return HomeRowConfig(
        id = rowId,
        type = type,
        libraries =
            libraries
                .split(Converters.SEPARATOR)
                .filter { it.isNotEmpty() }
                .map(::LibraryId)
                .toSet(),
        style = CardStyle.entries.firstOrNull { it.name == style } ?: type.defaultStyle,
        windowDays = windowDays,
    )
}

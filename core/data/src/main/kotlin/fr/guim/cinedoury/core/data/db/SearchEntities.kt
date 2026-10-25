package fr.guim.cinedoury.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import fr.guim.cinedoury.core.model.ImageRef
import kotlinx.coroutines.flow.Flow

/**
 * A film or a series in the local title index (`SPEC.md` § 4.12): its titles for the instant search,
 * and what its card shows before the server confirms it. Per server, like the technical index.
 */
@Entity(
    tableName = "search_titles",
    primaryKeys = ["server_id", "item_id"],
    indices = [Index(value = ["server_id", "library_id"])],
)
data class SearchTitleEntity(
    @ColumnInfo(name = "server_id") val serverId: String,
    @ColumnInfo(name = "item_id") val itemId: String,
    @ColumnInfo(name = "library_id") val libraryId: String,
    /** `Movie` or `Series`. */
    val kind: String,
    val title: String,
    @ColumnInfo(name = "original_title") val originalTitle: String?,
    @ColumnInfo(name = "sort_title") val sortTitle: String?,
    val year: Int?,
    /** Milliseconds since the epoch. */
    @ColumnInfo(name = "date_added") val dateAdded: Long?,
    val primary: ImageRef?,
    val backdrop: ImageRef?,
    /** When the row was last read from the server (ms): a full read drops the rows it did not see. */
    @ColumnInfo(name = "indexed_at") val indexedAt: Long,
)

/** Where the title index of a library stands. */
@Entity(tableName = "search_state", primaryKeys = ["server_id", "library_id"])
data class SearchStateEntity(
    @ColumnInfo(name = "server_id") val serverId: String,
    @ColumnInfo(name = "library_id") val libraryId: String,
    /** When the last read started (ms). */
    @ColumnInfo(name = "synced_at") val syncedAt: Long,
    /** When the last read of everything ended (ms). */
    @ColumnInfo(name = "full_sync_at") val fullSyncAt: Long,
)

/** A recent search of a profile (`FONCTIONNALITES.md` § 6): the ten last, per profile. */
@Entity(tableName = "recent_searches", primaryKeys = ["server_id", "user_id", "normalized"])
data class RecentSearchEntity(
    @ColumnInfo(name = "server_id") val serverId: String,
    @ColumnInfo(name = "user_id") val userId: String,
    /**
     * The search text of [text] ([fr.guim.cinedoury.core.model.SearchText]): « Amélie » and « amelie »
     * are one search.
     */
    val normalized: String,
    /** As the user typed it last. */
    val text: String,
    @ColumnInfo(name = "used_at") val usedAt: Long,
)

/** One function per query. */
@Suppress("TooManyFunctions")
@Dao
abstract class SearchDao {
    @Upsert
    abstract suspend fun upsert(titles: List<SearchTitleEntity>)

    @Query("SELECT * FROM search_titles WHERE server_id = :serverId")
    abstract fun observeTitles(serverId: String): Flow<List<SearchTitleEntity>>

    /** Forgets titles deleted on the server, at once (the next update would find it too). */
    @Query("DELETE FROM search_titles WHERE server_id = :serverId AND item_id IN (:itemIds)")
    abstract suspend fun forget(
        serverId: String,
        itemIds: List<String>,
    )

    @Query("SELECT COUNT(*) FROM search_titles WHERE server_id = :serverId AND library_id = :libraryId")
    abstract suspend fun count(
        serverId: String,
        libraryId: String,
    ): Int

    @Query("SELECT * FROM search_state WHERE server_id = :serverId AND library_id = :libraryId")
    abstract suspend fun state(
        serverId: String,
        libraryId: String,
    ): SearchStateEntity?

    @Upsert
    abstract suspend fun saveState(state: SearchStateEntity)

    @Query("DELETE FROM search_titles WHERE server_id = :serverId AND library_id = :libraryId AND indexed_at < :before")
    abstract suspend fun dropUnseen(
        serverId: String,
        libraryId: String,
        before: Long,
    )

    @Query("DELETE FROM search_titles WHERE server_id = :serverId AND library_id NOT IN (:keep)")
    abstract suspend fun dropLibrariesExcept(
        serverId: String,
        keep: List<String>,
    )

    @Query("DELETE FROM search_state WHERE server_id = :serverId AND library_id NOT IN (:keep)")
    abstract suspend fun dropStatesExcept(
        serverId: String,
        keep: List<String>,
    )

    @Query(
        """
        SELECT * FROM recent_searches WHERE server_id = :serverId AND user_id = :userId
        ORDER BY used_at DESC LIMIT :limit
        """,
    )
    abstract fun observeRecent(
        serverId: String,
        userId: String,
        limit: Int,
    ): Flow<List<RecentSearchEntity>>

    /** Remembers [search], then forgets all but the [keep] most recent of its profile. */
    @Transaction
    open suspend fun remember(
        search: RecentSearchEntity,
        keep: Int,
    ) {
        upsertRecent(search)
        trimRecent(search.serverId, search.userId, keep)
    }

    @Upsert
    protected abstract suspend fun upsertRecent(search: RecentSearchEntity)

    @Query(
        """
        DELETE FROM recent_searches WHERE server_id = :serverId AND user_id = :userId AND normalized NOT IN (
            SELECT normalized FROM recent_searches WHERE server_id = :serverId AND user_id = :userId
            ORDER BY used_at DESC LIMIT :keep
        )
        """,
    )
    protected abstract suspend fun trimRecent(
        serverId: String,
        userId: String,
        keep: Int,
    )

    @Query("DELETE FROM recent_searches WHERE server_id = :serverId AND user_id = :userId AND normalized = :normalized")
    abstract suspend fun forgetRecent(
        serverId: String,
        userId: String,
        normalized: String,
    )

    @Query("DELETE FROM recent_searches WHERE server_id = :serverId AND user_id = :userId")
    abstract suspend fun clearRecent(
        serverId: String,
        userId: String,
    )

    /** Forgets the title index of a server and every recent search of its profiles. */
    @Transaction
    open suspend fun clearServer(serverId: String) {
        clearTitles(serverId)
        clearStates(serverId)
        clearAllRecent(serverId)
    }

    @Query("DELETE FROM search_titles WHERE server_id = :serverId")
    protected abstract suspend fun clearTitles(serverId: String)

    @Query("DELETE FROM search_state WHERE server_id = :serverId")
    protected abstract suspend fun clearStates(serverId: String)

    @Query("DELETE FROM recent_searches WHERE server_id = :serverId")
    protected abstract suspend fun clearAllRecent(serverId: String)
}

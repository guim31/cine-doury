package fr.guim.cinedoury.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import fr.guim.cinedoury.core.model.ImageRef
import kotlinx.coroutines.flow.Flow

/**
 * An item of a cached home row, as the cards and the banner show it (`SPEC.md` § 4.9): never its
 * media sources. Per user, since it carries what the user watched.
 */
@Entity(tableName = "cached_items", primaryKeys = ["server_id", "user_id", "item_id"])
data class CachedItemEntity(
    @ColumnInfo(name = "server_id") val serverId: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "item_id") val itemId: String,
    val kind: String,
    val title: String,
    @ColumnInfo(name = "sort_title") val sortTitle: String?,
    val year: Int?,
    /** Days since the epoch. */
    @ColumnInfo(name = "premiere_date") val premiereDate: Long?,
    /** Milliseconds since the epoch. */
    @ColumnInfo(name = "date_added") val dateAdded: Long?,
    val overview: String?,
    val tagline: String?,
    @ColumnInfo(name = "runtime_ms") val runtimeMs: Long?,
    @ColumnInfo(name = "official_rating") val officialRating: String?,
    @ColumnInfo(name = "community_rating") val communityRating: Float?,
    /** Separated by [Converters.SEPARATOR]. */
    val genres: String,
    val primary: ImageRef?,
    val backdrop: ImageRef?,
    val logo: ImageRef?,
    val thumb: ImageRef?,
    @ColumnInfo(name = "series_primary") val seriesPrimary: ImageRef?,
    @ColumnInfo(name = "parent_backdrop") val parentBackdrop: ImageRef?,
    @ColumnInfo(name = "parent_logo") val parentLogo: ImageRef?,
    @ColumnInfo(name = "parent_thumb") val parentThumb: ImageRef?,
    /** Null when the server sent no user data. */
    val played: Boolean?,
    @ColumnInfo(name = "position_ms") val positionMs: Long,
    @ColumnInfo(name = "play_count") val playCount: Int,
    val favorite: Boolean,
    @ColumnInfo(name = "unplayed_count") val unplayedCount: Int?,
    @ColumnInfo(name = "last_played") val lastPlayed: Long?,
    @ColumnInfo(name = "series_id") val seriesId: String?,
    @ColumnInfo(name = "series_title") val seriesTitle: String?,
    @ColumnInfo(name = "season_id") val seasonId: String?,
    @ColumnInfo(name = "season_number") val seasonNumber: Int?,
    @ColumnInfo(name = "episode_number") val episodeNumber: Int?,
    @ColumnInfo(name = "child_count") val childCount: Int?,
)

/** The place of an item in a cached home row. */
@Entity(
    tableName = "home_row_items",
    primaryKeys = ["server_id", "user_id", "row_key", "position"],
    indices = [Index(value = ["server_id", "user_id", "item_id"])],
)
data class HomeRowItemEntity(
    @ColumnInfo(name = "server_id") val serverId: String,
    @ColumnInfo(name = "user_id") val userId: String,
    /** The displayed row: its configuration and, for a row per library, the library. */
    @ColumnInfo(name = "row_key") val rowKey: String,
    val position: Int,
    @ColumnInfo(name = "item_id") val itemId: String,
)

/** The libraries of the user, for the rail before the server answers. */
@Entity(tableName = "cached_libraries", primaryKeys = ["server_id", "user_id", "library_id"])
data class CachedLibraryEntity(
    @ColumnInfo(name = "server_id") val serverId: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "library_id") val libraryId: String,
    val position: Int,
    val name: String,
    val kind: String,
    val image: ImageRef?,
    @ColumnInfo(name = "excluded_from_home") val excludedFromHome: Boolean,
    @ColumnInfo(name = "excluded_from_latest") val excludedFromLatest: Boolean,
)

/** An item of a cached row, with the key of its row. */
data class CachedRowItem(
    @ColumnInfo(name = "row_key") val rowKey: String,
    @Embedded val item: CachedItemEntity,
)

/** One function per query. */
@Suppress("TooManyFunctions")
@Dao
abstract class HomeCacheDao {
    @Query(
        """
        SELECT r.row_key, i.* FROM home_row_items r
        JOIN cached_items i ON i.server_id = r.server_id AND i.user_id = r.user_id AND i.item_id = r.item_id
        WHERE r.server_id = :serverId AND r.user_id = :userId
        ORDER BY r.row_key, r.position
        """,
    )
    abstract fun observeRows(
        serverId: String,
        userId: String,
    ): Flow<List<CachedRowItem>>

    /** What the user did with an item, wherever the home shows it (J4: at the end of playback). */
    @Query(
        """
        UPDATE cached_items SET played = :played, position_ms = :positionMs, play_count = :playCount,
        last_played = :lastPlayed
        WHERE server_id = :serverId AND user_id = :userId AND item_id = :itemId
        """,
    )
    abstract suspend fun updateUserData(
        serverId: String,
        userId: String,
        itemId: String,
        played: Boolean,
        positionMs: Long,
        playCount: Int,
        lastPlayed: Long?,
    )

    @Query("SELECT * FROM cached_libraries WHERE server_id = :serverId AND user_id = :userId ORDER BY position")
    abstract fun observeLibraries(
        serverId: String,
        userId: String,
    ): Flow<List<CachedLibraryEntity>>

    /**
     * Replaces every cached row of the user at once, so that the home never shows half of an
     * update; items no row uses any more are dropped.
     */
    @Transaction
    open suspend fun replaceRows(
        serverId: String,
        userId: String,
        rows: Map<String, List<CachedItemEntity>>,
    ) {
        deleteRowItems(serverId, userId)
        upsertItems(rows.values.flatten().distinctBy { it.itemId })
        insertRowItems(
            rows.flatMap { (key, items) ->
                items.mapIndexed { position, item -> HomeRowItemEntity(serverId, userId, key, position, item.itemId) }
            },
        )
        deleteUnusedItems(serverId, userId)
    }

    @Transaction
    open suspend fun replaceLibraries(
        serverId: String,
        userId: String,
        libraries: List<CachedLibraryEntity>,
    ) {
        deleteLibraries(serverId, userId)
        upsertLibraries(libraries)
    }

    /** Forgets everything cached for a user (signed out) or, with a null [userId], for a server. */
    @Transaction
    open suspend fun clear(
        serverId: String,
        userId: String?,
    ) {
        clearRowItems(serverId, userId)
        clearItems(serverId, userId)
        clearLibraries(serverId, userId)
    }

    @Upsert
    protected abstract suspend fun upsertItems(items: List<CachedItemEntity>)

    @Insert
    protected abstract suspend fun insertRowItems(items: List<HomeRowItemEntity>)

    @Upsert
    protected abstract suspend fun upsertLibraries(libraries: List<CachedLibraryEntity>)

    @Query("DELETE FROM home_row_items WHERE server_id = :serverId AND user_id = :userId")
    protected abstract suspend fun deleteRowItems(
        serverId: String,
        userId: String,
    )

    @Query("DELETE FROM cached_libraries WHERE server_id = :serverId AND user_id = :userId")
    protected abstract suspend fun deleteLibraries(
        serverId: String,
        userId: String,
    )

    @Query(
        """
        DELETE FROM cached_items WHERE server_id = :serverId AND user_id = :userId AND item_id NOT IN
        (SELECT item_id FROM home_row_items WHERE server_id = :serverId AND user_id = :userId)
        """,
    )
    protected abstract suspend fun deleteUnusedItems(
        serverId: String,
        userId: String,
    )

    @Query("DELETE FROM home_row_items WHERE server_id = :serverId AND (:userId IS NULL OR user_id = :userId)")
    protected abstract suspend fun clearRowItems(
        serverId: String,
        userId: String?,
    )

    @Query("DELETE FROM cached_items WHERE server_id = :serverId AND (:userId IS NULL OR user_id = :userId)")
    protected abstract suspend fun clearItems(
        serverId: String,
        userId: String?,
    )

    @Query("DELETE FROM cached_libraries WHERE server_id = :serverId AND (:userId IS NULL OR user_id = :userId)")
    protected abstract suspend fun clearLibraries(
        serverId: String,
        userId: String?,
    )
}

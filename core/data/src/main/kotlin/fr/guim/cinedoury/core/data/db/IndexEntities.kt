package fr.guim.cinedoury.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * A film or an episode in the local index (`SPEC.md` § 4.10): its technical facts, every version
 * together, for the filters Jellyfin lacks and the badges of the details screens. Per server, not per
 * user: the files are the same for every profile, and every answer is checked against the server's.
 * Sets of values are bit masks over the ordinals of their enums ([fr.guim.cinedoury.core.model.DynamicRange],
 * [fr.guim.cinedoury.core.model.VideoCodec], [fr.guim.cinedoury.core.model.AudioFeature]).
 */
@Entity(
    tableName = "media_index",
    primaryKeys = ["server_id", "item_id"],
    indices = [Index(value = ["server_id", "library_id"])],
)
data class IndexedItemEntity(
    @ColumnInfo(name = "server_id") val serverId: String,
    @ColumnInfo(name = "item_id") val itemId: String,
    @ColumnInfo(name = "library_id") val libraryId: String,
    /** `Movie` or `Episode`. */
    val kind: String,
    /** The series of an episode: a series matches a filter when one of its episodes does. */
    @ColumnInfo(name = "series_id") val seriesId: String?,
    val title: String,
    @ColumnInfo(name = "max_width") val maxWidth: Int?,
    val ranges: Int,
    @ColumnInfo(name = "video_codecs") val videoCodecs: Int,
    val audio: Int,
    /** ISO 639-2 codes separated by [Converters.SEPARATOR]. */
    @ColumnInfo(name = "audio_languages") val audioLanguages: String,
    @ColumnInfo(name = "subtitle_languages") val subtitleLanguages: String,
    @ColumnInfo(name = "forced_languages") val forcedLanguages: String,
    val versions: Int,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long?,
    /** When the row was last read from the server (ms): a full read drops the rows it did not see. */
    @ColumnInfo(name = "indexed_at") val indexedAt: Long,
)

/** Where the index of a library stands. */
@Entity(tableName = "index_state", primaryKeys = ["server_id", "library_id"])
data class IndexStateEntity(
    @ColumnInfo(name = "server_id") val serverId: String,
    @ColumnInfo(name = "library_id") val libraryId: String,
    /** When the last read started (ms): the next one asks for what changed since. */
    @ColumnInfo(name = "synced_at") val syncedAt: Long,
    /** When the last read of everything ended (ms). */
    @ColumnInfo(name = "full_sync_at") val fullSyncAt: Long,
)

/** The icon the user chose for a library (`FONCTIONNALITES.md` § 3), per server. */
@Entity(tableName = "library_icons", primaryKeys = ["server_id", "library_id"])
data class LibraryIconEntity(
    @ColumnInfo(name = "server_id") val serverId: String,
    @ColumnInfo(name = "library_id") val libraryId: String,
    /** A [fr.guim.cinedoury.core.model.LibraryIcon] name. */
    val icon: String,
)

/** Count of the index by kind. */
data class IndexCount(
    val kind: String,
    val count: Int,
)

/** One function per query. */
@Suppress("TooManyFunctions")
@Dao
abstract class IndexDao {
    @Upsert
    abstract suspend fun upsert(items: List<IndexedItemEntity>)

    /**
     * The items of [libraryId] that match the masks (0: no filter); a series stands for its episodes.
     * Within a mask the values add up, the masks combine.
     */
    @Query(
        """
        SELECT DISTINCT CASE WHEN series_id IS NULL THEN item_id ELSE series_id END FROM media_index
        WHERE server_id = :serverId AND library_id = :libraryId
        AND (:ranges = 0 OR (ranges & :ranges) != 0)
        AND (:audio = 0 OR (audio & :audio) != 0)
        AND (:codecs = 0 OR (video_codecs & :codecs) != 0)
        """,
    )
    abstract suspend fun matching(
        serverId: String,
        libraryId: String,
        ranges: Int,
        audio: Int,
        codecs: Int,
    ): List<String>

    @Query("SELECT * FROM media_index WHERE server_id = :serverId AND item_id = :itemId")
    abstract suspend fun item(
        serverId: String,
        itemId: String,
    ): IndexedItemEntity?

    @Query("SELECT * FROM media_index WHERE server_id = :serverId AND series_id = :seriesId")
    abstract suspend fun episodesOf(
        serverId: String,
        seriesId: String,
    ): List<IndexedItemEntity>

    /** Forgets an item deleted on the server (J6), and the episodes of a series. */
    @Query(
        """
        DELETE FROM media_index WHERE server_id = :serverId
        AND (item_id IN (:itemIds) OR series_id IN (:itemIds))
        """,
    )
    abstract suspend fun forget(
        serverId: String,
        itemIds: List<String>,
    )

    @Query("SELECT COUNT(*) FROM media_index WHERE server_id = :serverId AND library_id = :libraryId")
    abstract suspend fun count(
        serverId: String,
        libraryId: String,
    ): Int

    @Query("SELECT kind, COUNT(*) AS count FROM media_index WHERE server_id = :serverId GROUP BY kind")
    abstract fun observeCounts(serverId: String): Flow<List<IndexCount>>

    @Query("SELECT * FROM index_state WHERE server_id = :serverId AND library_id = :libraryId")
    abstract suspend fun state(
        serverId: String,
        libraryId: String,
    ): IndexStateEntity?

    @Query("SELECT * FROM index_state WHERE server_id = :serverId")
    abstract fun observeStates(serverId: String): Flow<List<IndexStateEntity>>

    @Upsert
    abstract suspend fun saveState(state: IndexStateEntity)

    /** After a read of everything: the rows of the library it did not see are gone from the server. */
    @Query("DELETE FROM media_index WHERE server_id = :serverId AND library_id = :libraryId AND indexed_at < :before")
    abstract suspend fun dropUnseen(
        serverId: String,
        libraryId: String,
        before: Long,
    )

    /** The libraries the user cannot see any more, or that are gone. */
    @Query("DELETE FROM media_index WHERE server_id = :serverId AND library_id NOT IN (:keep)")
    abstract suspend fun dropLibrariesExcept(
        serverId: String,
        keep: List<String>,
    )

    @Query("DELETE FROM index_state WHERE server_id = :serverId AND library_id NOT IN (:keep)")
    abstract suspend fun dropStatesExcept(
        serverId: String,
        keep: List<String>,
    )

    @Query("SELECT icon FROM library_icons WHERE server_id = :serverId AND library_id = :libraryId")
    abstract suspend fun icon(
        serverId: String,
        libraryId: String,
    ): String?

    @Query("SELECT * FROM library_icons WHERE server_id = :serverId")
    abstract fun observeIcons(serverId: String): Flow<List<LibraryIconEntity>>

    @Upsert
    abstract suspend fun setIcon(icon: LibraryIconEntity)

    @Query("DELETE FROM library_icons WHERE server_id = :serverId AND library_id = :libraryId")
    abstract suspend fun resetIcon(
        serverId: String,
        libraryId: String,
    )

    /** Forgets the index, its states and the icons of a server (the server was forgotten). */
    @Transaction
    open suspend fun clearServer(serverId: String) {
        clearItems(serverId)
        clearStates(serverId)
        clearIcons(serverId)
    }

    @Query("DELETE FROM media_index WHERE server_id = :serverId")
    protected abstract suspend fun clearItems(serverId: String)

    @Query("DELETE FROM index_state WHERE server_id = :serverId")
    protected abstract suspend fun clearStates(serverId: String)

    @Query("DELETE FROM library_icons WHERE server_id = :serverId")
    protected abstract suspend fun clearIcons(serverId: String)
}

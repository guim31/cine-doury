package fr.guim.cinedoury.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** A row of the home as the user arranged it (`SPEC.md` § 4.9). No line at all: the default home. */
@Entity(tableName = "home_layout", primaryKeys = ["server_id", "user_id", "row_id"])
data class HomeRowConfigEntity(
    @ColumnInfo(name = "server_id") val serverId: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "row_id") val rowId: String,
    val position: Int,
    val type: String,
    /** Library ids separated by [Converters.SEPARATOR]; empty for every library. */
    val libraries: String,
    val style: String,
    /** « À suivre » only: how many days back a series counts as in progress; null without limit. */
    @ColumnInfo(name = "window_days") val windowDays: Int?,
)

/** What a library screen remembers: its tab, view, sort, filters and position. */
@Entity(tableName = "library_state", primaryKeys = ["server_id", "user_id", "library_id"])
data class LibraryStateEntity(
    @ColumnInfo(name = "server_id") val serverId: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "library_id") val libraryId: String,
    val tab: String,
    val view: String,
    @ColumnInfo(name = "sort_field") val sortField: String,
    val ascending: Boolean,
    /** `LibraryFiltersCodec` JSON. */
    val filters: String,
    @ColumnInfo(name = "group_collections") val groupCollections: Boolean,
    /** Index of the item that had the focus. */
    val position: Int,
)

/** A library the user took out of the rail (it stays in the server's list). */
@Entity(tableName = "rail_hidden", primaryKeys = ["server_id", "user_id", "library_id"])
data class RailHiddenEntity(
    @ColumnInfo(name = "server_id") val serverId: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "library_id") val libraryId: String,
)

/** One function per query. */
@Suppress("TooManyFunctions")
@Dao
abstract class PreferencesDao {
    @Query("SELECT * FROM home_layout WHERE server_id = :serverId AND user_id = :userId ORDER BY position")
    abstract fun observeLayout(
        serverId: String,
        userId: String,
    ): Flow<List<HomeRowConfigEntity>>

    @Transaction
    open suspend fun replaceLayout(
        serverId: String,
        userId: String,
        rows: List<HomeRowConfigEntity>,
    ) {
        deleteLayout(serverId, userId)
        upsertLayout(rows)
    }

    @Query("DELETE FROM home_layout WHERE server_id = :serverId AND user_id = :userId")
    abstract suspend fun deleteLayout(
        serverId: String,
        userId: String,
    )

    @Upsert
    protected abstract suspend fun upsertLayout(rows: List<HomeRowConfigEntity>)

    @Query("SELECT * FROM library_state WHERE server_id = :serverId AND user_id = :userId AND library_id = :libraryId")
    abstract suspend fun libraryState(
        serverId: String,
        userId: String,
        libraryId: String,
    ): LibraryStateEntity?

    @Upsert
    abstract suspend fun saveLibraryState(state: LibraryStateEntity)

    @Query("SELECT library_id FROM rail_hidden WHERE server_id = :serverId AND user_id = :userId")
    abstract fun observeRailHidden(
        serverId: String,
        userId: String,
    ): Flow<List<String>>

    @Upsert
    abstract suspend fun hideInRail(entry: RailHiddenEntity)

    @Query("DELETE FROM rail_hidden WHERE server_id = :serverId AND user_id = :userId AND library_id = :libraryId")
    abstract suspend fun showInRail(
        serverId: String,
        userId: String,
        libraryId: String,
    )

    /** Forgets the preferences of every user of a server (the server was forgotten). */
    @Transaction
    open suspend fun clearServer(serverId: String) {
        clearLayout(serverId)
        clearLibraryStates(serverId)
        clearRailHidden(serverId)
    }

    @Query("DELETE FROM home_layout WHERE server_id = :serverId")
    protected abstract suspend fun clearLayout(serverId: String)

    @Query("DELETE FROM library_state WHERE server_id = :serverId")
    protected abstract suspend fun clearLibraryStates(serverId: String)

    @Query("DELETE FROM rail_hidden WHERE server_id = :serverId")
    protected abstract suspend fun clearRailHidden(serverId: String)
}

package fr.guim.cinedoury.core.data.db

import androidx.room.AutoMigration
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * The local store (`SPEC.md` § 4.7 and § 4.9): servers and profiles (J1), the cache of the home and
 * the home and library preferences (J2), the local index of the libraries and the icons of the
 * libraries (J3), the title index and the recent searches (J5). Tokens are stored encrypted by a key of the Android
 * Keystore, PINs as salted hashes. The schema of each version is exported to `core/data/schemas/`,
 * and every migration is tested from it (`CineDatabaseMigrationTest`).
 */
@Database(
    entities = [
        ServerEntity::class,
        ProfileEntity::class,
        CachedItemEntity::class,
        HomeRowItemEntity::class,
        CachedLibraryEntity::class,
        HomeRowConfigEntity::class,
        LibraryStateEntity::class,
        RailHiddenEntity::class,
        IndexedItemEntity::class,
        IndexStateEntity::class,
        LibraryIconEntity::class,
        SearchTitleEntity::class,
        SearchStateEntity::class,
        RecentSearchEntity::class,
    ],
    version = 4,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
    ],
)
@TypeConverters(Converters::class)
abstract class CineDatabase : RoomDatabase() {
    abstract fun servers(): ServerDao

    abstract fun profiles(): ProfileDao

    abstract fun homeCache(): HomeCacheDao

    abstract fun preferences(): PreferencesDao

    abstract fun index(): IndexDao

    abstract fun search(): SearchDao

    companion object {
        const val NAME = "cine-doury.db"
    }
}

@Entity(tableName = "servers")
data class ServerEntity(
    @PrimaryKey val id: String,
    val name: String,
    val version: String,
    @ColumnInfo(name = "local_address") val localAddress: String,
    @ColumnInfo(name = "remote_address") val remoteAddress: String?,
    @ColumnInfo(name = "added_at") val addedAt: Long,
)

@Entity(tableName = "profiles", primaryKeys = ["server_id", "user_id"])
data class ProfileEntity(
    @ColumnInfo(name = "server_id") val serverId: String,
    @ColumnInfo(name = "user_id") val userId: String,
    val name: String,
    @ColumnInfo(name = "is_administrator") val isAdministrator: Boolean,
    @ColumnInfo(name = "image_tag") val imageTag: String?,
    /** Shown by the server's profile list (`/Users/Public`). */
    @ColumnInfo(name = "is_public") val isPublic: Boolean,
    /** The access token, encrypted by the Keystore key (`TokenCipher`), Base64; null when signed out. */
    @ColumnInfo(name = "encrypted_token") val encryptedToken: String?,
    /** `PinHasher` output; null without a PIN. */
    @ColumnInfo(name = "pin_hash") val pinHash: String?,
    @ColumnInfo(name = "last_used_at") val lastUsedAt: Long?,
)

@Dao
interface ServerDao {
    @Query("SELECT * FROM servers WHERE id = :id")
    suspend fun get(id: String): ServerEntity?

    @Query("SELECT * FROM servers WHERE id = :id")
    fun observe(id: String): Flow<ServerEntity?>

    @Upsert
    suspend fun upsert(server: ServerEntity)

    @Query("DELETE FROM servers WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles WHERE server_id = :serverId ORDER BY name COLLATE NOCASE")
    fun observe(serverId: String): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles WHERE server_id = :serverId")
    suspend fun all(serverId: String): List<ProfileEntity>

    @Query("SELECT * FROM profiles WHERE server_id = :serverId AND user_id = :userId")
    suspend fun get(serverId: String, userId: String): ProfileEntity?

    @Upsert
    suspend fun upsert(profile: ProfileEntity)

    @Query("DELETE FROM profiles WHERE server_id = :serverId AND user_id = :userId")
    suspend fun delete(serverId: String, userId: String)

    @Query("DELETE FROM profiles WHERE server_id = :serverId")
    suspend fun deleteAll(serverId: String)
}

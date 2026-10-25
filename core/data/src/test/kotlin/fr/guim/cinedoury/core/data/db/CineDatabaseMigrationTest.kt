package fr.guim.cinedoury.core.data.db

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import fr.guim.cinedoury.core.data.home.HomeRepository
import fr.guim.cinedoury.core.data.home.ProfileKey
import fr.guim.cinedoury.core.data.library.LibraryIconRepository
import fr.guim.cinedoury.core.model.LibraryId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Each migration from the exported schemas (`core/data/schemas/`): a database created as the
 * previous version wrote it, with data, opened by the current version. Room checks at opening that
 * the migrated tables are exactly those of the current entities.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CineDatabaseMigrationTest {
    private val context = RuntimeEnvironment.getApplication()

    /** The tables of [version] as Room created them, from its exported schema. */
    private fun create(
        name: String,
        version: Int,
    ): SQLiteDatabase {
        val file =
            context.getDatabasePath(name).also {
                it.parentFile?.mkdirs()
                it.delete()
            }
        val schema =
            JSONObject(
                File("schemas/${CineDatabase::class.java.name}/$version.json").readText(),
            ).getJSONObject("database")
        val database = SQLiteDatabase.openOrCreateDatabase(file, null)
        val entities = schema.getJSONArray("entities")
        for (index in 0 until entities.length()) {
            val entity = entities.getJSONObject(index)
            database.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
        }
        val setup = schema.getJSONArray("setupQueries")
        for (index in 0 until setup.length()) database.execSQL(setup.getString(index))
        database.version = version
        return database
    }

    @Test
    fun `version 1 of J1 keeps its servers and profiles in version 2`() =
        runTest {
            create("migration-1.db", 1).use { v1 ->
                v1.execSQL(
                    "INSERT INTO servers (id, name, version, local_address, remote_address, added_at) " +
                        "VALUES ('home', 'Maison', '12.3.0', 'http://192.0.2.10:8096', NULL, 1)",
                )
                v1.execSQL(
                    "INSERT INTO profiles (server_id, user_id, name, is_administrator, image_tag, is_public, " +
                        "encrypted_token, pin_hash, last_used_at) " +
                        "VALUES ('home', 'u1', 'Camille', 1, NULL, 1, 'x', NULL, NULL)",
                )
            }
            val database = Room.databaseBuilder(context, CineDatabase::class.java, "migration-1.db").build()
            try {
                assertEquals("Maison", database.servers().get("home")?.name)
                assertEquals("Camille", database.profiles().get("home", "u1")?.name)
                // The new tables are there and empty.
                val home = HomeRepository(database.homeCache(), database.preferences())
                assertEquals(emptyMap(), home.rows(ProfileKey("home", "u1")).first())
            } finally {
                database.close()
            }
        }

    @Test
    fun `version 2 of J2 keeps its preferences in version 3, with an empty index`() =
        runTest {
            create("migration-2.db", 2).use { v2 ->
                v2.execSQL(
                    "INSERT INTO rail_hidden (server_id, user_id, library_id) VALUES ('home', 'u1', 'films')",
                )
            }
            val database = Room.databaseBuilder(context, CineDatabase::class.java, "migration-2.db").build()
            try {
                val home = HomeRepository(database.homeCache(), database.preferences())
                assertEquals(setOf(LibraryId("films")), home.railHidden(ProfileKey("home", "u1")).first())
                assertEquals(0, database.index().count("home", "films"))
                assertEquals(emptyMap(), LibraryIconRepository(database.index()).chosen("home").first())
            } finally {
                database.close()
            }
        }

    @Test
    fun `the exported schema is the one of the code`() {
        val latest =
            File("schemas/${CineDatabase::class.java.name}").listFiles().orEmpty().maxOf {
                it.nameWithoutExtension.toInt()
            }
        assertEquals(3, latest, "export the schema of the new version (CineDatabase.version)")
    }
}

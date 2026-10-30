package fr.guim.cinedoury.core.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import fr.guim.cinedoury.core.model.MarginBudget
import java.io.File

/**
 * The disk of the margin (M2, `SPEC.md` § 4.27): one Media3 [SimpleCache] in the cache folder of the
 * app, for the whole process (Media3 refuses two on a folder). Each playback writes under its own
 * prefix and empties it at its end; what a killed app left is removed when the cache opens. No
 * evictor: the margin stops before [budget] rather than lose what is ahead.
 */
@OptIn(UnstableApi::class)
class PlaybackCache(
    context: Context,
) {
    private val folder = File(context.cacheDir, FOLDER)
    private val databases = StandaloneDatabaseProvider(context)

    val cache: Cache by lazy {
        SimpleCache(folder, NoOpCacheEvictor(), databases).also { cache ->
            cache.keys.toList().forEach(cache::removeResource)
        }
    }

    /** The bytes the margin may take on the disk now ([MarginBudget.diskBytes] of the free space). */
    fun budget(): Long = MarginBudget.diskBytes((folder.parentFile?.usableSpace ?: 0L) + cache.cacheSpace)

    /** Removes what one playback wrote (its [prefix]). */
    fun clear(prefix: String) {
        cache.keys
            .filter { it.startsWith(prefix) }
            .forEach(cache::removeResource)
    }

    private companion object {
        const val FOLDER = "playback"
    }
}

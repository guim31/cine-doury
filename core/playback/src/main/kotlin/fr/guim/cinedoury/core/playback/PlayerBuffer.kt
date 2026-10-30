package fr.guim.cinedoury.core.playback

import androidx.media3.datasource.DataSource
import fr.guim.cinedoury.core.model.MarginBudget
import fr.guim.cinedoury.core.model.MarginPlan

/**
 * How much of the film the player keeps ahead (`SPEC.md` § 4.11): ExoPlayer starts with
 * [startMillis] loaded, starts again after a stall with [rebufferMillis], and keeps between [minMillis]
 * and [maxMillis] ahead, up to [targetBytes] in memory.
 *
 * [Tv] is what the TV has always had, and never changes. [phone] is the memory stage of the margin of
 * the phone (M2, `SPEC.md` § 4.27): the same start (1.5 s), then about 2 min ahead, capped in bytes by
 * the heap of the app; the disk stage follows it ([Nomad]).
 */
data class PlayerBuffer(
    val minMillis: Int,
    val maxMillis: Int,
    val startMillis: Int,
    val rebufferMillis: Int,
    val targetBytes: Int,
) {
    companion object {
        val Tv =
            PlayerBuffer(
                minMillis = 30_000,
                maxMillis = 90_000,
                startMillis = 1_500,
                rebufferMillis = 4_000,
                targetBytes = 192 * 1024 * 1024,
            )

        /** The memory of the phone's margin: up to 2 min, at most [MarginBudget.memoryBytes] of its heap. */
        fun phone(memoryClassMegabytes: Int) =
            PlayerBuffer(
                minMillis = MarginPlan.LOW_MEMORY.inWholeMilliseconds.toInt(),
                maxMillis = PHONE_MEMORY_MILLIS,
                startMillis = Tv.startMillis,
                rebufferMillis = Tv.rebufferMillis,
                targetBytes = MarginBudget.memoryBytes(memoryClassMegabytes),
            )

        private const val PHONE_MEMORY_MILLIS = 120_000
    }
}

/**
 * Wraps the source of the bytes of a stream (M1): none on the TV; M2 puts a `CacheDataSource` over it
 * on the phone, a buffer on disk. The headers of the stream (Jellyfin: `Authorization`) stay on the
 * HTTP source it is given.
 */
fun interface DataSources {
    fun wrap(upstream: DataSource.Factory): DataSource.Factory

    companion object {
        val Direct = DataSources { it }
    }
}

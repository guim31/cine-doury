package fr.guim.cinedoury.core.playback

import androidx.media3.datasource.DataSource

/**
 * How much of the film the player keeps ahead (`SPEC.md` § 4.11): ExoPlayer starts with
 * [startMillis] loaded, starts again after a stall with [rebufferMillis], and keeps between [minMillis]
 * and [maxMillis] ahead, up to [targetBytes] in memory.
 *
 * [Tv] is what the TV has always had. [Phone] is the same until M2 sets the phone's own (a buffer on
 * disk against the places where the network is poor, `SPEC.md` § 4.26): the TV never changes with it.
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

        /** The phone's (M1): the TV's for now; M2 tunes it. */
        val Phone = Tv
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

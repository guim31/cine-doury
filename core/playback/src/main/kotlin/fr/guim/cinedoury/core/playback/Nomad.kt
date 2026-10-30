package fr.guim.cinedoury.core.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import kotlin.time.Duration

/**
 * What the phone adds to the player (M2, `SPEC.md` § 4.27), none of it on the TV:
 * - the margin, in memory then on the disk ([cache]), up to [margin] ahead;
 * - the address of the server read at each request ([address]): a playback goes on through the other
 *   address when the phone moves from the Wi-Fi of the house to the mobile network, or back;
 * - patience: a request that fails for want of a network is tried again as long as it takes, while
 *   the margin plays, and the film starts again by itself when the network comes back;
 * - the focus of the sound (a call pauses, the end of it resumes) and headphones unplugged (pause).
 */
class Nomad(
    val cache: PlaybackCache,
    val network: NetworkMonitor,
    val address: () -> String?,
    val margin: Duration,
)

/**
 * The retries of the phone: a lost network forever (every 3 s at most, the margin plays meanwhile); an
 * answer of the server in error, [LOAD_RETRIES] times as on the TV, then the error screen.
 */
@OptIn(UnstableApi::class)
internal class NomadRetries : DefaultLoadErrorHandlingPolicy(LOAD_RETRIES) {
    override fun getMinimumLoadableRetryCount(dataType: Int): Int = Int.MAX_VALUE

    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
        val delay = super.getRetryDelayMsFor(loadErrorInfo)
        val answered =
            generateSequence<Throwable>(loadErrorInfo.exception) { it.cause }
                .any { it is HttpDataSource.InvalidResponseCodeException }
        return when {
            delay == C.TIME_UNSET -> C.TIME_UNSET
            answered && loadErrorInfo.errorCount > LOAD_RETRIES -> C.TIME_UNSET
            else -> minOf(delay, MAX_DELAY_MS)
        }
    }

    companion object {
        const val LOAD_RETRIES = 20
        private const val MAX_DELAY_MS = 3_000L
    }
}

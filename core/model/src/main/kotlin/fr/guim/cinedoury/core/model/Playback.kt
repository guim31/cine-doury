package fr.guim.cinedoury.core.model

import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.time.Duration
import kotlin.time.toJavaDuration

/** What is left to watch: the runtime minus the position of a started item; null without runtime. */
val MediaItem.remaining: Duration?
    get() {
        val total = runtime?.takeIf { it.isPositive() } ?: return null
        val position = userData?.playbackPosition?.takeIf { progress != null } ?: Duration.ZERO
        return (total - position).coerceAtLeast(Duration.ZERO)
    }

/**
 * When the item would end if played [now] (« se termine à 22 h 47 », `FONCTIONNALITES.md` § 4): now
 * plus what is left, to the nearest minute. Null without runtime.
 */
fun MediaItem.endsAt(now: ZonedDateTime): LocalTime? {
    val left = remaining ?: return null
    val end = now.plus(left.toJavaDuration())
    val rounded = if (end.second >= HALF_MINUTE) end.plusMinutes(1) else end
    return rounded.truncatedTo(ChronoUnit.MINUTES).toLocalTime()
}

private const val HALF_MINUTE = 30

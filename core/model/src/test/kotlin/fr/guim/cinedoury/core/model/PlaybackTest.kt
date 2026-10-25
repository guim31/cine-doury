package fr.guim.cinedoury.core.model

import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class PlaybackTest {
    private val now = ZonedDateTime.of(2026, 10, 25, 20, 58, 10, 0, ZoneId.of("Europe/Paris"))

    private fun film(
        runtime: Duration?,
        position: Duration = Duration.ZERO,
    ) = MediaItem(
        id = ItemId("film"),
        kind = ItemKind.Movie,
        title = "Film",
        runtime = runtime,
        userData = UserData(played = false, playbackPosition = position, playCount = 0, isFavorite = false),
    )

    @Test
    fun `the end of a film not started`() {
        // 20:58:10 + 1 h 49 = 22:47:10.
        assertEquals(LocalTime.of(22, 47), film(109.minutes).endsAt(now))
    }

    @Test
    fun `the end of a film started counts what is left`() {
        assertEquals(69.minutes, film(109.minutes, 40.minutes).remaining)
        assertEquals(LocalTime.of(22, 7), film(109.minutes, 40.minutes).endsAt(now))
    }

    @Test
    fun `to the nearest minute, across midnight`() {
        assertEquals(LocalTime.of(20, 59), film(50.seconds).endsAt(now))
        assertEquals(LocalTime.of(0, 3), film(185.minutes).endsAt(now))
    }

    @Test
    fun `nothing without a runtime`() {
        assertNull(film(null).endsAt(now))
        assertNull(film(Duration.ZERO).remaining)
    }
}

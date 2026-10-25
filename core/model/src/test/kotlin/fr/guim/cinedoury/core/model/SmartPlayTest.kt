package fr.guim.cinedoury.core.model

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class SmartPlayTest {
    private fun episode(
        season: Int,
        number: Int,
        played: Boolean = false,
        position: Duration = Duration.ZERO,
        lastPlayedDay: Long? = null,
    ) = MediaItem(
        id = ItemId("s${season}e$number"),
        kind = ItemKind.Episode,
        title = "S${season}E$number",
        runtime = 42.minutes,
        userData =
            UserData(
                played = played,
                playbackPosition = position,
                playCount = if (played) 1 else 0,
                isFavorite = false,
                lastPlayed = lastPlayedDay?.let { Instant.ofEpochSecond(it * 86_400) },
            ),
        episode = EpisodeInfo(ItemId("series"), "Série", ItemId("season$season"), season, number),
    )

    @Test
    fun `an episode in progress is resumed, the one played last`() {
        val play =
            SmartPlay.of(
                listOf(
                    episode(1, 1, played = true, lastPlayedDay = 1),
                    episode(1, 2, position = 10.minutes, lastPlayedDay = 2),
                    episode(2, 5, position = 30.minutes, lastPlayedDay = 5),
                ),
            )!!
        assertEquals("s2e5", play.episode.id.value)
        assertTrue(play.resume)
        assertEquals(12.minutes, play.remaining)
    }

    @Test
    fun `the first episode not watched after the one watched last`() {
        val play =
            SmartPlay.of(
                listOf(
                    episode(1, 1, played = true, lastPlayedDay = 1),
                    episode(1, 2),
                    episode(2, 1, played = true, lastPlayedDay = 9),
                    episode(2, 2),
                ),
            )!!
        assertEquals("s2e2", play.episode.id.value)
        assertFalse(play.resume)
        assertNull(play.remaining)
    }

    @Test
    fun `an unwatched episode before the last watched one when nothing follows`() {
        val play =
            SmartPlay.of(
                listOf(episode(1, 1), episode(1, 2, played = true, lastPlayedDay = 3)),
            )!!
        assertEquals("s1e1", play.episode.id.value)
    }

    @Test
    fun `the first episode when nothing is watched, or everything`() {
        assertEquals(
            "s1e1",
            SmartPlay
                .of(listOf(episode(1, 2), episode(1, 1)))!!
                .episode.id.value,
        )
        assertEquals(
            "s1e1",
            SmartPlay
                .of(listOf(episode(1, 1, played = true), episode(1, 2, played = true)))!!
                .episode.id.value,
        )
    }

    @Test
    fun `specials are left out unless there is nothing else`() {
        assertEquals(
            "s1e1",
            SmartPlay
                .of(listOf(episode(0, 1), episode(1, 1)))!!
                .episode.id.value,
        )
        assertEquals(
            "s0e1",
            SmartPlay
                .of(listOf(episode(0, 1)))!!
                .episode.id.value,
        )
        assertNull(SmartPlay.of(emptyList()))
    }
}

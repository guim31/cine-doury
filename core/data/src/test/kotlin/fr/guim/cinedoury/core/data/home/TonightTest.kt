package fr.guim.cinedoury.core.data.home

import fr.guim.cinedoury.core.model.EpisodeInfo
import fr.guim.cinedoury.core.model.ItemId
import fr.guim.cinedoury.core.model.ItemKind
import fr.guim.cinedoury.core.model.MediaItem
import fr.guim.cinedoury.core.model.UserData
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class TonightTest {
    private val now = Instant.parse("2026-10-29T20:00:00Z")

    private fun film(
        id: String,
        played: Boolean = false,
        position: Duration = Duration.ZERO,
        lastPlayed: Instant? = null,
        added: Instant? = null,
    ) = MediaItem(
        ItemId(id),
        ItemKind.Movie,
        id,
        runtime = 100.minutes,
        dateAdded = added,
        userData = UserData(played, position, if (played) 1 else 0, false, lastPlayed = lastPlayed),
    )

    private fun episode(
        id: String,
        series: String,
        added: Instant? = null,
    ) = MediaItem(
        ItemId(id),
        ItemKind.Episode,
        id,
        dateAdded = added,
        userData = UserData(false, Duration.ZERO, 0, false),
        episode = EpisodeInfo(ItemId(series), series, null, 1, 1),
    )

    private fun ago(days: Long) = now.minus(days, ChronoUnit.DAYS)

    @Test
    fun `the latest playback on the large tile, then the order of the maintainer`() {
        val sources =
            TonightSources(
                resume = listOf(film("a", position = 20.minutes), film("b", position = 40.minutes)),
                nextUp = listOf(episode("e1", "s1", added = ago(60)), episode("e2", "s2", added = ago(2))),
                latest = listOf(film("c", added = ago(1))),
                rediscover = listOf(film("d")),
            )
        val tiles = Tonight.pick(sources, now)
        assertEquals(listOf("a", "b", "e1", "e2", "c"), tiles.map { it.item.id.value })
        assertEquals(
            listOf(
                TonightKind.Resume,
                TonightKind.Resume,
                TonightKind.NextUp,
                TonightKind.NewEpisode,
                TonightKind.Added,
            ),
            tiles.map { it.kind },
        )
    }

    @Test
    fun `without a playback, the next episode is the large tile, else the latest addition`() {
        val next = TonightSources(nextUp = listOf(episode("e1", "s1")), latest = listOf(film("c")))
        assertEquals(
            "e1",
            Tonight
                .pick(next, now)
                .first()
                .item.id.value,
        )
        val fresh = TonightSources(latest = listOf(film("c"), film("f")), rediscover = listOf(film("d")))
        assertEquals(listOf("c", "f", "d"), Tonight.pick(fresh, now).map { it.item.id.value })
    }

    @Test
    fun `always full from the library, never twice a title or a series`() {
        val sources =
            TonightSources(
                resume = listOf(film("a", position = 20.minutes)),
                nextUp = listOf(episode("e1", "s1"), episode("e2", "s1")),
                latest = listOf(film("a"), episode("e3", "s1")),
                rediscover = listOf(film("d"), film("g"), film("h"), film("i")),
            )
        val tiles = Tonight.pick(sources, now)
        assertEquals(Tonight.TILES, tiles.size)
        assertEquals(tiles.size, tiles.map { it.item.episode?.seriesId ?: it.item.id }.toSet().size)
    }

    @Test
    fun `to rediscover, never watched or watched more than a year ago`() {
        val sources =
            TonightSources(
                rediscover =
                    listOf(
                        film("recent", played = true, lastPlayed = ago(30)),
                        film("old", played = true, lastPlayed = ago(800)),
                        film("never"),
                    ),
            )
        val ids = Tonight.pick(sources, now).map { it.item.id.value }.toSet()
        assertEquals(setOf("old", "never"), ids)
    }
}

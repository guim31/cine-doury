package fr.guim.cinedoury.core.data.home

import fr.guim.cinedoury.core.data.home.Samples.film
import fr.guim.cinedoury.core.model.HomeExclusions
import fr.guim.cinedoury.core.source.ServerAddress
import fr.guim.cinedoury.core.source.Session
import fr.guim.cinedoury.core.source.fake.InMemorySource
import kotlinx.coroutines.test.runTest
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes

class HomeRowsTest {
    private val excluded = HomeExclusions(fromHome = setOf(Samples.shows.id), fromLatest = setOf(Samples.series.id))

    @Test
    fun `plans one latest row per library, without the excluded ones`() {
        val specs = HomeRows.plan(HomeLayouts.DEFAULT, Samples.all, excluded)
        assertEquals(listOf("resume", "next-up", "latest:films", "released"), specs.map { it.key })
        // The libraries excluded from the home are read one by one, never through the whole server.
        val resume = specs.first()
        assertEquals(listOf(Samples.series, Samples.films), resume.scope)
        assertEquals(false, resume.wholeServer)
        assertEquals(listOf(Samples.series), specs.single { it.key == "next-up" }.scope)
    }

    @Test
    fun `without exclusions a row reads the whole server at once`() {
        val specs = HomeRows.plan(HomeLayouts.DEFAULT, Samples.all, HomeExclusions())
        assertEquals(true, specs.first().wholeServer)
        assertEquals(
            listOf("latest:series", "latest:films", "latest:spectacles"),
            specs
                .filter {
                    it.library != null
                }.map { it.key },
        )
    }

    @Test
    fun `a row limited to libraries the user cannot see disappears`() {
        val layout = listOf(HomeRowConfig("next-up", HomeRowType.NextUp, libraries = setOf(Samples.films.id)))
        assertEquals(emptyList(), HomeRows.plan(layout, Samples.all, HomeExclusions()))
    }

    @Test
    fun `merges the rows of several libraries`() =
        runTest {
            val now = Instant.parse("2026-10-25T12:00:00Z")
            val source =
                InMemorySource(
                    libraries = Samples.all,
                    contents =
                        mapOf(
                            Samples.films.id to
                                listOf(
                                    film("old", 1990, 20.minutes, now.minusSeconds(7_200)),
                                    film("new", 2024),
                                    film("future", 2030),
                                ),
                            Samples.series.id to listOf(film("ep", 2010, 5.minutes, now.minusSeconds(60))),
                            Samples.shows.id to listOf(film("hidden", 2025, 5.minutes, now)),
                        ),
                )
            val library =
                source.open(
                    Session(ServerAddress.of("http://192.0.2.10:8096"), "s", source.user("admin"), "t"),
                )
            val specs = HomeRows.plan(HomeLayouts.DEFAULT, Samples.all, excluded).associateBy { it.key }
            val clock = Clock.fixed(now, ZoneOffset.UTC)
            assertEquals(listOf("ep", "old"), HomeRows.fetch(specs.getValue("resume"), library, clock).map { it.title })
            assertEquals(
                listOf("new", "ep", "old"),
                HomeRows.fetch(specs.getValue("released"), library, clock).map { it.title },
            )
        }

    @Test
    fun `the next up window is counted back from now`() =
        runTest {
            val now = Instant.parse("2026-10-25T12:00:00Z")
            val recent = film("recent", lastPlayed = now.minusSeconds(3 * 86_400))
            val old = film("old", lastPlayed = now.minusSeconds(30 * 86_400))
            val source =
                InMemorySource(
                    libraries = Samples.all,
                    contents = mapOf(Samples.series.id to emptyList()),
                    nextUp = mapOf(Samples.series.id to listOf(recent, old)),
                )
            val library =
                source.open(
                    Session(ServerAddress.of("http://192.0.2.10:8096"), "s", source.user("admin"), "t"),
                )
            val row = HomeRows.plan(HomeLayouts.DEFAULT, Samples.all, excluded).single { it.key == "next-up" }
            val clock = Clock.fixed(now, ZoneOffset.UTC)
            assertEquals(listOf("recent"), HomeRows.fetch(row, library, clock).map { it.title })
            val unlimited = row.copy(config = row.config.copy(windowDays = null))
            assertEquals(listOf("recent", "old"), HomeRows.fetch(unlimited, library, clock).map { it.title })
        }
}

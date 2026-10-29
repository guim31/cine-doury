package fr.guim.cinedoury.tv

import fr.guim.cinedoury.core.data.home.CardStyle
import fr.guim.cinedoury.core.data.home.HomeRowType
import fr.guim.cinedoury.core.model.EpisodeInfo
import fr.guim.cinedoury.core.model.ItemId
import fr.guim.cinedoury.core.model.MediaItem
import fr.guim.cinedoury.feature.home.HomeRow
import fr.guim.cinedoury.feature.home.withContinue
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Essai réel 5, F: under the mosaic, « Reprendre » and « À suivre » are one row « Continuer » where
 * the first of them stood, the playbacks in progress first, never what the mosaic shows.
 */
class R5ContinueTest {
    private val films = J2Samples.movies
    private val next =
        J2Samples.homeRows
            .first { it.key == "next-up" }
            .items
            .first()

    private fun episodeOf(
        id: String,
        series: String,
    ): MediaItem =
        next.copy(id = ItemId(id), episode = EpisodeInfo(ItemId(series), series, null, 1, 1))

    private fun row(
        key: String,
        type: HomeRowType,
        items: List<MediaItem>,
    ) = HomeRow(key, type, null, CardStyle.Thumbnail, items)

    private val latest = row("latest:films", HomeRowType.Latest, films.take(3))

    @Test
    fun resumeThenNextUpInOneRowWhereTheFirstStood() {
        val brume = episodeOf("brume-3", "brume")
        val rows =
            listOf(
                latest,
                row("next-up", HomeRowType.NextUp, listOf(brume)),
                row("resume", HomeRowType.Resume, listOf(films[4], films[5])),
            )
        val shown = withContinue(rows, inMosaic = emptySet())
        assertEquals(listOf("latest:films", "continue"), shown.map { it.key })
        val continueRow = shown[1]
        assertEquals(HomeRowType.Continue, continueRow.type)
        assertEquals(listOf(films[4], films[5], brume), continueRow.items, "in progress first")
        // « Retirer de Continuer » takes a playback in progress out of its row of the cache.
        assertEquals("resume", continueRow.resumeRows[films[4].id])
        assertNull(continueRow.resumeRows[brume.id])
    }

    @Test
    fun nothingTheMosaicShowsAndOneCardPerSeries() {
        val inProgress = episodeOf("marees-2", "marees")
        val rows =
            listOf(
                row("resume", HomeRowType.Resume, listOf(inProgress, films[4])),
                row(
                    "next-up",
                    HomeRowType.NextUp,
                    listOf(episodeOf("marees-3", "marees"), episodeOf("brume-1", "brume")),
                ),
                latest,
            )
        // The mosaic shows the film: « Les Marées » once, in progress; « Brume » after it.
        val shown = withContinue(rows, inMosaic = setOf(films[4].id))
        assertEquals(listOf("marees-2", "brume-1"), shown.first().items.map { it.id.value })
    }

    @Test
    fun noRowWhenTheMosaicTookEverything() {
        val rows = listOf(row("resume", HomeRowType.Resume, listOf(films[4])), latest)
        assertEquals(listOf("latest:films"), withContinue(rows, inMosaic = setOf(films[4].id)).map { it.key })
        // Without either row, nothing changes.
        assertEquals(listOf(latest), withContinue(listOf(latest), inMosaic = emptySet()))
    }
}

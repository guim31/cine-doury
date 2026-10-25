package fr.guim.cinedoury.core.data.library

import fr.guim.cinedoury.core.model.AudioFormat
import fr.guim.cinedoury.core.model.DynamicRange
import fr.guim.cinedoury.core.model.ItemFilters
import fr.guim.cinedoury.core.model.NamedId
import fr.guim.cinedoury.core.model.Resolution
import fr.guim.cinedoury.core.model.VideoCodec
import fr.guim.cinedoury.core.model.WatchStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FiltersCodecTest {
    @Test
    fun `every filter survives the round trip`() {
        val filters =
            ItemFilters(
                watch = WatchStatus.InProgress,
                genres = setOf("Comédie", "Science-fiction"),
                decades = setOf(1980),
                years = setOf(2001),
                minRating = 7,
                resolution = Resolution.Uhd,
                audioLanguages = setOf("fra", "eng"),
                subtitleLanguages = setOf("fra"),
                officialRatings = setOf("Tous publics", "-12"),
                studios = setOf(NamedId("s1", "Films du Phare")),
                people = setOf(NamedId("p1", "Marie Duval"), NamedId("p2", "Paul Mercier")),
                collection = NamedId("c1", "Le Phare : la saga"),
                tags = setOf("Coup de cœur"),
                favorite = true,
                ranges = setOf(DynamicRange.DolbyVision, DynamicRange.Hdr10),
                audioFormats = setOf(AudioFormat.Atmos),
                videoCodecs = setOf(VideoCodec.Hevc),
            )
        assertEquals(filters, FiltersCodec.decode(FiltersCodec.encode(filters)))
        assertEquals(16, filters.count)
        assertTrue(filters.usesIndex)
        assertFalse(filters.forServer.usesIndex)
    }

    @Test
    fun `no filter is an empty text, and an unknown value is skipped`() {
        assertEquals("", FiltersCodec.encode(ItemFilters.NONE))
        assertEquals(ItemFilters.NONE, FiltersCodec.decode(""))
        val newer = "watch=Someday\u001Egenres=Drame\u001Emood=calme"
        assertEquals(ItemFilters(genres = setOf("Drame")), FiltersCodec.decode(newer))
    }

    @Test
    fun `decades expand into years`() {
        assertEquals((1980..1989).toSet() + 2001, ItemFilters(decades = setOf(1980), years = setOf(2001)).allYears)
    }
}

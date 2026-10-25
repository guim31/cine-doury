package fr.guim.cinedoury.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SearchTest {
    private fun score(
        query: String,
        title: String,
    ) = SearchMatcher.score(SearchQuery.of(query), PreparedTitle.of(title))

    private fun matches(
        query: String,
        title: String,
    ) = score(query, title) != null

    @Test
    fun `normalization drops accents, case, ligatures and punctuation`() {
        assertEquals("amelie", SearchText.normalize("Amélie"))
        assertEquals("l eclipse", SearchText.normalize("L'Éclipse"))
        assertEquals("coeur de pirate", SearchText.normalize("Cœur  de pirate !"))
        assertEquals("spider man no way home", SearchText.normalize("Spider-Man: No Way Home"))
        assertEquals("ile de sel", SearchText.normalize("  Île de sel"))
        assertEquals("strasse", SearchText.normalize("Straße"))
        assertEquals(listOf("le", "phare", "la", "saga"), SearchText.words("Le Phare : la saga"))
    }

    @Test
    fun `accents and case do not matter`() {
        assertEquals(SearchMatcher.EXACT, score("amelie", "Amélie"))
        assertEquals(SearchMatcher.EXACT, score("AMÉLIE", "amelie"))
        assertTrue(matches("echappee", "Échappée belle"))
        assertTrue(matches("ile", "Île de sel"))
    }

    @Test
    fun `results from the first letter`() {
        assertTrue(matches("a", "Amélie"))
        assertTrue(matches("é", "Échappée belle"))
        assertTrue(matches("b", "Échappée belle"))
        assertNull(score("x", "Amélie"))
    }

    @Test
    fun `typos are forgiven according to the length of the word`() {
        assertTrue(matches("seigneur anaux", "Le Seigneur des anneaux"))
        assertTrue(matches("seigeur des anneaux", "Le Seigneur des anneaux"))
        assertTrue(matches("eclipce", "L'Éclipse"))
        // Two letters swapped count as one typo.
        assertTrue(matches("frequecnes", "Fréquences"))
        // The word being typed, with a typo.
        assertTrue(matches("frequan", "Fréquences"))
        // Too short for a typo.
        assertNull(score("ab", "Ac"))
        // The first letter must be right.
        assertNull(score("maree", "Durée"))
        assertEquals(0, SearchMatcher.allowedTypos(2))
        assertEquals(1, SearchMatcher.allowedTypos(4))
        assertEquals(2, SearchMatcher.allowedTypos(7))
    }

    @Test
    fun `every word of the query must match, articles aside`() {
        assertNull(score("phare nuit", "Le Phare"))
        assertTrue(matches("le phare", "Phare"))
        assertTrue(matches("spiderman", "Spider-Man"))
    }

    @Test
    fun `ranking - exact, then start, then whole words, then typos`() {
        val exact = assertNotNull(score("le phare", "Le Phare"))
        val start = assertNotNull(score("le phare", "Le Phare : la saga"))
        val words = assertNotNull(score("phare", "Retour au phare"))
        val typo = assertNotNull(score("phrae", "Retour au phare"))
        assertTrue(exact > start, "$exact > $start")
        assertTrue(start > words, "$start > $words")
        assertTrue(words > typo, "$words > $typo")
        // A shorter title first when both start with the query.
        assertTrue(
            assertNotNull(score("phare", "Phare et lumière")) >
                assertNotNull(score("phare", "Phare et balises du nord")),
        )
        // The query starting where the title starts beats a word in the middle.
        assertTrue(assertNotNull(score("marée", "Marée basse")) > assertNotNull(score("marée", "Grande marée")))
    }

    @Test
    fun `the original and sort titles count, a little less`() {
        val query = SearchQuery.of("amelie poulain")
        val titles = listOf("Le Fabuleux Destin d'Amélie Poulain", "Amélie").map(PreparedTitle::of)
        assertNotNull(SearchMatcher.best(query, titles))
        assertEquals(SearchMatcher.EXACT - 5, SearchMatcher.best(SearchQuery.of("amelie"), titles))
    }

    @Test
    fun `rows - one per group, best first, groups by their best item`() {
        fun item(
            id: String,
            kind: ItemKind,
            year: Int? = null,
        ) = MediaItem(ItemId(id), kind, title = id, year = year)
        val rows =
            SearchRanking.rows(
                listOf(
                    SearchHit(item("Phare 1", ItemKind.Movie, 2001), 600),
                    SearchHit(item("Phare 2", ItemKind.Movie, 2010), 600),
                    SearchHit(item("Phare 3", ItemKind.Movie), 900),
                    SearchHit(item("Le Phare", ItemKind.Person), 1000),
                    SearchHit(item("Saga", ItemKind.Collection), 600),
                    SearchHit(item("Phare 3", ItemKind.Movie), 100),
                    SearchHit(item("Saison", ItemKind.Season), 1000),
                ),
            )
        assertEquals(listOf(SearchGroup.People, SearchGroup.Movies, SearchGroup.Collections), rows.map { it.group })
        assertEquals(listOf("Phare 3", "Phare 2", "Phare 1"), rows[1].items.map { it.title })
    }

    @Test
    fun `the distance`() {
        assertEquals(0, Typos.distance("phare", "phare", 2))
        assertEquals(1, Typos.distance("phrae", "phare", 2))
        assertEquals(2, Typos.distance("anaux", "anneaux", 2))
        assertEquals(3, Typos.distance("abc", "xyzab", 2))
    }
}

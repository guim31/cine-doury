package fr.guim.cinedoury.feature.details

import fr.guim.cinedoury.core.model.Credit
import fr.guim.cinedoury.core.model.CreditType
import fr.guim.cinedoury.core.model.ImageRef
import fr.guim.cinedoury.core.model.ImageType
import fr.guim.cinedoury.core.model.ItemId
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The server gives one credit per role: the cast keeps one card per person, roles joined. */
class CastCardsTest {
    private fun credit(
        id: String,
        role: String?,
        type: CreditType = CreditType.Actor,
    ) = Credit(ItemId(id), "Name $id", type, role)

    @Test
    fun `a person who plays several roles has one card, at their first place, with every role once`() {
        val cards =
            listOf(
                credit("a", "Role A"),
                credit("b", "Other"),
                credit("a", "Role B"),
                credit("c", null),
                credit("a", "Role C"),
                credit("a", "Role B"),
                credit("a", "  "),
                credit("a", null),
            ).castCards(", ")
        assertEquals(listOf("a", "b", "c"), cards.map { it.id.value })
        assertEquals("Role A, Role B, Role C", cards[0].overview)
        assertEquals("Other", cards[1].overview)
        assertNull(cards[2].overview)
    }

    @Test
    fun `only actors and guest stars, the same person under both kinds counted once`() {
        val cards =
            listOf(
                credit("d", null, CreditType.Director),
                credit("a", "Role A"),
                credit("a", "Role B", CreditType.GuestStar),
                credit("w", null, CreditType.Writer),
            ).castCards(" / ")
        assertEquals(listOf("a"), cards.map { it.id.value })
        assertEquals("Role A / Role B", cards.single().overview)
        assertEquals("Name a", cards.single().title)
    }

    @Test
    fun `the photo is the first one the server gives for that person`() {
        val photo = ImageRef(ItemId("a"), ImageType.Primary, "tag")
        val cards =
            listOf(credit("a", "Role A"), credit("a", "Role B").copy(image = photo)).castCards(", ")
        assertEquals(photo, cards.single().images.primary)
    }
}

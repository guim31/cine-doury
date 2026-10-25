package fr.guim.cinedoury.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class PageTest {
    @Test
    fun `next start index follows the last item`() {
        assertEquals(2, Page(listOf("a", "b"), startIndex = 0, totalCount = 3).nextStartIndex)
    }

    @Test
    fun `last page has no next start index`() {
        assertNull(Page(listOf("c"), startIndex = 2, totalCount = 3).nextStartIndex)
        assertNull(Page(emptyList<String>(), startIndex = 0, totalCount = 0).nextStartIndex)
    }

    @Test
    fun `page request rejects invalid bounds`() {
        assertFailsWith<IllegalArgumentException> { PageRequest(startIndex = -1) }
        assertFailsWith<IllegalArgumentException> { PageRequest(limit = 0) }
    }
}

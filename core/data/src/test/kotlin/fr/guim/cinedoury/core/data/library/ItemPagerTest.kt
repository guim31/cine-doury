package fr.guim.cinedoury.core.data.library

import fr.guim.cinedoury.core.data.home.Samples.film
import fr.guim.cinedoury.core.model.MediaItem
import fr.guim.cinedoury.core.model.Page
import fr.guim.cinedoury.core.model.PageRequest
import fr.guim.cinedoury.core.source.SourceException
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ItemPagerTest {
    private val all = (0 until 25).map { film("f$it") }
    private val asked = mutableListOf<Int>()
    private var failing = false

    private suspend fun read(request: PageRequest): Page<MediaItem> {
        asked += request.startIndex
        if (failing) throw SourceException.Unreachable()
        return Page(all.drop(request.startIndex).take(request.limit), request.startIndex, all.size)
    }

    private fun TestScope.pager() = ItemPager(backgroundScope, pageSize = 10).also { it.load { r -> read(r) } }

    @Test
    fun `the first page gives the size of the whole list`() =
        runTest {
            val pager = pager()
            runCurrent()
            val items = pager.items.value
            assertEquals(25, items.total)
            assertEquals("f3", items[3]?.title)
            assertNull(items[12], "not read yet: a skeleton")
            assertEquals(listOf(0), asked)
        }

    @Test
    fun `nearing the end of a page reads the next one, once`() =
        runTest {
            val pager = pager()
            runCurrent()
            pager.request(6)
            pager.request(7)
            runCurrent()
            assertEquals(listOf(0, 10), asked)
            pager.request(22)
            runCurrent()
            assertEquals(listOf(0, 10, 20), asked, "no page after the last one")
            assertEquals(25, pager.items.value.loadedCount)
            assertEquals(21, pager.items.value.indexOf(all[21].id))
        }

    @Test
    fun `starts at a remembered position, then reads the page before`() =
        runTest {
            val pager = ItemPager(backgroundScope, pageSize = 10)
            pager.load(around = 21) { read(it) }
            runCurrent()
            assertEquals(listOf(20, 10), asked)
        }

    @Test
    fun `a failed page is read again on retry`() =
        runTest {
            failing = true
            val pager = pager()
            runCurrent()
            assertNotNull(pager.items.value.error)
            failing = false
            pager.retry()
            runCurrent()
            assertNull(pager.items.value.error)
            assertEquals(25, pager.items.value.total)
        }

    @Test
    fun `a new query replaces the list`() =
        runTest {
            val pager = pager()
            runCurrent()
            val generation = pager.items.value.generation
            pager.load { Page(listOf(film("other")), 0, 1) }
            assertNull(pager.items.value.total)
            runCurrent()
            assertEquals(1, pager.items.value.total)
            assertEquals(generation + 1, pager.items.value.generation)
        }
}

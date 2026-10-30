package fr.guim.cinedoury.mobile

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The rules of the architecture of M1 (`SPEC.md` § 4.26), read in the sources: no screen of the phone
 * imports Compose for TV, and the app of the phone holds no screen of its own beyond its shell.
 */
class MobileArchitectureTest {
    private val root =
        generateSequence(File("").absoluteFile) {
            it.parentFile
        }.first { File(it, "settings.gradle.kts").isFile }

    /** The sources of the phone: the app, and every `mobile` package of the modules. */
    private fun mobileSources(): List<File> =
        root
            .walkTopDown()
            .onEnter { it.name != "build" && it.name != ".git" }
            .filter { it.isFile && it.extension == "kt" && "/src/main/" in it.path }
            .filter { "/app/mobile/" in it.path || "/mobile/" in it.path.substringAfter("/src/main/") }
            .toList()

    @Test
    fun `no screen of the phone imports Compose for TV`() {
        val sources = mobileSources()
        assertTrue(sources.size > 10, "the sources of the phone were not found under $root")
        val offenders = sources.filter { file -> file.readLines().any { it.startsWith("import androidx.tv") } }
        assertEquals(emptyList(), offenders.map { it.relativeTo(root).path })
    }
}

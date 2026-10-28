package fr.guim.cinedoury.core.update

import java.io.File
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UpdateLogicTest {
    @Test
    fun `versions from tags, their code grows with them`() {
        assertEquals(AppVersion(1, 2, 3), AppVersion.parse("v1.2.3"))
        assertEquals(AppVersion(1, 2, 3), AppVersion.parse("1.2.3"))
        assertEquals(1_020_300, AppVersion(1, 2, 3).versionCode)
        assertEquals(1_000_000, AppVersion(1, 0, 0).versionCode)
        assertNull(AppVersion.parse("dev"))
        assertNull(AppVersion.parse("1.2.3-rc1"))
        assertNull(AppVersion.parse("v1.2"))
        assertNull(AppVersion.parse("1.100.0"))
        val sorted =
            listOf(
                "1.10.0",
                "1.2.0",
                "1.9.9",
                "2.0.0",
                "1.2.10",
            ).map { checkNotNull(AppVersion.parse(it)) }.sorted()
        assertEquals(listOf("1.2.0", "1.2.10", "1.9.9", "1.10.0", "2.0.0"), sorted.map { it.name })
        assertTrue(AppVersion(1, 10, 0).versionCode > AppVersion(1, 9, 99).versionCode)
    }

    @Test
    fun `trial builds install over their release, and the next release over them`() {
        val trial = checkNotNull(AppVersion.parse("v0.9.0-essai.7"))
        assertEquals(AppVersion(0, 9, 0, trial = 7), trial)
        assertEquals("0.9.0-essai.7", trial.name)
        assertEquals(90_007, trial.versionCode)
        assertTrue(trial.isTrial)
        // Above the 0.9.0 of the old scheme (900) and of the new one, under the 0.9.1.
        assertEquals(90_000, AppVersion(0, 9, 0).versionCode)
        assertEquals(90_100, AppVersion(0, 9, 1).versionCode)
        assertTrue(AppVersion(0, 9, 0).versionCode > 900)
        assertTrue(trial > AppVersion(0, 9, 0))
        assertTrue(trial < AppVersion(0, 9, 1))
        assertNull(AppVersion.parse("0.9.0-essai.0"))
        assertNull(AppVersion.parse("0.9.0-essai.100"))
        assertNull(AppVersion.parse("0.9.0-essai"))
    }

    @Test
    fun `a trial build is offered the next release, never another trial`() {
        val trial = AppVersion(0, 9, 0, trial = 7)

        fun release(version: AppVersion) =
            Release(
                version = version,
                tag = "v${version.name}",
                title = version.name,
                publishedAt = null,
                notes = "",
                apk = ReleaseAsset("cine-doury-${version.name}.apk", "https://example.com/a.apk", 1),
                checksum = null,
                page = null,
            )
        assertEquals(
            UpdateStatus.UpToDate(trial),
            UpdatePolicy.status(trial, listOf(release(AppVersion(0, 9, 0)), release(AppVersion(0, 9, 0, 9))), null),
        )
        val next = release(AppVersion(0, 9, 1))
        assertEquals(UpdateStatus.Available(next), UpdatePolicy.status(trial, listOf(next), null))
    }

    @Test
    fun `a check at start at most once a day, never for a development build`() {
        val now = Instant.parse("2026-10-26T10:00:00Z")
        val version = AppVersion(1, 0, 0)
        assertTrue(UpdatePolicy.shouldCheckAtStart(version, null, now))
        assertFalse(UpdatePolicy.shouldCheckAtStart(version, now.minusSeconds(3_600), now))
        assertTrue(UpdatePolicy.shouldCheckAtStart(version, now.minusSeconds(86_400), now))
        assertFalse(UpdatePolicy.shouldCheckAtStart(null, null, now))
    }

    @Test
    fun `the SHA-256 of sha256sum files`() {
        val hex = "a".repeat(64)
        assertEquals(hex, Checksums.parse("$hex  cine-doury-1.2.0.apk\n", "cine-doury-1.2.0.apk"))
        assertEquals(hex, Checksums.parse("$hex *cine-doury-1.2.0.apk", "cine-doury-1.2.0.apk"))
        assertEquals(hex, Checksums.parse(hex.uppercase(), "other.apk"), "a lone checksum")
        assertEquals(
            hex,
            Checksums.parse("${"b".repeat(64)}  other.apk\n$hex  cine-doury-1.2.0.apk", "cine-doury-1.2.0.apk"),
        )
        assertNull(Checksums.parse("not a checksum", "cine-doury-1.2.0.apk"))
    }

    @Test
    fun `a file whose SHA-256 differs is refused`() {
        val file = File(Files.createTempDirectory("sha").toFile(), "a.apk").apply { writeText("abc") }
        // `printf abc | sha256sum`
        val abc = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
        assertEquals(abc, Checksums.sha256(file))
        Checksums.verify(file, abc.uppercase())
        file.writeText("abd")
        val mismatch = assertFailsWith<UpdateException.ChecksumMismatch> { Checksums.verify(file, abc) }
        assertEquals(abc, mismatch.expected)
    }

    @Test
    fun `the changelog in simple Markdown`() {
        val blocks =
            Changelog.parse(
                """
                ## Nouveautés

                - Les **réglages**, une page par thème
                * La [mise à jour](https://example.com) proposée
                Une phrase
                sur deux lignes, avec `code`.

                # Corrections
                + Le clavier se ferme
                """.trimIndent(),
            )
        assertEquals(
            listOf(
                ChangelogBlock.Heading("Nouveautés"),
                ChangelogBlock.Bullet("Les réglages, une page par thème"),
                ChangelogBlock.Bullet("La mise à jour proposée"),
                ChangelogBlock.Paragraph("Une phrase sur deux lignes, avec code."),
                ChangelogBlock.Heading("Corrections"),
                ChangelogBlock.Bullet("Le clavier se ferme"),
            ),
            blocks,
        )
    }
}

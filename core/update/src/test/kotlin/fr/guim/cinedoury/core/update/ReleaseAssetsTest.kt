package fr.guim.cinedoury.core.update

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Each app takes its own APK from a release (M1, `SPEC.md` § 4.26), whatever the order of its assets;
 * and the TVs in 1.0.0, which take the first `.apk` of the list, still get the TV's.
 */
class ReleaseAssetsTest {
    private val tvApk = "cine-doury-1.1.0.apk"
    private val mobileApk = "cine-doury-mobile-1.1.0.apk"

    private fun asset(name: String) =
        """{"name":"$name","browser_download_url":"https://example.com/$name","size":16}"""

    private fun release(vararg names: String) =
        """[{"tag_name":"v1.1.0","name":"Ciné Doury 1.1.0","draft":false,"prerelease":false,
            "published_at":"2026-11-02T10:00:00Z","body":"","assets":[${names.joinToString(",") { asset(it) }}]}]"""

    private val tvFirst = release(tvApk, "$tvApk.sha256", mobileApk, "$mobileApk.sha256")
    private val mobileFirst = release(mobileApk, "$mobileApk.sha256", tvApk, "$tvApk.sha256")

    private fun pick(
        app: AppTarget,
        body: String,
    ) = ReleaseClient(app = app).parse(body).single()

    @Test
    fun `the TV takes its APK and its SHA-256, in both orders`() {
        for (body in listOf(tvFirst, mobileFirst)) {
            val release = pick(AppTarget.Tv, body)
            assertEquals(tvApk, release.apk.name)
            assertEquals("$tvApk.sha256", release.checksum?.name)
        }
    }

    @Test
    fun `the phone takes its APK and its SHA-256, in both orders`() {
        for (body in listOf(tvFirst, mobileFirst)) {
            val release = pick(AppTarget.Mobile, body)
            assertEquals(mobileApk, release.apk.name)
            assertEquals("$mobileApk.sha256", release.checksum?.name)
        }
    }

    @Test
    fun `a release without the APK of an app is not offered to it`() {
        assertTrue(ReleaseClient(app = AppTarget.Mobile).parse(release(tvApk, "$tvApk.sha256")).isEmpty())
        assertTrue(ReleaseClient(app = AppTarget.Tv).parse(release(mobileApk, "$mobileApk.sha256")).isEmpty())
    }

    @Test
    fun `the TV also takes a TV APK named for the TV`() {
        val release = pick(AppTarget.Tv, release(mobileApk, "cine-doury-tv-1.1.0.apk"))
        assertEquals("cine-doury-tv-1.1.0.apk", release.apk.name)
    }

    @Test
    fun `a SHA-256 of the other app is never taken`() {
        assertNull(pick(AppTarget.Tv, release(tvApk, mobileApk, "$mobileApk.sha256")).checksum)
    }

    /**
     * The logic of the TVs in 1.0.0 (`ReleaseClient` of the 1.0.0): the first asset whose name ends in
     * `.apk`, in the order the GitHub API lists them.
     */
    private fun firstApkOf100(body: String): String? =
        Json
            .parseToJsonElement(body)
            .jsonArray
            .single()
            .jsonObject["assets"]!!
            .jsonArray
            .map { it.jsonObject["name"]!!.jsonPrimitive.content }
            .firstOrNull { it.endsWith(".apk") }

    @Test
    fun `a TV in 1_0_0 gets the TV's APK when it is uploaded first`() {
        // GitHub lists the assets of a release in the order they were uploaded (their ids); it does
        // not document it, hence the rule of release-yml below: the TV's alone in the release.
        assertEquals(tvApk, firstApkOf100(tvFirst))
        assertEquals(mobileApk, firstApkOf100(mobileFirst), "the risk this test guards against")
    }

    @Test
    fun `the release workflow publishes the TV's APK alone`() {
        val workflow = File(repositoryRoot(), ".github/workflows/release.yml").readText()
        val publish = workflow.substringAfter("gh release create").substringBefore("--title")
        // M1: the APK of the phone is an artifact only, never in a release, so that a TV in 1.0.0 can
        // never take it, whatever order GitHub lists the assets in. Its publication comes with M3.
        assertFalse("mobile" in publish, publish)
        assertTrue("dist/tv/" in publish, publish)
    }

    private fun repositoryRoot(): File =
        generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, ".github").isDirectory }
}

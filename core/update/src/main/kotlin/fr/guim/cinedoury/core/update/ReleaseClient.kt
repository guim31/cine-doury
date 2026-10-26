package fr.guim.cinedoury.core.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.concurrent.TimeUnit

/**
 * The releases of the app on GitHub (`SPEC.md` § 4.17): `GET /repos/{repo}/releases`, without a
 * token (60 requests an hour per address, plenty for a check a day). Drafts, prereleases, tags that
 * are not `vX.Y.Z` and releases without an APK are left out. Only the TV talks to GitHub for this,
 * and only to read: nothing about the household is sent.
 */
class ReleaseClient(
    private val http: OkHttpClient = defaultClient(),
    private val api: String = GITHUB_API,
    private val repository: String = REPOSITORY,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Every published release, the newest first. */
    suspend fun releases(): List<Release> =
        withContext(Dispatchers.IO) {
            val request =
                Request
                    .Builder()
                    .url("${api.trimEnd('/')}/repos/$repository/releases?per_page=$PAGE")
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", API_VERSION)
                    .build()
            val (code, body, reset) =
                try {
                    http.newCall(request).execute().use { response ->
                        Triple(response.code, response.body.string(), response.header("X-RateLimit-Reset"))
                    }
                } catch (error: IOException) {
                    throw UpdateException.Unreachable(error)
                }
            when {
                code == HTTP_FORBIDDEN || code == HTTP_TOO_MANY -> {
                    throw UpdateException.RateLimited(reset?.toLongOrNull()?.let(Instant::ofEpochSecond))
                }

                code !in HTTP_OK -> {
                    throw UpdateException.Http(code)
                }
            }
            parse(body)
        }

    /** The text of a small file of a release (its SHA-256). */
    suspend fun text(asset: ReleaseAsset): String =
        withContext(Dispatchers.IO) {
            try {
                http.newCall(Request.Builder().url(asset.url).build()).execute().use { response ->
                    if (response.code !in HTTP_OK) throw UpdateException.Http(response.code)
                    response.body.string()
                }
            } catch (error: IOException) {
                throw UpdateException.Unreachable(error)
            }
        }

    /**
     * Downloads [asset] into [target], telling [progress] the bytes read and the total (the size of the
     * asset when the answer gives none).
     */
    suspend fun download(
        asset: ReleaseAsset,
        target: File,
        progress: (read: Long, total: Long) -> Unit,
    ) = withContext(Dispatchers.IO) {
        try {
            http.newCall(Request.Builder().url(asset.url).build()).execute().use { response ->
                if (response.code !in HTTP_OK) throw UpdateException.Http(response.code)
                val total = response.body.contentLength().takeIf { it > 0 } ?: asset.size
                target.parentFile?.mkdirs()
                response.body.byteStream().use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(BUFFER)
                        var read = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            read += count
                            progress(read, total)
                        }
                    }
                }
            }
        } catch (error: IOException) {
            target.delete()
            throw UpdateException.Unreachable(error)
        }
    }

    internal fun parse(body: String): List<Release> {
        val list =
            try {
                json.parseToJsonElement(body) as? JsonArray
            } catch (error: SerializationException) {
                throw UpdateException.Invalid("Not JSON", error)
            } ?: throw UpdateException.Invalid("Not a list of releases")
        return list
            .mapNotNull { (it as? JsonObject)?.toRelease() }
            .sortedByDescending { it.version }
    }

    private fun JsonObject.toRelease(): Release? {
        if (bool("draft") == true || bool("prerelease") == true) return null
        val tag = string("tag_name") ?: return null
        val version = AppVersion.parse(tag) ?: return null
        val assets = (this["assets"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.toAsset() }
        val apk = assets.firstOrNull { it.name.endsWith(".apk") } ?: return null
        return Release(
            version = version,
            tag = tag,
            title = string("name")?.takeIf { it.isNotBlank() } ?: tag,
            publishedAt =
                string("published_at")?.let {
                    try {
                        Instant.parse(it)
                    } catch (_: DateTimeParseException) {
                        null
                    }
                },
            notes = string("body").orEmpty(),
            apk = apk,
            checksum =
                assets.firstOrNull { it.name == "${apk.name}.sha256" }
                    ?: assets.firstOrNull { it.name.endsWith(".sha256") },
            page = string("html_url"),
        )
    }

    private fun JsonObject.toAsset(): ReleaseAsset? {
        val name = string("name") ?: return null
        val url = string("browser_download_url") ?: return null
        return ReleaseAsset(name, url, (this["size"] as? JsonPrimitive)?.longOrNull ?: 0L)
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull

    companion object {
        const val GITHUB_API = "https://api.github.com"
        const val REPOSITORY = "guim31/cine-doury"
        private const val API_VERSION = "2022-11-28"
        private const val PAGE = 30
        private const val BUFFER = 64 * 1024
        private const val HTTP_FORBIDDEN = 403
        private const val HTTP_TOO_MANY = 429
        private val HTTP_OK = 200..299
        private const val CONNECT_SECONDS = 10L
        private const val READ_SECONDS = 30L

        fun defaultClient(): OkHttpClient =
            OkHttpClient
                .Builder()
                .connectTimeout(CONNECT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(READ_SECONDS, TimeUnit.SECONDS)
                .followRedirects(true)
                .build()
    }
}

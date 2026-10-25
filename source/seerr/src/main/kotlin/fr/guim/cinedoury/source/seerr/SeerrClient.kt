package fr.guim.cinedoury.source.seerr

import fr.guim.cinedoury.core.model.RequestCandidate
import fr.guim.cinedoury.core.model.RequestKind
import fr.guim.cinedoury.core.model.RequestSeason
import fr.guim.cinedoury.core.model.RequestStatus
import fr.guim.cinedoury.core.source.MediaRequests
import fr.guim.cinedoury.core.source.RequestConnector
import fr.guim.cinedoury.core.source.SourceException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** Opens Seerr servers with one HTTP client. */
class SeerrConnector(
    private val http: OkHttpClient = defaultClient(),
) : RequestConnector {
    override fun open(
        address: String,
        apiKey: String,
        mediaUserId: String?,
    ): MediaRequests = SeerrClient(address, apiKey, mediaUserId, http)

    companion object {
        /** A request server on the local network answers fast; the search waits at most 10 s. */
        fun defaultClient(): OkHttpClient =
            OkHttpClient
                .Builder()
                .connectTimeout(CONNECT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(READ_SECONDS, TimeUnit.SECONDS)
                .build()

        private const val CONNECT_SECONDS = 3L
        private const val READ_SECONDS = 10L
    }
}

/**
 * [MediaRequests] over the API of Seerr, Jellyseerr and Overseerr (`/api/v1`, `SPEC.md` § 4.12),
 * authenticated by the `X-Api-Key` header. Requests are made in the name of the Seerr user linked to
 * the Jellyfin user of the profile (`X-Api-User`), when there is one.
 */
internal class SeerrClient(
    address: String,
    private val apiKey: String,
    private val mediaUserId: String?,
    private val http: OkHttpClient,
) : MediaRequests {
    private val base: HttpUrl? = normalizeAddress(address).toHttpUrlOrNull()
    private val json = Json { ignoreUnknownKeys = true }
    private val userLock = Mutex()
    private var seerrUser: Int? = null
    private var userLooked = false

    override suspend fun check(): String {
        val status =
            get("status", auth = false) as? JsonObject ?: throw SourceException.NotAServer("Not a Seerr answer")
        val version = status.string("version") ?: throw SourceException.NotAServer("No Seerr version in /status")
        // The key: `/auth/me` answers 401 or 403 for a wrong one.
        get("auth/me")
        return version
    }

    override suspend fun search(
        query: String,
        language: String,
    ): List<RequestCandidate> {
        val body =
            get("search") {
                // Seerr refuses a query that is not fully encoded (spaces as « + », quotes).
                addEncodedQueryParameter("query", encode(query))
                addQueryParameter("page", "1")
                addQueryParameter("language", language)
            }
        return ((body as? JsonObject)?.get("results") as? JsonArray)
            .orEmpty()
            .mapNotNull { (it as? JsonObject)?.toCandidate() }
    }

    override suspend fun seasons(
        tmdbId: Int,
        language: String,
    ): List<RequestSeason> {
        val show = get("tv/$tmdbId") { addQueryParameter("language", language) } as? JsonObject ?: return emptyList()
        val statuses =
            (show["mediaInfo"] as? JsonObject)
                ?.get("seasons")
                ?.let { it as? JsonArray }
                .orEmpty()
                .mapNotNull { it as? JsonObject }
                .associate { (it.int("seasonNumber") ?: -1) to status(it.int("status")) }
        return (show["seasons"] as? JsonArray)
            .orEmpty()
            .mapNotNull { it as? JsonObject }
            .mapNotNull { season ->
                val number = season.int("seasonNumber") ?: return@mapNotNull null
                if (number == 0) return@mapNotNull null
                RequestSeason(
                    number = number,
                    name = season.string("name"),
                    episodes = season.int("episodeCount") ?: 0,
                    status = statuses[number] ?: RequestStatus.None,
                )
            }.sortedBy { it.number }
    }

    override suspend fun request(
        candidate: RequestCandidate,
        seasons: List<Int>,
    ): RequestStatus {
        val payload =
            buildJsonObject {
                put("mediaType", if (candidate.kind == RequestKind.Movie) "movie" else "tv")
                put("mediaId", candidate.tmdbId)
                if (candidate.kind == RequestKind.Series) {
                    putJsonArray("seasons") { seasons.sorted().forEach { add(JsonPrimitive(it)) } }
                }
            }
        val url = url("request") {}
        val request =
            authorized(Request.Builder().url(url))
                .post(payload.toString().toRequestBody(JSON_TYPE))
                .build()
        return when (val code = execute(request).first) {
            in SUCCESS -> RequestStatus.Requested

            // Already asked for, by someone else maybe.
            CONFLICT -> RequestStatus.Requested

            else -> throw failure(code)
        }
    }

    private suspend fun get(
        path: String,
        auth: Boolean = true,
        query: HttpUrl.Builder.() -> Unit = {},
    ): JsonElement {
        val builder = Request.Builder().url(url(path, query))
        val request = (if (auth) authorized(builder) else builder).get().build()
        val (code, body) = execute(request)
        if (code !in SUCCESS) throw failure(code)
        return try {
            json.parseToJsonElement(body)
        } catch (error: SerializationException) {
            throw SourceException.NotAServer("Not a Seerr answer: ${error.message}", error)
        }
    }

    private suspend fun authorized(builder: Request.Builder): Request.Builder {
        builder.header("X-Api-Key", apiKey).header("Accept", "application/json")
        userId()?.let { builder.header("X-Api-User", it.toString()) }
        return builder
    }

    /** The Seerr user linked to the Jellyfin user, looked up once; null when there is none. */
    private suspend fun userId(): Int? =
        userLock.withLock {
            if (!userLooked && mediaUserId != null) {
                userLooked = true
                seerrUser =
                    runCatching {
                        val request =
                            Request
                                .Builder()
                                .url(url("user") { addQueryParameter("take", USERS) })
                                .header("X-Api-Key", apiKey)
                                .header("Accept", "application/json")
                                .build()
                        val (code, body) = execute(request)
                        if (code !in SUCCESS) return@runCatching null
                        val wanted = mediaUserId?.bareId()
                        (json.parseToJsonElement(body).jsonObject["results"] as? JsonArray)
                            .orEmpty()
                            .mapNotNull { it as? JsonObject }
                            .firstOrNull { it.string("jellyfinUserId")?.bareId() == wanted }
                            ?.int("id")
                    }.getOrNull()
            }
            seerrUser
        }

    private fun url(
        path: String,
        query: HttpUrl.Builder.() -> Unit,
    ): HttpUrl {
        val root = base ?: throw SourceException.NotAServer("Invalid address")
        return root
            .newBuilder()
            .addPathSegments("api/v1")
            .addPathSegments(path)
            .apply(query)
            .build()
    }

    private suspend fun execute(request: Request): Pair<Int, String> =
        withContext(Dispatchers.IO) {
            try {
                http.newCall(request).execute().use { response -> response.code to response.body.string() }
            } catch (error: IOException) {
                throw SourceException.Unreachable(error)
            }
        }

    private fun failure(code: Int): SourceException =
        when (code) {
            UNAUTHORIZED, FORBIDDEN -> SourceException.Unauthorized()
            NOT_FOUND -> SourceException.NotAServer("No Seerr API at this address")
            else -> SourceException.Server(code)
        }

    private fun JsonObject.toCandidate(): RequestCandidate? {
        val kind = KINDS[string("mediaType")]
        val media = this["mediaInfo"] as? JsonObject
        val code = media?.int("status")
        val id = int("id")
        val movie = kind == RequestKind.Movie
        val title = string(if (movie) "title" else "name")
        // People are not requested; a blocklisted title cannot be.
        if (code == BLOCKLISTED) return null
        if (kind == null || id == null || title == null) return null
        return RequestCandidate(
            tmdbId = id,
            kind = kind,
            title = title,
            year = string(if (movie) "releaseDate" else "firstAirDate")?.take(YEAR_LENGTH)?.toIntOrNull(),
            overview = string("overview")?.takeIf { it.isNotBlank() },
            posterUrl = string("posterPath")?.let { posterUrl(it) },
            status = status(code),
            libraryItemId = media?.string("jellyfinMediaId")?.bareId(),
        )
    }

    /** TMDb's poster, through Seerr's image proxy: the TV only talks to the servers of the house. */
    private fun posterUrl(path: String): String? =
        base
            ?.newBuilder()
            ?.addPathSegments("imageproxy/tmdb/t/p/$POSTER_SIZE")
            ?.addPathSegments(path.trimStart('/'))
            ?.build()
            ?.toString()

    private fun status(code: Int?): RequestStatus =
        when (code) {
            PENDING, PROCESSING -> RequestStatus.Requested
            PARTIALLY_AVAILABLE -> RequestStatus.PartlyAvailable
            AVAILABLE -> RequestStatus.Available
            else -> RequestStatus.None
        }

    private companion object {
        val JSON_TYPE = "application/json".toMediaType()
        val SUCCESS = 200..299
        const val UNAUTHORIZED = 401
        const val FORBIDDEN = 403
        const val NOT_FOUND = 404
        const val CONFLICT = 409

        // Seerr's MediaStatus.
        const val PENDING = 2
        const val PROCESSING = 3
        const val PARTIALLY_AVAILABLE = 4
        const val AVAILABLE = 5
        const val BLOCKLISTED = 6

        const val YEAR_LENGTH = 4
        val KINDS = mapOf("movie" to RequestKind.Movie, "tv" to RequestKind.Series)
        const val POSTER_SIZE = "w300_and_h450_face"

        /** Users read to find the profile's: a house has a handful. */
        const val USERS = "100"
    }
}

/** « 192.0.2.10:5055/ » → « http://192.0.2.10:5055 »: `http://` when no scheme is given. */
internal fun normalizeAddress(address: String): String {
    val trimmed = address.trim().trimEnd('/')
    return if ("://" in trimmed) trimmed else "http://$trimmed"
}

/** Every character encoded, spaces as `%20`. */
private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8).replace("+", "%20")

/** A Jellyfin id as 32 hexadecimal digits, without dashes, in lower case. */
private fun String.bareId(): String = replace("-", "").lowercase()

private fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)
        ?.takeIf {
            it.isString
        }?.contentOrNull

private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull

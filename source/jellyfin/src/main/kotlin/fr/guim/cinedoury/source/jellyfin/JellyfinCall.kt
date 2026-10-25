package fr.guim.cinedoury.source.jellyfin

import fr.guim.cinedoury.core.source.FailureLog
import fr.guim.cinedoury.core.source.SourceException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.exception.InvalidContentException
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.exception.SecureConnectionException
import org.jellyfin.sdk.api.client.exception.TimeoutException
import kotlin.coroutines.cancellation.CancellationException

private const val UNAUTHORIZED = 401
private const val FORBIDDEN = 403
private const val NOT_FOUND = 404
private const val SERVER_ERRORS = 500

/**
 * Runs an SDK call off the main thread and turns every failure into a [SourceException] that names
 * the request (method and path) and its HTTP status; every failure is written to [FailureLog].
 */
internal suspend fun <T> jellyfinCall(
    onUnauthorized: () -> SourceException = { SourceException.Unauthorized() },
    what: String = "resource",
    block: suspend () -> T,
): T {
    val trace = RequestTrace()
    return try {
        // Decoding a large answer takes time: never on the main thread either.
        withContext(Dispatchers.Default + trace) { block() }
    } catch (error: CancellationException) {
        throw error
    } catch (
        @Suppress("TooGenericExceptionCaught") error: Exception,
    ) {
        val failure = error.toSourceException(onUnauthorized, what).at(trace.request, trace.status)
        FailureLog.warn(
            "Jellyfin: ${failure::class.simpleName} on ${failure.request ?: what}" +
                (failure.httpStatus?.let { " (HTTP $it)" } ?: ""),
            error,
        )
        throw failure
    }
}

private fun Exception.toSourceException(
    onUnauthorized: () -> SourceException,
    what: String,
): SourceException =
    when (this) {
        is SourceException -> {
            this
        }

        is InvalidStatusException -> {
            when {
                status == UNAUTHORIZED -> onUnauthorized()
                status == FORBIDDEN -> SourceException.Forbidden()
                status == NOT_FOUND -> SourceException.NotFound(what)
                status >= SERVER_ERRORS -> SourceException.Server(status)
                else -> SourceException.Unexpected(this)
            }
        }

        is TimeoutException -> {
            SourceException.Unreachable(this)
        }

        is SecureConnectionException -> {
            SourceException.Unreachable(this)
        }

        is InvalidContentException -> {
            SourceException.NotAServer("Unexpected answer from the server", this)
        }

        is ApiClientException -> {
            SourceException.Unreachable(this)
        }

        else -> {
            SourceException.Unexpected(this)
        }
    }

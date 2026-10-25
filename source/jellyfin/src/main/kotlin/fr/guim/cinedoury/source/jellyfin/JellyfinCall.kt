package fr.guim.cinedoury.source.jellyfin

import fr.guim.cinedoury.core.source.SourceException
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

/** Runs an SDK call and turns every SDK failure into a [SourceException]. */
internal suspend fun <T> jellyfinCall(
    onUnauthorized: () -> SourceException = { SourceException.Unauthorized() },
    what: String = "resource",
    block: suspend () -> T,
): T =
    try {
        block()
    } catch (error: CancellationException) {
        throw error
    } catch (error: SourceException) {
        throw error
    } catch (error: InvalidStatusException) {
        throw when {
            error.status == UNAUTHORIZED -> onUnauthorized()
            error.status == FORBIDDEN -> SourceException.Forbidden()
            error.status == NOT_FOUND -> SourceException.NotFound(what)
            error.status >= SERVER_ERRORS -> SourceException.Server(error.status)
            else -> SourceException.Unexpected(error)
        }
    } catch (error: TimeoutException) {
        throw SourceException.Unreachable(error)
    } catch (error: SecureConnectionException) {
        throw SourceException.Unreachable(error)
    } catch (error: InvalidContentException) {
        throw SourceException.NotAServer("Unexpected answer from the server", error)
    } catch (error: ApiClientException) {
        throw SourceException.Unreachable(error)
    } catch (
        @Suppress("TooGenericExceptionCaught") error: Exception,
    ) {
        throw SourceException.Unexpected(error)
    }

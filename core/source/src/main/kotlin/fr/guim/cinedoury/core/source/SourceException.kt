package fr.guim.cinedoury.core.source

/**
 * The typed failures of a source. Screens map each case to a two-sentence message (`UI.md` § 10);
 * [message] is the technical detail shown under « Plus d'infos ».
 */
sealed class SourceException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** No answer: wrong address, server off, network down, timeout. */
    class Unreachable(cause: Throwable? = null) : SourceException("Server unreachable", cause)

    /** The server answered but is not one this source can talk to. */
    class NotAServer(detail: String, cause: Throwable? = null) : SourceException(detail, cause)

    class InvalidCredentials : SourceException("Invalid username or password")

    /** The session is no longer valid: token revoked or expired. */
    class Unauthorized : SourceException("Session rejected by the server")

    /** The user may not do this. */
    class Forbidden : SourceException("Forbidden")

    class NotFound(what: String) : SourceException("Not found: $what")

    class Incompatible(val serverVersion: String, val minimumVersion: String) :
        SourceException("Server version $serverVersion is older than $minimumVersion")

    class Server(val status: Int) : SourceException("Server error $status")

    class Unexpected(cause: Throwable) : SourceException(cause.message ?: cause::class.simpleName.orEmpty(), cause)
}

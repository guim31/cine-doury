package fr.guim.cinedoury.core.update

import java.time.Instant

/**
 * A published version of the app (a GitHub release, `SPEC.md` § 4.18).
 *
 * @param notes the changelog, in simple Markdown (the body of the release).
 * @param apk the APK to install; [checksum] the file of its SHA-256, published with it.
 */
data class Release(
    val version: AppVersion,
    val tag: String,
    val title: String,
    val publishedAt: Instant?,
    val notes: String,
    val apk: ReleaseAsset,
    val checksum: ReleaseAsset?,
    val page: String?,
)

data class ReleaseAsset(
    val name: String,
    val url: String,
    val size: Long,
)

/** Why the releases could not be read, or an update not installed. */
sealed class UpdateException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    /** No answer from GitHub (no network, no Internet from the TV). */
    class Unreachable(
        cause: Throwable? = null,
    ) : UpdateException("GitHub unreachable", cause)

    /** GitHub refuses for now: 60 requests an hour without a token (403 or 429). */
    class RateLimited(
        val retryAt: Instant?,
    ) : UpdateException("GitHub rate limit, retry at $retryAt")

    /** An answer that is not a list of releases. */
    class Invalid(
        detail: String,
        cause: Throwable? = null,
    ) : UpdateException(detail, cause)

    class Http(
        val status: Int,
    ) : UpdateException("GitHub answered $status")

    /** The SHA-256 of the file downloaded is not the one published: never installed. */
    class ChecksumMismatch(
        val expected: String,
        val actual: String,
    ) : UpdateException("SHA-256 $actual, expected $expected")

    /** The release has no SHA-256 published with its APK: never installed. */
    class NoChecksum : UpdateException("No SHA-256 published with the APK")
}

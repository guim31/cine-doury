package fr.guim.cinedoury.core.update

import java.time.Duration
import java.time.Instant

/** What the app tells about updates (`SPEC.md` § 4.18). */
sealed interface UpdateStatus {
    /** A build without a version (debug, local): it says so and offers nothing. */
    data object Development : UpdateStatus

    data class UpToDate(
        val current: AppVersion,
    ) : UpdateStatus

    /** A newer version; [ignored] when the user chose « Ignorer cette version ». */
    data class Available(
        val release: Release,
        val ignored: Boolean = false,
    ) : UpdateStatus
}

/**
 * When to look, and what to offer: proposed, never imposed (`FONCTIONNALITES.md` § 0). At start, at
 * most once a day, and an ignored version is not offered again; from the settings, at any time, and
 * an ignored version shows (marked so) for the user to change their mind.
 */
object UpdatePolicy {
    val CHECK_EVERY: Duration = Duration.ofDays(1)

    /** Whether the start of the app looks for an update. */
    fun shouldCheckAtStart(
        current: AppVersion?,
        lastCheck: Instant?,
        now: Instant,
    ): Boolean = current != null && (lastCheck == null || !now.isBefore(lastCheck.plus(CHECK_EVERY)))

    /**
     * The newest release above [current], if any: from a trial build (`X.Y.Z-essai.N`), the next
     * release, never another trial.
     */
    fun status(
        current: AppVersion?,
        releases: List<Release>,
        ignoredVersion: String?,
    ): UpdateStatus {
        current ?: return UpdateStatus.Development
        val newest = releases.filter { !it.version.isTrial && it.version > current }.maxByOrNull { it.version }
        return newest?.let { UpdateStatus.Available(it, ignored = it.version.name == ignoredVersion) }
            ?: UpdateStatus.UpToDate(current)
    }

    /** Whether the start of the app shows the update: available, and not ignored. */
    fun offersAtStart(status: UpdateStatus): Boolean = status is UpdateStatus.Available && !status.ignored
}

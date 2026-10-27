package fr.guim.cinedoury.feature.update

import fr.guim.cinedoury.core.data.prefs.AppPreferences
import fr.guim.cinedoury.core.source.FailureLog
import fr.guim.cinedoury.core.update.AppVersion
import fr.guim.cinedoury.core.update.Checksums
import fr.guim.cinedoury.core.update.Release
import fr.guim.cinedoury.core.update.ReleaseClient
import fr.guim.cinedoury.core.update.UpdateException
import fr.guim.cinedoury.core.update.UpdatePolicy
import fr.guim.cinedoury.core.update.UpdateStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.Clock
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException

/** Where an update stands (`SPEC.md` § 4.18): one screen shows each phase. */
sealed interface UpdatePhase {
    data object Idle : UpdatePhase

    data object Checking : UpdatePhase

    /** A build without a version: nothing is ever offered. */
    data object Development : UpdatePhase

    data object UpToDate : UpdatePhase

    data class Available(
        val release: Release,
        val ignored: Boolean = false,
    ) : UpdatePhase

    /** Android needs « Sources inconnues » for the app before it may install anything. */
    data class PermissionMissing(
        val release: Release,
    ) : UpdatePhase

    /** [progress] from 0 to 1. */
    data class Downloading(
        val release: Release,
        val progress: Float,
    ) : UpdatePhase

    /** Downloaded and checked (SHA-256, signature): Android asks the user to confirm. */
    data class Ready(
        val release: Release,
    ) : UpdatePhase

    /** Signed by another key than the app installed: Android would refuse it. */
    data class SignatureMismatch(
        val release: Release,
    ) : UpdatePhase

    data class Failed(
        val release: Release?,
        val error: UpdateFailure,
    ) : UpdatePhase
}

/** Why an update failed, as the screen explains it. */
sealed interface UpdateFailure {
    data object Unreachable : UpdateFailure

    data class RateLimited(
        val retryAt: Instant?,
    ) : UpdateFailure

    data object Invalid : UpdateFailure

    /** The file is not the one published (SHA-256), or none was published. */
    data object Checksum : UpdateFailure

    /** Android refused the APK, with its own words. */
    data class Install(
        val message: String?,
    ) : UpdateFailure
}

/**
 * @param current the version of the app; null for a development build.
 * @param versionName as the app shows it (« 1.2.0 », « 0.1.0-dev »).
 * @param lastCheck when the releases were last read.
 */
data class UpdateState(
    val versionName: String,
    val current: AppVersion?,
    val lastCheck: Instant? = null,
    val phase: UpdatePhase = UpdatePhase.Idle,
) {
    val development: Boolean get() = current == null
}

/**
 * The updates of the app, proposed and never imposed (`FONCTIONNALITES.md` § 13, `SPEC.md` § 4.18):
 * the releases of GitHub read at start (once a day at most) or on demand; the APK downloaded, its
 * SHA-256 checked against the one published, its signature against the installed app's, then given
 * to Android, which asks the user. Lives as long as the app ([scope]).
 */
@Suppress("TooManyFunctions") // One function per gesture of the update screen.
class UpdateRepository(
    private val client: ReleaseClient,
    private val preferences: AppPreferences,
    private val gate: PackageGate,
    versionName: String,
    private val downloads: File,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val current = AppVersion.parse(versionName)
    private val local = MutableStateFlow(UpdateState(versionName, current))
    val state: StateFlow<UpdateState> = local.asStateFlow()
    private var job: Job? = null
    private var installing: Job? = null
    private var checkedAtStart = false

    /**
     * At start: reads the releases if the last check is a day old, and tells whether to show the
     * update (available, not ignored). Never fails: a failure waits for the next day.
     */
    suspend fun checkAtStart(): Boolean {
        // Once per run of the app: coming back to the home asks nothing more.
        if (checkedAtStart) return false
        checkedAtStart = true
        val memory = preferences.updates.first()
        local.update { it.copy(lastCheck = memory.lastCheck) }
        if (!UpdatePolicy.shouldCheckAtStart(current, memory.lastCheck, clock.instant())) return false
        read(memory.ignoredVersion)
        return (local.value.phase as? UpdatePhase.Available)?.ignored == false
    }

    /** « Rechercher une mise à jour » (settings, « Réessayer »): now, an ignored version included. */
    fun check() {
        if (job?.isActive == true) return
        job = scope.launch { read(preferences.updates.first().ignoredVersion) }
    }

    private suspend fun read(ignored: String?) {
        if (current == null) {
            local.update { it.copy(phase = UpdatePhase.Development) }
            return
        }
        local.update { it.copy(phase = UpdatePhase.Checking) }
        val phase =
            failures(release = null) {
                val releases = client.releases()
                val now = clock.instant()
                preferences.setLastUpdateCheck(now)
                local.update { it.copy(lastCheck = now) }
                when (val status = UpdatePolicy.status(current, releases, ignored)) {
                    is UpdateStatus.Available -> UpdatePhase.Available(status.release, status.ignored)
                    else -> UpdatePhase.UpToDate
                }
            }
        local.update { it.copy(phase = phase) }
    }

    /** « Ignorer cette version »: not offered again at start (still shown in the settings). */
    fun ignore() {
        val release = releaseOf(local.value.phase) ?: return
        scope.launch {
            preferences.setIgnoredVersion(release.version.name)
            local.update { it.copy(phase = UpdatePhase.Available(release, ignored = true)) }
        }
    }

    /** « Installer »: the permission, the download and its checks, then [confirm]. */
    fun install() {
        val release = releaseOf(local.value.phase) ?: return
        if (job?.isActive == true) return
        if (!gate.canInstall()) {
            local.update { it.copy(phase = UpdatePhase.PermissionMissing(release)) }
            return
        }
        job = scope.launch { download(release) }
    }

    /** Back from the settings of Android: the permission may have been given. */
    fun permissionMaybeGiven() {
        if (local.value.phase is UpdatePhase.PermissionMissing && gate.canInstall()) install()
    }

    fun openPermissionSettings() = gate.openUnknownSourcesSettings()

    private suspend fun download(release: Release) {
        val file = File(downloads, release.apk.name)
        val phase =
            failures(release) {
                local.update { it.copy(phase = UpdatePhase.Downloading(release, 0f)) }
                client.download(release.apk, file) { read, total ->
                    val progress = if (total > 0) (read.toFloat() / total).coerceIn(0f, 1f) else 0f
                    local.update { it.copy(phase = UpdatePhase.Downloading(release, progress)) }
                }
                val sums = release.checksum ?: throw UpdateException.NoChecksum()
                val expected =
                    Checksums.parse(client.text(sums), release.apk.name) ?: throw UpdateException.NoChecksum()
                Checksums.verify(file, expected)
                if (SignatureCheck.differs(gate.installedSigners(), gate.archiveSigners(file))) {
                    UpdatePhase.SignatureMismatch(release)
                } else {
                    UpdatePhase.Ready(release)
                }
            }
        if (phase !is UpdatePhase.Ready) file.delete()
        local.update { it.copy(phase = phase) }
    }

    /** « Installer maintenant »: Android shows its confirmation; on success the app restarts. */
    fun confirm() {
        val release = (local.value.phase as? UpdatePhase.Ready)?.release ?: return
        if (installing?.isActive == true) return
        installing =
            scope.launch {
                val result = gate.install(File(downloads, release.apk.name))
                // Started: Android asks the user, then restarts the app; cancelled: still ready.
                if (result is InstallResult.Failed) {
                    FailureLog.warn("Update: Android refused ${release.apk.name}: ${result.message}")
                    local.update {
                        it.copy(
                            phase = UpdatePhase.Failed(release, UpdateFailure.Install(result.message)),
                        )
                    }
                }
            }
    }

    /** « Plus tard », or leaving the screen: the next offer is for the next check. */
    fun dismiss() {
        job?.cancel()
        local.update { it.copy(phase = UpdatePhase.Idle) }
    }

    private suspend fun failures(
        release: Release?,
        block: suspend () -> UpdatePhase,
    ): UpdatePhase =
        try {
            block()
        } catch (error: CancellationException) {
            throw error
        } catch (error: UpdateException) {
            FailureLog.warn("Update: ${error.message}", error)
            UpdatePhase.Failed(release, error.toFailure())
        }

    private fun releaseOf(phase: UpdatePhase): Release? =
        when (phase) {
            is UpdatePhase.Available -> phase.release
            is UpdatePhase.PermissionMissing -> phase.release
            is UpdatePhase.Failed -> phase.release
            else -> null
        }

    private fun UpdateException.toFailure(): UpdateFailure =
        when (this) {
            is UpdateException.Unreachable, is UpdateException.Http -> UpdateFailure.Unreachable
            is UpdateException.RateLimited -> UpdateFailure.RateLimited(retryAt)
            is UpdateException.Invalid -> UpdateFailure.Invalid
            is UpdateException.ChecksumMismatch, is UpdateException.NoChecksum -> UpdateFailure.Checksum
        }
}

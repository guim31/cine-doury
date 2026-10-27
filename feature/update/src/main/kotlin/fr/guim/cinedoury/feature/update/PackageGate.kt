package fr.guim.cinedoury.feature.update

import java.io.File

/** What Android answers when the app gives it an APK. */
sealed interface InstallResult {
    /** Android took the APK and asks the user (the app restarts once it is installed). */
    data object Started : InstallResult

    data object Cancelled : InstallResult

    data class Failed(
        val message: String?,
    ) : InstallResult
}

/**
 * What the update needs of Android (`SPEC.md` § 4.18), behind an interface so that the rules are
 * tested without it ([AndroidPackageGate] on the device).
 */
interface PackageGate {
    /** « Sources inconnues » allowed for the app (`REQUEST_INSTALL_PACKAGES`). */
    fun canInstall(): Boolean

    /** The SHA-256 of the certificates the installed app is signed with. */
    fun installedSigners(): Set<String>

    /** Those of [apk]; null when Android cannot read it. */
    fun archiveSigners(apk: File): Set<String>?

    /** Gives [apk] to `PackageInstaller`. */
    suspend fun install(apk: File): InstallResult

    /** Opens the screen of Android where « Sources inconnues » is given to the app. */
    fun openUnknownSourcesSettings()

    /** Opens the screen of the app in the settings of Android (to uninstall it). */
    fun openAppDetails()
}

/**
 * Android installs an update only when it is signed by the key of the installed app. A debug build
 * (signed by the debug key of the repository) and a release (signed by the release key) cannot
 * replace one another: the update screen says so before anything is installed.
 */
object SignatureCheck {
    fun differs(
        installed: Set<String>,
        archive: Set<String>?,
    ): Boolean = archive == null || installed.isEmpty() || installed.intersect(archive).isEmpty()
}

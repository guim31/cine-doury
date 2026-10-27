package fr.guim.cinedoury.feature.update

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import fr.guim.cinedoury.core.update.Checksums
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/**
 * [PackageGate] on Android: `PackageManager` for the permission and the certificates,
 * `PackageInstaller` for the install (`SPEC.md` § 4.18).
 */
class AndroidPackageGate(
    private val context: Context,
) : PackageGate {
    private val packages: PackageManager get() = context.packageManager

    override fun canInstall(): Boolean = packages.canRequestPackageInstalls()

    override fun installedSigners(): Set<String> =
        runCatching { packages.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES) }
            .getOrNull()
            ?.signers()
            .orEmpty()

    @SuppressLint("PackageManagerGetSignatures") // Read to compare, never to trust: Android checks again.
    @Suppress("DEPRECATION")
    override fun archiveSigners(apk: File): Set<String>? {
        // An archive gives its certificates through GET_SIGNING_CERTIFICATES on recent versions only.
        val info =
            packages
                .getPackageArchiveInfo(apk.path, PackageManager.GET_SIGNING_CERTIFICATES)
                ?.takeIf { it.signingInfo != null }
                ?: packages.getPackageArchiveInfo(apk.path, PackageManager.GET_SIGNATURES)
                ?: return null
        return info.signers().takeIf { it.isNotEmpty() }
    }

    @Suppress("DEPRECATION")
    private fun PackageInfo.signers(): Set<String> {
        val signing = signingInfo
        val signatures: Array<out Signature> =
            when {
                signing == null -> signatures.orEmpty()
                signing.hasMultipleSigners() -> signing.apkContentsSigners.orEmpty()
                else -> signing.signingCertificateHistory.orEmpty()
            }
        return signatures.map { Checksums.sha256(it.toByteArray()) }.toSet()
    }

    override suspend fun install(apk: File): InstallResult {
        val installer = packages.packageInstaller
        val session =
            withContext(Dispatchers.IO) {
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                val id = installer.createSession(params)
                installer.openSession(id).also { session ->
                    session.openWrite(apk.name, 0, apk.length()).use { output ->
                        apk.inputStream().use { it.copyTo(output) }
                        session.fsync(output)
                    }
                }
            }
        return suspendCancellableCoroutine { continuation ->
            val action = "${context.packageName}.UPDATE_INSTALLED"
            val receiver =
                object : BroadcastReceiver() {
                    override fun onReceive(
                        context: Context,
                        intent: Intent,
                    ) {
                        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
                        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                            // Android's own confirmation; once it is accepted, a last status comes.
                            IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)?.let {
                                context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                            if (continuation.isActive) continuation.resume(InstallResult.Started)
                            return
                        }
                        context.unregisterReceiver(this)
                        val result =
                            when (status) {
                                PackageInstaller.STATUS_SUCCESS -> {
                                    InstallResult.Started
                                }

                                PackageInstaller.STATUS_FAILURE_ABORTED -> {
                                    InstallResult.Cancelled
                                }

                                else -> {
                                    InstallResult.Failed(
                                        intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE),
                                    )
                                }
                            }
                        if (continuation.isActive) continuation.resume(result)
                    }
                }
            ContextCompat.registerReceiver(context, receiver, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED)
            // The system writes the status into the intent: it must be mutable.
            val flags =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val pending =
                PendingIntent.getBroadcast(
                    context,
                    0,
                    Intent(action).setPackage(context.packageName),
                    flags or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            session.commit(pending.intentSender)
            session.close()
        }
    }

    /**
     * « Sources inconnues » of the app: its own screen where Android has it (Google TV), else the
     * security settings (Shield: Préférences de l'appareil → Sécurité et restrictions), else the
     * settings.
     */
    override fun openUnknownSourcesSettings() =
        open(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")),
            Intent(Settings.ACTION_SECURITY_SETTINGS),
            Intent(Settings.ACTION_SETTINGS),
        )

    override fun openAppDetails() =
        open(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
            Intent(Settings.ACTION_SETTINGS),
        )

    /** The first of [intents] the TV has a screen for. */
    private fun open(vararg intents: Intent) {
        for (intent in intents) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: ActivityNotFoundException) {
                // Not on this TV: the next one.
            }
        }
    }
}

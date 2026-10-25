package fr.guim.cinedoury.core.data

import android.content.Context
import android.os.Build
import android.provider.Settings
import java.io.File
import java.util.UUID

/**
 * How this installation introduces itself to the server (`SPEC.md` § 4.7). The [deviceId] is drawn
 * at random on first launch (32 hexadecimal digits) and kept in `noBackupFilesDir`: stable for the
 * life of the installation, never copied to another device, gone with the app. Jellyfin ties
 * sessions and tokens to it.
 */
class DeviceIdentity(
    context: Context,
) {
    val deviceId: String = readOrCreate(File(context.noBackupFilesDir, FILE))

    /** The name given to the TV in its settings, else its model (« SHIELD Android TV »). */
    val deviceName: String =
        Settings.Global
            .getString(context.contentResolver, Settings.Global.DEVICE_NAME)
            ?.takeIf { it.isNotBlank() }
            ?: Build.MODEL

    private companion object {
        const val FILE = "device-id"
        val FORMAT = Regex("[0-9a-f]{32}")

        fun readOrCreate(file: File): String {
            val existing = runCatching { file.readText().trim() }.getOrNull()
            if (existing != null && FORMAT.matches(existing)) return existing
            val id = UUID.randomUUID().toString().replace("-", "")
            file.writeText(id)
            return id
        }
    }
}

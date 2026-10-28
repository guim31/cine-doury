package fr.guim.cinedoury.feature.settings

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.view.WindowManager
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.guim.cinedoury.core.data.ConnectionMode
import fr.guim.cinedoury.core.data.SessionManager
import fr.guim.cinedoury.core.data.prefs.AppPreferences
import fr.guim.cinedoury.core.data.search.RequestSettings
import fr.guim.cinedoury.core.model.DeviceCapabilities
import fr.guim.cinedoury.core.model.PlaybackPreferences
import fr.guim.cinedoury.core.playback.PlayerSettingsStore
import fr.guim.cinedoury.core.source.DiagnosticInput
import fr.guim.cinedoury.core.source.DiagnosticReport
import fr.guim.cinedoury.core.source.SourceException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/** What the device says of itself, for the report (Android, or a fake in tests). */
fun interface DeviceFacts {
    fun read(): Pair<DiagnosticInput.App, DiagnosticInput.Device>
}

/** The facts of this Android device: version of the app, model, mode of the screen, density. */
class AndroidDeviceFacts(
    private val context: Context,
) : DeviceFacts {
    /** What sizes the caches of images (`ImageBudget`): the heap, low RAM or not, the ABIs. */
    private fun memory(): String {
        val activities = context.getSystemService(ActivityManager::class.java)
        val lowRam = if (activities.isLowRamDevice) "yes" else "no"
        return "heap ${activities.memoryClass} MB, low RAM: $lowRam, ABIs: ${Build.SUPPORTED_ABIS.joinToString()}"
    }

    override fun read(): Pair<DiagnosticInput.App, DiagnosticInput.Device> {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        val debug = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        val app =
            DiagnosticInput.App(
                versionName = info.versionName.orEmpty(),
                versionCode = info.longVersionCode.toInt(),
                buildType = if (debug) "debug" else "release",
            )

        @Suppress("DEPRECATION") // The display of the window manager: no activity here.
        val display = context.getSystemService(WindowManager::class.java)?.defaultDisplay
        val mode = display?.mode
        val metrics = context.resources.displayMetrics
        val widthDp = (metrics.widthPixels / metrics.density).toInt()
        val heightDp = (metrics.heightPixels / metrics.density).toInt()
        val device =
            DiagnosticInput.Device(
                model = "${Build.MANUFACTURER} ${Build.MODEL}",
                android = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                displayMode =
                    mode?.let {
                        "${it.physicalWidth} × ${it.physicalHeight} @ ${"%.2f".format(
                            Locale.ROOT,
                            it.refreshRate,
                        )} Hz"
                    }
                        ?: "?",
                density =
                    "${metrics.density} (${metrics.densityDpi} dpi), " +
                        "$widthDp × $heightDp dp",
                memory = memory(),
            )
        return app to device
    }
}

/** The report, and where its sending stands. */
@Immutable
data class DiagnosticState(
    val report: String? = null,
    val sending: Boolean = false,
    val sentAs: String? = null,
    val failed: Boolean = false,
    val connected: Boolean = false,
)

/**
 * « Rapport de diagnostic » (`SPEC.md` § 4.19): built when the screen opens, shown, sent to the logs
 * of the server on demand. The token of the profile and the key of Seerr are masked even if a
 * message carried one.
 */
class DiagnosticViewModel(
    private val sessions: SessionManager,
    private val player: PlayerSettingsStore,
    private val preferences: AppPreferences,
    private val requests: RequestSettings,
    private val facts: DeviceFacts,
    private val capabilities: () -> DeviceCapabilities? = { null },
) : ViewModel() {
    private val local = MutableStateFlow(DiagnosticState(connected = sessions.active.value != null))
    val state: StateFlow<DiagnosticState> = local.asStateFlow()

    init {
        viewModelScope.launch { local.update { it.copy(report = build()) } }
    }

    private suspend fun build(): String {
        val active = sessions.active.value
        val (app, device) = facts.read()
        val serverPreferences: PlaybackPreferences? =
            try {
                active?.library?.playbackPreferences()
            } catch (_: SourceException) {
                null
            }
        val server =
            active?.connection?.let {
                DiagnosticInput.Server(
                    name = it.server.name,
                    version = it.server.version,
                    remote = it.mode == ConnectionMode.Remote,
                    https = it.address.url.startsWith("https://"),
                )
            }
        val appearance = preferences.appearance.first()
        val input =
            DiagnosticInput(
                app = app,
                device = device,
                capabilities = withContext(Dispatchers.Default) { capabilities() },
                server = server,
                settings = player.current(),
                preferences = serverPreferences,
                otherSettings =
                    listOf(
                        "Network" to preferences.networkMode.first().name,
                        "Appearance" to
                            "${appearance.theme}, reduced motion ${appearance.reducedMotion}, " +
                            "text ${appearance.textSize}, interface ${appearance.interfaceSize}",
                    ),
            )
        val seerr =
            active
                ?.connection
                ?.server
                ?.id
                ?.let { requests.server(it).first() }
        // Secrets, and addresses too: the report names the server, never where it is.
        val secrets =
            listOfNotNull(
                active?.session?.accessToken,
                seerr?.apiKey,
                seerr?.address,
                active
                    ?.connection
                    ?.server
                    ?.localAddress
                    ?.url,
                active
                    ?.connection
                    ?.server
                    ?.remoteAddress
                    ?.url,
            )
        return DiagnosticReport.build(input, secrets)
    }

    /** `POST /ClientLog/Document`: the name of the file the server made, or a failure. */
    fun send() {
        val report = local.value.report ?: return
        val source = sessions.active.value?.library ?: return
        if (local.value.sending) return
        local.update { it.copy(sending = true, failed = false) }
        viewModelScope.launch {
            try {
                val name = source.sendDiagnostic(report)
                local.update { it.copy(sending = false, sentAs = name) }
            } catch (_: SourceException) {
                local.update { it.copy(sending = false, failed = true) }
            }
        }
    }
}

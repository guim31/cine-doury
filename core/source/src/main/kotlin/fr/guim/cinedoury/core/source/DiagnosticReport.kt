package fr.guim.cinedoury.core.source

import fr.guim.cinedoury.core.model.DeviceCapabilities
import fr.guim.cinedoury.core.model.PlaybackPreferences
import fr.guim.cinedoury.core.model.PlaybackProfiles
import fr.guim.cinedoury.core.model.PlayerSettings
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * What the diagnostic report says (`SPEC.md` § 4.19). Every field is a fact about the app, the device
 * or the server, never an address, a token, a password or a key.
 */
data class DiagnosticInput(
    val app: App,
    val device: Device,
    val capabilities: DeviceCapabilities?,
    val server: Server?,
    val settings: PlayerSettings,
    val preferences: PlaybackPreferences?,
    val otherSettings: List<Pair<String, String>> = emptyList(),
    val failures: List<FailureLog.Entry> = FailureLog.recent(),
    val decisions: List<PlaybackLog.Entry> = PlaybackLog.recent(),
    val generatedAt: Instant = Instant.now(),
    val zone: ZoneId = ZoneId.systemDefault(),
) {
    data class App(
        val versionName: String,
        val versionCode: Int,
        val buildType: String,
    )

    /**
     * @param displayMode the mode of the screen (« 3840 × 2160 @ 59.94 Hz »).
     * @param density the density and the size of the interface (« 4.0 (640 dpi), 960 × 540 dp »).
     * @param memory the heap, low RAM or not, and the ABIs (« heap 256 MB, low RAM: no, ABIs:
     * armeabi-v7a, armeabi »): what sizes the caches of images (J8).
     */
    data class Device(
        val model: String,
        val android: String,
        val displayMode: String,
        val density: String,
        val memory: String = "?",
    )

    /** @param remote reached through its remote address; [https] whether that address is secure. */
    data class Server(
        val name: String,
        val version: String,
        val remote: Boolean,
        val https: Boolean,
    )
}

/**
 * The diagnostic report (`SPEC.md` § 4.19): plain text a person reads, shown on the TV and sent to the
 * logs of the server. A technical document in English, like the logs of the server it joins. Any of
 * [secrets] that a message would carry by mistake (the token of the profile, the key of Seerr) is
 * masked all the same.
 */
object DiagnosticReport {
    const val MASK = "***"
    private val TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT)

    fun build(
        input: DiagnosticInput,
        secrets: Collection<String> = emptyList(),
    ): String {
        val text =
            buildString {
                line("Cine Doury diagnostic report")
                line("Generated: ${input.generatedAt.at(input)}")
                section("App")
                line("Version: ${input.app.versionName} (code ${input.app.versionCode}, ${input.app.buildType})")
                section("Device")
                line("Model: ${input.device.model}")
                line("Android: ${input.device.android}")
                line("Display mode: ${input.device.displayMode}")
                line("Density: ${input.device.density}")
                line("Memory: ${input.device.memory}")
                capabilities(input)
                section("Server")
                val server = input.server
                if (server == null) {
                    line("Not connected")
                } else {
                    line("Server: ${server.name}, Jellyfin ${server.version}")
                    val access = if (server.remote) "remote" else "local"
                    line("Access: $access (${if (server.https) "https" else "http"})")
                }
                settings(input)
                failures(input)
                decisions(input)
            }
        return secrets.filter { it.length >= MIN_SECRET }.fold(text) { acc, secret -> acc.replace(secret, MASK) }
    }

    private fun StringBuilder.capabilities(input: DiagnosticInput) {
        val device = input.capabilities ?: return
        val display = device.display
        line("Screen: ${display.width} × ${display.height}, ${display.hdr.joinToString { it.name }.ifEmpty { "SDR" }}")
        (display.announcedHdr - display.hdr).takeIf { it.isNotEmpty() }?.let { ignored ->
            // P (`SPEC.md` § 4.29): announced by the display, not by its current mode.
            line("HDR announced but not in the screen's mode (ignored): ${ignored.joinToString { it.name }}")
        }
        line("Refresh rates: ${display.refreshRates.joinToString { "%.3f".format(Locale.ROOT, it) }}")
        device.videoDecoders.forEach { decoder ->
            val kind = if (decoder.hardware) "hardware" else "software"
            val size = "${decoder.maxWidth} × ${decoder.maxHeight}, ${decoder.maxBitDepth} bits"
            val profiles = decoder.profiles.sorted().joinToString()
            val level = decoder.maxLevel ?: "?"
            val codec = decoder.codec.name.uppercase()
            line("Decoder $codec: ${decoder.name}, $kind, $size, profiles $profiles, level $level")
        }
        line("Dolby Vision profiles: ${device.dolbyVisionProfiles.sorted().joinToString().ifEmpty { "none" }}")
        line("Audio decoders: ${device.audioDecoders.joinToString { it.codecName }}")
        val passthrough = device.passthrough.joinToString { it.name }.ifEmpty { "none" }
        line("Audio output: passthrough $passthrough, ${device.maxPcmChannels} PCM channels")
        val profile = PlaybackProfiles.build(device, input.settings.profileOptions(input.server?.remote == true))
        line(
            "Profile sent: ${profile.video.joinToString { rule -> rule.summary() }}; " +
                "audio ${profile.audioCodecs.joinToString()} up to ${profile.maxAudioChannels} channels; " +
                "transcoding ${profile.transcodeVideoCodecs.joinToString()} in ${profile.transcodeContainer}, " +
                "${profile.maxBitrate / MEGABIT} Mb/s",
        )
    }

    private fun StringBuilder.settings(input: DiagnosticInput) {
        val settings = input.settings
        section("Playback settings")
        val ceiling = settings.bitrateCeiling / MEGABIT
        line(
            "Quality: local ${settings.localQuality.mbps()}, remote ${settings.remoteQuality.mbps()}, " +
                "ceiling $ceiling Mb/s",
        )
        line("Direct play: ${settings.directPlay}")
        val allowed = settings.passthrough.joinToString { it.name }.ifEmpty { "none" }
        line("Passthrough allowed: $allowed; forced stereo: ${settings.forceStereo}")
        line("Audio offset: ${settings.audioOffsetMillis} ms")
        line("Frame rate matching: ${settings.matchFrameRate}; resolution matching: ${settings.matchResolution}")
        line("HDR allowed: ${settings.allowHdr}; Dolby Vision allowed: ${settings.allowDolbyVision}")
        line("ASS drawn by the server: ${settings.burnInAss}; aspect: ${settings.aspect}")
        val next = "${settings.autoplayNext} after ${settings.nextCountdownSeconds} s"
        line("Intro: ${settings.segments}; next episode: $next")
        input.preferences?.let {
            line(
                "Profile on the server: audio ${it.audioLanguage ?: "default"}, " +
                    "subtitles ${it.subtitleLanguage ?: "none"}, " +
                    "mode ${it.subtitleMode}",
            )
        }
        input.otherSettings.forEach { (name, value) -> line("$name: $value") }
    }

    private fun StringBuilder.failures(input: DiagnosticInput) {
        section("Last failures (${input.failures.size})")
        if (input.failures.isEmpty()) line("None")
        input.failures.asReversed().forEach { failure ->
            val error = failure.error?.let { " — $it" }.orEmpty()
            line("${failure.at.at(input)} ${failure.level}: ${failure.message}$error")
        }
    }

    private fun StringBuilder.decisions(input: DiagnosticInput) {
        section("Last playback decisions (${input.decisions.size})")
        if (input.decisions.isEmpty()) line("None")
        input.decisions.asReversed().forEach { decision ->
            val why = decision.reasons.joinToString { it.name }.ifEmpty { "-" }
            line(
                "${decision.at.at(input)} ${decision.title}: ${decision.method}" +
                    (if (decision.fallback) " (fallback after a failure of the player)" else "") +
                    (if (decision.forced) " (forced direct play)" else "") +
                    ", ${decision.container ?: "?"}, video ${decision.video ?: "?"}, audio ${decision.audio ?: "?"}, " +
                    "${decision.bitrate.mbps()}, reasons: $why",
            )
        }
    }

    private fun fr.guim.cinedoury.core.model.VideoRule.summary(): String =
        "$codec ${ranges.joinToString("/") { it.serverName }}"

    private fun Instant.at(input: DiagnosticInput): String = TIME.format(atZone(input.zone))

    private fun Int?.mbps(): String = this?.let { "${it / MEGABIT} Mb/s" } ?: "original"

    private fun StringBuilder.section(title: String) {
        append('\n').append("## ").append(title).append('\n')
    }

    private fun StringBuilder.line(text: String) {
        append(text).append('\n')
    }

    private const val MEGABIT = 1_000_000

    /** A short value (« 1 ») would mask half the report: secrets are long. */
    private const val MIN_SECRET = 6
}

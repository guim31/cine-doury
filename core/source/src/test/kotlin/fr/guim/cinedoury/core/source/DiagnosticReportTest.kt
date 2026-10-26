package fr.guim.cinedoury.core.source

import fr.guim.cinedoury.core.model.PlayMethod
import fr.guim.cinedoury.core.model.PlaybackPreferences
import fr.guim.cinedoury.core.model.PlayerSettings
import fr.guim.cinedoury.core.model.SubtitleMode
import fr.guim.cinedoury.core.model.TranscodeReason
import fr.guim.cinedoury.core.source.fake.SampleDevices
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiagnosticReportTest {
    private val token = "0f3c9a1b2c3d4e5f60718293a4b5c6d7"
    private val seerrKey = "MTcwMDAwMDAwMDAwMGFiY2RlZmdoaWprbG1ub3A="
    private val password = "cinedoury-parent"
    private val at = Instant.parse("2026-10-26T14:02:00Z")

    private fun input(failures: List<FailureLog.Entry> = emptyList()) =
        DiagnosticInput(
            app = DiagnosticInput.App("1.2.0", 10_200, "release"),
            device = DiagnosticInput.Device("NVIDIA SHIELD Android TV", "11 (API 30)", "3840 × 2160 @ 59.94 Hz", "4.0 (640 dpi), 960 × 540 dp"),
            capabilities = SampleDevices.shield,
            server = DiagnosticInput.Server("Maison", "12.3.0", remote = true, https = true),
            settings = PlayerSettings(localQuality = 20_000_000),
            preferences = PlaybackPreferences("fre", "fre", SubtitleMode.Smart),
            otherSettings = listOf("Network" to "Automatic"),
            failures = failures,
            decisions =
                listOf(
                    PlaybackLog.Entry(at, "Le Phare", PlayMethod.Transcode, setOf(TranscodeReason.AudioCodecNotSupported), "ts", "hevc", "truehd", 20_000_000, fallback = true),
                ),
            generatedAt = at,
            zone = ZoneOffset.UTC,
        )

    @Test
    fun `the report says the app, the device, the server, the settings, the failures and the decisions`() {
        val report =
            DiagnosticReport.build(
                input(listOf(FailureLog.Entry(at, FailureLog.Level.Warning, "GET /Items/{itemId} failed", "Unreachable: Server unreachable"))),
            )
        listOf(
            "Version: 1.2.0 (code 10200, release)",
            "Model: NVIDIA SHIELD Android TV",
            "Android: 11 (API 30)",
            "Display mode: 3840 × 2160 @ 59.94 Hz",
            "Density: 4.0 (640 dpi), 960 × 540 dp",
            "Decoder HEVC",
            "Audio output: passthrough",
            "Server: Maison, Jellyfin 12.3.0",
            "Access: remote (https)",
            "Quality: local 20 Mb/s, remote 8 Mb/s",
            "mode Smart",
            "Network: Automatic",
            "2026-10-26 14:02:00 Warning: GET /Items/{itemId} failed — Unreachable: Server unreachable",
            "Le Phare: Transcode (fallback after a failure of the player), ts, video hevc, audio truehd, 20 Mb/s, reasons: AudioCodecNotSupported",
        ).forEach { assertTrue(it in report, "« $it » in\n$report") }
    }

    @Test
    fun `no secret ever: neither the token, nor a password, nor the key of Seerr`() {
        // Even a failure that would carry one by mistake.
        val leaky =
            listOf(
                FailureLog.Entry(at, FailureLog.Level.Error, "Token=\"$token\" rejected", null),
                FailureLog.Entry(at, FailureLog.Level.Warning, "Seerr X-Api-Key $seerrKey", "Forbidden: $password"),
            )
        val report = DiagnosticReport.build(input(leaky), secrets = listOf(token, seerrKey, password))
        assertFalse(token in report)
        assertFalse(seerrKey in report)
        assertFalse(password in report)
        assertTrue(DiagnosticReport.MASK in report)
        // Nor any address: the server is named, its address is not.
        assertFalse("http://" in report || "https://" in report)
    }
}

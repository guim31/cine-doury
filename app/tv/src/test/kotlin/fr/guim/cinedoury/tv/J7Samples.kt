package fr.guim.cinedoury.tv

import fr.guim.cinedoury.core.data.ConnectionMode
import fr.guim.cinedoury.core.model.PassthroughFormat
import fr.guim.cinedoury.core.model.PlayMethod
import fr.guim.cinedoury.core.model.PlaybackPreferences
import fr.guim.cinedoury.core.model.PlayerSettings
import fr.guim.cinedoury.core.model.SubtitleBackground
import fr.guim.cinedoury.core.model.SubtitleMode
import fr.guim.cinedoury.core.model.TranscodeReason
import fr.guim.cinedoury.core.source.DiagnosticInput
import fr.guim.cinedoury.core.source.DiagnosticReport
import fr.guim.cinedoury.core.source.FailureLog
import fr.guim.cinedoury.core.source.PlaybackLog
import fr.guim.cinedoury.core.source.fake.SampleDevices
import fr.guim.cinedoury.core.update.AppVersion
import fr.guim.cinedoury.core.update.Release
import fr.guim.cinedoury.core.update.ReleaseAsset
import fr.guim.cinedoury.feature.settings.DiagnosticState
import fr.guim.cinedoury.feature.settings.ServerPreferences
import fr.guim.cinedoury.feature.settings.SettingsState
import fr.guim.cinedoury.feature.update.UpdateFailure
import fr.guim.cinedoury.feature.update.UpdatePhase
import fr.guim.cinedoury.feature.update.UpdateState
import java.time.Instant
import java.time.ZoneOffset

/** Sample content of the J7 screenshots: the settings of a Shield, an update, a report. */
internal object J7Samples {
    private val at = Instant.parse("2026-10-26T14:02:00Z")

    val release =
        Release(
            version = AppVersion(1, 1, 0),
            tag = "v1.1.0",
            title = "Ciné Doury 1.1.0",
            publishedAt = Instant.parse("2026-10-25T18:00:00Z"),
            notes =
                """
                ## Nouveautés
                - Les **réglages**, une page par thème, avec leur valeur par défaut.
                - La mise à jour proposée depuis l'appli, jamais imposée.
                - Le rapport de diagnostic, envoyé au serveur.

                ## Corrections
                - Le clavier se ferme sur Android 11.
                """.trimIndent(),
            apk = ReleaseAsset("cine-doury-1.1.0.apk", "https://example.com/cine-doury-1.1.0.apk", 31_400_000),
            checksum =
                ReleaseAsset("cine-doury-1.1.0.apk.sha256", "https://example.com/cine-doury-1.1.0.apk.sha256", 90),
            page = "https://example.com/v1.1.0",
        )

    private fun update(phase: UpdatePhase) = UpdateState("1.0.0", AppVersion(1, 0, 0), at, phase)

    val available = update(UpdatePhase.Available(release))
    val downloading = update(UpdatePhase.Downloading(release, 0.42f))
    val ready = update(UpdatePhase.Ready(release))
    val signature = update(UpdatePhase.SignatureMismatch(release))
    val permission = update(UpdatePhase.PermissionMissing(release))
    val failed = update(UpdatePhase.Failed(release, UpdateFailure.Checksum))
    val upToDate = update(UpdatePhase.UpToDate)
    val development = UpdateState("0.1.0-dev", null, null, UpdatePhase.Development)

    /** A Shield behind an amplifier that takes everything but DTS-HD today, its owner's habits. */
    val settings =
        SettingsState(
            session = J1Samples.session(ConnectionMode.Local),
            libraries = J3Samples.libraries,
            hidden = setOf(J3Samples.libraries[4].id),
            index = J3Samples.indexReady,
            player =
                PlayerSettings(
                    passthrough = PassthroughFormat.entries.toSet() - PassthroughFormat.TrueHd,
                    audioOffsetMillis = 50,
                    localQuality = null,
                    seekForwardSeconds = 15,
                    subtitles = PlayerSettings().subtitles.copy(scale = 1.2f, background = SubtitleBackground.Shaded),
                ),
            capabilities =
                SampleDevices.shield.copy(passthrough = SampleDevices.shield.passthrough - PassthroughFormat.DtsHd),
            server = ServerPreferences.Ready(PlaybackPreferences("fre", "fre", SubtitleMode.Smart)),
            tvName = "SHIELD Android TV",
            update = available.copy(phase = UpdatePhase.Idle),
        )

    val report =
        DiagnosticState(
            report =
                DiagnosticReport.build(
                    DiagnosticInput(
                        app = DiagnosticInput.App("1.0.0", 10_000, "release"),
                        device =
                            DiagnosticInput.Device(
                                "NVIDIA SHIELD Android TV",
                                "11 (API 30)",
                                "3840 × 2160 @ 59.94 Hz",
                                "4.0 (640 dpi), 960 × 540 dp",
                            ),
                        capabilities = SampleDevices.shield,
                        server = DiagnosticInput.Server("Maison", "12.3.0", remote = false, https = false),
                        settings = settings.player,
                        preferences = PlaybackPreferences("fre", "fre", SubtitleMode.Smart),
                        failures = failures,
                        decisions =
                            listOf(
                                PlaybackLog.Entry(
                                    at,
                                    "Le Phare",
                                    PlayMethod.Transcode,
                                    setOf(TranscodeReason.AudioCodecNotSupported),
                                    "ts",
                                    "hevc",
                                    "truehd",
                                    null,
                                ),
                            ),
                        generatedAt = at,
                        zone = ZoneOffset.UTC,
                    ),
                ),
            connected = true,
            sentAs = "upload_Cine Doury_1.0.0_20261026140200_9aa8.log",
        )

    val failures: List<FailureLog.Entry>
        get() =
            listOf(
                FailureLog.Entry(
                    at,
                    FailureLog.Level.Warning,
                    "GET /Items/{itemId} failed",
                    "Unreachable: Server unreachable",
                ),
                FailureLog.Entry(
                    at.plusSeconds(60),
                    FailureLog.Level.Error,
                    "Sign-in: the session could not be kept",
                    "IllegalStateException: no key",
                ),
            )
}

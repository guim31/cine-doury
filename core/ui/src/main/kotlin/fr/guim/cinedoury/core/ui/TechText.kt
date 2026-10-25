package fr.guim.cinedoury.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import fr.guim.cinedoury.core.model.AudioFormat
import fr.guim.cinedoury.core.model.DynamicRange
import fr.guim.cinedoury.core.model.MediaSource
import fr.guim.cinedoury.core.model.MediaStream
import fr.guim.cinedoury.core.model.Resolution
import fr.guim.cinedoury.core.model.SpatialAudio
import fr.guim.cinedoury.core.model.StreamType
import fr.guim.cinedoury.core.model.TechBadge
import fr.guim.cinedoury.core.model.VideoCodec
import fr.guim.cinedoury.core.model.dynamicRange
import java.text.NumberFormat
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The clock of the screens (end times): the system's, a fixed one in screenshots. */
val LocalClock = staticCompositionLocalOf<Clock> { Clock.systemDefaultZone() }

/** The language of the screens (`fr` by default, `en`). */
@Composable
@ReadOnlyComposable
fun screenLocale(): Locale = LocalConfiguration.current.locales[0]

/** « 22 h 47 » in French, « 10:47 PM » in English (`UI.md` § 5). */
@Composable
fun LocalTime.toTimeText(): String =
    DateTimeFormatter.ofPattern(stringResource(R.string.time_pattern), screenLocale()).format(this)

/** « 24 oct. 2026 » (`UI.md` § 5), « Oct 24, 2026 ». */
@Composable
fun LocalDate.toDateText(): String =
    DateTimeFormatter.ofPattern(stringResource(R.string.date_pattern), screenLocale()).format(this)

/** The label of a badge (`FONCTIONNALITES.md` § 4): technical names stay as they are written. */
@Composable
fun TechBadge.label(): String =
    stringResource(
        when (this) {
            TechBadge.Uhd -> R.string.badge_uhd
            TechBadge.FullHd -> R.string.badge_full_hd
            TechBadge.Hd -> R.string.badge_hd
            TechBadge.Sd -> R.string.badge_sd
            TechBadge.DolbyVision -> R.string.badge_dolby_vision
            TechBadge.Hdr10Plus -> R.string.badge_hdr10_plus
            TechBadge.Hdr10 -> R.string.badge_hdr10
            TechBadge.Hlg -> R.string.badge_hlg
            TechBadge.Atmos -> R.string.badge_atmos
            TechBadge.DtsX -> R.string.badge_dts_x
            TechBadge.TrueHd -> R.string.badge_truehd
            TechBadge.DtsHd -> R.string.badge_dts_hd
            TechBadge.Dts -> R.string.badge_dts
            TechBadge.Surround71 -> R.string.badge_71
            TechBadge.Surround51 -> R.string.badge_51
            TechBadge.LocalAudio -> R.string.badge_local_audio
            TechBadge.Subtitled -> R.string.badge_subtitled
            TechBadge.Original -> R.string.badge_original
        },
    )

@Composable
fun DynamicRange.label(): String =
    stringResource(
        when (this) {
            DynamicRange.Sdr -> R.string.range_sdr
            DynamicRange.Hdr10 -> R.string.badge_hdr10
            DynamicRange.Hdr10Plus -> R.string.badge_hdr10_plus
            DynamicRange.Hlg -> R.string.badge_hlg
            DynamicRange.DolbyVision -> R.string.badge_dolby_vision
        },
    )

@Composable
fun AudioFormat.label(): String =
    stringResource(
        when (this) {
            AudioFormat.Stereo -> R.string.audio_stereo
            AudioFormat.Surround51 -> R.string.badge_51
            AudioFormat.Surround71 -> R.string.badge_71
            AudioFormat.Atmos -> R.string.audio_atmos
            AudioFormat.Dts -> R.string.audio_dts
            AudioFormat.TrueHd -> R.string.audio_truehd
        },
    )

@Composable
fun VideoCodec.label(): String =
    stringResource(
        when (this) {
            VideoCodec.H264 -> R.string.codec_h264
            VideoCodec.Hevc -> R.string.codec_hevc
            VideoCodec.Av1 -> R.string.codec_av1
            VideoCodec.Vp9 -> R.string.codec_vp9
            VideoCodec.Mpeg2 -> R.string.codec_mpeg2
            VideoCodec.Vc1 -> R.string.codec_vc1
            VideoCodec.Other -> R.string.codec_other
        },
    )

/**
 * The name of a language from its ISO 639-2 code (« fra » → « Français »), in the language of the
 * screens; the code itself when unknown.
 */
fun languageName(
    code: String,
    locale: Locale,
): String {
    val twoLetters = BIBLIOGRAPHIC[code.lowercase()] ?: ISO3[code.lowercase()] ?: code
    val name = Locale.forLanguageTag(twoLetters).getDisplayLanguage(locale)
    return name.ifEmpty { code }.replaceFirstChar { it.titlecase(locale) }
}

@Composable
fun languageName(code: String): String = languageName(code, screenLocale())

private val ISO3: Map<String, String> by lazy {
    Locale.getISOLanguages().associateBy { Locale.forLanguageTag(it).isO3Language }
}

/** ISO 639-2/B codes some files carry instead of the 639-2/T ones Java knows. */
private val BIBLIOGRAPHIC =
    mapOf("fre" to "fr", "ger" to "de", "chi" to "zh", "dut" to "nl", "gre" to "el", "cze" to "cs", "rum" to "ro")

private const val MONO = 1
private const val STEREO = 2
private const val SURROUND_51 = 6
private const val SURROUND_71 = 8

/** The name of a codec as people know it: « E-AC3 », « DTS-HD MA », « HEVC », « PGS ». */
@Composable
fun MediaStream.codecLabel(): String? {
    val profile = profile.orEmpty()
    return when (codec?.lowercase()) {
        null -> null

        "eac3" -> stringResource(R.string.codec_eac3)

        "ac3" -> stringResource(R.string.codec_ac3)

        "truehd" -> stringResource(R.string.audio_truehd)

        // The server's profile says « DTS-HD MA » or « DTS-HD MA + DTS:X ».
        "dts" -> profile.takeIf { "HD" in it }?.substringBefore(" +") ?: stringResource(R.string.audio_dts)

        "aac" -> stringResource(R.string.codec_aac)

        "flac" -> stringResource(R.string.codec_flac)

        "opus" -> stringResource(R.string.codec_opus)

        "mp3" -> stringResource(R.string.codec_mp3)

        "h264" -> stringResource(R.string.codec_h264)

        "hevc", "h265" -> stringResource(R.string.codec_hevc)

        "av1" -> stringResource(R.string.codec_av1)

        "vp9" -> stringResource(R.string.codec_vp9)

        "mpeg2video" -> stringResource(R.string.codec_mpeg2)

        "subrip", "srt" -> stringResource(R.string.codec_srt)

        "ass", "ssa" -> stringResource(R.string.codec_ass)

        "pgssub", "pgs" -> stringResource(R.string.codec_pgs)

        "dvdsub", "dvd_subtitle", "vobsub" -> stringResource(R.string.codec_vobsub)

        "webvtt", "vtt" -> stringResource(R.string.codec_vtt)

        else -> codec?.uppercase()
    }
}

/** « Stéréo », « 5.1 », « 7.1 », from the channels of an audio track. */
@Composable
fun MediaStream.channelsLabel(): String? =
    when (val count = channels) {
        null -> null
        MONO -> stringResource(R.string.audio_mono)
        STEREO -> stringResource(R.string.audio_stereo)
        SURROUND_51 -> stringResource(R.string.badge_51)
        SURROUND_71 -> stringResource(R.string.badge_71)
        else -> stringResource(R.string.audio_channels, count)
    }

/**
 * A track described in words (`UI.md` § 8, « Lecteur »): « Français — 5.1 — E-AC3 — par défaut »,
 * « Anglais — ASS », « Français — SRT — forcés ».
 */
@Composable
fun MediaStream.describe(): String {
    val parts = mutableListOf<String>()
    parts += language?.let { languageName(it) } ?: title ?: stringResource(R.string.track_unknown_language)
    if (type == StreamType.Audio) {
        channelsLabel()?.let { parts += it }
        val codec = codecLabel()
        val atmos = spatialAudio == SpatialAudio.DolbyAtmos || profile.orEmpty().contains("Atmos")
        when {
            codec != null && atmos -> parts += stringResource(R.string.track_with_atmos, codec)
            codec != null -> parts += codec
        }
    } else {
        codecLabel()?.let { parts += it }
    }
    if (isForced) parts += stringResource(R.string.track_forced)
    if (isHearingImpaired) parts += stringResource(R.string.track_hearing_impaired)
    if (isDefault) parts += stringResource(R.string.track_default)
    if (isExternal) parts += stringResource(R.string.track_external)
    return parts.joinToString(TRACK_SEPARATOR)
}

/** « 4K — HEVC — Dolby Vision — 12,3 Go »: a version of a film, for the choice of the version. */
@Composable
fun MediaSource.describe(): String {
    val video = video
    val parts = mutableListOf<String>()
    video?.width?.let { parts += Resolution.of(it).label() }
    video?.codecLabel()?.let { parts += it }
    video?.dynamicRange?.let { parts += it.label() }
    sizeBytes?.let { parts += it.sizeText() }
    return parts.joinToString(TRACK_SEPARATOR).ifEmpty { name.orEmpty() }
}

@Composable
fun Resolution.label(): String =
    stringResource(
        when (this) {
            Resolution.Sd -> R.string.badge_sd
            Resolution.Hd -> R.string.badge_hd
            Resolution.FullHd -> R.string.badge_full_hd
            Resolution.Uhd -> R.string.badge_uhd
        },
    )

private const val BYTES_PER_GB = 1_000_000_000.0
private const val BYTES_PER_MB = 1_000_000.0

/** « 12,3 Go », « 850 Mo ». */
@Composable
fun Long.sizeText(): String {
    val format = NumberFormat.getNumberInstance(screenLocale()).apply { maximumFractionDigits = 1 }
    return if (this >= BYTES_PER_GB) {
        stringResource(R.string.size_gb, format.format(this / BYTES_PER_GB))
    } else {
        stringResource(R.string.size_mb, format.format(this / BYTES_PER_MB))
    }
}

/** Between the parts of the description of a track. */
const val TRACK_SEPARATOR = " — "

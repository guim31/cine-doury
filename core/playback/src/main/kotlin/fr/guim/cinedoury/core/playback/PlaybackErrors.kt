package fr.guim.cinedoury.core.playback

import androidx.annotation.OptIn
import androidx.media3.common.ParserException
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import fr.guim.cinedoury.core.model.PlaybackFailure

/**
 * What an error of ExoPlayer means for the next step ([fr.guim.cinedoury.core.model.PlaybackFallback]).
 *
 * The family of the code is not enough (G, M2): an extractor that refuses what it reads, inside a
 * loader (« Unexpected IllegalStateException: Top bit not zero », the negative `tfdt` of an H.264
 * copied into fMP4 by Jellyfin), comes as code 2000, an input/output error, and was told as « La
 * connexion au serveur est perdue », which « Réessayer » replayed. A [ParserException] or an
 * [IllegalStateException] in the causes is a [PlaybackFailure.Format]: the fallback follows.
 */
@OptIn(UnstableApi::class)
object PlaybackErrors {
    fun failureOf(error: PlaybackException): PlaybackFailure {
        val causes = generateSequence(error.cause) { it.cause }.take(MAX_CAUSES).toList()
        return when {
            error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> PlaybackFailure.Server
            error.errorCode in IO_ERRORS && causes.any { it.isParsing() } -> PlaybackFailure.Format
            error.errorCode in IO_ERRORS -> PlaybackFailure.Network
            error.errorCode in PARSING_ERRORS -> PlaybackFailure.Format
            error.errorCode in DECODING_ERRORS || error.errorCode in AUDIO_TRACK_ERRORS -> PlaybackFailure.Decoder
            else -> PlaybackFailure.Unknown
        }
    }

    /** « ERROR_CODE_IO_UNSPECIFIED: Source error ← IllegalStateException: Top bit not zero », for « Plus d'infos ». */
    fun detail(error: PlaybackException): String {
        val causes = generateSequence(error.cause) { it.cause }.take(MAX_CAUSES)
        return (
            listOf("${error.errorCodeName}: ${error.message}") +
                causes.map { "${it.javaClass.simpleName}: ${it.message}" }
        ).joinToString(" ← ")
    }

    /** « HttpDataSourceException: … ← SocketTimeoutException: … »: a failed load, for « Plus d'infos » (N). */
    fun causes(error: Throwable): String =
        generateSequence(error) { it.cause }
            .take(MAX_CAUSES)
            .joinToString(" ← ") { "${it.javaClass.simpleName}: ${it.message}" }

    private fun Throwable.isParsing(): Boolean = this is ParserException || this is IllegalStateException

    private const val MAX_CAUSES = 8

    // The families of error codes of Media3: 2xxx input/output, 3xxx parsing, 4xxx decoding, 5xxx audio track.
    private val IO_ERRORS = 2000..2999
    private val PARSING_ERRORS = 3000..3999
    private val DECODING_ERRORS = 4000..4999
    private val AUDIO_TRACK_ERRORS = 5000..5999
}

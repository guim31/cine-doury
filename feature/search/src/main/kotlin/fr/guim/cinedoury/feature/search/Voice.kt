package fr.guim.cinedoury.feature.search

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import java.util.Locale

/** Why the voice search stopped without a query. */
enum class VoiceProblem {
    /** Nothing understood (silence, noise). */
    NoMatch,

    /** The microphone is not allowed. */
    Denied,

    /** No recognizer on this device, or it failed. */
    Unavailable,
}

/**
 * The voice search of the screen (`SPEC.md` § 4.12): the microphone button, the « Recherche » key of
 * the remote. [level] is read at drawing time only.
 */
@Stable
interface VoiceSearch {
    /** A recognizer exists on the device: the microphone key shows. */
    val available: Boolean
    val listening: Boolean

    /** What was understood so far, while listening. */
    val transcript: String?

    /** The loudness of the voice, from 0 to 1. */
    val level: Float
    val problem: VoiceProblem?

    fun start()

    fun cancel()
}

/** A voice search drawn as given, for screenshots. */
class PreviewVoice(
    override val available: Boolean = true,
    override val listening: Boolean = false,
    override val transcript: String? = null,
    override val level: Float = 0f,
    override val problem: VoiceProblem? = null,
) : VoiceSearch {
    override fun start() = Unit

    override fun cancel() = Unit
}

/**
 * The voice search of Android TV, in the language of the app: [SpeechRecognizer] in the app (our
 * own listening screen, the words as they come), after asking for the microphone once; without it,
 * the voice activity of the system ([RecognizerIntent]). [onResult] receives what was said.
 */
@Composable
fun rememberVoiceSearch(onResult: (String) -> Unit): VoiceSearch {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val result by rememberUpdatedState(onResult)
    val voice = remember(context) { AndroidVoiceSearch(context) }
    voice.locale = locale
    voice.onResult = { result(it) }
    val system =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { answer ->
            val said =
                answer.data
                    ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    ?.firstOrNull()
                    ?.takeIf { answer.resultCode == Activity.RESULT_OK }
            if (said != null) result(said) else voice.problem = VoiceProblem.NoMatch
        }
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) voice.listen() else voice.problem = VoiceProblem.Denied
        }
    voice.askPermission = { permission.launch(Manifest.permission.RECORD_AUDIO) }
    voice.systemActivity = { intent -> runCatching { system.launch(intent) }.isSuccess }
    DisposableEffect(voice) { onDispose { voice.release() } }
    return voice
}

/** [VoiceSearch] over the recognizer of the device. */
private class AndroidVoiceSearch(
    private val context: Context,
) : VoiceSearch {
    var locale: Locale = Locale.getDefault()
    var onResult: (String) -> Unit = {}
    var askPermission: () -> Unit = {}
    var systemActivity: (Intent) -> Boolean = { false }

    private val inApp = SpeechRecognizer.isRecognitionAvailable(context)
    private val withActivity = recognizeIntent().resolveActivity(context.packageManager) != null
    private var recognizer: SpeechRecognizer? = null

    override val available: Boolean = inApp || withActivity
    override var listening by mutableStateOf(false)
    override var transcript by mutableStateOf<String?>(null)
    override var level by mutableFloatStateOf(0f)
    override var problem by mutableStateOf<VoiceProblem?>(null)

    private fun recognizeIntent() =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)

    override fun start() {
        problem = null
        when {
            inApp && granted() -> listen()
            inApp -> askPermission()
            withActivity -> if (!systemActivity(recognizeIntent())) problem = VoiceProblem.Unavailable
            else -> problem = VoiceProblem.Unavailable
        }
    }

    private fun granted() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    fun listen() {
        val speech = recognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also { recognizer = it }
        speech.setRecognitionListener(listener)
        transcript = null
        level = 0f
        listening = true
        speech.startListening(recognizeIntent())
    }

    override fun cancel() {
        recognizer?.cancel()
        listening = false
    }

    fun release() {
        recognizer?.destroy()
        recognizer = null
        listening = false
    }

    private val listener =
        object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit

            override fun onBeginningOfSpeech() = Unit

            override fun onRmsChanged(rmsdB: Float) {
                level = ((rmsdB - QUIET_DB) / RANGE_DB).coerceIn(0f, 1f)
            }

            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEndOfSpeech() = Unit

            override fun onError(error: Int) {
                listening = false
                problem =
                    when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> VoiceProblem.NoMatch
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> VoiceProblem.Denied
                        else -> VoiceProblem.Unavailable
                    }
            }

            override fun onResults(results: Bundle?) {
                listening = false
                val said = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (said.isNullOrBlank()) problem = VoiceProblem.NoMatch else onResult(said)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { transcript = it }
            }

            override fun onEvent(
                eventType: Int,
                params: Bundle?,
            ) = Unit
        }

    private companion object {
        /** The recognizer gives the loudness from about −2 dB (silence) to 10 dB (a loud voice). */
        const val QUIET_DB = -2f
        const val RANGE_DB = 12f
    }
}

package fr.guim.cinedoury.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.guim.cinedoury.core.data.SessionManager
import fr.guim.cinedoury.core.source.Credentials
import fr.guim.cinedoury.core.source.SourceException
import fr.guim.cinedoury.core.ui.Events
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** The Quick Connect screen. */
sealed interface QuickConnectState {
    data object Loading : QuickConnectState

    /** [code] is shown; the TV waits for the phone, without blocking anything. */
    data class Waiting(
        val code: String,
    ) : QuickConnectState

    /** The server forgot the request: a new code is needed. */
    data object Expired : QuickConnectState

    data class Failed(
        val error: SourceException,
    ) : QuickConnectState
}

enum class SignInEvent {
    SignedIn,

    /** Quick Connect is off on the server: the password screen takes over. */
    UsePassword,
}

/**
 * Quick Connect (`SPEC.md` § 4.7): a 6-digit code from the server, polled every two seconds until
 * a signed-in device approves it, then exchanged for a session.
 */
class QuickConnectViewModel(
    private val sessions: SessionManager,
    private val pollEvery: Duration = POLL_EVERY,
) : ViewModel() {
    private val mutableState = MutableStateFlow<QuickConnectState>(QuickConnectState.Loading)
    val state: StateFlow<QuickConnectState> = mutableState.asStateFlow()
    val events = Events<SignInEvent>()
    private var job: Job? = null

    init {
        start()
    }

    /** Asks for a code (again, after an expiry or an error). */
    fun start() {
        job?.cancel()
        mutableState.value = QuickConnectState.Loading
        job = viewModelScope.launch { run() }
    }

    private suspend fun run() {
        try {
            if (!sessions.quickConnectEnabled()) {
                events.send(SignInEvent.UsePassword)
                return
            }
            val request = sessions.startQuickConnect()
            mutableState.value = QuickConnectState.Waiting(request.code)
            while (!sessions.isQuickConnectApproved(request)) delay(pollEvery)
            sessions.signIn(Credentials.QuickConnect(request))
            events.send(SignInEvent.SignedIn)
        } catch (_: SourceException.QuickConnectExpired) {
            mutableState.value = QuickConnectState.Expired
        } catch (_: SourceException.QuickConnectDisabled) {
            events.send(SignInEvent.UsePassword)
        } catch (error: SourceException) {
            mutableState.value = QuickConnectState.Failed(error)
        }
    }

    companion object {
        val POLL_EVERY = 2.seconds
    }
}

data class PasswordState(
    val username: String,
    val password: String = "",
    val showPassword: Boolean = false,
    val busy: Boolean = false,
    /** Wrong username or password: said under the fields, the focus back on the password. */
    val refused: Boolean = false,
    val error: SourceException? = null,
)

/** Username and password, typed with the remote (`FONCTIONNALITES.md` § 1). */
class PasswordViewModel(
    private val sessions: SessionManager,
    username: String?,
) : ViewModel() {
    private val mutableState = MutableStateFlow(PasswordState(username = username.orEmpty()))
    val state: StateFlow<PasswordState> = mutableState.asStateFlow()
    val signedIn = Events<Unit>()

    fun editUsername(text: String) = mutableState.update { it.copy(username = text, refused = false) }

    fun editPassword(text: String) = mutableState.update { it.copy(password = text, refused = false) }

    fun toggleShowPassword() = mutableState.update { it.copy(showPassword = !it.showPassword) }

    fun submit() {
        val current = mutableState.value
        if (current.busy || current.username.isBlank()) return
        mutableState.update { it.copy(busy = true, refused = false, error = null) }
        viewModelScope.launch {
            try {
                sessions.signIn(Credentials.Password(current.username.trim(), current.password))
                mutableState.update { it.copy(busy = false) }
                signedIn.send(Unit)
            } catch (_: SourceException.InvalidCredentials) {
                mutableState.update { it.copy(busy = false, refused = true, password = "") }
            } catch (error: SourceException) {
                mutableState.update { it.copy(busy = false, error = error) }
            }
        }
    }

    fun dismissError() = mutableState.update { it.copy(error = null) }
}

package fr.guim.cinedoury.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.guim.cinedoury.core.data.ConnectionMode
import fr.guim.cinedoury.core.data.Profile
import fr.guim.cinedoury.core.data.SessionManager
import fr.guim.cinedoury.core.data.security.PinHasher
import fr.guim.cinedoury.core.source.SourceException
import fr.guim.cinedoury.core.ui.Events
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfilesState(
    val profiles: List<Profile> = emptyList(),
    val remote: Boolean = false,
    val loading: Boolean = true,
    /** The server could not give its profiles and none is known here. */
    val error: SourceException? = null,
)

sealed interface ProfilesEvent {
    data object Opened : ProfilesEvent

    /** The profile needs a sign-in ([username] null for « Autre compte »). */
    data class SignIn(
        val username: String?,
    ) : ProfilesEvent

    data class Pin(
        val profile: Profile,
    ) : ProfilesEvent
}

/**
 * The profile picker (`FONCTIONNALITES.md` § 1): public users of the server and profiles signed in
 * on this device. A signed-in profile opens directly (behind its PIN); another one signs in first.
 */
class ProfilesViewModel(
    private val sessions: SessionManager,
) : ViewModel() {
    private val mutableState =
        MutableStateFlow(ProfilesState(remote = sessions.connection.value?.mode == ConnectionMode.Remote))
    val state: StateFlow<ProfilesState> = mutableState.asStateFlow()
    val events = Events<ProfilesEvent>()

    init {
        viewModelScope.launch {
            sessions.profiles().collect { profiles -> mutableState.update { it.copy(profiles = profiles) } }
        }
        refresh()
    }

    fun refresh() {
        mutableState.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val error =
                try {
                    sessions.refreshProfiles()
                    null
                } catch (error: SourceException) {
                    error
                }
            // Known profiles still open without the server's list.
            mutableState.update { it.copy(loading = false, error = error?.takeIf { _ -> it.profiles.isEmpty() }) }
        }
    }

    fun choose(profile: Profile) {
        viewModelScope.launch {
            when {
                !profile.isSignedIn -> events.send(ProfilesEvent.SignIn(profile.user.name))
                profile.hasPin -> events.send(ProfilesEvent.Pin(profile))
                sessions.open(profile) -> events.send(ProfilesEvent.Opened)
                else -> events.send(ProfilesEvent.SignIn(profile.user.name))
            }
        }
    }

    fun otherAccount() = events.send(ProfilesEvent.SignIn(null))

    fun avatarUrl(profile: Profile): String? = sessions.userImageUrl(profile.user)
}

/** Why the PIN pad is shown. */
enum class PinPurpose {
    /** Opens a locked profile. */
    Unlock,

    /** Sets a new PIN: typed twice. */
    Create,

    /** Removes the PIN: the current one first. */
    Remove,
}

enum class PinMessage {
    Wrong,
    Mismatch,
}

data class PinState(
    val purpose: PinPurpose,
    val profile: Profile,
    val digits: String = "",
    /** [PinPurpose.Create]: the second entry, to confirm. */
    val confirming: Boolean = false,
    val message: PinMessage? = null,
)

/** The four-digit PIN of a profile, local to this device (`SPEC.md` § 4.7). */
class PinViewModel(
    private val sessions: SessionManager,
    profile: Profile,
    purpose: PinPurpose,
) : ViewModel() {
    private val mutableState = MutableStateFlow(PinState(purpose = purpose, profile = profile))
    val state: StateFlow<PinState> = mutableState.asStateFlow()

    /** The profile opened, or the PIN changed. */
    val done = Events<Unit>()
    private var first: String? = null

    fun digit(value: Char) {
        val current = mutableState.value
        if (current.digits.length >= PinHasher.PIN_DIGITS || value !in '0'..'9') return
        val digits = current.digits + value
        mutableState.update { it.copy(digits = digits, message = null) }
        if (digits.length == PinHasher.PIN_DIGITS) complete(digits)
    }

    fun erase() = mutableState.update { it.copy(digits = it.digits.dropLast(1), message = null) }

    private fun complete(pin: String) {
        val current = mutableState.value
        viewModelScope.launch {
            when (current.purpose) {
                PinPurpose.Unlock -> {
                    if (sessions.checkPin(current.profile, pin) && sessions.open(current.profile)) {
                        done.send(Unit)
                    } else {
                        retry(PinMessage.Wrong)
                    }
                }

                PinPurpose.Remove -> {
                    if (sessions.checkPin(current.profile, pin)) {
                        sessions.setPin(current.profile, null)
                        done.send(Unit)
                    } else {
                        retry(PinMessage.Wrong)
                    }
                }

                PinPurpose.Create -> {
                    create(pin)
                }
            }
        }
    }

    private suspend fun create(pin: String) {
        val chosen = first
        when {
            chosen == null -> {
                first = pin
                mutableState.update { it.copy(digits = "", confirming = true) }
            }

            chosen == pin -> {
                sessions.setPin(mutableState.value.profile, pin)
                done.send(Unit)
            }

            else -> {
                first = null
                mutableState.update { it.copy(digits = "", confirming = false, message = PinMessage.Mismatch) }
            }
        }
    }

    private fun retry(message: PinMessage) = mutableState.update { it.copy(digits = "", message = message) }
}

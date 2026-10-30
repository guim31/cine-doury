package fr.guim.cinedoury.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.guim.cinedoury.core.data.SessionManager
import fr.guim.cinedoury.core.source.SourceException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * « Connecter un appareil » (M1): the code typed so far, the server's answer.
 *
 * @param approved the code the server approved: the other device signs in.
 */
data class ConnectDeviceState(
    val code: String = "",
    val busy: Boolean = false,
    val error: SourceException? = null,
    val approved: String? = null,
) {
    val complete: Boolean get() = code.length == CODE_LENGTH

    companion object {
        /** Jellyfin's Quick Connect codes: six digits. */
        const val CODE_LENGTH = 6
    }
}

/**
 * Approves the Quick Connect code another device shows (`FONCTIONNALITES.md` § 1: « la TV affiche un
 * code à 6 chiffres, validé depuis le téléphone »): the TV signs in as the open profile, without a
 * password typed with the remote.
 */
class ConnectDeviceViewModel(
    private val sessions: SessionManager,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ConnectDeviceState())
    val state: StateFlow<ConnectDeviceState> = mutableState.asStateFlow()

    /** The code as typed: its digits only, six at most. */
    fun edit(text: String) {
        val code = text.filter(Char::isDigit).take(ConnectDeviceState.CODE_LENGTH)
        mutableState.update { it.copy(code = code, error = null, approved = null) }
    }

    fun submit() {
        val current = mutableState.value
        if (current.busy || !current.complete) return
        mutableState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                sessions.authorizeQuickConnect(current.code)
                mutableState.update { it.copy(busy = false, approved = current.code, code = "") }
            } catch (error: SourceException) {
                mutableState.update { it.copy(busy = false, error = error) }
            }
        }
    }

    /** Another code, after one was approved or refused. */
    fun again() = mutableState.update { ConnectDeviceState() }
}

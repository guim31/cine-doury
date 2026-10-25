package fr.guim.cinedoury.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.guim.cinedoury.core.data.SessionManager
import fr.guim.cinedoury.core.data.server.AddressInput
import fr.guim.cinedoury.core.source.DiscoveredServer
import fr.guim.cinedoury.core.source.ServerAddress
import fr.guim.cinedoury.core.source.ServerDiscovery
import fr.guim.cinedoury.core.source.SourceException
import fr.guim.cinedoury.core.ui.Events
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ServerSearchState(
    val servers: List<DiscoveredServer> = emptyList(),
    val searching: Boolean = true,
    /** The server being checked after a choice. */
    val checking: ServerAddress? = null,
    val error: SourceException? = null,
    val failedAddress: ServerAddress? = null,
)

/** The servers of the local network, listed as they answer (`FONCTIONNALITES.md` § 1). */
class ServerSearchViewModel(
    private val sessions: SessionManager,
    private val discovery: ServerDiscovery,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ServerSearchState())
    val state: StateFlow<ServerSearchState> = mutableState.asStateFlow()
    val connected = Events<Unit>()
    private var search: Job? = null

    init {
        search()
    }

    fun search() {
        search?.cancel()
        mutableState.update { it.copy(servers = emptyList(), searching = true) }
        search =
            viewModelScope.launch {
                discovery.discover().collect { server ->
                    mutableState.update { it.copy(servers = it.servers + server) }
                }
                mutableState.update { it.copy(searching = false) }
            }
    }

    fun choose(address: ServerAddress) {
        if (mutableState.value.checking != null) return
        mutableState.update { it.copy(checking = address, error = null) }
        viewModelScope.launch {
            try {
                sessions.addServer(address)
                connected.send(Unit)
                mutableState.update { it.copy(checking = null) }
            } catch (error: SourceException) {
                mutableState.update { it.copy(checking = null, error = error, failedAddress = address) }
            }
        }
    }

    fun retry() {
        mutableState.value.failedAddress?.let(::choose)
    }

    fun dismissError() {
        mutableState.update { it.copy(error = null, failedAddress = null) }
    }
}

/** What the address screen changes. */
enum class AddressMode {
    /** A new server (first launch). */
    Add,

    /** The local address of the server in use. */
    EditLocal,

    /** The remote address of the server in use; empty removes it. */
    EditRemote,
}

data class AddressState(
    val mode: AddressMode,
    val text: String = "",
    val invalid: Boolean = false,
    val busy: Boolean = false,
    val error: SourceException? = null,
)

/** An address typed with the remote, checked against the server before it is kept. */
class AddressViewModel(
    private val sessions: SessionManager,
    mode: AddressMode,
) : ViewModel() {
    private val mutableState = MutableStateFlow(AddressState(mode = mode, text = initialText(mode)))
    val state: StateFlow<AddressState> = mutableState.asStateFlow()
    val done = Events<Unit>()

    private fun initialText(mode: AddressMode): String {
        val server = sessions.connection.value?.server
        return when (mode) {
            AddressMode.Add -> ""
            AddressMode.EditLocal -> server?.localAddress?.url.orEmpty()
            AddressMode.EditRemote -> server?.remoteAddress?.url.orEmpty()
        }
    }

    fun edit(text: String) {
        mutableState.update { it.copy(text = text, invalid = false) }
    }

    fun submit() {
        val current = mutableState.value
        if (current.busy) return
        if (current.mode == AddressMode.EditRemote && current.text.isBlank()) {
            save(null)
            return
        }
        val address = AddressInput.parse(current.text)
        if (address == null) {
            mutableState.update { it.copy(invalid = true) }
        } else {
            save(address)
        }
    }

    private fun save(address: ServerAddress?) {
        mutableState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                val server = sessions.connection.value?.server
                when (mutableState.value.mode) {
                    AddressMode.Add -> {
                        sessions.addServer(checkNotNull(address))
                    }

                    AddressMode.EditLocal -> {
                        sessions.checkAddress(checkNotNull(address))
                        sessions.setAddresses(address, server?.remoteAddress)
                    }

                    AddressMode.EditRemote -> {
                        address?.let { sessions.checkAddress(it) }
                        sessions.setAddresses(checkNotNull(server).localAddress, address)
                    }
                }
                mutableState.update { it.copy(busy = false) }
                done.send(Unit)
            } catch (error: SourceException) {
                mutableState.update { it.copy(busy = false, error = error) }
            }
        }
    }

    fun dismissError() {
        mutableState.update { it.copy(error = null) }
    }
}

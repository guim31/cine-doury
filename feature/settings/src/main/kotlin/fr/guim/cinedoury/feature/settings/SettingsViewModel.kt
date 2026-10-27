package fr.guim.cinedoury.feature.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.guim.cinedoury.core.data.ActiveSession
import fr.guim.cinedoury.core.data.SessionManager
import fr.guim.cinedoury.core.data.StartupMode
import fr.guim.cinedoury.core.data.home.HomeRepository
import fr.guim.cinedoury.core.data.home.ProfileKey
import fr.guim.cinedoury.core.data.home.UserLibraries
import fr.guim.cinedoury.core.data.index.IndexScheduler
import fr.guim.cinedoury.core.data.index.IndexStatus
import fr.guim.cinedoury.core.data.index.LibraryIndex
import fr.guim.cinedoury.core.data.library.LibraryIconRepository
import fr.guim.cinedoury.core.data.prefs.AppPreferences
import fr.guim.cinedoury.core.data.prefs.Appearance
import fr.guim.cinedoury.core.data.prefs.Browsing
import fr.guim.cinedoury.core.data.prefs.NetworkMode
import fr.guim.cinedoury.core.model.DeviceCapabilities
import fr.guim.cinedoury.core.model.Library
import fr.guim.cinedoury.core.model.LibraryIcon
import fr.guim.cinedoury.core.model.LibraryId
import fr.guim.cinedoury.core.model.PlaybackPreferences
import fr.guim.cinedoury.core.model.PlayerSettings
import fr.guim.cinedoury.core.playback.PlayerSettingsStore
import fr.guim.cinedoury.core.source.SourceException
import fr.guim.cinedoury.core.ui.Events
import fr.guim.cinedoury.feature.update.UpdateRepository
import fr.guim.cinedoury.feature.update.UpdateState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the profile keeps on the server (`SPEC.md` § 4.17): read when the settings open. */
sealed interface ServerPreferences {
    data object Loading : ServerPreferences

    data class Ready(
        val preferences: PlaybackPreferences,
    ) : ServerPreferences

    data class Failed(
        val error: SourceException,
    ) : ServerPreferences
}

/** A short confirmation at the bottom of a page. */
enum class SettingsNotice {
    CacheCleared,
    IndexRebuilding,
    SaveFailed,
}

/**
 * Everything the pages of settings show (`FONCTIONNALITES.md` § 12).
 *
 * @param libraries the libraries of the profile, in its order, with [hidden] those left out of the rail.
 * @param chosen the libraries whose icon the user picked (the others follow their name).
 * @param index where the local index of the server stands.
 * @param capabilities what the device plays and passes to the amplifier, read when the page opens.
 * @param deviceName the name given in the settings; null for the one of the TV ([tvName]).
 */
@Immutable
data class SettingsState(
    val session: ActiveSession? = null,
    val startupMode: StartupMode = AppPreferences.DEFAULT_STARTUP,
    val libraries: List<Library> = emptyList(),
    val hidden: Set<LibraryId> = emptySet(),
    val chosen: Map<LibraryId, LibraryIcon> = emptyMap(),
    val index: IndexStatus = IndexStatus(),
    val player: PlayerSettings = PlayerSettings(),
    val capabilities: DeviceCapabilities? = null,
    val server: ServerPreferences = ServerPreferences.Loading,
    val appearance: Appearance = Appearance(),
    val browsing: Browsing = Browsing(),
    val network: NetworkMode = NetworkMode.Automatic,
    val deviceName: String? = null,
    val tvName: String = "",
    val update: UpdateState? = null,
    val notice: SettingsNotice? = null,
)

enum class SettingsEvent {
    /** The profile was signed out or left: the profile picker. */
    ProfileClosed,

    /** The server was forgotten: the welcome screen. */
    ServerForgotten,
}

/** What the pages of settings change (the view model; nothing in the screenshots). */
@Suppress("TooManyFunctions") // One function per kind of setting.
interface SettingsActions {
    fun updatePlayer(change: (PlayerSettings) -> PlayerSettings)

    fun updateServerPreferences(change: (PlaybackPreferences) -> PlaybackPreferences)

    fun placeLibrary(
        library: LibraryId,
        position: Int?,
    )

    fun updateAppearance(change: (Appearance) -> Appearance)

    fun setNetworkMode(mode: NetworkMode)

    fun updateBrowsing(change: (Browsing) -> Browsing)

    fun toggleStartupMode()

    fun setIcon(
        library: LibraryId,
        icon: LibraryIcon?,
    )

    fun rebuildIndex()

    fun clearCache(clearImages: () -> Unit)

    fun switchProfile()

    fun signOut()

    fun forgetServer()
}

/**
 * The settings of the profile and of the device (`FONCTIONNALITES.md` § 12, `SPEC.md` § 4.17): each
 * change is kept at once where it belongs — the device (DataStore), the profile on this device
 * (DataStore, per profile), the profile on the server (`/Users/Configuration`).
 */
@Suppress("TooManyFunctions", "LongParameterList") // One function per setting; the stores it joins.
class SettingsViewModel(
    private val sessions: SessionManager,
    private val home: HomeRepository,
    private val icons: LibraryIconRepository,
    index: LibraryIndex,
    private val scheduler: IndexScheduler,
    private val preferences: AppPreferences,
    private val player: PlayerSettingsStore,
    private val readCapabilities: () -> DeviceCapabilities,
    tvName: String,
    updates: UpdateRepository? = null,
) : ViewModel(),
    SettingsActions {
    private val active = sessions.active.value
    private val profile = active?.let { ProfileKey(it.profile.serverId, it.profile.user.id.value) }
    private val local = MutableStateFlow(Local())

    /** What only this screen knows: the capabilities, the server's answer, a notice. */
    private data class Local(
        val capabilities: DeviceCapabilities? = null,
        val server: ServerPreferences = ServerPreferences.Loading,
        val notice: SettingsNotice? = null,
    )

    val events = Events<SettingsEvent>()

    private val device: Flow<DeviceSettings> =
        combine(
            sessions.startupMode,
            preferences.appearance,
            preferences.networkMode,
            preferences.deviceName,
            player.settings,
        ) { startup, appearance, network, name, playerSettings ->
            DeviceSettings(startup, appearance, network, name, playerSettings)
        }

    private data class DeviceSettings(
        val startup: StartupMode,
        val appearance: Appearance,
        val network: NetworkMode,
        val deviceName: String?,
        val player: PlayerSettings,
    )

    private val libraries: Flow<LibrariesOfProfile> =
        if (profile == null) {
            flowOf(LibrariesOfProfile())
        } else {
            combine(
                home.libraries(profile),
                home.railHidden(profile),
                icons.chosen(profile.serverId),
                index.status(profile.serverId),
                preferences.browsing(profile),
            ) { libraries: UserLibraries, hidden, chosen, status, browsing ->
                LibrariesOfProfile(libraries.libraries, hidden, chosen, status, browsing)
            }
        }

    private data class LibrariesOfProfile(
        val libraries: List<Library> = emptyList(),
        val hidden: Set<LibraryId> = emptySet(),
        val chosen: Map<LibraryId, LibraryIcon> = emptyMap(),
        val index: IndexStatus = IndexStatus(),
        val browsing: Browsing = Browsing(),
    )

    val state: StateFlow<SettingsState> =
        combine(
            sessions.active,
            device,
            libraries,
            updates?.state ?: flowOf(null),
            local,
        ) { session, device, libraries, update, local ->
            SettingsState(
                session = session,
                startupMode = device.startup,
                libraries = libraries.libraries,
                hidden = libraries.hidden,
                chosen = libraries.chosen,
                index = libraries.index,
                player = device.player,
                capabilities = local.capabilities,
                server = local.server,
                appearance = device.appearance,
                browsing = libraries.browsing,
                network = device.network,
                deviceName = device.deviceName,
                tvName = tvName,
                update = update,
                notice = local.notice,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsState(sessions.active.value, tvName = tvName))

    init {
        // An amplifier may have been turned on since the last time: read again at each opening.
        viewModelScope.launch {
            val capabilities = withContext(Dispatchers.Default) { readCapabilities() }
            local.update { it.copy(capabilities = capabilities) }
        }
        viewModelScope.launch { loadServerPreferences() }
    }

    private suspend fun loadServerPreferences() {
        val source = active?.library ?: return
        val loaded =
            try {
                ServerPreferences.Ready(source.playbackPreferences())
            } catch (error: SourceException) {
                ServerPreferences.Failed(error)
            }
        local.update { it.copy(server = loaded) }
    }

    // The player (device and profile).

    override fun updatePlayer(change: (PlayerSettings) -> PlayerSettings) {
        viewModelScope.launch { player.update(change) }
    }

    // The profile, on the server.

    /** Keeps the languages or the mode of the subtitles on the server; the screen changes at once. */
    override fun updateServerPreferences(change: (PlaybackPreferences) -> PlaybackPreferences) {
        val current = (local.value.server as? ServerPreferences.Ready)?.preferences ?: return
        val source = active?.library ?: return
        val next = change(current)
        local.update { it.copy(server = ServerPreferences.Ready(next)) }
        viewModelScope.launch {
            try {
                source.setPlaybackPreferences(next)
            } catch (_: SourceException) {
                local.update { it.copy(server = ServerPreferences.Ready(current), notice = SettingsNotice.SaveFailed) }
            }
        }
    }

    /**
     * The place of [library] in the rail and the home: [position] from 0, or null to hide it from the
     * rail (its place on the server stays).
     */
    override fun placeLibrary(
        library: LibraryId,
        position: Int?,
    ) {
        val profile = profile ?: return
        val source = active?.library ?: return
        viewModelScope.launch {
            home.setShownInRail(profile, library, shown = position != null)
            if (position == null) return@launch
            val order =
                state.value.libraries
                    .map { it.id }
                    .filter { it != library }
                    .toMutableList()
            order.add(position.coerceIn(0, order.size), library)
            try {
                source.setLibraryOrder(order)
                home.refresh(profile, source)
            } catch (_: SourceException) {
                local.update { it.copy(notice = SettingsNotice.SaveFailed) }
            }
        }
    }

    // The device.

    override fun updateAppearance(change: (Appearance) -> Appearance) {
        viewModelScope.launch { preferences.setAppearance(change) }
    }

    override fun setNetworkMode(mode: NetworkMode) {
        viewModelScope.launch { preferences.setNetworkMode(mode) }
    }

    fun setDeviceName(name: String?) {
        viewModelScope.launch { preferences.setDeviceName(name) }
    }

    // The profile, on this device.

    override fun updateBrowsing(change: (Browsing) -> Browsing) {
        val profile = profile ?: return
        viewModelScope.launch { preferences.setBrowsing(profile, change) }
    }

    override fun toggleStartupMode() {
        val next =
            if (state.value.startupMode ==
                StartupMode.LastProfile
            ) {
                StartupMode.AlwaysAsk
            } else {
                StartupMode.LastProfile
            }
        viewModelScope.launch { sessions.setStartupMode(next) }
    }

    /** The icon of [library] in the rail; null goes back to the one its name suggests. */
    override fun setIcon(
        library: LibraryId,
        icon: LibraryIcon?,
    ) {
        val serverId = profile?.serverId ?: return
        viewModelScope.launch { icons.set(serverId, library, icon) }
    }

    /** Reads every film and episode again into the local index, in the background. */
    override fun rebuildIndex() {
        scheduler.rebuild()
        local.update { it.copy(notice = SettingsNotice.IndexRebuilding) }
    }

    /** « Vider le cache »: [clearImages] (the app's), then the index read again. */
    override fun clearCache(clearImages: () -> Unit) {
        clearImages()
        scheduler.rebuild()
        local.update { it.copy(notice = SettingsNotice.CacheCleared) }
    }

    fun dismissNotice() = local.update { it.copy(notice = null) }

    override fun switchProfile() {
        sessions.leaveProfile()
        events.send(SettingsEvent.ProfileClosed)
    }

    override fun signOut() {
        val profile = state.value.session?.profile ?: return
        viewModelScope.launch {
            sessions.signOut(profile)
            events.send(SettingsEvent.ProfileClosed)
        }
    }

    override fun forgetServer() {
        viewModelScope.launch {
            sessions.forgetServer()
            events.send(SettingsEvent.ServerForgotten)
        }
    }
}

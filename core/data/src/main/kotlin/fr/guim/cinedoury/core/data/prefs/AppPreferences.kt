package fr.guim.cinedoury.core.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import fr.guim.cinedoury.core.data.StartupMode
import fr.guim.cinedoury.core.data.home.ProfileKey
import fr.guim.cinedoury.core.data.library.LibraryView
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant

/** The colours of the app (`UI.md` § 4): dark, or OLED black. */
enum class ThemeChoice { Dark, OledBlack }

/** The size of the text, against the scale of `UI.md` § 5 (`FONCTIONNALITES.md` § 13, accessibility). */
enum class TextSize(
    val scale: Float,
) {
    Normal(1f),
    Large(1.15f),
    Larger(1.3f),
}

/**
 * « Taille de l'interface » (essai réel 3): how much a screen shows, by the width it is laid out on
 * (`LayoutSize` of the design system, `SPEC.md` § 4.21). Very compact by default since essai réel 4 (a
 * size already chosen is kept). « Grande » (`Large`) was dropped on 28/10: kept on a device, it reads
 * as [Normal].
 */
enum class InterfaceSize { VeryCompact, Compact, Normal }

/** « Apparence » of this device (`SPEC.md` § 4.17). */
data class Appearance(
    val theme: ThemeChoice = ThemeChoice.Dark,
    val reducedMotion: Boolean = false,
    val textSize: TextSize = TextSize.Normal,
    val interfaceSize: InterfaceSize = InterfaceSize.VeryCompact,
)

/** Which address of the server the app tries at start (`SPEC.md` § 4.7 and § 4.17). */
enum class NetworkMode {
    /** The local address, then the remote one: the automatic switch. */
    Automatic,

    /** The local address only: the TV never goes out through the Internet. */
    LocalOnly,

    /** The remote address only (the TV is away from home). */
    RemoteOnly,
}

/**
 * « Accueil et navigation » of a profile on this device (`SPEC.md` § 4.17).
 *
 * @param featuredBanner the banner at the top of the home.
 * @param tonightMosaic the mosaic « Ce soir » at the top of the home, instead of the banner (essai réel 5).
 * @param posterTitles the title and year under every poster, not only the focused one.
 * @param unwatchedMarks the « non vu » triangle and the count of unwatched episodes.
 * @param defaultView the view of a library where the profile chose none.
 */
data class Browsing(
    val featuredBanner: Boolean = true,
    val tonightMosaic: Boolean = true,
    val posterTitles: Boolean = false,
    val unwatchedMarks: Boolean = true,
    val defaultView: LibraryView = LibraryView.Posters,
)

/** What the app remembers of the updates (`SPEC.md` § 4.18). */
data class UpdateMemory(
    val lastCheck: Instant? = null,
    val ignoredVersion: String? = null,
)

/**
 * Device preferences (DataStore `settings`): the last server and profile, the startup mode (J1);
 * the appearance, the network, the name of the device, the updates and, per profile, the browsing
 * (J7).
 */
@Suppress("TooManyFunctions") // A getter and a setter per setting.
class AppPreferences(
    private val store: DataStore<Preferences>,
) {
    val startupMode: Flow<StartupMode> =
        store.data.map { prefs ->
            prefs[STARTUP_MODE]?.let { name -> StartupMode.entries.firstOrNull { it.name == name } } ?: DEFAULT_STARTUP
        }

    suspend fun setStartupMode(mode: StartupMode) {
        store.edit { it[STARTUP_MODE] = mode.name }
    }

    suspend fun lastServerId(): String? = store.data.first()[LAST_SERVER]

    suspend fun lastUserId(): String? = store.data.first()[LAST_USER]

    suspend fun setLastServer(id: String?) {
        store.edit { prefs ->
            if (id == null) prefs.remove(LAST_SERVER) else prefs[LAST_SERVER] = id
            prefs.remove(LAST_USER)
        }
    }

    suspend fun setLastUser(id: String?) {
        store.edit { prefs -> if (id == null) prefs.remove(LAST_USER) else prefs[LAST_USER] = id }
    }

    val appearance: Flow<Appearance> =
        store.data
            .map { prefs ->
                Appearance(
                    theme = enumOf(prefs[THEME], ThemeChoice.Dark),
                    reducedMotion = prefs[REDUCED_MOTION] ?: false,
                    textSize = enumOf(prefs[TEXT_SIZE], TextSize.Normal),
                    interfaceSize = interfaceSizeOf(prefs[INTERFACE_SIZE]),
                )
            }.distinctUntilChanged()

    suspend fun setAppearance(change: (Appearance) -> Appearance) {
        store.edit { prefs ->
            val next =
                change(
                    Appearance(
                        enumOf(prefs[THEME], ThemeChoice.Dark),
                        prefs[REDUCED_MOTION] ?: false,
                        enumOf(prefs[TEXT_SIZE], TextSize.Normal),
                        interfaceSizeOf(prefs[INTERFACE_SIZE]),
                    ),
                )
            prefs[THEME] = next.theme.name
            prefs[REDUCED_MOTION] = next.reducedMotion
            prefs[TEXT_SIZE] = next.textSize.name
            prefs[INTERFACE_SIZE] = next.interfaceSize.name
        }
    }

    val networkMode: Flow<NetworkMode> = store.data.map { enumOf(it[NETWORK], NetworkMode.Automatic) }

    suspend fun setNetworkMode(mode: NetworkMode) {
        store.edit { it[NETWORK] = mode.name }
    }

    /** The name the user gave this device for the server; null for the one of the TV. */
    val deviceName: Flow<String?> = store.data.map { it[DEVICE_NAME] }

    suspend fun setDeviceName(name: String?) {
        store.edit { prefs ->
            val trimmed = name?.trim()?.takeIf { it.isNotEmpty() }
            if (trimmed == null) prefs.remove(DEVICE_NAME) else prefs[DEVICE_NAME] = trimmed
        }
    }

    fun browsing(profile: ProfileKey): Flow<Browsing> =
        store.data.map { it.browsing(BrowsingKeys(profile)) }.distinctUntilChanged()

    suspend fun setBrowsing(
        profile: ProfileKey,
        change: (Browsing) -> Browsing,
    ) {
        val keys = BrowsingKeys(profile)
        store.edit { prefs ->
            val next = change(prefs.browsing(keys))
            prefs[keys.banner] = next.featuredBanner
            prefs[keys.tonight] = next.tonightMosaic
            prefs[keys.titles] = next.posterTitles
            prefs[keys.unwatched] = next.unwatchedMarks
            prefs[keys.view] = next.defaultView.name
        }
    }

    val updates: Flow<UpdateMemory> =
        store.data.map { prefs ->
            UpdateMemory(prefs[LAST_UPDATE_CHECK]?.let(Instant::ofEpochMilli), prefs[IGNORED_VERSION])
        }

    suspend fun setLastUpdateCheck(at: Instant) {
        store.edit { it[LAST_UPDATE_CHECK] = at.toEpochMilli() }
    }

    suspend fun setIgnoredVersion(version: String?) {
        store.edit { prefs -> if (version == null) prefs.remove(IGNORED_VERSION) else prefs[IGNORED_VERSION] = version }
    }

    private class BrowsingKeys(
        profile: ProfileKey,
    ) {
        private val suffix = "@${profile.serverId}/${profile.userId}"
        val banner = booleanPreferencesKey("featured_banner$suffix")
        val tonight = booleanPreferencesKey("tonight_mosaic$suffix")
        val titles = booleanPreferencesKey("poster_titles$suffix")
        val unwatched = booleanPreferencesKey("unwatched_marks$suffix")
        val view = stringPreferencesKey("default_view$suffix")
    }

    private fun Preferences.browsing(keys: BrowsingKeys): Browsing {
        val defaults = Browsing()
        return Browsing(
            featuredBanner = this[keys.banner] ?: defaults.featuredBanner,
            tonightMosaic = this[keys.tonight] ?: defaults.tonightMosaic,
            posterTitles = this[keys.titles] ?: defaults.posterTitles,
            unwatchedMarks = this[keys.unwatched] ?: defaults.unwatchedMarks,
            defaultView = enumOf(this[keys.view], defaults.defaultView),
        )
    }

    /** The size kept on this device; « Grande », dropped, becomes « Normale ». */
    private fun interfaceSizeOf(name: String?): InterfaceSize =
        if (name == DROPPED_LARGE) InterfaceSize.Normal else enumOf(name, InterfaceSize.VeryCompact)

    private inline fun <reified T : Enum<T>> enumOf(
        name: String?,
        default: T,
    ): T = enumValues<T>().firstOrNull { it.name == name } ?: default

    companion object {
        const val FILE = "settings"

        /** A shared TV: the profile picker by default. */
        val DEFAULT_STARTUP = StartupMode.AlwaysAsk
        private val STARTUP_MODE = stringPreferencesKey("startup_mode")
        private val LAST_SERVER = stringPreferencesKey("last_server")
        private val LAST_USER = stringPreferencesKey("last_user")
        private val THEME = stringPreferencesKey("theme")
        private val REDUCED_MOTION = booleanPreferencesKey("reduced_motion")
        private val TEXT_SIZE = stringPreferencesKey("text_size")
        private val INTERFACE_SIZE = stringPreferencesKey("interface_size")
        private const val DROPPED_LARGE = "Large"
        private val NETWORK = stringPreferencesKey("network_mode")
        private val DEVICE_NAME = stringPreferencesKey("device_name")
        private val LAST_UPDATE_CHECK = longPreferencesKey("last_update_check")
        private val IGNORED_VERSION = stringPreferencesKey("ignored_version")
    }
}

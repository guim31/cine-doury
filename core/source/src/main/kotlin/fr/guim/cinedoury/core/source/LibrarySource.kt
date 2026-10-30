package fr.guim.cinedoury.core.source

import fr.guim.cinedoury.core.model.FilterOptions
import fr.guim.cinedoury.core.model.HomeExclusions
import fr.guim.cinedoury.core.model.ItemId
import fr.guim.cinedoury.core.model.ItemKind
import fr.guim.cinedoury.core.model.ItemQuery
import fr.guim.cinedoury.core.model.Library
import fr.guim.cinedoury.core.model.LibraryId
import fr.guim.cinedoury.core.model.MediaItem
import fr.guim.cinedoury.core.model.Page
import fr.guim.cinedoury.core.model.PageRequest
import fr.guim.cinedoury.core.model.PlaybackPreferences
import fr.guim.cinedoury.core.model.UserData
import java.time.Instant

/** The libraries of a signed-in user. Every failure is a [SourceException]. */
@Suppress("TooManyFunctions") // One function per call the screens make.
interface LibrarySource {
    val session: Session

    /** Playback of the items of this session (J4, `SPEC.md` § 4.11). */
    val playback: PlaybackSource

    /** Management of the server, for an administrator (J6, `SPEC.md` § 4.14). */
    val manage: ManageSource

    /**
     * Every library of the user, hidden ones included, in the order the user chose on the server
     * (Jellyfin: `OrderedViews`).
     */
    suspend fun libraries(): List<Library>

    /** The libraries the user left out of the home, or out of « Ajouts récents ». */
    suspend fun homeExclusions(): HomeExclusions

    /**
     * The top-level items of a library, sorted by sort title. Films stay individual items even
     * when they belong to a collection.
     */
    suspend fun items(library: LibraryId, page: PageRequest = PageRequest()): Page<MediaItem> =
        items(ItemQuery(library), page)

    /** One page of the items that match [query], with the total count. */
    suspend fun items(query: ItemQuery, page: PageRequest): Page<MediaItem>

    /** How many items match [query]. */
    suspend fun count(query: ItemQuery): Int

    /** One item with everything the detail screen needs, media sources included. */
    suspend fun item(id: ItemId): MediaItem

    /** Films and episodes started and not finished, the last played first (« Reprendre »). */
    suspend fun resume(library: LibraryId?, limit: Int): List<MediaItem>

    /**
     * The next episode of each series in progress (« À suivre »), the series watched last first.
     * Episodes already started are in [resume] and not here. [since]: only the series watched
     * since then; null for all.
     */
    suspend fun nextUp(library: LibraryId?, since: Instant?, limit: Int): List<MediaItem>

    /** The latest additions to [library], newest first; new episodes are grouped by series. */
    suspend fun latest(library: LibraryId, limit: Int): List<MediaItem>

    /** The values the filters of [library] can take, for items of [kinds]. */
    suspend fun filterOptions(library: LibraryId, kinds: Set<ItemKind>): FilterOptions

    // Details (J3, `SPEC.md` § 4.10).

    /** The ids of every item that matches [query], in its order: for the filters of the local index. */
    suspend fun itemIds(query: ItemQuery): List<ItemId>

    /** The seasons of a series, in order, with what the user watched. */
    suspend fun seasons(series: ItemId): List<MediaItem>

    /** Every episode of a series, every season, in order, with what the user watched. */
    suspend fun episodes(series: ItemId): List<MediaItem>

    /** Items of the libraries that look like [item] (same genres, people), the closest first. */
    suspend fun similar(item: ItemId, limit: Int): List<MediaItem>

    /** The collections (sagas) that hold [item]. */
    suspend fun collectionsOf(item: ItemId): List<MediaItem>

    /** The items of a collection, in release order. */
    suspend fun collectionItems(collection: ItemId): List<MediaItem>

    /** The films and series of the libraries where [person] appears, the newest first. */
    suspend fun filmography(person: ItemId): List<MediaItem>

    /** The trailers stored with [item] on the server. */
    suspend fun localTrailers(item: ItemId): List<MediaItem>

    /** Marks [item] watched or not; for a series or a season, every episode. Returns the new data. */
    suspend fun setPlayed(item: ItemId, played: Boolean): UserData

    suspend fun setFavorite(item: ItemId, favorite: Boolean): UserData

    /**
     * Forgets where [item] was left, without marking it watched: it leaves « Reprendre » (essai réel
     * 5, « Retirer de Continuer »). Returns the new data.
     */
    suspend fun clearProgress(item: ItemId): UserData

    /** How the profile chooses its audio and subtitle tracks. */
    suspend fun playbackPreferences(): PlaybackPreferences

    // Settings (J7, `SPEC.md` § 4.17): what follows the person, on the server.

    /**
     * Keeps [preferences] on the server (Jellyfin: `POST /Users/Configuration`, the rest of the
     * configuration untouched): every app of the profile chooses its tracks by them.
     */
    suspend fun setPlaybackPreferences(preferences: PlaybackPreferences)

    /**
     * The order of the libraries of the profile (Jellyfin: `OrderedViews`), as [libraries] gives
     * them: the rail and the rows of the home follow it. Libraries left out go after, in their order.
     */
    suspend fun setLibraryOrder(order: List<LibraryId>)

    /**
     * Sends a diagnostic report (plain text) to the logs of the server (Jellyfin:
     * `POST /ClientLog/Document`), where the maintainer reads it; returns the name of the file made.
     */
    suspend fun sendDiagnostic(report: String): String

    /**
     * Approves the Quick Connect [code] another device shows (« Connecter un appareil », M1): that
     * device signs in as the user of this session. Jellyfin: `POST /QuickConnect/Authorize`. Throws
     * [SourceException.QuickConnectExpired] for a code the server does not know (mistyped, or older
     * than 10 minutes), [SourceException.QuickConnectDisabled] when Quick Connect is off.
     */
    suspend fun authorizeQuickConnect(code: String)

    /**
     * Follows the server to [address] (M1, `SPEC.md` § 4.26): the phone went from the Wi-Fi of the
     * house to the mobile network, or back. Every call from then on, the screens already open
     * included, goes there; [session] keeps the address of the sign-in.
     */
    fun moveTo(address: ServerAddress) {}

    // The local index (J3, `SPEC.md` § 4.10).

    /**
     * One page of the films and episodes of [library] with their media sources and tracks, for the
     * local index; [changedSince] keeps those saved by the server since then (null for all).
     */
    suspend fun technicalItems(library: LibraryId, changedSince: Instant?, page: PageRequest): Page<MediaItem>

    // Search (J5, `SPEC.md` § 4.12).

    /**
     * Up to [limit] items of [kinds] whose name the server matches with [term], the best first.
     * Jellyfin compares without accents nor case, from the start of each word; it forgives no typo
     * (the local title index does).
     */
    suspend fun search(
        term: String,
        kinds: Set<ItemKind>,
        limit: Int,
    ): List<MediaItem>

    /**
     * One page of the films and series of [library] with their titles (title, original, sort) and
     * the images of their cards, for the local title index; [changedSince] keeps those saved by the
     * server since then (null for all).
     */
    suspend fun titles(
        library: LibraryId,
        changedSince: Instant?,
        page: PageRequest,
    ): Page<MediaItem>
}

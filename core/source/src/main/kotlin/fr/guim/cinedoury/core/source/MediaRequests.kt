package fr.guim.cinedoury.core.source

import fr.guim.cinedoury.core.model.RequestCandidate
import fr.guim.cinedoury.core.model.RequestSeason
import fr.guim.cinedoury.core.model.RequestStatus

/**
 * Opens a request server: Seerr (or Jellyseerr, Overseerr), optional, configured in the settings
 * (`SPEC.md` § 4.12). The screens only know this interface.
 */
interface RequestConnector {
    /**
     * The server at [address] (« http://192.0.2.10:5055 »), with its API key. [mediaUserId] is the
     * Jellyfin user of the profile: requests are made in their name when the request server knows
     * them, else in the name of the key's owner.
     */
    fun open(
        address: String,
        apiKey: String,
        mediaUserId: String?,
    ): MediaRequests
}

/** A request server. Every failure is a [SourceException]. */
interface MediaRequests {
    /**
     * Checks the address and the key: the version of the server. [SourceException.NotAServer] when
     * something else answers, [SourceException.Unauthorized] for a wrong key.
     */
    suspend fun check(): String

    /** Films and series matching [query], in the server's order; [language]: ISO 639-1 (« fr »). */
    suspend fun search(
        query: String,
        language: String,
    ): List<RequestCandidate>

    /** The seasons of a series, specials aside, with where each stands. */
    suspend fun seasons(
        tmdbId: Int,
        language: String,
    ): List<RequestSeason>

    /** Asks for [candidate]; for a series, [seasons] (their numbers). Returns its new status. */
    suspend fun request(
        candidate: RequestCandidate,
        seasons: List<Int> = emptyList(),
    ): RequestStatus
}

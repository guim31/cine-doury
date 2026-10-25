package fr.guim.cinedoury.core.model

/** What a request server (Seerr) can be asked for. */
enum class RequestKind {
    Movie,
    Series,
}

/**
 * Where a title stands on the request server (Seerr's `MediaStatus`): nothing yet, asked for (pending
 * approval or being downloaded), partly or wholly available.
 */
enum class RequestStatus {
    None,
    Requested,
    PartlyAvailable,
    Available,
    ;

    /** Whether « Demander » still makes sense: nothing asked for yet, or a series not complete. */
    val requestable: Boolean get() = this == None || this == PartlyAvailable
}

/**
 * A title the request server found (`SPEC.md` § 4.12), by its TMDb id.
 *
 * @param posterUrl its poster through the request server, which serves TMDb's images itself; null without one.
 * @param libraryItemId the item of the media server once the title is there (Seerr: `jellyfinMediaId`).
 */
data class RequestCandidate(
    val tmdbId: Int,
    val kind: RequestKind,
    val title: String,
    val year: Int? = null,
    val overview: String? = null,
    val posterUrl: String? = null,
    val status: RequestStatus = RequestStatus.None,
    val libraryItemId: String? = null,
) {
    /** A stable key across both kinds (a film and a series may share a TMDb id). */
    val key: String get() = "${kind.name}:$tmdbId"
}

/** A season of a series on the request server, for the choice of the seasons to ask for. */
data class RequestSeason(
    val number: Int,
    val name: String?,
    val episodes: Int,
    val status: RequestStatus = RequestStatus.None,
)

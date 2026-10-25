package fr.guim.cinedoury.tv

import fr.guim.cinedoury.core.data.search.SearchSuggestions
import fr.guim.cinedoury.core.model.EpisodeInfo
import fr.guim.cinedoury.core.model.ImageType
import fr.guim.cinedoury.core.model.ItemId
import fr.guim.cinedoury.core.model.ItemImages
import fr.guim.cinedoury.core.model.ItemKind
import fr.guim.cinedoury.core.model.MediaItem
import fr.guim.cinedoury.core.model.RequestCandidate
import fr.guim.cinedoury.core.model.RequestKind
import fr.guim.cinedoury.core.model.RequestSeason
import fr.guim.cinedoury.core.model.RequestStatus
import fr.guim.cinedoury.core.model.SearchGroup
import fr.guim.cinedoury.core.model.SearchRow
import fr.guim.cinedoury.core.model.UserData
import fr.guim.cinedoury.feature.search.RequestDialog
import fr.guim.cinedoury.feature.search.RequestsState
import fr.guim.cinedoury.feature.search.SearchState
import fr.guim.cinedoury.feature.search.SeerrSettingsState
import kotlin.time.Duration

/**
 * Sample content of the J5 screenshots: titles of the test server (`ci/jellyfin`) with the artwork
 * its bootstrap generates ([SampleArt]), and what a fake Seerr would find.
 */
internal object J5Samples {
    private val unwatched =
        UserData(played = false, playbackPosition = Duration.ZERO, playCount = 0, isFavorite = false)

    private fun title(
        title: String,
        year: Int,
        kind: ItemKind = ItemKind.Movie,
    ) = MediaItem(
        id = ItemId("${kind.name}-$title"),
        kind = kind,
        title = title,
        year = year,
        images =
            ItemImages(
                primary = SampleArt.image(title, ImageType.Primary),
                backdrops = listOf(SampleArt.image("$title ", ImageType.Backdrop)),
            ),
        userData = unwatched,
    )

    private fun episode(
        title: String,
        season: Int,
        number: Int,
    ) = MediaItem(
        id = ItemId("episode-$title"),
        kind = ItemKind.Episode,
        title = title,
        images = ItemImages(primary = SampleArt.image("Les Marées $title", ImageType.Thumb)),
        episode = EpisodeInfo(ItemId("Series-Les Marées"), "Les Marées", ItemId("s$season"), season, number),
        userData = unwatched,
    )

    private fun person(name: String) =
        MediaItem(
            id = ItemId("person-$name"),
            kind = ItemKind.Person,
            title = name,
            images = ItemImages(primary = SampleArt.photo(name)),
        )

    private val phare = J3Samples.phare
    private val retour = J3Samples.retour
    private val mareeBasse = title("Marée basse", 2001)
    private val marees = title("Les Marées", 2015, ItemKind.Series)
    private val eclipse = title("L'Éclipse", 1972)
    private val echappee = title("Échappée belle", 2016)
    private val zephyr = title("Zéphyr", 2022)
    private val frequences = title("Fréquences", 2023)
    private val saga = title("Le Phare : la saga", 2001, ItemKind.Collection)

    /** Nothing typed: recent searches, the latest additions, the genres. */
    val idle =
        SearchState(
            rail = J3Samples.rail,
            recent = listOf("Le Phare", "marées", "Camille Martin", "zephyr"),
            suggestions =
                SearchSuggestions(
                    recentlyAdded = listOf(frequences, zephyr, echappee, phare, retour, eclipse),
                    genres =
                        listOf(
                            "Animation",
                            "Aventure",
                            "Comédie",
                            "Documentaire",
                            "Drame",
                            "Famille",
                            "Fantastique",
                            "Policier",
                            "Romance",
                            "Science-fiction",
                            "Thriller",
                        ),
                ),
        )

    /** « mar »: one row per group. */
    val partial =
        idle.copy(
            query = "mar",
            rows =
                listOf(
                    SearchRow(SearchGroup.Movies, listOf(mareeBasse, title("Marées d'équinoxe", 1998))),
                    SearchRow(SearchGroup.Series, listOf(marees)),
                    SearchRow(
                        SearchGroup.Episodes,
                        listOf(episode("Grande marée", 1, 1), episode("Mortes-eaux", 1, 2)),
                    ),
                    SearchRow(SearchGroup.People, listOf(person("Marie Duval"))),
                ),
        )

    /** « eclipce »: the typo forgiven by the local index. */
    val typo =
        idle.copy(
            query = "eclipce",
            rows = listOf(SearchRow(SearchGroup.Movies, listOf(eclipse))),
        )

    /** « camille »: a person, found by the server. */
    val person =
        idle.copy(
            query = "camille",
            rows =
                listOf(
                    SearchRow(SearchGroup.People, listOf(person("Camille Martin"))),
                    SearchRow(SearchGroup.Movies, listOf(phare)),
                ),
        )

    private fun candidate(
        id: Int,
        title: String,
        year: Int,
        status: RequestStatus,
        kind: RequestKind = RequestKind.Movie,
    ) = RequestCandidate(
        tmdbId = id,
        kind = kind,
        title = title,
        year = year,
        posterUrl = SampleArt.sources(SampleArt.image(title, ImageType.Primary)).url(POSTER_WIDTH, POSTER_HEIGHT),
        status = status,
    )

    private val fellowship = candidate(120, "La Communauté de l'anneau", 2001, RequestStatus.None)
    private val rings =
        candidate(84773, "Les Anneaux de pouvoir", 2022, RequestStatus.PartlyAvailable, RequestKind.Series)

    /** « anneaux »: nothing in the library, Seerr has the rest. */
    val requests =
        idle.copy(
            query = "anneaux",
            rows = listOf(SearchRow(SearchGroup.Collections, listOf(saga.copy(title = "Les Anneaux : la saga")))),
            requests =
                RequestsState(
                    listOf(
                        fellowship,
                        candidate(121, "Les Deux Tours", 2002, RequestStatus.Requested),
                        rings,
                        candidate(122, "Le Retour du roi", 2003, RequestStatus.Available),
                    ),
                ),
        )

    val confirm = requests.copy(dialog = RequestDialog.Confirm(fellowship))

    val seasons =
        requests.copy(
            dialog =
                RequestDialog.Seasons(
                    rings,
                    seasons =
                        listOf(
                            RequestSeason(1, "Saison 1", 8, RequestStatus.Available),
                            RequestSeason(2, "Saison 2", 8, RequestStatus.Requested),
                            RequestSeason(3, "Saison 3", 6),
                        ),
                    chosen = setOf(3),
                ),
        )

    val seerrSettings =
        SeerrSettingsState(
            address = "http://192.0.2.10:5055",
            apiKey = "0123456789abcdef0123456789abcdef",
            configured = true,
            version = "2.7.3",
        )

    private const val POSTER_WIDTH = 240
    private const val POSTER_HEIGHT = 360
}

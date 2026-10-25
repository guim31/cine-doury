package fr.guim.cinedoury.tv

import fr.guim.cinedoury.core.model.DeletionPreview
import fr.guim.cinedoury.core.model.IdentifyMatch
import fr.guim.cinedoury.core.model.ImageType
import fr.guim.cinedoury.core.model.ItemId
import fr.guim.cinedoury.core.model.ItemKind
import fr.guim.cinedoury.core.model.LibraryProblems
import fr.guim.cinedoury.core.model.ManageRights
import fr.guim.cinedoury.core.model.MediaItem
import fr.guim.cinedoury.core.model.RemoteImage
import fr.guim.cinedoury.core.model.ScanStatus
import fr.guim.cinedoury.feature.details.DetailsState
import fr.guim.cinedoury.feature.manage.Deletion
import fr.guim.cinedoury.feature.manage.IdentifyState
import fr.guim.cinedoury.feature.manage.ImagesState
import fr.guim.cinedoury.feature.manage.ProblemsState
import java.net.URLEncoder
import java.time.Instant

/**
 * Sample content of the J6 screenshots: the films of J3 seen by an administrator, a folder name
 * misspelt as the maintainer's server has them, the answers of the providers drawn as the test
 * server's artwork ([SampleArt]: never a real poster).
 */
internal object J6Samples {
    val admin = ManageRights(administrator = true, deleteEverywhere = true)

    /** An address [SampleArt] draws: the providers' pictures are URLs, not images of the server. */
    private fun art(
        title: String,
        type: ImageType,
        width: Int,
        height: Int,
    ) = "sample:${type.name}:${URLEncoder.encode(title, Charsets.UTF_8)}:$width:$height"

    private val phare =
        J3Samples.phare.copy(
            path = "/media/films/Le Phare (2001)",
            providerIds = mapOf("Tmdb" to "424242", "Imdb" to "tt0424242"),
            dateAdded = Instant.parse("2026-08-08T18:00:00Z"),
            canDelete = true,
        )

    val film = J3Samples.film.copy(item = phare, rights = admin)

    val series =
        J3Samples.series.copy(
            item =
                J3Samples.marees.copy(
                    path = "/media/series/Les Marées (2015)",
                    providerIds = mapOf("Tvdb" to "300100", "Tmdb" to "61000"),
                    canDelete = true,
                ),
            rights = admin,
        )

    val child = J3Samples.film.copy(item = phare.copy(canDelete = false))

    val filmDeletion =
        Deletion(
            DeletionPreview(
                item = phare,
                paths = phare.mediaSources.mapNotNull { it.path },
                sizeBytes = phare.mediaSources.mapNotNull { it.sizeBytes }.sum(),
            ),
        )

    private val seriesPreview =
        DeletionPreview(
            item = series.item,
            paths = listOfNotNull(series.item.path),
            sizeBytes = 38_400_000_000,
            episodes = (1..6).map { ItemId("ep-$it") },
        )

    val seriesDeletion = Deletion(seriesPreview)
    val seriesSecondDeletion = Deletion(seriesPreview, second = true)

    fun withDeletion(
        state: DetailsState,
        deletion: Deletion,
    ) = state.copy(deletion = deletion)

    /** « Rox ete Rouky »: a folder name misspelt, as the maintainer's server has a dozen of them. */
    val misnamed =
        MediaItem(
            id = ItemId("rox"),
            kind = ItemKind.Movie,
            title = "Le Phrae.2001.1080p",
            year = 2001,
            path = "/media/films/Le Phrae.2001.1080p/Le.Phrae.2001.1080p.mkv",
            canDelete = true,
        )

    private fun match(
        title: String,
        year: Int,
        overview: String,
    ) = IdentifyMatch(
        title = title,
        year = year,
        overview = overview,
        imageUrl = art(title, ImageType.Primary, 300, 450),
        provider = "TheMovieDb",
        providerIds = mapOf("Tmdb" to "${title.hashCode()}"),
    )

    val identify =
        IdentifyState(
            item = misnamed,
            title = "Le Phrae",
            year = "2001",
            providers = listOf("TheMovieDb", "AniDB"),
            provider = "TheMovieDb",
            results =
                listOf(
                    match("Le Phare", 2001, "Un gardien de phare consigne chaque nuit ce que la mer lui rapporte."),
                    match("Retour au phare", 2004, "Des années plus tard, la lumière se rallume."),
                    match("Le Phare de la Pointe", 1998, "Un documentaire sur les derniers gardiens de la côte."),
                    match("Les Phares", 2011, "Une série de portraits, du Finistère à la Manche."),
                    match("Phare Ouest", 2019, "Une comédie au bout du monde."),
                ),
        )

    val images =
        ImagesState(
            item = phare,
            providers = listOf("TheMovieDb", "Fanart"),
            images =
                listOf(
                    Triple("Le Phare", "fr", 2000),
                    Triple("Le Phare affiche 2", "fr", 1400),
                    Triple("Le Phare affiche 3", null, 2000),
                    Triple("Le Phare affiche 4", "fr", 1000),
                    Triple("Le Phare affiche 5", "fr", 2000),
                    Triple("Le Phare affiche 6", null, 680),
                    Triple("Le Phare affiche 7", "fr", 2000),
                    Triple("Le Phare affiche 8", "fr", 1500),
                ).mapIndexed { index, (title, language, width) ->
                    RemoteImage(
                        url = art(title, ImageType.Primary, width, width * 3 / 2),
                        thumbnailUrl = null,
                        type = ImageType.Primary,
                        width = width,
                        height = width * 3 / 2,
                        language = language,
                        provider = if (index % 3 == 2) "Fanart" else "TheMovieDb",
                    )
                },
        )

    private val unidentified =
        listOf(
            misnamed,
            MediaItem(
                ItemId("rox2"),
                ItemKind.Movie,
                "Rox ete Rouky",
                path = "/media/films/Rox ete Rouky/Rox ete Rouky.mkv",
            ),
            MediaItem(
                ItemId("cerf"),
                ItemKind.Movie,
                "Le Cerf volnt (1958)",
                year = 1958,
                path = "/media/films/Le Cerf volnt (1958)/Le Cerf volnt (1958).mkv",
            ),
            MediaItem(ItemId("brume"), ItemKind.Series, "Brume.S01.MULTi", path = "/media/series/Brume.S01.MULTi"),
        )

    val problems =
        ProblemsState(
            library = J2Samples.films.id,
            problems = LibraryProblems(unidentified = unidentified, withoutPoster = unidentified.take(2)),
            rights = admin,
        )

    val problemsChosen = problems.copy(chosen = misnamed)

    val library =
        J2Samples.library().copy(rights = admin, scan = ScanStatus(running = true, progress = 0.42f))

    val selection =
        J2Samples.library().let { state ->
            val ticked = listOf(0, 2, 3).mapNotNull { state.items[it]?.id }.toSet()
            state.copy(rights = admin, selection = ticked)
        }
}

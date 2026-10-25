package fr.guim.cinedoury.tv

import fr.guim.cinedoury.core.data.home.CardStyle
import fr.guim.cinedoury.core.data.home.HomeLayouts
import fr.guim.cinedoury.core.data.home.HomeRowType
import fr.guim.cinedoury.core.data.home.UserLibraries
import fr.guim.cinedoury.core.data.library.LibraryState
import fr.guim.cinedoury.core.data.library.PagedItems
import fr.guim.cinedoury.core.designsystem.image.ImageSource
import fr.guim.cinedoury.core.model.EpisodeInfo
import fr.guim.cinedoury.core.model.FilterOptions
import fr.guim.cinedoury.core.model.HomeExclusions
import fr.guim.cinedoury.core.model.ImageRef
import fr.guim.cinedoury.core.model.ImageType
import fr.guim.cinedoury.core.model.ItemId
import fr.guim.cinedoury.core.model.ItemImages
import fr.guim.cinedoury.core.model.ItemKind
import fr.guim.cinedoury.core.model.Library
import fr.guim.cinedoury.core.model.LibraryId
import fr.guim.cinedoury.core.model.LibraryKind
import fr.guim.cinedoury.core.model.MediaItem
import fr.guim.cinedoury.core.model.NamedId
import fr.guim.cinedoury.core.model.UserData
import fr.guim.cinedoury.core.ui.RailState
import fr.guim.cinedoury.feature.home.HomeRow
import fr.guim.cinedoury.feature.home.HomeSettingsState
import fr.guim.cinedoury.feature.home.HomeState
import fr.guim.cinedoury.feature.library.LibraryUiState
import java.time.LocalDate
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Sample content of the J2 screenshots: the test library of `ci/jellyfin` (titles, years, genres,
 * and the BlurHashes the test server computed for their generated posters), the same in every
 * language. No image is loaded: the BlurHash stands in for each picture.
 */
internal object J2Samples {
    /** BlurHashes of the generated posters and backdrops of the test server. */
    private val posters =
        listOf(
            "dWN\$Q%s=a}xH44W?fRW;C6nnjba}w9rxjZjbz~nTbFjb",
            "dON=ES+o+w,JwA;2BzOY#UG8OG:=#sOX+gKIr]SwjZKM",
            "dOMWoWWYayxGIfJ6a{WX1bJ#j?WV2RO8sWWo6bsVsWa{",
            "dRJAF#%2jbxt3wbtjuW.Q:X5bHayoejGj@f7Swayf7a|",
            "dpG_B^o{fjoyHds*j?f60sWEj?WXofR.aza#xGjZazjt",
            "dgG,2uoyfQoy~moefQoeDWazfQaz93azfQazbHazfQfQ",
            "dZIPl60{Pdt8EMPL#sr2ays:rLOlkDbFa{XNayj[nlay",
            "ddHxc#R-a{of}@z#ahn,C.wNrxbFTCOkn+fjt7W-R%fQ",
            "dKM=7XNES0\$,8_EIScWB4,J2X4axEJOjkUjuEbTBogjc",
            "daI\$0|3cs?ou3J,Lv}Sys?v}KLkDkRW;kDrrjsjcjYW=",
        )
    private val backdrops =
        listOf(
            "W.D7*I\$;JYJVWsoNoHslspWqS2WpfPjsjsjta{a{oMj]a}a}a}fR",
            "Wb6^,+qCo{oxkVkUVGV]Xmg2j?fki{ixaLXSbbbHoej@jFi{babb",
            "WpLhp+{w~i.NIEM~tPV]n%xrt5WCa#azayj@oekAxsn%odoefQaz",
            "WLO-\$[_LQ:%Loe\$+_2Z*S|xaRQg2oeV[oya2S|wz=|f*n%W.s:aL",
            "WhAq%t.{pGLvPRX+pHg2TaTHjGnjj[babajGnjjZo}jbbababGfk",
            "WSCQNzwijaoijZoiThwhjZj]jaj]F?s=n,fkjua}OtSzsqjuj[j[",
        )

    val noImages: (ImageRef) -> ImageSource = { ImageSource { _, _ -> null } }

    val films = Library(LibraryId("films"), "Films", LibraryKind.Movies)
    val series = Library(LibraryId("series"), "Séries", LibraryKind.Shows)
    val spectacles = Library(LibraryId("spectacles"), "Spectacles", LibraryKind.Movies)
    val libraries = listOf(series, films, spectacles)

    val rail = RailState(libraries = libraries, profileName = "Camille")

    private fun image(
        id: String,
        type: ImageType,
        hash: String,
    ) = ImageRef(ItemId(id), type, "tag", blurHash = hash)

    /** Title, year, genres, rating, runtime in minutes: the catalogue of generate-media.sh. */
    private val catalogue =
        listOf(
            Film("2 Soleils", 2019, "Science-fiction", 6.8f, "-12", 104),
            Film("Après la pluie", 1987, "Drame", 7.4f, "Tous publics", 96),
            Film("Bleu acier", 1999, "Thriller", 6.2f, "-16", 118, watched = true),
            Film("Chambre noire", 2008, "Thriller", 6.5f, "-16", 93),
            Film("Double jeu", 2012, "Comédie", null, null, 6),
            Film("Échappée belle", 2016, "Comédie", 6.4f, "Tous publics", 99),
            Film("Fréquences", 2023, "Science-fiction", 7.0f, "-12", 107, position = 40),
            Film("Givre", 2012, "Drame", 7.3f, "-12", 98, watched = true),
            Film("Horizon perdu", 1979, "Aventure", 7.2f, "-10", 127),
            Film("Île de sel", 2005, "Documentaire", 7.7f, "Tous publics", 84),
            Film("Jours tranquilles", 1968, "Comédie", 6.1f, "Tous publics", 91),
            Film("Kilomètre zéro", 2018, "Comédie", 6.3f, "Tous publics", 102),
            Film("La Dune", 1983, "Aventure", 6.9f, "Tous publics", 132),
            Film("La Lanterne", 1985, "Fantastique", 7.5f, "Tous publics", 95),
            Film("La Route du nord", 1955, "Aventure", 7.3f, "Tous publics", 118),
            Film("L'Arpenteur", 1964, "Policier", 7.9f, "-12", 112),
            Film("Le Cerf-volant", 1958, "Comédie", 7.6f, "Tous publics", 88, watched = true),
            Film("Le Grand Large", 1991, "Aventure", 6.7f, "Tous publics", 121),
            Film("Le Phare", 2001, "Drame", null, null, 6, watched = true),
            Film("Le Pont suspendu", 1975, "Policier", 7.0f, "-12", 106),
            Film("Le Veilleur", 1977, "Fantastique", 7.1f, "-12", 101),
            Film("L'Éclipse", 1972, "Science-fiction", 7.8f, "-10", 115),
            Film("Les Brisants", 2021, "Drame", 7.1f, "-10", 101),
            Film("Les Étoiles filantes", 1981, "Science-fiction", 7.0f, "Tous publics", 94),
        )

    private data class Film(
        val title: String,
        val year: Int,
        val genre: String,
        val rating: Float?,
        val certification: String?,
        val minutes: Int,
        val watched: Boolean = false,
        val position: Int = 0,
    )

    val movies: List<MediaItem> =
        catalogue.mapIndexed { index, film ->
            val id = "film-$index"
            MediaItem(
                id = ItemId(id),
                kind = ItemKind.Movie,
                title = film.title,
                year = film.year,
                premiereDate = LocalDate.of(film.year, 6, 15),
                overview = "${film.title}, ${film.year}. Un film du catalogue de test.",
                runtime = film.minutes.minutes,
                officialRating = film.certification,
                communityRating = film.rating,
                genres = listOf(film.genre),
                images =
                    ItemImages(
                        // Every fifth film has no poster: its card is generated.
                        primary = image(id, ImageType.Primary, posters[index % posters.size]).takeIf { index % 5 != 4 },
                        backdrops = listOf(image(id, ImageType.Backdrop, backdrops[index % backdrops.size])),
                    ),
                userData =
                    UserData(
                        played = film.watched,
                        playbackPosition = film.position.minutes,
                        playCount = if (film.watched) 1 else 0,
                        isFavorite = false,
                    ),
            )
        }

    private fun episode(
        id: String,
        series: String,
        season: Int,
        number: Int,
        title: String,
        position: Duration = Duration.ZERO,
        hash: String,
    ) = MediaItem(
        id = ItemId(id),
        kind = ItemKind.Episode,
        title = title,
        overview = "Épisode $number de la saison $season.",
        runtime = 42.minutes,
        images =
            ItemImages(
                primary = image(id, ImageType.Primary, hash),
                parentBackdrop = image("$id-series", ImageType.Backdrop, hash),
            ),
        userData = UserData(played = false, playbackPosition = position, playCount = 0, isFavorite = false),
        episode = EpisodeInfo(ItemId("$id-series"), series, null, season, number),
    )

    val resumeEpisode = episode("ep-1", "Les Marées", 1, 2, "Mortes-eaux", 18.minutes, backdrops[1])

    private fun showOf(
        id: String,
        title: String,
        year: Int,
        unwatched: Int,
        hash: String,
    ) = MediaItem(
        id = ItemId(id),
        kind = ItemKind.Series,
        title = title,
        year = year,
        images = ItemImages(primary = image(id, ImageType.Primary, hash)),
        userData =
            UserData(
                played = false,
                playbackPosition = Duration.ZERO,
                playCount = 0,
                isFavorite = false,
                unplayedCount = unwatched,
            ),
    )

    val shows =
        listOf(
            showOf("brume", "Brume", 2019, 2, posters[3]),
            showOf("marees", "Les Marées", 2015, 3, posters[7]),
            showOf("saison", "Une saison", 2018, 2, posters[0]),
        )

    val homeRows =
        listOf(
            HomeRow(
                "resume",
                HomeRowType.Resume,
                null,
                CardStyle.Thumbnail,
                listOf(
                    resumeEpisode,
                    movies[6],
                    movies[1].copy(userData = movies[1].userData?.copy(playbackPosition = 25.minutes)),
                ),
            ),
            HomeRow(
                "next-up",
                HomeRowType.NextUp,
                null,
                CardStyle.Thumbnail,
                listOf(episode("ep-2", "Une saison", 1, 2, "Été", hash = backdrops[4])),
            ),
            HomeRow("latest:films", HomeRowType.Latest, films, CardStyle.Poster, movies.reversed().take(10)),
            HomeRow("latest:series", HomeRowType.Latest, series, CardStyle.Poster, shows),
            HomeRow(
                "released",
                HomeRowType.RecentlyReleased,
                null,
                CardStyle.Poster,
                movies.sortedByDescending { it.year },
            ),
        )

    val home = HomeState(rail = rail, remote = false, rows = homeRows)

    /** « Films » sorted by title: the first page of the 45 films is read, the rest still skeletons. */
    fun library(
        state: LibraryState = LibraryState(),
        loaded: Int = movies.size,
    ) = LibraryUiState(
        library = films,
        rail = rail,
        browse = state,
        items = PagedItems(total = 45, pages = mapOf(0 to movies.take(loaded)), pageSize = movies.size),
        options = options,
        restored = true,
    )

    val options =
        FilterOptions(
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
                ),
            years = listOf(1955, 1964, 1972, 1983, 1991, 2001, 2012, 2023),
            officialRatings = listOf("-10", "-12", "-16", "Tous publics"),
            tags = listOf("Classique", "Coup de cœur", "Noir et blanc"),
            audioLanguages = listOf("eng", "fra", "jpn"),
            subtitleLanguages = listOf("eng", "fra"),
            studios = listOf(NamedId("s1", "Films du Phare"), NamedId("s2", "Gaumont Bleu")),
            people = listOf(NamedId("p1", "Marie Duval"), NamedId("p2", "Paul Mercier")),
            collections =
                listOf(
                    NamedId("c1", "Le Phare : la saga"),
                    NamedId("c2", "Les Étoiles filantes : la trilogie"),
                ),
        )

    val collections =
        listOf(
            MediaItem(
                ItemId("c1"),
                ItemKind.Collection,
                "Le Phare : la saga",
                images = ItemImages(primary = image("c1", ImageType.Primary, posters[2])),
            ),
            MediaItem(ItemId("c2"), ItemKind.Collection, "Les Étoiles filantes : la trilogie"),
        )

    val customization =
        HomeSettingsState(
            layout = HomeLayouts.DEFAULT,
            libraries =
                UserLibraries(
                    libraries,
                    HomeExclusions(fromHome = setOf(spectacles.id), fromLatest = setOf(spectacles.id)),
                ),
            railHidden = emptySet(),
        )
}

package fr.guim.cinedoury.mobile

import fr.guim.cinedoury.core.data.ActiveSession
import fr.guim.cinedoury.core.data.Connection
import fr.guim.cinedoury.core.data.ConnectionMode
import fr.guim.cinedoury.core.data.Profile
import fr.guim.cinedoury.core.data.SavedServer
import fr.guim.cinedoury.core.data.home.CardStyle
import fr.guim.cinedoury.core.data.home.HomeRowType
import fr.guim.cinedoury.core.data.home.TonightKind
import fr.guim.cinedoury.core.data.home.TonightTile
import fr.guim.cinedoury.core.data.library.LibraryState
import fr.guim.cinedoury.core.data.library.PagedItems
import fr.guim.cinedoury.core.data.search.SearchSuggestions
import fr.guim.cinedoury.core.designsystem.image.ImageSource
import fr.guim.cinedoury.core.model.Credit
import fr.guim.cinedoury.core.model.CreditType
import fr.guim.cinedoury.core.model.EpisodeInfo
import fr.guim.cinedoury.core.model.FilterOptions
import fr.guim.cinedoury.core.model.ImageRef
import fr.guim.cinedoury.core.model.ImageType
import fr.guim.cinedoury.core.model.ItemId
import fr.guim.cinedoury.core.model.ItemImages
import fr.guim.cinedoury.core.model.ItemKind
import fr.guim.cinedoury.core.model.Library
import fr.guim.cinedoury.core.model.LibraryId
import fr.guim.cinedoury.core.model.LibraryKind
import fr.guim.cinedoury.core.model.MediaItem
import fr.guim.cinedoury.core.model.MediaSource
import fr.guim.cinedoury.core.model.MediaStream
import fr.guim.cinedoury.core.model.SearchGroup
import fr.guim.cinedoury.core.model.SearchRow
import fr.guim.cinedoury.core.model.SeriesStatus
import fr.guim.cinedoury.core.model.SmartPlay
import fr.guim.cinedoury.core.model.StreamType
import fr.guim.cinedoury.core.model.TechBadge
import fr.guim.cinedoury.core.model.TrackSelection
import fr.guim.cinedoury.core.model.User
import fr.guim.cinedoury.core.model.UserData
import fr.guim.cinedoury.core.model.UserId
import fr.guim.cinedoury.core.source.DiscoveredServer
import fr.guim.cinedoury.core.source.ServerAddress
import fr.guim.cinedoury.core.source.Session
import fr.guim.cinedoury.core.source.fake.InMemorySource
import fr.guim.cinedoury.core.ui.RailState
import fr.guim.cinedoury.feature.details.DetailsState
import fr.guim.cinedoury.feature.home.HomeRow
import fr.guim.cinedoury.feature.home.HomeState
import fr.guim.cinedoury.feature.home.TonightState
import fr.guim.cinedoury.feature.library.LibraryUiState
import fr.guim.cinedoury.feature.search.SearchState
import java.time.Instant
import java.time.LocalDate
import kotlin.time.Duration.Companion.minutes

/**
 * Sample content of the screenshots of the phone (M1): the titles of the test library of
 * `ci/jellyfin`, drawn by [SampleArt]. No address of the house: `192.0.2.x` and `example.com`.
 */
internal object MobileSamples {
    val noImages: (ImageRef) -> ImageSource = { ImageSource { _, _ -> null } }

    val local: ServerAddress = ServerAddress.of("http://192.0.2.10:8096")
    val remote: ServerAddress = ServerAddress.of("https://example.com")

    val servers =
        listOf(
            DiscoveredServer("home", "Maison", local),
            DiscoveredServer("cabin", "Chalet", ServerAddress.of("http://192.0.2.24:8096")),
        )

    val server = SavedServer("home", "Maison", "12.3.0", local, remote)

    private fun user(
        id: String,
        name: String,
    ) = User(UserId(id), name, isAdministrator = name == "Camille", hasPassword = true)

    val profiles =
        listOf(
            Profile("home", user("1", "Camille"), isSignedIn = true, hasPin = true),
            Profile("home", user("2", "Léo"), isSignedIn = true, hasPin = false),
            Profile("home", user("3", "enfant"), isSignedIn = false, hasPin = false),
        )

    fun session(mode: ConnectionMode = ConnectionMode.Local): ActiveSession {
        val session = Session(local, "home", profiles.first().user, "token")
        return ActiveSession(
            Connection(server, if (mode == ConnectionMode.Local) local else remote, mode),
            profiles.first(),
            session,
            InMemorySource().open(session),
        )
    }

    val films = Library(LibraryId("films"), "Films", LibraryKind.Movies)
    val series = Library(LibraryId("series"), "Séries", LibraryKind.Shows)
    val libraries = listOf(films, series, Library(LibraryId("spectacles"), "Spectacles", LibraryKind.Movies))
    val rail = RailState(libraries = libraries, profileName = "Camille")

    private fun art(
        title: String,
        type: ImageType,
    ) = SampleArt.image(title, type)

    private data class Film(
        val title: String,
        val year: Int,
        val genre: String,
        val minutes: Int,
        val watched: Boolean = false,
        val position: Int = 0,
    )

    private val catalogue =
        listOf(
            Film("Le Phare", 2001, "Drame", 112, position = 64),
            Film("Fréquences", 2023, "Science-fiction", 107, position = 40),
            Film("Horizon perdu", 1979, "Aventure", 127),
            Film("Île de sel", 2005, "Documentaire", 84),
            Film("La Lanterne", 1985, "Fantastique", 95, watched = true),
            Film("L'Éclipse", 1972, "Science-fiction", 115),
            Film("Les Brisants", 2021, "Drame", 101),
            Film("Givre", 2012, "Drame", 98, watched = true),
            Film("Kilomètre zéro", 2018, "Comédie", 102),
            Film("Le Veilleur", 1977, "Fantastique", 101),
            Film("Bleu acier", 1999, "Thriller", 118),
            Film("2 Soleils", 2019, "Science-fiction", 104),
        )

    val movies: List<MediaItem> =
        catalogue.mapIndexed { index, film ->
            MediaItem(
                id = ItemId("film-$index"),
                kind = ItemKind.Movie,
                title = film.title,
                year = film.year,
                premiereDate = LocalDate.of(film.year, 6, 15),
                dateAdded = Instant.parse("2026-10-28T20:00:00Z"),
                overview =
                    "${film.title} (${film.year}). Un film du catalogue de test : six mois sans soleil dans une " +
                        "station scientifique, et un signal qui revient chaque nuit.",
                tagline = "Une lumière dans la nuit.".takeIf { index == 0 },
                runtime = film.minutes.minutes,
                officialRating = "Tous publics",
                communityRating = 7.2f,
                genres = listOf(film.genre),
                images =
                    ItemImages(
                        primary = art(film.title, ImageType.Primary).takeIf { index % 6 != 5 },
                        backdrops = listOf(art(film.title, ImageType.Backdrop)),
                        logo = art(film.title, ImageType.Logo).takeIf { index == 0 },
                    ),
                userData =
                    UserData(
                        film.watched,
                        film.position.minutes,
                        if (film.watched) 1 else 0,
                        isFavorite =
                            index == 2,
                    ),
                people =
                    listOf(
                        Credit(ItemId("p1"), "Inès Garnier", CreditType.Actor, role = "Marthe"),
                        Credit(ItemId("p2"), "Paul Moreau", CreditType.Actor, role = "Le gardien"),
                        Credit(ItemId("p3"), "Marie Duval", CreditType.Director),
                    ),
                mediaSources = listOf(source(film.title)),
            )
        }

    private fun source(title: String) =
        MediaSource(
            id = "source-$title",
            name = "1080p",
            container = "mkv",
            sizeBytes = 2_000_000_000,
            streams =
                listOf(
                    MediaStream(0, StreamType.Video, "h264", width = 1920, height = 1080),
                    MediaStream(1, StreamType.Audio, "eac3", language = "fre", isDefault = true, channels = 6),
                    MediaStream(2, StreamType.Audio, "aac", language = "eng", channels = 2),
                    MediaStream(3, StreamType.Subtitle, "srt", language = "fre"),
                    MediaStream(4, StreamType.Subtitle, "srt", language = "eng", isForced = true),
                ),
        )

    val show =
        MediaItem(
            id = ItemId("series-0"),
            kind = ItemKind.Series,
            title = "Les Marées",
            year = 2015,
            premiereDate = LocalDate.of(2015, 3, 2),
            endDate = LocalDate.of(2016, 5, 10),
            seriesStatus = SeriesStatus.Ended,
            childCount = 2,
            overview = "Une famille de pêcheurs, deux saisons de tempêtes et de secrets.",
            images =
                ItemImages(
                    primary = art("Les Marées", ImageType.Primary),
                    backdrops = listOf(art("Les Marées", ImageType.Backdrop)),
                ),
            userData = UserData(false, 0.minutes, 0, false, unplayedCount = 5),
        )

    val seasons =
        (1..2).map { number ->
            MediaItem(ItemId("season-$number"), ItemKind.Season, "Saison $number", images = show.images)
        }

    val episodes =
        (1..6).map { index ->
            val season = if (index <= 3) 1 else 2
            MediaItem(
                id = ItemId("episode-$index"),
                kind = ItemKind.Episode,
                title =
                    listOf("La grande marée", "Mortes-eaux", "Dernier jour", "Le retour", "Brume", "Le large")[
                        index -
                            1,
                    ],
                runtime = 42.minutes,
                premiereDate = LocalDate.of(2015, 3, 2).plusWeeks(index.toLong()),
                overview = "Un épisode des Marées, où la mer monte plus haut que prévu.",
                episode = EpisodeInfo(show.id, show.title, ItemId("season-$season"), season, (index - 1) % 3 + 1),
                images =
                    ItemImages(
                        primary = art("Les Marées $index", ImageType.Backdrop),
                        seriesPrimary = show.images.primary,
                    ),
                userData =
                    UserData(
                        index == 1,
                        if (index ==
                            2
                        ) {
                            20.minutes
                        } else {
                            0.minutes
                        },
                        if (index == 1) 1 else 0,
                        false,
                    ),
            )
        }

    val person =
        MediaItem(
            id = ItemId("p1"),
            kind = ItemKind.Person,
            title = "Inès Garnier",
            premiereDate = LocalDate.of(1970, 3, 2),
            productionLocations = listOf("Brest"),
            overview = "Actrice de théâtre et de cinéma.",
        )

    val home =
        HomeState(
            rail = rail,
            rows =
                listOf(
                    HomeRow(
                        "continue",
                        HomeRowType.Continue,
                        null,
                        CardStyle.Thumbnail,
                        listOf(movies[1], episodes[1]),
                        mapOf(movies[1].id to "resume"),
                    ),
                    HomeRow("latest:films", HomeRowType.Latest, films, CardStyle.Poster, movies.drop(4)),
                    HomeRow("released", HomeRowType.RecentlyReleased, null, CardStyle.Poster, movies.reversed()),
                ),
            tonight =
                TonightState(
                    listOf(
                        TonightTile(movies[0], TonightKind.Resume),
                        TonightTile(episodes[2], TonightKind.NextUp),
                        TonightTile(movies[2], TonightKind.Added),
                        TonightTile(movies[3], TonightKind.Added),
                        TonightTile(movies[6], TonightKind.Rediscover),
                    ),
                ),
        )

    val library =
        LibraryUiState(
            library = films,
            rail = rail,
            browse = LibraryState(),
            items = PagedItems(total = movies.size, pages = mapOf(0 to movies.sortedBy { it.title })),
            options =
                FilterOptions(
                    genres =
                        movies
                            .flatMap {
                                it.genres
                            }.distinct()
                            .sorted(),
                    years = movies.mapNotNull { it.year },
                ),
            restored = true,
        )

    val film =
        DetailsState(
            item = movies[0],
            loaded = true,
            badges = listOf(TechBadge.FullHd, TechBadge.Surround51, TechBadge.Subtitled),
            similar = movies.drop(2).take(6),
            selection = TrackSelection(sourceId = "source-Le Phare", audioIndex = 1, subtitleIndex = null),
        )

    val seriesDetails =
        DetailsState(
            item = show,
            loaded = true,
            seasons = seasons,
            episodes = episodes,
            season = seasons.first().id,
            smartPlay = SmartPlay.of(episodes),
        )

    val personDetails = DetailsState(item = person, loaded = true, filmography = movies.take(5))

    val searchIdle =
        SearchState(
            rail = rail,
            recent = listOf("phare", "inès", "marées"),
            suggestions =
                SearchSuggestions(
                    recentlyAdded = movies.take(6),
                    genres = listOf("Aventure", "Comédie", "Documentaire", "Drame", "Fantastique", "Science-fiction"),
                ),
        )

    val searchResults =
        SearchState(
            rail = rail,
            query = "le",
            rows =
                listOf(
                    SearchRow(SearchGroup.Movies, movies.filter { it.title.startsWith("L") }),
                    SearchRow(SearchGroup.Episodes, episodes.take(2)),
                ),
        )
}

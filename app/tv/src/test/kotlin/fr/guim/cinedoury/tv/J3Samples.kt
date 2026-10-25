package fr.guim.cinedoury.tv

import fr.guim.cinedoury.core.data.home.CardStyle
import fr.guim.cinedoury.core.data.home.HomeRowType
import fr.guim.cinedoury.core.data.index.IndexStatus
import fr.guim.cinedoury.core.model.Credit
import fr.guim.cinedoury.core.model.CreditType
import fr.guim.cinedoury.core.model.EpisodeInfo
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
import fr.guim.cinedoury.core.model.PlaybackPreferences
import fr.guim.cinedoury.core.model.RemoteTrailer
import fr.guim.cinedoury.core.model.SeriesStatus
import fr.guim.cinedoury.core.model.SmartPlay
import fr.guim.cinedoury.core.model.StreamType
import fr.guim.cinedoury.core.model.SubtitleMode
import fr.guim.cinedoury.core.model.TechBadge
import fr.guim.cinedoury.core.model.TechnicalSummary
import fr.guim.cinedoury.core.model.TrackPreselection
import fr.guim.cinedoury.core.model.UserData
import fr.guim.cinedoury.core.model.VideoRange
import fr.guim.cinedoury.core.ui.RailState
import fr.guim.cinedoury.feature.details.DetailsState
import fr.guim.cinedoury.feature.details.Trailer
import fr.guim.cinedoury.feature.home.HomeRow
import fr.guim.cinedoury.feature.home.HomeState
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Sample content of the J3 screenshots: the films and series of the test server (`ci/jellyfin`),
 * with the artwork its bootstrap generates ([SampleArt]) and richer files than the generated ones
 * (a 4K Dolby Vision version, Atmos), so that every badge and track shows.
 */
internal object J3Samples {
    /** 20:58 in Paris: « se termine à 22 h 07 » for « Le Phare » resumed at 40 min. */
    val clock: Clock = Clock.fixed(Instant.parse("2026-10-25T19:58:10Z"), ZoneId.of("Europe/Paris"))

    private fun images(
        title: String,
        logo: Boolean = true,
    ) = ItemImages(
        primary = SampleArt.image(title, ImageType.Primary),
        backdrops = listOf(SampleArt.image("$title ", ImageType.Backdrop)),
        logo = if (logo) SampleArt.image(title, ImageType.Logo) else null,
    )

    private fun stream(
        index: Int,
        type: StreamType,
        codec: String,
        language: String? = null,
        channels: Int? = null,
        profile: String? = null,
        default: Boolean = false,
        forced: Boolean = false,
        width: Int? = null,
        range: VideoRange? = null,
    ) = MediaStream(
        index = index,
        type = type,
        codec = codec,
        language = language,
        channels = channels,
        profile = profile,
        isDefault = default,
        isForced = forced,
        width = width,
        height = width?.let { it * 9 / 16 },
        videoRange = range,
        displayTitle = listOfNotNull(language, codec.uppercase(), profile).joinToString(" - "),
    )

    private val uhd =
        MediaSource(
            id = "phare-2160",
            name = "Le Phare (2001) - 2160p",
            container = "mkv",
            sizeBytes = 48_300_000_000,
            path = "/media/films/Le Phare (2001)/Le Phare (2001) - 2160p.mkv",
            bitrate = 58_000_000,
            streams =
                listOf(
                    stream(
                        0,
                        StreamType.Video,
                        "hevc",
                        width = 3840,
                        range = VideoRange.DolbyVision,
                        profile = "Main 10",
                    ),
                    stream(1, StreamType.Audio, "truehd", "eng", 8, "Dolby TrueHD + Dolby Atmos"),
                    stream(2, StreamType.Audio, "eac3", "fra", 6, default = true),
                    stream(3, StreamType.Audio, "aac", "jpn", 2),
                    stream(4, StreamType.Subtitle, "subrip", "fra", forced = true),
                    stream(5, StreamType.Subtitle, "subrip", "fra"),
                    stream(6, StreamType.Subtitle, "ass", "eng"),
                    stream(7, StreamType.Subtitle, "pgssub", "fra"),
                ),
        )

    private val hd =
        MediaSource(
            id = "phare-1080",
            name = "Le Phare (2001) - 1080p",
            container = "mkv",
            sizeBytes = 9_800_000_000,
            path = "/media/films/Le Phare (2001)/Le Phare (2001) - 1080p.mkv",
            bitrate = 12_000_000,
            streams =
                listOf(
                    stream(0, StreamType.Video, "h264", width = 1920, range = VideoRange.Sdr),
                    stream(1, StreamType.Audio, "eac3", "fra", 6, default = true),
                    stream(2, StreamType.Subtitle, "subrip", "fra", forced = true),
                ),
        )

    private fun credit(
        name: String,
        type: CreditType,
        role: String? = null,
        photo: Boolean = true,
    ) = Credit(ItemId("person-$name"), name, type, role, if (photo) SampleArt.photo(name) else null)

    private fun film(
        title: String,
        year: Int,
        minutes: Int,
        watched: Boolean = false,
    ) = MediaItem(
        id = ItemId("film-$title"),
        kind = ItemKind.Movie,
        title = title,
        year = year,
        premiereDate = LocalDate.of(year, 6, 15),
        runtime = minutes.minutes,
        images = images(title),
        userData = UserData(watched, Duration.ZERO, if (watched) 1 else 0, false),
    )

    val retour = film("Retour au phare", 2004, 102)

    val phare =
        film("Le Phare", 2001, 109).copy(
            overview =
                listOf(
                    "Un gardien de phare consigne chaque nuit ce que la mer lui rapporte.",
                    "Quand un cargo s'échoue sur les récifs de la Pointe, ses carnets deviennent la seule mémoire",
                    "d'une nuit que tout le village voudrait oublier. Vingt ans plus tard, sa fille revient les lire,",
                    "page après page, et découvre ce que son père n'a jamais dit.",
                ).joinToString(" "),
            officialRating = "Tous publics",
            communityRating = 7.4f,
            genres = listOf("Drame", "Mystère"),
            mediaSources = listOf(uhd, hd),
            people =
                listOf(
                    credit("Camille Martin", CreditType.Actor, "Yann, le gardien"),
                    credit("Louis Bernard", CreditType.Actor, "Le capitaine"),
                    credit("Inès Garnier", CreditType.Actor, "Anna"),
                    credit("Hugo Lefèvre", CreditType.Actor, "Le maire", photo = false),
                    credit("Jeanne Roux", CreditType.Actor, "La postière"),
                    credit("Marie Duval", CreditType.Director),
                    credit("Claire Fontaine", CreditType.Writer),
                ),
            remoteTrailers = listOf(RemoteTrailer("https://www.youtube.com/watch?v=aqz-KE-bpKQ")),
            userData = UserData(false, 40.minutes, 0, isFavorite = true),
        )

    private val similar =
        listOf(
            film("Nuit polaire", 2020, 98),
            film("Rivages", 2024, 112),
            film("Givre", 2012, 98, watched = true),
            film("Marée basse", 2001, 110),
            film("Les Brisants", 2021, 101),
            film("Terre ferme", 2015, 108),
            film("Zéphyr", 2022, 83),
        )

    val saga =
        MediaItem(
            ItemId("saga"),
            ItemKind.Collection,
            "Le Phare : la saga",
            images = images("Le Phare : la saga", logo = false),
        )

    private val preferences =
        PlaybackPreferences(audioLanguage = "fra", subtitleLanguage = "fra", subtitleMode = SubtitleMode.Smart)

    val film =
        DetailsState(
            item = phare,
            loaded = true,
            badges = TechnicalSummary.of(phare.mediaSources).badges(),
            saga = saga,
            sagaItems = listOf(phare, retour),
            similar = similar,
            trailer = Trailer.Remote("https://www.youtube.com/watch?v=aqz-KE-bpKQ"),
            selection = TrackPreselection.choose(uhd, preferences),
        )

    private fun episode(
        series: String,
        season: Int,
        number: Int,
        title: String,
        aired: LocalDate,
        played: Boolean = false,
        position: Duration = Duration.ZERO,
    ) = MediaItem(
        id = ItemId("$series-$season-$number"),
        kind = ItemKind.Episode,
        title = title,
        overview = "Épisode $number de la saison $season. Au large, la houle monte ; à terre, on attend les bateaux.",
        runtime = 42.minutes,
        premiereDate = aired,
        images = ItemImages(primary = SampleArt.image("$title $number", ImageType.Thumb)),
        userData =
            UserData(
                played,
                position,
                if (played) 1 else 0,
                false,
                lastPlayed = if (position.isPositive()) Instant.EPOCH else null,
            ),
        episode = EpisodeInfo(ItemId(series), series, ItemId("$series-s$season"), season, number),
    )

    private fun season(
        series: String,
        number: Int,
        unplayed: Int,
    ) = MediaItem(
        id = ItemId("$series-s$number"),
        kind = ItemKind.Season,
        title = "Saison $number",
        userData = UserData(unplayed == 0, Duration.ZERO, 0, false, unplayedCount = unplayed),
        episode = EpisodeInfo(ItemId(series), series, null, number, null),
    )

    private val mareesEpisodes =
        listOf(
            episode("Les Marées", 1, 1, "Grande marée", LocalDate.of(2015, 3, 2), played = true),
            episode("Les Marées", 1, 2, "Mortes-eaux", LocalDate.of(2015, 3, 9), position = 18.minutes),
            episode("Les Marées", 1, 3, "Le coefficient", LocalDate.of(2015, 3, 16)),
            episode("Les Marées", 1, 4, "Contre-courant", LocalDate.of(2015, 3, 23)),
            episode("Les Marées", 2, 1, "Le retour", LocalDate.of(2016, 3, 7)),
            episode("Les Marées", 2, 2, "Le chenal", LocalDate.of(2016, 3, 14)),
        )

    val marees =
        MediaItem(
            id = ItemId("Les Marées"),
            kind = ItemKind.Series,
            title = "Les Marées",
            year = 2015,
            premiereDate = LocalDate.of(2015, 3, 2),
            endDate = LocalDate.of(2016, 3, 14),
            seriesStatus = SeriesStatus.Ended,
            childCount = 2,
            officialRating = "-10",
            overview = "Une famille de pêcheurs, au rythme des coefficients : deux saisons entre le port et le large.",
            genres = listOf("Drame"),
            images = images("Les Marées"),
            people =
                listOf(
                    credit("Inès Garnier", CreditType.Actor, "Maëlle"),
                    credit("Louis Bernard", CreditType.Actor, "Le patron"),
                ),
            userData = UserData(false, Duration.ZERO, 0, false, unplayedCount = 5),
        )

    val series =
        DetailsState(
            item = marees,
            loaded = true,
            badges = listOf(TechBadge.FullHd, TechBadge.Surround51, TechBadge.LocalAudio),
            seasons = listOf(season("Les Marées", 1, 3), season("Les Marées", 2, 2)),
            episodes = mareesEpisodes,
            season = ItemId("Les Marées-s1"),
            smartPlay = SmartPlay.of(mareesEpisodes),
            similar = similar.take(4),
        )

    private val saisonEpisodes =
        listOf(
            episode("Une saison", 1, 1, "Printemps", LocalDate.of(2018, 4, 1), played = true),
            episode("Une saison", 1, 2, "Été", LocalDate.of(2018, 4, 8)),
            episode("Une saison", 1, 3, "Automne", LocalDate.of(2018, 4, 15)),
        )

    val singleSeason =
        DetailsState(
            item =
                MediaItem(
                    id = ItemId("Une saison"),
                    kind = ItemKind.Series,
                    title = "Une saison",
                    year = 2018,
                    premiereDate = LocalDate.of(2018, 4, 1),
                    endDate = LocalDate.of(2018, 4, 15),
                    seriesStatus = SeriesStatus.Ended,
                    childCount = 1,
                    overview = "Une série courte, à saison unique : trois épisodes, trois saisons de l'année.",
                    genres = listOf("Comédie"),
                    images = images("Une saison"),
                    userData = UserData(false, Duration.ZERO, 0, false, unplayedCount = 2),
                ),
            loaded = true,
            seasons = listOf(season("Une saison", 1, 2)),
            episodes = saisonEpisodes,
            season = ItemId("Une saison-s1"),
            smartPlay = SmartPlay.of(saisonEpisodes),
        )

    val episode =
        mareesEpisodes[1].let { item ->
            DetailsState(
                item =
                    item.copy(
                        images =
                            item.images.copy(
                                parentBackdrop = SampleArt.image("Les Marées ", ImageType.Backdrop),
                                parentLogo = SampleArt.image("Les Marées", ImageType.Logo),
                            ),
                        mediaSources = listOf(hd.copy(id = "ep", name = null)),
                    ),
                loaded = true,
                badges = TechnicalSummary.of(listOf(hd)).badges(),
                series = marees,
                selection = TrackPreselection.choose(hd.copy(id = "ep"), preferences),
            )
        }

    val person =
        DetailsState(
            item =
                MediaItem(
                    id = ItemId("person-Camille Martin"),
                    kind = ItemKind.Person,
                    title = "Camille Martin",
                    premiereDate = LocalDate.of(1978, 5, 14),
                    productionLocations = listOf("Brest, France"),
                    overview =
                        listOf(
                            "Camille Martin débute au théâtre à Brest avant de tourner pour la télévision.",
                            "Son rôle de gardien dans « Le Phare » lui vaut une reconnaissance tardive ;",
                            "on la retrouve depuis dans des drames maritimes et des comédies.",
                        ).joinToString(" "),
                    images = ItemImages(primary = SampleArt.photo("Camille Martin")),
                ),
            loaded = true,
            filmography = listOf(phare, retour) + similar.take(3) + listOf(marees),
        )

    // Home and rail (retouches of J2).

    val libraries =
        listOf(
            Library(LibraryId("films"), "Films", LibraryKind.Movies),
            Library(LibraryId("series"), "Séries", LibraryKind.Shows),
            Library(LibraryId("animes"), "Animes JPN", LibraryKind.Shows),
            Library(LibraryId("dessins"), "Dessins animés", LibraryKind.Movies),
            Library(LibraryId("docs"), "Documentaires", LibraryKind.Movies),
            Library(LibraryId("spectacles"), "Spectacles", LibraryKind.Movies),
            Library(LibraryId("mma"), "MMA", LibraryKind.Shows),
            Library(LibraryId("youtube"), "YouTube", LibraryKind.HomeVideos),
            Library(LibraryId("enfants"), "Films pour enfants", LibraryKind.Movies),
            Library(LibraryId("collections"), "Collections", LibraryKind.Collections),
        )

    val rail = RailState(libraries = libraries, profileName = "Camille")

    private val resumeRow =
        listOf(
            mareesEpisodes[1].copy(
                images =
                    ItemImages(
                        primary = SampleArt.image("Mortes-eaux 2", ImageType.Thumb),
                        parentBackdrop = SampleArt.image("Les Marées ", ImageType.Backdrop),
                    ),
            ),
            phare,
            similar[1].copy(userData = UserData(false, 25.minutes, 0, false)),
            similar[0].copy(userData = UserData(false, 70.minutes, 0, false)),
            similar[3].copy(userData = UserData(false, 12.minutes, 0, false)),
            similar[5].copy(userData = UserData(false, 54.minutes, 0, false)),
        )

    val home =
        HomeState(
            rail = rail,
            rows =
                listOf(
                    HomeRow("resume", HomeRowType.Resume, null, CardStyle.Thumbnail, resumeRow),
                    HomeRow(
                        "latest:films",
                        HomeRowType.Latest,
                        libraries[0],
                        CardStyle.Poster,
                        listOf(retour) + similar,
                    ),
                    HomeRow("released", HomeRowType.RecentlyReleased, null, CardStyle.Poster, similar.reversed()),
                ),
        )

    val indexRunning = IndexStatus(running = true, read = 3_200, total = 11_523, films = 1_480, episodes = 1_720)
    val indexReady =
        IndexStatus(films = 1_523, episodes = 10_000, updatedAt = Instant.parse("2026-10-25T13:02:00Z"))
}

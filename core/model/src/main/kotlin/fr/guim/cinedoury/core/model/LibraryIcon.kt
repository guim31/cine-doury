package fr.guim.cinedoury.core.model

import java.text.Normalizer

/**
 * The icon of a library in the rail (`FONCTIONNALITES.md` § 3). The genres and the uses (« 4K »,
 * « Classiques », « Vidéos de famille »…) since the essai réel 3: three libraries shared the same
 * icon in the rail of the maintainer.
 */
enum class LibraryIcon {
    Movies,
    Shows,
    Animation,
    Anime,
    Kids,
    HomeVideos,
    Documentaries,
    Nature,
    Stage,
    MusicVideos,
    Comedy,
    Sport,
    OnlineVideo,
    TvPrograms,
    Learning,
    Cooking,
    Holidays,
    Horror,
    SciFi,
    Action,
    Romance,
    Classics,
    World,
    Uhd,
    Favorites,
    Collections,
    Folder,
    ;

    companion object {
        /**
         * The icon a library gets until the user picks another: from words of its name, in French
         * or in English (« Animes JPN », « Dessins animés », « Films 4K », « Classiques », « VOST »,
         * « Tutos »), else from its kind. Accents and case do not count, but for « animé(e)s »
         * (cartoons) against « anime(s) » (Japanese animation); a word matches whole, or as the
         * start of a word for the longer ones (« docu » in « Documentaires »). The first rule that
         * matches wins: the genres and the uses before « Films » and « Séries », which only name the
         * kind of a library of folders.
         */
        fun defaultFor(
            kind: LibraryKind,
            name: String,
        ): LibraryIcon {
            val raw = name.lowercase().split(Regex("[^\\p{L}0-9]+")).filter { it.isNotEmpty() }
            if (raw.any { it in ANIMATED }) return Animation
            val words = words(name)
            val text = words.joinToString(" ", prefix = " ", postfix = " ")
            return KEYWORDS.firstOrNull { (_, rule) -> rule.matches(words, text) }?.first
                ?: when (kind) {
                    LibraryKind.Movies -> Movies
                    LibraryKind.Shows -> Shows
                    LibraryKind.Collections -> Collections
                    LibraryKind.HomeVideos -> HomeVideos
                    LibraryKind.Mixed, LibraryKind.Other -> Folder
                }
        }

        private fun words(name: String): List<String> =
            Normalizer
                // The ligatures are letters of their own: « cœur » would split in « c » and « ur ».
                .normalize(name.lowercase().replace("œ", "oe").replace("æ", "ae"), Normalizer.Form.NFD)
                .replace(Regex("\\p{M}"), "")
                .split(Regex("[^a-z0-9]+"))
                .filter { it.isNotEmpty() }

        /** « Films animés », « Séries animées »: cartoons, where « Animes » alone is Japanese. */
        private val ANIMATED = setOf("animé", "animés", "animée", "animées")

        /** In order: « Dessins animés japonais » is animation, not anime; « Films 4K » is 4K. */
        private val KEYWORDS: List<Pair<LibraryIcon, Keywords>> =
            listOf(
                Animation to
                    Keywords(
                        words = setOf("animation", "animations", "cartoon", "cartoons", "pixar", "disney"),
                        phrases = setOf("dessin anime", "dessins animes"),
                    ),
                Anime to Keywords(words = setOf("anime", "animes", "manga", "mangas", "japanimation", "crunchyroll")),
                HomeVideos to
                    Keywords(
                        words =
                            setOf(
                                "souvenirs",
                                "vacances",
                                "perso",
                                "personnel",
                                "personnelles",
                                "camescope",
                                "mariage",
                                "anniversaire",
                                "gopro",
                            ),
                        phrases =
                            setOf(
                                "videos de famille",
                                "video de famille",
                                "films de famille",
                                "home video",
                                "home videos",
                                "home movies",
                            ),
                    ),
                Kids to
                    Keywords(
                        words =
                            setOf(
                                "enfant",
                                "enfants",
                                "kid",
                                "kids",
                                "jeunesse",
                                "famille",
                                "family",
                                "children",
                                "junior",
                                "juniors",
                                "petits",
                            ),
                    ),
                Sport to
                    Keywords(
                        words =
                            setOf(
                                "mma",
                                "ufc",
                                "boxe",
                                "boxing",
                                "foot",
                                "football",
                                "soccer",
                                "rugby",
                                "f1",
                                "nba",
                                "nfl",
                                "catch",
                                "wwe",
                                "tennis",
                                "cyclisme",
                                "velo",
                                "ski",
                                "golf",
                                "moto",
                                "motogp",
                            ),
                        prefixes = setOf("sport"),
                        phrases = setOf("formule 1"),
                    ),
                Comedy to
                    Keywords(
                        words =
                            setOf(
                                "humour",
                                "humor",
                                "humoristes",
                                "comedie",
                                "comedies",
                                "comedy",
                                "rire",
                                "sketch",
                                "sketchs",
                                "standup",
                            ),
                        phrases = setOf("stand up", "one man show", "one woman show"),
                    ),
                Stage to
                    Keywords(
                        words =
                            setOf(
                                "concert",
                                "concerts",
                                "theatre",
                                "theater",
                                "opera",
                                "operas",
                                "ballet",
                                "ballets",
                                "danse",
                                "cirque",
                                "live",
                                "lives",
                            ),
                        prefixes = setOf("spectacle"),
                    ),
                MusicVideos to
                    Keywords(
                        words = setOf("clip", "clips", "karaoke", "musique", "musiques", "music", "musicaux"),
                        phrases = setOf("music video", "music videos", "videos musicales"),
                    ),
                Documentaries to Keywords(prefixes = setOf("docu", "reportage")),
                Nature to
                    Keywords(
                        words =
                            setOf("nature", "animaux", "animals", "wildlife", "planete", "planet", "ocean", "oceans"),
                    ),
                OnlineVideo to
                    Keywords(
                        words =
                            setOf(
                                "youtube",
                                "twitch",
                                "vimeo",
                                "peertube",
                                "web",
                                "podcast",
                                "podcasts",
                                "chaine",
                                "chaines",
                                "youtubeurs",
                                "youtubers",
                            ),
                    ),
                TvPrograms to
                    Keywords(
                        words =
                            setOf(
                                "emission",
                                "emissions",
                                "replay",
                                "magazine",
                                "magazines",
                                "telerealite",
                                "talkshow",
                                "jt",
                            ),
                        phrases = setOf("tele realite", "talk show", "reality tv"),
                    ),
                Learning to
                    Keywords(
                        words =
                            setOf(
                                "cours",
                                "tuto",
                                "tutos",
                                "tutoriel",
                                "tutoriels",
                                "tutorial",
                                "tutorials",
                                "formation",
                                "formations",
                                "education",
                                "conference",
                                "conferences",
                                "ted",
                                "masterclass",
                                "apprendre",
                                "learning",
                            ),
                    ),
                Cooking to
                    Keywords(
                        words =
                            setOf(
                                "cuisine",
                                "cooking",
                                "recette",
                                "recettes",
                                "recipe",
                                "recipes",
                                "food",
                                "chef",
                                "chefs",
                                "gastronomie",
                            ),
                    ),
                Holidays to
                    Keywords(
                        words = setOf("noel", "christmas", "xmas", "fete", "fetes", "holiday", "holidays"),
                    ),
                Horror to
                    Keywords(
                        words = setOf("halloween", "frisson", "frissons", "slasher", "zombie", "zombies", "gore"),
                        prefixes = setOf("horreur", "horror", "epouvante"),
                    ),
                SciFi to
                    Keywords(
                        words = setOf("sf", "scifi", "fantasy", "fantastique", "espace", "space", "anticipation"),
                        phrases = setOf("science fiction"),
                    ),
                Action to
                    Keywords(
                        words =
                            setOf(
                                "action",
                                "aventure",
                                "aventures",
                                "adventure",
                                "adventures",
                                "blockbuster",
                                "blockbusters",
                                "superheros",
                                "superheroes",
                            ),
                    ),
                Romance to
                    Keywords(
                        words =
                            setOf(
                                "romance",
                                "romances",
                                "romantique",
                                "romantiques",
                                "romantic",
                                "amour",
                                "love",
                                "romcom",
                            ),
                    ),
                Classics to
                    Keywords(
                        words =
                            setOf("patrimoine", "vintage", "retro", "culte", "cult", "cinematheque", "muet", "silent"),
                        prefixes = setOf("classique", "classic"),
                        phrases = setOf("noir et blanc", "black and white"),
                    ),
                World to
                    Keywords(
                        words =
                            setOf(
                                "monde",
                                "world",
                                "international",
                                "internationaux",
                                "etranger",
                                "etrangers",
                                "foreign",
                                "vo",
                                "vost",
                                "vostfr",
                                "asie",
                                "asian",
                                "bollywood",
                                "coreen",
                                "korean",
                            ),
                    ),
                Uhd to
                    Keywords(
                        words = setOf("4k", "uhd", "2160p", "hdr", "hdr10", "remux", "dv"),
                        phrases = setOf("dolby vision", "ultra hd"),
                    ),
                Favorites to
                    Keywords(
                        words =
                            setOf(
                                "favoris",
                                "favori",
                                "favorite",
                                "favorites",
                                "favourites",
                                "best",
                                "top",
                                "selection",
                                "preferes",
                                "pepites",
                            ),
                        phrases = setOf("coup de coeur", "coups de coeur"),
                    ),
                Movies to Keywords(words = setOf("film", "films", "movie", "movies", "cinema")),
                Shows to
                    Keywords(
                        words = setOf("serie", "series", "feuilleton", "feuilletons"),
                        phrases = setOf("tv shows", "series tv"),
                    ),
            )
    }
}

private data class Keywords(
    val words: Set<String> = emptySet(),
    val prefixes: Set<String> = emptySet(),
    val phrases: Set<String> = emptySet(),
) {
    fun matches(
        candidates: List<String>,
        text: String,
    ): Boolean =
        candidates.any { word -> word in words || prefixes.any { word.startsWith(it) } } ||
            phrases.any { " $it " in text }
}

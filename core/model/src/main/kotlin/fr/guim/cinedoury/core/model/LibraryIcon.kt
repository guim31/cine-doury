package fr.guim.cinedoury.core.model

import java.text.Normalizer

/** The icon of a library in the rail (`FONCTIONNALITES.md` § 3). */
enum class LibraryIcon {
    Movies,
    Shows,
    Animation,
    Anime,
    Documentaries,
    Stage,
    Sport,
    OnlineVideo,
    Kids,
    Collections,
    Folder,
    ;

    companion object {
        /**
         * The icon a library gets until the user picks another: from words of its name, in French
         * or in English (« Animes JPN », « Dessins animés », « Documentaires », « MMA », « YouTube »),
         * else from its kind. Accents and case do not count; a word matches whole, or as the start
         * of a word for the longer ones (« docu » in « Documentaires »).
         */
        fun defaultFor(
            kind: LibraryKind,
            name: String,
        ): LibraryIcon {
            val words = words(name)
            val text = words.joinToString(" ", prefix = " ", postfix = " ")
            return KEYWORDS.firstOrNull { (_, rule) -> rule.matches(words, text) }?.first
                ?: when (kind) {
                    LibraryKind.Movies -> Movies
                    LibraryKind.Shows -> Shows
                    LibraryKind.Collections -> Collections
                    LibraryKind.HomeVideos, LibraryKind.Mixed, LibraryKind.Other -> Folder
                }
        }

        private fun words(name: String): List<String> =
            Normalizer
                .normalize(name.lowercase(), Normalizer.Form.NFD)
                .replace(Regex("\\p{M}"), "")
                .split(Regex("[^a-z0-9]+"))
                .filter { it.isNotEmpty() }

        /** In order: « Dessins animés japonais » is animation, not anime. */
        private val KEYWORDS: List<Pair<LibraryIcon, Keywords>> =
            listOf(
                Animation to
                    Keywords(
                        words = setOf("animation", "cartoon", "cartoons"),
                        phrases = setOf("dessin anime", "dessins animes"),
                    ),
                Anime to Keywords(words = setOf("anime", "animes", "manga", "mangas", "japanimation")),
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
                                "rugby",
                                "f1",
                                "nba",
                                "nfl",
                                "catch",
                                "wwe",
                                "tennis",
                            ),
                        prefixes = setOf("sport"),
                        phrases = setOf("formule 1"),
                    ),
                Stage to
                    Keywords(
                        words =
                            setOf(
                                "concert",
                                "concerts",
                                "theatre",
                                "theater",
                                "standup",
                                "humour",
                                "humor",
                                "opera",
                                "ballet",
                            ),
                        prefixes = setOf("spectacle"),
                        phrases = setOf("stand up", "one man show"),
                    ),
                Documentaries to Keywords(words = setOf("nature"), prefixes = setOf("docu", "reportage")),
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
                            ),
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

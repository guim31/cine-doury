package fr.guim.cinedoury.core.model

import java.time.Year

/**
 * The title and year a search for the identity of an item starts from (« Corriger
 * l'identification »), cleaned of what a file or folder name carries: a server that did not
 * recognise a title keeps the name of its folder (« Le Phare (2001) », « Le.Phrae.2001.1080p »).
 * The user corrects the spelling; the cleaning only removes what is not the title.
 */
data class SearchTitle(
    val title: String,
    val year: Int?,
) {
    companion object {
        /**
         * Cleans [name]: dots and underscores become spaces; what is in brackets goes (« [tmdbid-1] »);
         * the year (a number after the title, followed by nothing, a release tag or another year) and
         * what follows it go; a release tag (« 1080p », « BluRay », « x265 », « MULTi ») and what follows
         * it go. A title that is only a year (« 1917 ») stays. [knownYear] is used when the name has none.
         */
        fun of(
            name: String,
            knownYear: Int? = null,
            currentYear: Int = Year.now().value,
        ): SearchTitle {
            val spaced =
                name
                    .replace(BRACKETED, " ")
                    .replace(SEPARATORS, " ")
                    .replace(SPACES, " ")
                    .trim()
            val tokens = spaced.split(' ').filter { it.isNotEmpty() }

            // The title ends at its year, or at the first release tag; the first word always stays.
            fun yearAt(index: Int): Boolean {
                val next = tokens.getOrNull(index + 1)
                val followed = next == null || next.isTag() || next.yearOrNull(currentYear) != null
                return tokens[index].yearOrNull(currentYear) != null && followed
            }
            val yearIndex = (1 until tokens.size).firstOrNull(::yearAt)
            val tagIndex = (1 until tokens.size).firstOrNull { tokens[it].isTag() }
            val end = listOfNotNull(yearIndex, tagIndex).minOrNull() ?: tokens.size
            val year = yearIndex?.takeIf { it == end }?.let { tokens[it].yearOrNull(currentYear) }
            val title =
                tokens
                    .take(end)
                    .joinToString(" ")
                    .trim { it in TRIMMED }
                    .ifEmpty { spaced }
            return SearchTitle(title, year ?: knownYear)
        }

        /** The years a film can have: from the first films to next year. */
        private const val FIRST_YEAR = 1880

        private fun String.yearOrNull(currentYear: Int): Int? {
            val digits = trim('(', ')', '[', ']')
            if (digits.length != YEAR_DIGITS || !digits.all { it.isDigit() }) return null
            return digits.toInt().takeIf { it in FIRST_YEAR..currentYear + 1 }
        }

        private fun String.isTag(): Boolean {
            val word = lowercase().trim('(', ')', '-')
            return word in TAGS || word.substringBefore('-') in TAGS || RESOLUTION.matches(word)
        }

        private const val YEAR_DIGITS = 4
        private val BRACKETED = Regex("""\[[^]]*]|\{[^}]*}""")
        private val SEPARATORS = Regex("""[._]""")
        private val SPACES = Regex("""\s+""")
        private val RESOLUTION = Regex("""(?i)\d{3,4}[pi]""")
        private const val TRIMMED = " -–(),:"

        /** Release tags, in lowercase: what follows the title in the name of a file. */
        private val TAGS =
            setOf(
                "4k",
                "uhd",
                "hdr",
                "hdr10",
                "dv",
                "dovi",
                "sdr",
                "remux",
                "bluray",
                "blu-ray",
                "bdrip",
                "brrip",
                "webrip",
                "web-dl",
                "webdl",
                "web",
                "hdtv",
                "dvdrip",
                "dvd",
                "x264",
                "x265",
                "h264",
                "h265",
                "hevc",
                "avc",
                "av1",
                "10bit",
                "aac",
                "ac3",
                "eac3",
                "dts",
                "truehd",
                "atmos",
                "multi",
                "french",
                "truefrench",
                "vff",
                "vfq",
                "vf",
                "vf2",
                "vostfr",
                "vost",
                "subfrench",
                "proper",
                "repack",
                "extended",
                "unrated",
                "remastered",
                "imax",
            )
    }
}

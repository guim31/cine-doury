package fr.guim.cinedoury.core.model

import java.text.Normalizer
import java.util.Locale

/**
 * The search text of a title or of what the user typed (`SPEC.md` § 4.12): lower case, without
 * accents (« Amélie » → « amelie »), ligatures spelt out (« œ » → « oe »), every other character
 * than a letter or a digit a space, spaces collapsed.
 */
object SearchText {
    private val marks = Regex("\\p{M}+")
    private val separators = Regex("[^\\p{L}\\p{N}]+")
    private val ligatures =
        mapOf(
            "œ" to "oe",
            "æ" to "ae",
            "ß" to "ss",
            "ø" to "o",
            "ł" to "l",
            "đ" to "d",
            "ð" to "d",
            "þ" to "th",
        )

    fun normalize(text: String): String {
        var lower = text.lowercase(Locale.ROOT)
        if (lower.any { it.toString() in ligatures }) {
            lower = ligatures.entries.fold(lower) { acc, (from, to) -> acc.replace(from, to) }
        }
        val bare = Normalizer.normalize(lower, Normalizer.Form.NFD).replace(marks, "")
        return bare.replace(separators, " ").trim()
    }

    fun words(text: String): List<String> = normalize(text).split(' ').filter { it.isNotEmpty() }
}

/** What the user typed, ready to be compared with many titles. */
class SearchQuery private constructor(
    val text: String,
    val words: List<String>,
) {
    val isEmpty: Boolean get() = words.isEmpty()

    companion object {
        fun of(typed: String): SearchQuery {
            val words = SearchText.words(typed)
            return SearchQuery(words.joinToString(" "), words)
        }
    }
}

/**
 * A title ready to be compared with queries: computed once per title (the local index holds a few
 * thousand), then each keystroke compares the query with every one of them.
 */
class PreparedTitle private constructor(
    val text: String,
    val words: List<String>,
) {
    /** The words written together (« spider man » → « spiderman »). */
    internal val joined: String = words.joinToString("")

    companion object {
        fun of(title: String): PreparedTitle {
            val words = SearchText.words(title)
            return PreparedTitle(words.joinToString(" "), words)
        }
    }
}

/**
 * Whether a title matches a query, and how well (`SPEC.md` § 4.12). The higher the score, the
 * better; null when it does not match.
 *
 * - The whole title equal to the query: [EXACT]. The title starting with the query: [STARTS].
 * - Otherwise word by word: every word of the query must match a word of the title, as a whole
 *   word, as its start (the word being typed), or with typos: up to one for a word of 3 or 4 letters,
 *   two from 5 letters, the first letter right (« anaux » finds « anneaux »). Articles and short
 *   linking words of the query (« le », « des », « the ») may match nothing.
 * - The score drops with each typo, each word only started, each word of the title left unmatched;
 *   it rises when the query starts where the title starts.
 */
object SearchMatcher {
    const val EXACT = 1000
    const val STARTS = 900
    private const val WORDS = 700
    private const val PER_TYPO = 60
    private const val PER_PREFIX = 10
    private const val PER_SKIPPED = 15
    private const val PER_EXTRA_WORD = 5
    private const val MAX_EXTRA_PENALTY = 50
    private const val FIRST_WORD_BONUS = 20
    private const val LENGTH_PENALTY_MAX = 40

    /** Words a query may hold without the title holding them. */
    private val linkingWords =
        setOf(
            "le",
            "la",
            "les",
            "l",
            "un",
            "une",
            "des",
            "de",
            "du",
            "d",
            "au",
            "aux",
            "et",
            "en",
            "the",
            "a",
            "an",
            "of",
            "and",
        )

    fun score(
        query: SearchQuery,
        title: PreparedTitle,
    ): Int? =
        when {
            query.isEmpty || title.words.isEmpty() -> {
                null
            }

            title.text == query.text -> {
                EXACT
            }

            title.text.startsWith(query.text) -> {
                STARTS -
                    (title.text.length - query.text.length).coerceAtMost(LENGTH_PENALTY_MAX)
            }

            else -> {
                wordScore(query, title)
            }
        }

    /** The best score of a query against several titles of one item (title, original, sort title). */
    fun best(
        query: SearchQuery,
        titles: List<PreparedTitle>,
    ): Int? =
        titles
            .mapIndexedNotNull {
                index,
                title,
                ->
                score(query, title)?.minus(index * SECONDARY_PENALTY)
            }.maxOrNull()

    private const val SECONDARY_PENALTY = 5

    private fun wordScore(
        query: SearchQuery,
        title: PreparedTitle,
    ): Int? {
        val used = BooleanArray(title.words.size)
        var typos = 0
        var prefixes = 0
        var skipped = 0
        var matched = 0
        var firstMatched = false
        for ((position, word) in query.words.withIndex()) {
            val match = bestWord(word, title, used)
            if (match == null) {
                // A single word against the title written together: « spiderman ».
                val joined = if (query.words.size == 1) match(word, title.joined) else null
                when {
                    joined != null -> {
                        typos += joined.typos
                        matched = title.words.size
                    }

                    word in linkingWords -> {
                        skipped++
                    }

                    else -> {
                        return null
                    }
                }
                continue
            }
            if (match.index >= 0) used[match.index] = true
            typos += match.typos
            if (match.prefix) prefixes++
            matched++
            if (position == 0 && match.index == firstWordIndex(title)) firstMatched = true
        }
        if (matched == 0) return null
        val extra = ((title.words.size - matched).coerceAtLeast(0) * PER_EXTRA_WORD).coerceAtMost(MAX_EXTRA_PENALTY)
        val score =
            WORDS - typos * PER_TYPO - prefixes * PER_PREFIX - skipped * PER_SKIPPED - extra +
                if (firstMatched) FIRST_WORD_BONUS else 0
        return score.coerceAtLeast(1)
    }

    /** The first word of the title that is not an article: « Le Seigneur… » starts at « seigneur ». */
    private fun firstWordIndex(title: PreparedTitle): Int {
        val index = title.words.indexOfFirst { it !in linkingWords }
        return if (index < 0) 0 else index
    }

    private class WordMatch(
        val index: Int,
        val typos: Int,
        val prefix: Boolean,
    )

    private fun bestWord(
        word: String,
        title: PreparedTitle,
        used: BooleanArray,
    ): WordMatch? {
        var best: WordMatch? = null
        for ((index, candidate) in title.words.withIndex()) {
            val match = if (used[index]) null else match(word, candidate)
            val found = match?.let { WordMatch(index, it.typos, it.prefix) }
            if (found != null && (best == null || found.better(best))) best = found
        }
        return best
    }

    private fun WordMatch.better(other: WordMatch): Boolean =
        typos < other.typos || (typos == other.typos && !prefix && other.prefix)

    private class Match(
        val typos: Int,
        val prefix: Boolean,
    )

    private fun match(
        word: String,
        candidate: String,
    ): Match? =
        when {
            word == candidate -> Match(0, prefix = false)

            candidate.startsWith(word) -> Match(0, prefix = true)

            // Typos never touch the first letter: it keeps « marée » from matching « durée ».
            candidate.isEmpty() || candidate[0] != word[0] -> null

            else -> fuzzy(word, candidate, allowedTypos(word.length))
        }

    private fun fuzzy(
        word: String,
        candidate: String,
        allowed: Int,
    ): Match? {
        if (allowed == 0) return null
        val whole = Typos.distance(word, candidate, allowed)
        if (whole <= allowed) return Match(whole, prefix = false)
        // The word being typed: against the starts of the candidate of about its length.
        val shortest = (word.length - allowed).coerceAtLeast(1)
        val longest = (word.length + allowed).coerceAtMost(candidate.length - 1)
        val best = (shortest..longest).minOfOrNull { Typos.distance(word, candidate.substring(0, it), allowed) }
        return if (best != null && best <= allowed) Match(best, prefix = true) else null
    }

    /** Typos allowed in a word of [length] letters. */
    fun allowedTypos(length: Int): Int =
        when {
            length <= NO_TYPO_UP_TO -> 0
            length <= ONE_TYPO_UP_TO -> 1
            else -> 2
        }

    private const val NO_TYPO_UP_TO = 2
    private const val ONE_TYPO_UP_TO = 4
}

/**
 * The optimal string alignment distance (Damerau–Levenshtein without repeated edits): insertions,
 * deletions, substitutions and swaps of two neighbouring letters count one each.
 */
internal object Typos {
    /** The distance, or anything above [limit] once it is sure to exceed it. */
    fun distance(
        a: String,
        b: String,
        limit: Int,
    ): Int {
        if (kotlin.math.abs(a.length - b.length) > limit) return limit + 1
        val rows = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) rows[i][0] = i
        for (j in 0..b.length) rows[0][j] = j
        for (i in 1..a.length) {
            var rowMin = Int.MAX_VALUE
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var value = minOf(rows[i - 1][j] + 1, rows[i][j - 1] + 1, rows[i - 1][j - 1] + cost)
                val swapped = i > 1 && j > 1 && a[i - 1] == b[j - 2]
                if (swapped && a[i - 2] == b[j - 1]) {
                    value = minOf(value, rows[i - 2][j - 2] + 1)
                }
                rows[i][j] = value
                rowMin = minOf(rowMin, value)
            }
            if (rowMin > limit) return limit + 1
        }
        return rows[a.length][b.length]
    }
}

/** The rows of the search results, in the order they come when they match equally well. */
enum class SearchGroup {
    Movies,
    Series,
    Episodes,
    People,
    Collections,
    ;

    companion object {
        fun of(kind: ItemKind): SearchGroup? =
            when (kind) {
                ItemKind.Movie, ItemKind.Video -> Movies
                ItemKind.Series -> Series
                ItemKind.Episode -> Episodes
                ItemKind.Person -> People
                ItemKind.Collection -> Collections
                else -> null
            }
    }
}

/** An item found, with its score ([SearchMatcher]). */
data class SearchHit(
    val item: MediaItem,
    val score: Int,
)

/** One row of results: its items, the best first. */
data class SearchRow(
    val group: SearchGroup,
    val items: List<MediaItem>,
)

object SearchRanking {
    /** The score of an item the server found but our matcher does not (it matched on something else). */
    const val SERVER_ONLY = 1

    /** Items shown per row. */
    const val ROW_LIMIT = 24

    /** The titles of an item a query is compared with: title, original title, sort title. */
    fun titles(item: MediaItem): List<PreparedTitle> {
        // An episode is also found by its series and its title together.
        val withSeries =
            item.episode
                ?.seriesTitle
                ?.takeIf { item.kind == ItemKind.Episode }
                ?.let { "$it ${item.title}" }
        return listOfNotNull(
            item.title,
            item.originalTitle,
            item.sortTitle,
            withSeries,
        ).distinct().map(PreparedTitle::of)
    }

    /**
     * Groups [hits] into rows (`SPEC.md` § 4.12): one item once (its best score), in each row the best
     * first, then the most recent, then by title; rows ordered by their best item, then in the order of
     * [SearchGroup]. Rows without items are left out.
     */
    fun rows(hits: List<SearchHit>): List<SearchRow> {
        val unique = hits.groupBy { it.item.id }.values.map { same -> same.maxBy { it.score } }
        return unique
            .mapNotNull { hit -> SearchGroup.of(hit.item.kind)?.let { it to hit } }
            .groupBy({ it.first }, { it.second })
            .map { (group, groupHits) ->
                val sorted =
                    groupHits.sortedWith(
                        compareByDescending<SearchHit> { it.score }
                            .thenByDescending { it.item.year ?: 0 }
                            .thenBy { SearchText.normalize(it.item.title) },
                    )
                Triple(group, sorted.first().score, sorted.take(ROW_LIMIT).map { it.item })
            }.sortedWith(
                compareByDescending<Triple<SearchGroup, Int, List<MediaItem>>> {
                    it.second
                }.thenBy { it.first.ordinal },
            ).map { (group, _, items) -> SearchRow(group, items) }
    }
}

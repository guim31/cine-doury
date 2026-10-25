package fr.guim.cinedoury.core.data.home

import fr.guim.cinedoury.core.model.LibraryId

/** The kinds of home rows of the 1.0 (`FONCTIONNALITES.md` § 2). */
enum class HomeRowType {
    /** « Reprendre »: films and episodes started. */
    Resume,

    /** « À suivre »: the next episode of the series in progress. */
    NextUp,

    /** « Ajouts récents »: one row per library. */
    Latest,

    /** « Récemment sortis »: films and series by release date. */
    RecentlyReleased,
}

/** How a row draws its items: 2:3 posters or 16:9 thumbnails. */
enum class CardStyle {
    Poster,
    Thumbnail,
}

/**
 * A row of the home as the user arranged it (`SPEC.md` § 4.9).
 *
 * @param id stable across edits: the cache and the focus memory of the row hang on it.
 * @param libraries the libraries the row draws from; empty for all of them (those excluded from the
 * home on the server aside).
 * @param windowDays « À suivre » only: a series counts as in progress if watched in the last
 * [windowDays] days; null without limit.
 */
data class HomeRowConfig(
    val id: String,
    val type: HomeRowType,
    val libraries: Set<LibraryId> = emptySet(),
    val style: CardStyle = type.defaultStyle,
    val windowDays: Int? = if (type == HomeRowType.NextUp) DEFAULT_NEXT_UP_DAYS else null,
) {
    companion object {
        /** Two weeks, like Plex (`OnDeckWindow = 2`). */
        const val DEFAULT_NEXT_UP_DAYS = 14
    }
}

val HomeRowType.defaultStyle: CardStyle
    get() =
        when (this) {
            HomeRowType.Resume, HomeRowType.NextUp -> CardStyle.Thumbnail
            HomeRowType.Latest, HomeRowType.RecentlyReleased -> CardStyle.Poster
        }

/** The home before the user changes it, and the edits of the customisation screen. */
object HomeLayouts {
    val DEFAULT: List<HomeRowConfig> =
        listOf(
            HomeRowConfig("resume", HomeRowType.Resume),
            HomeRowConfig("next-up", HomeRowType.NextUp),
            HomeRowConfig("latest", HomeRowType.Latest),
            HomeRowConfig("released", HomeRowType.RecentlyReleased),
        )

    /** The choices of the « À suivre » window, in days; null is « Sans limite ». */
    val NEXT_UP_WINDOWS: List<Int?> = listOf(7, 14, 30, 90, null)

    fun moveUp(
        rows: List<HomeRowConfig>,
        id: String,
    ): List<HomeRowConfig> = move(rows, id, -1)

    fun moveDown(
        rows: List<HomeRowConfig>,
        id: String,
    ): List<HomeRowConfig> = move(rows, id, 1)

    private fun move(
        rows: List<HomeRowConfig>,
        id: String,
        by: Int,
    ): List<HomeRowConfig> {
        val from = rows.indexOfFirst { it.id == id }
        val to = from + by
        if (from < 0 || to !in rows.indices) return rows
        return rows.toMutableList().apply { add(to, removeAt(from)) }
    }

    fun remove(
        rows: List<HomeRowConfig>,
        id: String,
    ): List<HomeRowConfig> = rows.filterNot { it.id == id }

    /** Adds a row of [type] at the end, with an id no other row has. */
    fun add(
        rows: List<HomeRowConfig>,
        type: HomeRowType,
    ): List<HomeRowConfig> {
        val base = DEFAULT.first { it.type == type }.id
        val id =
            generateSequence(1) { it + 1 }
                .map {
                    if (it ==
                        1
                    ) {
                        base
                    } else {
                        "$base-$it"
                    }
                }.first { id -> rows.none { it.id == id } }
        return rows + HomeRowConfig(id, type)
    }

    fun update(
        rows: List<HomeRowConfig>,
        id: String,
        change: (HomeRowConfig) -> HomeRowConfig,
    ): List<HomeRowConfig> = rows.map { if (it.id == id) change(it) else it }

    fun toggleStyle(row: HomeRowConfig): HomeRowConfig =
        row.copy(style = if (row.style == CardStyle.Poster) CardStyle.Thumbnail else CardStyle.Poster)

    /** Adds or removes [library] from the libraries of [row]; « all » when none is left. */
    fun toggleLibrary(
        row: HomeRowConfig,
        library: LibraryId,
        all: List<LibraryId>,
    ): HomeRowConfig {
        val current = row.libraries.ifEmpty { all.toSet() }
        val next = if (library in current) current - library else current + library
        return row.copy(libraries = if (next.isEmpty() || next.containsAll(all)) emptySet() else next)
    }

    /** The next « À suivre » window, in the order of [NEXT_UP_WINDOWS]. */
    fun nextWindow(row: HomeRowConfig): HomeRowConfig {
        val index = NEXT_UP_WINDOWS.indexOf(row.windowDays)
        return row.copy(windowDays = NEXT_UP_WINDOWS[(index + 1) % NEXT_UP_WINDOWS.size])
    }
}

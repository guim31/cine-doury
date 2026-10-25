package fr.guim.cinedoury.core.data.library

import fr.guim.cinedoury.core.model.ItemFilters
import fr.guim.cinedoury.core.model.NamedId
import fr.guim.cinedoury.core.model.Resolution
import fr.guim.cinedoury.core.model.WatchStatus

/**
 * [ItemFilters] as one line of text, for Room: `key=value,value` pairs separated by ASCII
 * separators, which no name contains. An unknown key or value is skipped, so that an older
 * version of the app reads what a newer one wrote.
 */
object FiltersCodec {
    private const val FIELD = "\u001E"
    private const val VALUE = "\u001F"
    private const val PAIR = "\u001D"
    private const val EQUALS = "="

    fun encode(filters: ItemFilters): String =
        buildList {
            filters.watch?.let { add("watch" to listOf(it.name)) }
            add("genres" to filters.genres.sorted())
            add("decades" to filters.decades.sorted().map { it.toString() })
            add("years" to filters.years.sorted().map { it.toString() })
            filters.minRating?.let { add("rating" to listOf(it.toString())) }
            filters.resolution?.let { add("resolution" to listOf(it.name)) }
            add("audio" to filters.audioLanguages.sorted())
            add("subtitles" to filters.subtitleLanguages.sorted())
            add("certifications" to filters.officialRatings.sorted())
            add("studios" to filters.studios.sortedBy { it.id }.map { "${it.id}$PAIR${it.name}" })
            add("people" to filters.people.sortedBy { it.id }.map { "${it.id}$PAIR${it.name}" })
            filters.collection?.let { add("collection" to listOf("${it.id}$PAIR${it.name}")) }
            add("tags" to filters.tags.sorted())
            if (filters.favorite) add("favorite" to listOf("true"))
        }.filter { (_, values) -> values.isNotEmpty() }
            .joinToString(FIELD) { (key, values) -> key + EQUALS + values.joinToString(VALUE) }

    fun decode(text: String): ItemFilters {
        val fields =
            text
                .split(FIELD)
                .filter { EQUALS in it }
                .associate { field ->
                    val key = field.substringBefore(EQUALS)
                    key to field.substringAfter(EQUALS).split(VALUE).filter { it.isNotEmpty() }
                }

        fun strings(key: String) = fields[key].orEmpty().toSet()

        fun ints(key: String) = fields[key].orEmpty().mapNotNull { it.toIntOrNull() }.toSet()

        fun named(key: String) =
            fields[key].orEmpty().mapNotNull { value ->
                val id = value.substringBefore(PAIR, missingDelimiterValue = "")
                id.takeIf { it.isNotEmpty() }?.let { NamedId(it, value.substringAfter(PAIR)) }
            }

        return ItemFilters(
            watch = fields["watch"]?.firstOrNull()?.let { name -> WatchStatus.entries.firstOrNull { it.name == name } },
            genres = strings("genres"),
            decades = ints("decades"),
            years = ints("years"),
            minRating = fields["rating"]?.firstOrNull()?.toIntOrNull(),
            resolution =
                fields["resolution"]?.firstOrNull()?.let { name ->
                    Resolution.entries.firstOrNull {
                        it.name ==
                            name
                    }
                },
            audioLanguages = strings("audio"),
            subtitleLanguages = strings("subtitles"),
            officialRatings = strings("certifications"),
            studios = named("studios").toSet(),
            people = named("people").toSet(),
            collection = named("collection").firstOrNull(),
            tags = strings("tags"),
            favorite = fields["favorite"]?.firstOrNull() == "true",
        )
    }
}

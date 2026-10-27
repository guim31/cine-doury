package fr.guim.cinedoury.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import fr.guim.cinedoury.core.data.index.IndexStatus
import fr.guim.cinedoury.core.data.library.LibraryView
import fr.guim.cinedoury.core.designsystem.component.CineIcons
import fr.guim.cinedoury.core.designsystem.component.InitialFocus
import fr.guim.cinedoury.core.designsystem.component.ListRow
import fr.guim.cinedoury.core.designsystem.component.SidePanel
import fr.guim.cinedoury.core.designsystem.component.previewFirstVisible
import fr.guim.cinedoury.core.designsystem.component.rememberCineFocus
import fr.guim.cinedoury.core.designsystem.theme.CineSizes
import fr.guim.cinedoury.core.designsystem.theme.CineSpacing
import fr.guim.cinedoury.core.model.AudioFormat
import fr.guim.cinedoury.core.model.DynamicRange
import fr.guim.cinedoury.core.model.FilterOptions
import fr.guim.cinedoury.core.model.ItemFilters
import fr.guim.cinedoury.core.model.LibraryScan
import fr.guim.cinedoury.core.model.NamedId
import fr.guim.cinedoury.core.model.Resolution
import fr.guim.cinedoury.core.model.SortField
import fr.guim.cinedoury.core.model.VideoCodec
import fr.guim.cinedoury.core.model.WatchStatus
import fr.guim.cinedoury.core.ui.label
import fr.guim.cinedoury.feature.manage.R as ManageR

/** The filters of the filter panel, in the order of `FONCTIONNALITES.md` § 3. */
enum class FilterCategory {
    Watch,
    Genre,
    Decade,
    Year,
    Rating,
    Resolution,

    /** HDR10, HDR10+, HLG, Dolby Vision: from the local index. */
    Range,

    /** Stereo, 5.1, 7.1, Atmos, DTS, TrueHD: from the local index. */
    AudioFormat,

    /** H.264, HEVC, AV1…: from the local index. */
    Codec,
    Audio,
    Subtitles,
    Certification,
    Studio,
    Person,
    Collection,
    Tag,
    Favorite,
}

/** Preview tag of the first row of a panel. */
const val PREVIEW_PANEL_FIRST = "panel-0"

/** The dynamic ranges offered: HDR first, SDR last. */
private val RANGES =
    listOf(DynamicRange.DolbyVision, DynamicRange.Hdr10Plus, DynamicRange.Hdr10, DynamicRange.Hlg, DynamicRange.Sdr)

/** The filters the local index applies. */
private val INDEXED = setOf(FilterCategory.Range, FilterCategory.AudioFormat, FilterCategory.Codec)

/**
 * Under a filter of the index: nothing once the index is complete, its progress while it fills
 * (« Index en cours : 1 200 sur 11 500 titres »), else that it has not started.
 */
@Composable
private fun IndexStatus?.note(): String? {
    val total = this?.total
    return when {
        this == null || (ready && !running) -> null
        running && total != null -> stringResource(R.string.filter_index_running, read, total)
        else -> stringResource(R.string.filter_index_pending)
    }
}

/** Minimal ratings offered, out of 10. */
private val RATINGS = (5..9).toList()

/** A row of a panel: a choice, its value or its check mark. */
private data class PanelEntry(
    val key: String,
    val title: String,
    val onClick: () -> Unit,
    val value: String? = null,
    val checked: Boolean = false,
    val description: String? = null,
    val icon: ImageVector? = null,
)

@Composable
internal fun LibraryPanels(
    panel: LibraryPanel?,
    category: FilterCategory?,
    state: LibraryUiState,
    actions: LibraryActions,
    onCategory: (FilterCategory?) -> Unit,
    onClose: () -> Unit,
    onProblems: () -> Unit = {},
) {
    val title =
        when (panel) {
            LibraryPanel.Sort -> stringResource(R.string.panel_sort)
            LibraryPanel.Filters -> category?.label() ?: stringResource(R.string.panel_filters)
            LibraryPanel.View -> stringResource(R.string.panel_view)
            LibraryPanel.Manage -> stringResource(ManageR.string.library_manage)
            null -> ""
        }
    val entries =
        when (panel) {
            LibraryPanel.Sort -> sortEntries(state, actions)
            LibraryPanel.View -> viewEntries(state, actions, onClose)
            LibraryPanel.Filters -> filterEntries(state, actions, category, onCategory)
            LibraryPanel.Manage -> manageEntries(state, actions, onClose, onProblems)
            null -> emptyList()
        }
    SidePanel(visible = panel != null, title = title, modifier = Modifier.testTag("panel")) {
        val first = remember(panel, category) { FocusRequester() }
        LazyColumn(
            state = rememberLazyListState(initialFirstVisibleItemIndex = previewFirstVisible("panel-")),
            contentPadding = PaddingValues(vertical = CineSizes.focusRoom, horizontal = CineSpacing.xs),
            verticalArrangement = Arrangement.spacedBy(CineSpacing.xs),
        ) {
            itemsIndexed(entries, key = { _, entry -> entry.key }) { index, entry ->
                ListRow(
                    title = entry.title,
                    value = entry.value,
                    description = entry.description,
                    icon = entry.icon,
                    trailingIcon = if (entry.checked) CineIcons.Watched else null,
                    onClick = entry.onClick,
                    focusRequester = if (index == 0) first else null,
                    focus = rememberCineFocus("panel-$index"),
                    modifier = Modifier.testTag("panel-$index"),
                )
            }
        }
        InitialFocus(first, key = panel to category, enabled = panel != null)
    }
}

/**
 * The « ⋯ » of a library, for an administrator (`FONCTIONNALITES.md` § 9): scan for new files (its
 * progress under it while it runs), refresh everything, the items with problems, the selection.
 */
@Composable
private fun manageEntries(
    state: LibraryUiState,
    actions: LibraryActions,
    onClose: () -> Unit,
    onProblems: () -> Unit,
): List<PanelEntry> {
    val scan = state.scan
    val progress = scan?.progress
    val running =
        when {
            scan?.running != true -> null
            progress != null -> stringResource(ManageR.string.scan_running, (progress * PERCENT).toInt())
            else -> stringResource(ManageR.string.scan_running_unknown)
        }
    return listOf(
        PanelEntry(
            key = "scan",
            title = stringResource(ManageR.string.scan_new_files),
            onClick = { actions.scan(LibraryScan.NewFiles) },
            description = running ?: stringResource(ManageR.string.scan_new_files_description),
            icon = CineIcons.Retry,
        ),
        PanelEntry(
            key = "refresh",
            title = stringResource(ManageR.string.scan_refresh_all),
            onClick = {
                actions.scan(LibraryScan.RefreshAll)
                onClose()
            },
            description = stringResource(ManageR.string.scan_refresh_all_description),
            icon = CineIcons.Retry,
        ),
        PanelEntry(
            key = "problems",
            title = stringResource(ManageR.string.problems_title),
            onClick = {
                onClose()
                onProblems()
            },
            description = stringResource(ManageR.string.library_problems_description),
            icon = CineIcons.Warning,
        ),
        PanelEntry(
            key = "select",
            title = stringResource(ManageR.string.library_select),
            onClick = {
                onClose()
                actions.startSelection(null)
            },
            description = stringResource(ManageR.string.library_select_description),
            icon = CineIcons.Checked,
        ),
    )
}

private const val PERCENT = 100

@Composable
private fun sortEntries(
    state: LibraryUiState,
    actions: LibraryActions,
): List<PanelEntry> =
    SortField.entries.map { field ->
        val current = state.browse.sort.field == field
        PanelEntry(
            key = field.name,
            title = field.label(),
            onClick = { actions.sortBy(field) },
            value = if (current) state.browse.sort.directionLabel() else null,
            checked = current,
        )
    }

@Composable
private fun viewEntries(
    state: LibraryUiState,
    actions: LibraryActions,
    onClose: () -> Unit,
): List<PanelEntry> {
    val views =
        LibraryView.entries.map { view ->
            PanelEntry(
                key = view.name,
                title = view.label(),
                onClick = {
                    actions.setView(view)
                    onClose()
                },
                checked = state.browse.view == view,
            )
        }
    val group =
        PanelEntry(
            key = "group",
            title = stringResource(R.string.view_group),
            onClick = actions::toggleGroupCollections,
            value = stringResource(if (state.browse.groupCollections) R.string.yes else R.string.no),
        )
    return if (state.groupable) views + group else views
}

/**
 * The categories of filters with their values in use, or the values of one category. A value is
 * toggled in place; several values of a category add up.
 */
@Composable
private fun filterEntries(
    state: LibraryUiState,
    actions: LibraryActions,
    category: FilterCategory?,
    onCategory: (FilterCategory?) -> Unit,
): List<PanelEntry> {
    val filters = state.browse.filters
    val options = state.options ?: FilterOptions()
    if (category == null) {
        return FilterCategory.entries.map { entry ->
            PanelEntry(
                entry.name,
                entry.label(),
                { onCategory(entry) },
                value = filters.valueOf(entry),
                description = if (entry in INDEXED) state.index.note() else null,
            )
        }
    }
    val none = PanelEntry("none", stringResource(R.string.filter_none_available), {})

    @Composable
    fun <T> choices(
        values: List<T>,
        label: @Composable (T) -> String,
        selected: (T) -> Boolean,
        toggle: (T) -> ItemFilters,
    ): List<PanelEntry> =
        values
            .map { value ->
                PanelEntry(
                    value.toString(),
                    label(value),
                    { actions.setFilters(toggle(value)) },
                    checked = selected(value),
                )
            }.ifEmpty { listOf(none) }

    return when (category) {
        FilterCategory.Watch -> {
            choices(WatchStatus.entries, { it.label() }, { filters.watch == it }) {
                filters.copy(watch = if (filters.watch == it) null else it)
            }
        }

        FilterCategory.Genre -> {
            choices(
                options.genres,
                { it },
                { it in filters.genres },
            ) { filters.copy(genres = filters.genres.toggle(it)) }
        }

        FilterCategory.Decade -> {
            choices(options.decades, { decadeLabel(it) }, { it in filters.decades }) {
                filters.copy(decades = filters.decades.toggle(it))
            }
        }

        FilterCategory.Year -> {
            choices(options.years.sortedDescending(), { it.toString() }, { it in filters.years }) {
                filters.copy(years = filters.years.toggle(it))
            }
        }

        FilterCategory.Rating -> {
            choices(RATINGS, { stringResource(R.string.filter_rating_value, it) }, { filters.minRating == it }) {
                filters.copy(minRating = if (filters.minRating == it) null else it)
            }
        }

        FilterCategory.Resolution -> {
            choices(Resolution.entries, { it.label }, { filters.resolution == it }) {
                filters.copy(resolution = if (filters.resolution == it) null else it)
            }
        }

        FilterCategory.Range -> {
            choices(RANGES, { it.label() }, { it in filters.ranges }) {
                filters.copy(ranges = filters.ranges.toggle(it))
            }
        }

        FilterCategory.AudioFormat -> {
            choices(AudioFormat.entries, { it.label() }, { it in filters.audioFormats }) {
                filters.copy(audioFormats = filters.audioFormats.toggle(it))
            }
        }

        FilterCategory.Codec -> {
            choices(VideoCodec.entries, { it.label() }, { it in filters.videoCodecs }) {
                filters.copy(videoCodecs = filters.videoCodecs.toggle(it))
            }
        }

        FilterCategory.Audio -> {
            choices(options.audioLanguages, { languageName(it) }, { it in filters.audioLanguages }) {
                filters.copy(audioLanguages = filters.audioLanguages.toggle(it))
            }
        }

        FilterCategory.Subtitles -> {
            choices(options.subtitleLanguages, { languageName(it) }, { it in filters.subtitleLanguages }) {
                filters.copy(subtitleLanguages = filters.subtitleLanguages.toggle(it))
            }
        }

        FilterCategory.Certification -> {
            choices(options.officialRatings, { it }, { it in filters.officialRatings }) {
                filters.copy(officialRatings = filters.officialRatings.toggle(it))
            }
        }

        FilterCategory.Studio -> {
            choices(
                options.studios,
                { it.name },
                { it in filters.studios },
            ) { filters.copy(studios = filters.studios.toggle(it)) }
        }

        FilterCategory.Person -> {
            choices(
                options.people,
                { it.name },
                { it in filters.people },
            ) { filters.copy(people = filters.people.toggle(it)) }
        }

        FilterCategory.Collection -> {
            choices(options.collections, { it.name }, { filters.collection == it }) {
                filters.copy(collection = if (filters.collection == it) null else it)
            }
        }

        FilterCategory.Tag -> {
            choices(options.tags, { it }, { it in filters.tags }) { filters.copy(tags = filters.tags.toggle(it)) }
        }

        FilterCategory.Favorite -> {
            listOf(
                PanelEntry(
                    key = "favorite",
                    title = stringResource(R.string.filter_favorite_only),
                    onClick = { actions.setFilters(filters.copy(favorite = !filters.favorite)) },
                    checked = filters.favorite,
                ),
            )
        }
    }
}

private fun <T> Set<T>.toggle(value: T): Set<T> = if (value in this) this - value else this + value

@Composable
internal fun FilterCategory.label(): String =
    stringResource(
        when (this) {
            FilterCategory.Watch -> R.string.filter_watch
            FilterCategory.Genre -> R.string.filter_genre
            FilterCategory.Decade -> R.string.filter_decade
            FilterCategory.Year -> R.string.filter_year
            FilterCategory.Rating -> R.string.filter_rating
            FilterCategory.Resolution -> R.string.filter_resolution
            FilterCategory.Range -> R.string.filter_range
            FilterCategory.AudioFormat -> R.string.filter_audio_format
            FilterCategory.Codec -> R.string.filter_codec
            FilterCategory.Audio -> R.string.filter_audio
            FilterCategory.Subtitles -> R.string.filter_subtitles
            FilterCategory.Certification -> R.string.filter_certification
            FilterCategory.Studio -> R.string.filter_studio
            FilterCategory.Person -> R.string.filter_person
            FilterCategory.Collection -> R.string.filter_collection
            FilterCategory.Tag -> R.string.filter_tag
            FilterCategory.Favorite -> R.string.filter_favorite
        },
    )

/** The values in use for a category, as the category row shows them; null when none. */
@Composable
private fun ItemFilters.valueOf(category: FilterCategory): String? {
    fun names(values: Collection<NamedId>) =
        values
            .map { it.name }
            .sorted()
            .joinToString(", ")
            .ifEmpty { null }

    fun texts(values: Collection<String>) = values.sorted().joinToString(", ").ifEmpty { null }
    return when (category) {
        FilterCategory.Watch -> {
            watch?.label()
        }

        FilterCategory.Genre -> {
            texts(genres)
        }

        FilterCategory.Decade -> {
            decades
                .sorted()
                .map { decadeLabel(it) }
                .joinToString(", ")
                .ifEmpty { null }
        }

        FilterCategory.Year -> {
            texts(years.map { it.toString() })
        }

        FilterCategory.Rating -> {
            minRating?.let { stringResource(R.string.filter_rating_value, it) }
        }

        FilterCategory.Resolution -> {
            resolution?.label
        }

        FilterCategory.Range -> {
            ranges
                .sortedBy { it.ordinal }
                .map { it.label() }
                .joinToString(", ")
                .ifEmpty { null }
        }

        FilterCategory.AudioFormat -> {
            audioFormats
                .sortedBy { it.ordinal }
                .map { it.label() }
                .joinToString(", ")
                .ifEmpty { null }
        }

        FilterCategory.Codec -> {
            videoCodecs
                .sortedBy { it.ordinal }
                .map { it.label() }
                .joinToString(", ")
                .ifEmpty { null }
        }

        FilterCategory.Audio -> {
            texts(audioLanguages.map(::languageName))
        }

        FilterCategory.Subtitles -> {
            texts(subtitleLanguages.map(::languageName))
        }

        FilterCategory.Certification -> {
            texts(officialRatings)
        }

        FilterCategory.Studio -> {
            names(studios)
        }

        FilterCategory.Person -> {
            names(people)
        }

        FilterCategory.Collection -> {
            collection?.name
        }

        FilterCategory.Tag -> {
            texts(tags)
        }

        FilterCategory.Favorite -> {
            if (favorite) stringResource(R.string.yes) else null
        }
    }
}

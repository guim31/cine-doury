package fr.guim.cinedoury.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import fr.guim.cinedoury.core.data.index.IndexStatus
import fr.guim.cinedoury.core.data.library.icon
import fr.guim.cinedoury.core.designsystem.component.CineIcons
import fr.guim.cinedoury.core.designsystem.component.InitialFocus
import fr.guim.cinedoury.core.designsystem.component.ListRow
import fr.guim.cinedoury.core.designsystem.component.ListSectionTitle
import fr.guim.cinedoury.core.designsystem.component.SidePanel
import fr.guim.cinedoury.core.designsystem.component.rememberCineFocus
import fr.guim.cinedoury.core.designsystem.theme.CineSpacing
import fr.guim.cinedoury.core.model.Library
import fr.guim.cinedoury.core.model.LibraryIcon
import fr.guim.cinedoury.core.model.LibraryId
import fr.guim.cinedoury.core.ui.toDateText
import fr.guim.cinedoury.core.ui.toTimeText
import fr.guim.cinedoury.core.ui.vector
import java.time.ZoneId

/** Preview tags of the settings of J3. */
object SettingsPreview {
    const val INDEX = "index-row"

    fun library(index: Int) = "library-$index"

    fun icon(index: Int) = "icon-$index"
}

/** A [LibraryId] kept across configuration changes. */
internal val LibraryIdSaver: Saver<LibraryId?, String> =
    Saver(save = { it?.value ?: "" }, restore = { value -> value.takeIf { it.isNotEmpty() }?.let(::LibraryId) })

/**
 * « Bibliothèques »: each library with its icon in the rail, OK to choose another; then where the
 * local index stands, OK to read everything again (`SPEC.md` § 4.10).
 */
internal fun LazyListScope.libraryItems(
    state: SettingsState,
    onIcon: (LibraryId) -> Unit,
    onRebuildIndex: () -> Unit,
    requesters: (LibraryId) -> FocusRequester,
) {
    if (state.libraries.isEmpty()) return
    item { ListSectionTitle(stringResource(R.string.section_libraries)) }
    itemsIndexed(state.libraries, key = { _, library -> library.id.value }) { index, library ->
        val icon = library.icon(state.chosen)
        ListRow(
            title = library.name,
            description =
                stringResource(
                    if (library.id in state.chosen) R.string.library_icon_chosen else R.string.library_icon_automatic,
                ),
            value = icon.label(),
            icon = icon.vector,
            onClick = { onIcon(library.id) },
            focusRequester = requesters(library.id),
            focus = rememberCineFocus(SettingsPreview.library(index)),
            modifier = Modifier.testTag(SettingsPreview.library(index)),
        )
    }
    item {
        ListRow(
            title = stringResource(R.string.index_title),
            description = state.index.text(),
            icon = CineIcons.Retry,
            onClick = onRebuildIndex,
            focus = rememberCineFocus(SettingsPreview.INDEX),
            modifier = Modifier.testTag(SettingsPreview.INDEX),
        )
    }
}

/**
 * « À jour : 1 523 films et 10 112 épisodes, le 25 oct. 2026 à 14 h 02 », « Mise à jour : 3 200 sur
 * 11 500 titres », a failure, or not built yet: a discreet line, never in the way.
 */
@Composable
private fun IndexStatus.text(): String {
    val total = total
    val updated = updatedAt?.atZone(ZoneId.systemDefault())
    return when {
        running && total != null -> {
            stringResource(R.string.index_running, read, total)
        }

        running -> {
            stringResource(R.string.index_starting)
        }

        error != null -> {
            stringResource(R.string.index_failed)
        }

        updated != null -> {
            stringResource(
                R.string.index_ready,
                films,
                episodes,
                updated.toLocalDate().toDateText(),
                updated.toLocalTime().toTimeText(),
            )
        }

        else -> {
            stringResource(R.string.index_empty)
        }
    }
}

/** The icons a library can take, « Automatique » first (the one its name suggests). */
@Composable
internal fun IconPanel(
    library: Library?,
    chosen: LibraryIcon?,
    onChoose: (LibraryId, LibraryIcon?) -> Unit,
) {
    SidePanel(visible = library != null, title = library?.name.orEmpty(), modifier = Modifier.testTag("icon-panel")) {
        if (library == null) return@SidePanel
        val first = remember(library.id) { FocusRequester() }
        val automatic = LibraryIcon.defaultFor(library.kind, library.name)
        val entries = listOf<LibraryIcon?>(null) + LibraryIcon.entries
        LazyColumn(
            contentPadding = PaddingValues(vertical = CineSpacing.xs, horizontal = CineSpacing.xs),
            verticalArrangement = Arrangement.spacedBy(CineSpacing.xs),
        ) {
            itemsIndexed(entries, key = { _, icon -> icon?.name ?: "automatic" }) { index, icon ->
                val selected = icon == chosen
                ListRow(
                    title = icon?.label() ?: stringResource(R.string.library_icon_auto, automatic.label()),
                    icon = (icon ?: automatic).vector,
                    trailingIcon = if (selected) CineIcons.Watched else null,
                    onClick = { onChoose(library.id, icon) },
                    focusRequester = if (selected) first else null,
                    focus = rememberCineFocus(SettingsPreview.icon(index)),
                    modifier = Modifier.testTag(SettingsPreview.icon(index)),
                )
            }
        }
        InitialFocus(first, key = library.id)
    }
}

@Composable
internal fun LibraryIcon.label(): String =
    stringResource(
        when (this) {
            LibraryIcon.Movies -> R.string.icon_movies
            LibraryIcon.Shows -> R.string.icon_shows
            LibraryIcon.Animation -> R.string.icon_animation
            LibraryIcon.Anime -> R.string.icon_anime
            LibraryIcon.Documentaries -> R.string.icon_documentaries
            LibraryIcon.Stage -> R.string.icon_stage
            LibraryIcon.Sport -> R.string.icon_sport
            LibraryIcon.OnlineVideo -> R.string.icon_online
            LibraryIcon.Kids -> R.string.icon_kids
            LibraryIcon.Collections -> R.string.icon_collections
            LibraryIcon.Folder -> R.string.icon_folder
        },
    )

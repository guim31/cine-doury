package fr.guim.cinedoury.feature.manage

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.guim.cinedoury.core.data.manage.ItemChange
import fr.guim.cinedoury.core.data.manage.ManageUpdates
import fr.guim.cinedoury.core.model.LibraryId
import fr.guim.cinedoury.core.model.LibraryProblems
import fr.guim.cinedoury.core.model.ManageRights
import fr.guim.cinedoury.core.model.MediaItem
import fr.guim.cinedoury.core.source.SourceException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The tabs of « Éléments à problème ». */
enum class ProblemTab {
    Unidentified,
    WithoutPoster,
    MissingFiles,
}

/** Where the check of the files stands: [checked] of [total]. */
@Immutable
data class FileCheck(
    val checked: Int,
    val total: Int,
)

/**
 * « Éléments à problème » of a library (`FONCTIONNALITES.md` § 9).
 *
 * @param problems null while read.
 * @param missing null until the files are checked (on demand: it reads one byte of each file).
 * @param chosen the item whose actions are shown.
 */
@Immutable
data class ProblemsState(
    val library: LibraryId,
    val tab: ProblemTab = ProblemTab.Unidentified,
    val problems: LibraryProblems? = null,
    val missing: List<MediaItem>? = null,
    val checking: FileCheck? = null,
    val rights: ManageRights = ManageRights.NONE,
    val chosen: MediaItem? = null,
    val deletion: Deletion? = null,
    val error: SourceException? = null,
) {
    /** The items of the tab shown. */
    val shown: List<MediaItem>?
        get() =
            when (tab) {
                ProblemTab.Unidentified -> problems?.unidentified
                ProblemTab.WithoutPoster -> problems?.withoutPoster
                ProblemTab.MissingFiles -> missing
            }
}

interface ProblemsActions {
    fun selectTab(tab: ProblemTab)

    fun checkFiles()

    fun choose(item: MediaItem?)

    /** « Supprimer de la bibliothèque »: the confirmation, with the files and their size. */
    fun askDelete(item: MediaItem)

    fun confirmDelete()

    fun cancelDelete()

    fun dismissError()
}

class ProblemsViewModel(
    library: LibraryId,
    private val manager: ItemManager,
    updates: ManageUpdates,
) : ViewModel(),
    ProblemsActions {
    private val local = MutableStateFlow(ProblemsState(library, rights = manager.cachedRights))
    val state: StateFlow<ProblemsState> = local.asStateFlow()

    init {
        load()
        viewModelScope.launch {
            val rights = guarded { manager.rights() } ?: return@launch
            local.update { it.copy(rights = rights) }
        }
        // Back from « Corriger l'identification »: the list is read again.
        viewModelScope.launch {
            updates.updates.collect { change ->
                when (change) {
                    is ItemChange.Deleted -> local.update { it.without(change) }
                    is ItemChange.Changed -> load()
                }
            }
        }
    }

    private fun load() {
        viewModelScope.launch {
            val problems = guarded { manager.problems(local.value.library) } ?: return@launch
            local.update { it.copy(problems = problems) }
        }
    }

    private suspend fun <T> guarded(block: suspend () -> T): T? =
        try {
            block()
        } catch (error: SourceException) {
            local.update { it.copy(error = error, deletion = null, checking = null) }
            null
        }

    override fun selectTab(tab: ProblemTab) = local.update { it.copy(tab = tab) }

    override fun checkFiles() {
        if (local.value.checking != null) return
        local.update { it.copy(checking = FileCheck(0, 0), missing = null) }
        viewModelScope.launch {
            val missing =
                guarded {
                    manager.missingFiles(local.value.library) { checked, total ->
                        local.update { it.copy(checking = FileCheck(checked, total)) }
                    }
                } ?: return@launch
            local.update { it.copy(missing = missing, checking = null) }
        }
    }

    override fun choose(item: MediaItem?) = local.update { it.copy(chosen = item) }

    override fun askDelete(item: MediaItem) {
        local.update { it.copy(chosen = null) }
        viewModelScope.launch {
            val preview = guarded { manager.deletionPreview(item) } ?: return@launch
            local.update { it.copy(deletion = Deletion(preview)) }
        }
    }

    override fun confirmDelete() {
        val deletion = local.value.deletion ?: return
        if (deletion.preview.needsSecondConfirmation && !deletion.second) {
            local.update { it.copy(deletion = deletion.copy(second = true)) }
            return
        }
        local.update { it.copy(deletion = deletion.copy(running = true)) }
        viewModelScope.launch {
            guarded { manager.delete(deletion.preview) } ?: return@launch
            local.update { it.copy(deletion = null) }
        }
    }

    override fun cancelDelete() = local.update { it.copy(deletion = null) }

    override fun dismissError() = local.update { it.copy(error = null) }
}

/** [change] taken out of every list. */
private fun ProblemsState.without(change: ItemChange.Deleted): ProblemsState {
    fun List<MediaItem>.kept() = filterNot { it.id in change || it.episode?.seriesId?.let(change::contains) == true }
    return copy(
        problems = problems?.let { LibraryProblems(it.unidentified.kept(), it.withoutPoster.kept()) },
        missing = missing?.kept(),
    )
}

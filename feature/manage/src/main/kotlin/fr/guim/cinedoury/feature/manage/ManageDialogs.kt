package fr.guim.cinedoury.feature.manage

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import fr.guim.cinedoury.core.designsystem.component.CineIcons
import fr.guim.cinedoury.core.designsystem.component.ConfirmDialog
import fr.guim.cinedoury.core.designsystem.component.InitialFocus
import fr.guim.cinedoury.core.designsystem.component.MessageDialog
import fr.guim.cinedoury.core.designsystem.component.StatusPill
import fr.guim.cinedoury.core.designsystem.component.rememberCineFocus
import fr.guim.cinedoury.core.designsystem.theme.CineSpacing
import fr.guim.cinedoury.core.model.DeletionPreview
import fr.guim.cinedoury.core.model.ItemKind
import fr.guim.cinedoury.core.source.SourceException
import fr.guim.cinedoury.core.ui.SEPARATOR
import fr.guim.cinedoury.core.ui.sizeText
import fr.guim.cinedoury.core.ui.toErrorText

/** Where a deletion stands: its first confirmation, the second one of a whole series, then the call. */
@Immutable
data class Deletion(
    val preview: DeletionPreview,
    val second: Boolean = false,
    val running: Boolean = false,
)

/** Preview tags of the dialogs, for tests and screenshots. */
object ManagePreview {
    const val DELETE_CANCEL = "delete-cancel"
    const val DELETE_CONFIRM = "delete-confirm"
    const val DIALOG = "manage-dialog"
    const val NOTICE = "manage-notice"
    const val ERROR_CLOSE = "manage-error-close"
}

/**
 * The confirmation of a deletion (`UI.md` § 8, « Action destructrice »): a `surface-2` box with the
 * title, the files and their size (and the episodes of a season or a series), « Supprimer » in
 * `danger` and the focus on « Annuler ». A whole series asks a second time; nothing is deleted
 * before the last « Supprimer ».
 */
@Composable
fun DeleteDialog(
    deletion: Deletion,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val preview = deletion.preview
    val item = preview.item
    val cancel = remember(deletion.second) { FocusRequester() }
    val title: String
    val message: String
    val confirm: String
    if (deletion.second) {
        val count = preview.episodeCount ?: 0
        title = stringResource(R.string.delete_series_title)
        message = pluralStringResource(R.plurals.delete_series_message, count, count, item.title)
        confirm = stringResource(R.string.delete_series_confirm)
    } else {
        val facts =
            listOfNotNull(
                preview.sizeBytes?.let { stringResource(R.string.delete_size, it.sizeText()) },
                preview.episodeCount?.let { pluralStringResource(R.plurals.delete_episodes, it, it) },
            ).joinToString(SEPARATOR)
        val warning =
            stringResource(
                if (item.kind == ItemKind.Collection) R.string.delete_message_collection else R.string.delete_message,
            )
        title = stringResource(R.string.delete_title, item.title)
        message = (preview.paths + listOf(facts).filter { it.isNotEmpty() } + warning).joinToString("\n")
        confirm = stringResource(R.string.delete_confirm)
    }
    ConfirmDialog(
        title = title,
        message = message,
        confirmLabel = confirm,
        cancelLabel = stringResource(R.string.delete_cancel),
        onConfirm = { if (!deletion.running) onConfirm() },
        onCancel = onCancel,
        cancelFocusRequester = cancel,
        cancelFocus = rememberCineFocus(ManagePreview.DELETE_CANCEL),
        confirmFocus = rememberCineFocus(ManagePreview.DELETE_CONFIRM),
        modifier = modifier.testTag(ManagePreview.DIALOG),
    )
    // The focus on « Annuler », at each step: OK pressed twice never deletes a series.
    InitialFocus(cancel, key = deletion.second)
}

/** A failure of a management action, in two sentences (`UI.md` § 10), never a false success. */
@Composable
fun ManageErrorDialog(
    error: SourceException,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val text = error.toErrorText()
    val close = remember { FocusRequester() }
    MessageDialog(
        title = text.title,
        message = text.message,
        buttonLabel = stringResource(R.string.action_close),
        onDismiss = onDismiss,
        buttonFocusRequester = close,
        buttonFocus = rememberCineFocus(ManagePreview.ERROR_CLOSE),
        modifier = modifier.testTag(ManagePreview.DIALOG),
    )
    InitialFocus(close)
}

/** What a management action said it did, at the bottom of the screen, never focusable. */
@Immutable
enum class ManageNotice {
    RefreshRequested,
    Refreshed,
    Identified,
    ImageChanged,
    ScanRequested,
    ScanDone,
    SelectionDone,
}

@Composable
fun ManageNoticePill(
    notice: ManageNotice?,
    modifier: Modifier = Modifier,
) {
    if (notice == null) return
    val text =
        stringResource(
            when (notice) {
                ManageNotice.RefreshRequested -> R.string.notice_refresh_requested
                ManageNotice.Refreshed -> R.string.notice_refreshed
                ManageNotice.Identified -> R.string.notice_identified
                ManageNotice.ImageChanged -> R.string.notice_image
                ManageNotice.ScanRequested -> R.string.scan_requested
                ManageNotice.ScanDone -> R.string.scan_done
                ManageNotice.SelectionDone -> R.string.selection_applied
            },
        )
    Box(modifier.fillMaxSize().padding(CineSpacing.xl), contentAlignment = Alignment.BottomCenter) {
        StatusPill(text = text, icon = CineIcons.Watched, modifier = Modifier.testTag(ManagePreview.NOTICE))
    }
}

package fr.guim.cinedoury.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.Text
import fr.guim.cinedoury.core.designsystem.component.CineIcons
import fr.guim.cinedoury.core.designsystem.component.ConfirmDialog
import fr.guim.cinedoury.core.designsystem.component.InitialFocus
import fr.guim.cinedoury.core.designsystem.component.ListRow
import fr.guim.cinedoury.core.designsystem.component.PrimaryButton
import fr.guim.cinedoury.core.designsystem.component.SidePanel
import fr.guim.cinedoury.core.designsystem.component.rememberCineFocus
import fr.guim.cinedoury.core.designsystem.theme.CineSpacing
import fr.guim.cinedoury.core.designsystem.theme.CineTheme
import fr.guim.cinedoury.core.model.RequestSeason
import fr.guim.cinedoury.core.model.RequestStatus

/**
 * « Demander » (`SPEC.md` § 4.12): a film asks for a confirmation, the focus on « Annuler » so that
 * no request leaves by mistake; a series opens the panel of its seasons, those not there yet
 * chosen, the focus on the first.
 */
@Composable
internal fun RequestDialogs(
    dialog: RequestDialog?,
    actions: SearchActions,
) {
    if (dialog == null) return
    when (dialog) {
        is RequestDialog.Confirm -> {
            val cancel = remember { FocusRequester() }
            InitialFocus(cancel)
            ConfirmDialog(
                title = stringResource(R.string.request_confirm_title, dialog.candidate.title),
                message = stringResource(R.string.request_confirm_message),
                confirmLabel = stringResource(R.string.request_confirm),
                cancelLabel = stringResource(R.string.request_cancel),
                onConfirm = actions::confirmRequest,
                onCancel = actions::dismissRequest,
                destructive = false,
                cancelFocusRequester = cancel,
                cancelFocus = rememberCineFocus(SearchPreview.DIALOG_CANCEL),
                confirmFocus = rememberCineFocus(SearchPreview.DIALOG_CONFIRM),
                modifier = Modifier.testTag("request-dialog"),
            )
        }

        is RequestDialog.Failed -> {
            val cancel = remember { FocusRequester() }
            InitialFocus(cancel)
            ConfirmDialog(
                title = stringResource(R.string.request_failed_title),
                message = stringResource(R.string.request_failed_message),
                confirmLabel = stringResource(R.string.request_retry),
                cancelLabel = stringResource(R.string.request_cancel),
                onConfirm = actions::confirmRequest,
                onCancel = actions::dismissRequest,
                destructive = false,
                cancelFocusRequester = cancel,
                modifier = Modifier.testTag("request-failed"),
            )
        }

        is RequestDialog.Seasons -> {
            SeasonsPanel(dialog, actions)
        }
    }
}

@Composable
private fun SeasonsPanel(
    dialog: RequestDialog.Seasons,
    actions: SearchActions,
) {
    val first = remember { FocusRequester() }
    val seasons = dialog.seasons
    SidePanel(
        visible = true,
        title = stringResource(R.string.request_seasons_title, dialog.candidate.title),
        modifier = Modifier.testTag("seasons-panel"),
    ) {
        if (seasons == null) {
            Text(
                text = stringResource(R.string.request_seasons_loading),
                style = CineTheme.typography.body,
                color = CineTheme.colors.text2,
            )
            return@SidePanel
        }
        Column(verticalArrangement = Arrangement.spacedBy(CineSpacing.xs)) {
            seasons.forEachIndexed { index, season ->
                SeasonRow(
                    season = season,
                    chosen = season.number in dialog.chosen,
                    onClick = { actions.toggleSeason(season.number) },
                    focusRequester = first.takeIf { index == 0 },
                    tag = SearchPreview.season(index),
                )
            }
        }
        val count = dialog.chosen.size
        if (count == 0) {
            Text(
                text = stringResource(R.string.request_seasons_none),
                style = CineTheme.typography.caption,
                color = CineTheme.colors.text2,
                modifier = Modifier.padding(top = CineSpacing.s),
            )
        } else {
            PrimaryButton(
                text = pluralStringResource(R.plurals.request_seasons_confirm, count, count),
                icon = CineIcons.Request,
                onClick = actions::confirmRequest,
                focus = rememberCineFocus(SearchPreview.SEASONS_CONFIRM),
                modifier = Modifier.padding(top = CineSpacing.s).fillMaxWidth().testTag(SearchPreview.SEASONS_CONFIRM),
            )
        }
    }
    InitialFocus(first, key = seasons != null, enabled = seasons != null)
}

@Composable
private fun SeasonRow(
    season: RequestSeason,
    chosen: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester?,
    tag: String,
) {
    val status =
        when (season.status) {
            RequestStatus.Requested -> stringResource(R.string.status_requested)
            RequestStatus.PartlyAvailable -> stringResource(R.string.status_partly)
            RequestStatus.Available -> stringResource(R.string.status_available)
            RequestStatus.None -> null
        }
    ListRow(
        title = season.name ?: stringResource(R.string.request_season, season.number),
        description = pluralStringResource(R.plurals.request_season_episodes, season.episodes, season.episodes),
        value = if (chosen) stringResource(R.string.request_season_chosen) else status,
        trailingIcon = if (chosen) CineIcons.Watched else null,
        onClick = onClick,
        focusRequester = focusRequester,
        focus = rememberCineFocus(tag),
        modifier = Modifier.testTag(tag),
    )
}

package fr.guim.cinedoury.feature.settings.page

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.Text
import fr.guim.cinedoury.core.designsystem.component.CineIcons
import fr.guim.cinedoury.core.designsystem.component.InitialFocus
import fr.guim.cinedoury.core.designsystem.component.ListRow
import fr.guim.cinedoury.core.designsystem.component.ListSectionTitle
import fr.guim.cinedoury.core.designsystem.component.SidePanel
import fr.guim.cinedoury.core.designsystem.component.StepperRow
import fr.guim.cinedoury.core.designsystem.component.Wordmark
import fr.guim.cinedoury.core.designsystem.component.rememberCineFocus
import fr.guim.cinedoury.core.designsystem.theme.CineSizes
import fr.guim.cinedoury.core.designsystem.theme.CineSpacing
import fr.guim.cinedoury.core.designsystem.theme.CineTheme
import fr.guim.cinedoury.feature.settings.R
import fr.guim.cinedoury.core.ui.R as UiR

/** Tags of the pages of settings, for tests and screenshots. */
object SettingsPagePreview {
    const val LIST = "settings-page"
    const val PANEL = "choice-panel"

    fun row(key: String) = "setting-$key"

    fun option(index: Int) = "option-$index"
}

/**
 * A page of settings (`FONCTIONNALITES.md` § 12, `UI.md` § 2): the title on the left, the lines on
 * the right, grouped by who they belong to (the profile, this device). A choice opens its values in a
 * panel at the right, the current one checked and focused; OK chooses, « Retour » closes, and the
 * focus comes back to the line.
 *
 * @param subtitle a line under the title (the profile, the server).
 * @param focusKey the line focused first (the first line otherwise).
 * @param previewChoice the choice whose panel is open (screenshots).
 * @param status under the title: a notice, the preview of the subtitles.
 */
@Composable
fun SettingsPageScreen(
    title: String,
    items: List<SettingItem>,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    focusKey: String? = null,
    previewChoice: String? = null,
    status: (@Composable () -> Unit)? = null,
) {
    var open by rememberSaveable { mutableStateOf(previewChoice) }
    var back by remember { mutableStateOf<String?>(null) }
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    val first = items.firstOrNull { it !is SettingItem.Section }?.key
    Box(modifier.fillMaxSize().background(CineTheme.colors.background)) {
        Row(Modifier.padding(start = CineSpacing.safeHorizontal * 2, end = CineSpacing.safeHorizontal)) {
            Column(
                modifier =
                    Modifier
                        .padding(top = CineSpacing.safeVertical + CineSpacing.m, end = CineSpacing.xl)
                        .width(CineSizes.avatarLarge * 2),
                verticalArrangement = Arrangement.spacedBy(CineSpacing.s),
            ) {
                Wordmark(stringResource(UiR.string.brand_name))
                Text(
                    text = title,
                    style = CineTheme.typography.title1,
                    color = CineTheme.colors.text,
                    modifier = Modifier.padding(top = CineSpacing.l),
                )
                if (subtitle != null) {
                    Text(text = subtitle, style = CineTheme.typography.caption, color = CineTheme.colors.text2)
                }
                status?.invoke()
            }
            LazyColumn(
                state = rememberLazyListState(),
                modifier = Modifier.width(CineSizes.settingsWidth).fillMaxHeight().testTag(SettingsPagePreview.LIST),
                contentPadding =
                    PaddingValues(
                        top = CineSpacing.safeVertical + CineSpacing.m,
                        bottom = CineSpacing.safeVertical + CineSpacing.xxl,
                        start = CineSpacing.xs,
                        end = CineSpacing.xs,
                    ),
                verticalArrangement = Arrangement.spacedBy(CineSpacing.xs),
            ) {
                itemsIndexed(items, key = {
                    _,
                    item,
                    ->
                    item.key
                }, contentType = { _, item -> item::class }) { _, item ->
                    SettingLine(item, requesters.requester(item.key)) { open = item.key }
                }
            }
        }
        val choice = items.firstOrNull { it.key == open } as? SettingItem.Choice
        ChoicePanel(
            choice = choice,
            onChoose = { index ->
                choice?.onSelect?.invoke(index)
                back = choice?.key
                open = null
            },
        )
    }
    BackHandler(enabled = open != null) {
        back = open
        open = null
    }
    val target = (back ?: focusKey ?: first)?.let { requesters.requester(it) }
    if (target != null) InitialFocus(target, key = back, enabled = open == null)
}

@Composable
private fun SettingLine(
    item: SettingItem,
    requester: FocusRequester,
    onOpen: () -> Unit,
) {
    val tag = SettingsPagePreview.row(item.key)
    when (item) {
        is SettingItem.Section -> {
            Column {
                ListSectionTitle(item.title)
                if (item.note != null) {
                    Text(
                        text = item.note,
                        style = CineTheme.typography.caption,
                        color = CineTheme.colors.text2,
                        modifier = Modifier.padding(bottom = CineSpacing.xs),
                    )
                }
            }
        }

        is SettingItem.Toggle -> {
            ListRow(
                title = item.title,
                description = item.description,
                value = stringResource(if (item.checked) R.string.setting_on else R.string.setting_off),
                onClick = { if (item.enabled) item.onToggle() },
                focusRequester = requester,
                focus = rememberCineFocus(tag),
                modifier = Modifier.testTag(tag),
            )
        }

        is SettingItem.Choice -> {
            ListRow(
                title = item.title,
                description = item.description,
                value = item.options.getOrNull(item.selected),
                onClick = { if (item.enabled) onOpen() },
                focusRequester = requester,
                focus = rememberCineFocus(tag),
                modifier = Modifier.testTag(tag),
            )
        }

        is SettingItem.Stepper -> {
            StepperRow(
                title = item.title,
                description = item.description,
                value = item.value,
                onStep = item.onStep,
                canDecrease = item.canDecrease,
                canIncrease = item.canIncrease,
                focusRequester = requester,
                focus = rememberCineFocus(tag),
                modifier = Modifier.fillMaxWidth().testTag(tag),
            )
        }

        is SettingItem.Action -> {
            ListRow(
                title = item.title,
                description = item.description,
                value = item.value,
                icon = item.icon,
                destructive = item.destructive,
                wrapTitle = item.wrap,
                onClick = item.onClick,
                focusRequester = requester,
                focus = rememberCineFocus(tag),
                modifier = Modifier.testTag(tag),
            )
        }
    }
}

/** The values of a choice, the current one checked and focused. */
@Composable
private fun ChoicePanel(
    choice: SettingItem.Choice?,
    onChoose: (Int) -> Unit,
) {
    SidePanel(
        visible = choice != null,
        title = choice?.title.orEmpty(),
        modifier = Modifier.testTag(SettingsPagePreview.PANEL),
    ) {
        if (choice == null) return@SidePanel
        val current = remember(choice.key) { FocusRequester() }
        LazyColumn(
            contentPadding = PaddingValues(CineSpacing.xs),
            verticalArrangement = Arrangement.spacedBy(CineSpacing.xs),
        ) {
            itemsIndexed(choice.options, key = { index, _ -> index }) { index, option ->
                val tag = SettingsPagePreview.option(index)
                ListRow(
                    title = option,
                    trailingIcon = if (index == choice.selected) CineIcons.Watched else null,
                    onClick = { onChoose(index) },
                    focusRequester = if (index == choice.selected) current else null,
                    focus = rememberCineFocus(tag),
                    modifier = Modifier.testTag(tag),
                )
            }
        }
        InitialFocus(current, key = choice.key)
    }
}

/** The requester of a line, made on first use: outside composition, where it may not be built. */
private fun MutableMap<String, FocusRequester>.requester(
    key: String,
): FocusRequester = getOrPut(key) { FocusRequester() }

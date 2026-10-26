package fr.guim.cinedoury.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import fr.guim.cinedoury.core.designsystem.component.CineIcons
import fr.guim.cinedoury.core.source.FailureLog
import fr.guim.cinedoury.core.ui.toTimeText
import fr.guim.cinedoury.feature.settings.page.SettingItem
import fr.guim.cinedoury.feature.settings.page.SettingsPageScreen
import java.time.ZoneId

/**
 * « Journaux de l'appli » (`SPEC.md` § 4.18): the failures caught since the app started, the newest
 * first, each with its time and the class of the error. What `adb logcat -s CineDoury` gives, on
 * the TV.
 */
@Composable
fun LogsScreen(
    modifier: Modifier = Modifier,
    entries: List<FailureLog.Entry> = remember { FailureLog.recent() },
) {
    val items =
        if (entries.isEmpty()) {
            listOf(SettingItem.Action(key = "empty", title = stringResource(R.string.logs_empty), onClick = {}))
        } else {
            entries.asReversed().mapIndexed { index, entry ->
                val time =
                    entry.at
                        .atZone(ZoneId.systemDefault())
                        .toLocalTime()
                        .toTimeText()
                val level =
                    stringResource(
                        if (entry.level ==
                            FailureLog.Level.Error
                        ) {
                            R.string.logs_error
                        } else {
                            R.string.logs_warning
                        },
                    )
                SettingItem.Action(
                    key = "log-$index",
                    title = entry.message,
                    description = listOfNotNull("$time · $level", entry.error).joinToString(" · "),
                    icon = if (entry.level == FailureLog.Level.Error) CineIcons.Warning else CineIcons.Info,
                    wrap = true,
                    onClick = {},
                )
            }
        }
    SettingsPageScreen(
        title = stringResource(R.string.logs_title),
        subtitle = stringResource(R.string.logs_subtitle),
        items = items,
        modifier = modifier.testTag("logs"),
    )
}

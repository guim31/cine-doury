package fr.guim.cinedoury.feature.update

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import fr.guim.cinedoury.core.designsystem.component.CardProgressBar
import fr.guim.cinedoury.core.designsystem.component.FormScreen
import fr.guim.cinedoury.core.designsystem.component.InitialFocus
import fr.guim.cinedoury.core.designsystem.component.PrimaryButton
import fr.guim.cinedoury.core.designsystem.component.SecondaryButton
import fr.guim.cinedoury.core.designsystem.component.rememberCineFocus
import fr.guim.cinedoury.core.designsystem.theme.CineSizes
import fr.guim.cinedoury.core.designsystem.theme.CineSpacing
import fr.guim.cinedoury.core.designsystem.theme.CineTheme
import fr.guim.cinedoury.core.ui.sizeText
import fr.guim.cinedoury.core.ui.toDateText
import fr.guim.cinedoury.core.ui.toTimeText
import fr.guim.cinedoury.core.update.Changelog
import fr.guim.cinedoury.core.update.ChangelogBlock
import fr.guim.cinedoury.core.update.Release
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.time.ZoneId
import fr.guim.cinedoury.core.ui.R as UiR

/** The gestures of the update screen. */
interface UpdateActions {
    fun install()

    fun confirm()

    fun later()

    fun ignore()

    fun retry()

    fun openPermissionSettings()

    fun openAppSettings()
}

/** Tags of the update screen, for tests and screenshots. */
object UpdatePreview {
    const val PRIMARY = "update-primary"
    const val LATER = "update-later"
    const val IGNORE = "update-ignore"
    const val NOTES = "update-notes"
}

/**
 * The update (`SPEC.md` § 4.17): « Retour » or « Plus tard » close it, nothing is installed without
 * « Installer » and Android's own confirmation.
 */
@Composable
fun UpdateRoute(
    onClose: () -> Unit,
    repository: UpdateRepository = koinInject(),
    gate: PackageGate = koinInject(),
) {
    val state by repository.state.collectAsStateWithLifecycle()
    BackHandler {
        repository.dismiss()
        onClose()
    }
    // Back from the settings of Android: « Sources inconnues » may be on now.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { repository.permissionMaybeGiven() }
    UpdateScreen(
        state = state,
        actions =
            object : UpdateActions {
                override fun install() = repository.install()

                override fun confirm() = repository.confirm()

                override fun later() {
                    repository.dismiss()
                    onClose()
                }

                override fun ignore() {
                    repository.ignore()
                    onClose()
                }

                override fun retry() = repository.check()

                override fun openPermissionSettings() = repository.openPermissionSettings()

                override fun openAppSettings() = gate.openAppDetails()
            },
    )
}

@Composable
fun UpdateScreen(
    state: UpdateState,
    actions: UpdateActions,
    modifier: Modifier = Modifier,
) {
    val primary = remember { FocusRequester() }
    val phase = state.phase
    FormScreen(
        brand = stringResource(UiR.string.brand_name),
        title = title(state),
        subtitle = stringResource(R.string.update_current, state.versionName),
        modifier = modifier.testTag("update"),
    ) {
        when (phase) {
            UpdatePhase.Idle, UpdatePhase.Checking -> {
                Paragraph(stringResource(R.string.update_checking))
            }

            UpdatePhase.Development -> {
                Paragraph(stringResource(R.string.update_development_message, state.versionName))
                Buttons(primary, stringResource(R.string.update_close), actions::later)
            }

            UpdatePhase.UpToDate -> {
                Paragraph(stringResource(R.string.update_up_to_date_message, state.versionName))
                Buttons(primary, stringResource(R.string.update_close), actions::later)
            }

            is UpdatePhase.Available -> {
                Available(phase.release, state, phase.ignored)
                Buttons(
                    primary,
                    stringResource(R.string.update_install),
                    actions::install,
                    later = actions::later,
                    ignore = actions::ignore.takeIf { !phase.ignored },
                )
            }

            is UpdatePhase.PermissionMissing -> {
                Paragraph(stringResource(R.string.update_permission_message))
                Paragraph(stringResource(R.string.update_permission_google_tv))
                Paragraph(stringResource(R.string.update_permission_shield))
                Buttons(primary, stringResource(R.string.update_open_settings), actions::openPermissionSettings, later = actions::later)
            }

            is UpdatePhase.Downloading -> {
                Downloading(phase)
                Buttons(primary, stringResource(R.string.update_cancel), actions::later)
            }

            is UpdatePhase.Ready -> {
                Paragraph(stringResource(R.string.update_ready_message))
                Buttons(primary, stringResource(R.string.update_install_now), actions::confirm, later = actions::later)
            }

            is UpdatePhase.SignatureMismatch -> {
                Paragraph(stringResource(R.string.update_signature_message, phase.release.version.name))
                Buttons(primary, stringResource(R.string.update_open_app_settings), actions::openAppSettings, later = actions::later)
            }

            is UpdatePhase.Failed -> {
                Paragraph(phase.error.text())
                Buttons(
                    primary,
                    stringResource(R.string.update_retry),
                    if (phase.release != null && phase.error !is UpdateFailure.Unreachable) actions::install else actions::retry,
                    later = actions::later,
                )
            }
        }
    }
    InitialFocus(primary, key = phase::class)
}

@Composable
private fun title(state: UpdateState): String =
    when (val phase = state.phase) {
        UpdatePhase.Idle, UpdatePhase.Checking -> stringResource(R.string.update_title)
        UpdatePhase.Development -> stringResource(R.string.update_development_title)
        UpdatePhase.UpToDate -> stringResource(R.string.update_up_to_date_title)
        is UpdatePhase.Available -> stringResource(R.string.update_available_title, phase.release.version.name)
        is UpdatePhase.PermissionMissing -> stringResource(R.string.update_permission_title)
        is UpdatePhase.Downloading -> stringResource(R.string.update_downloading_title, phase.release.version.name)
        is UpdatePhase.Ready -> stringResource(R.string.update_ready_title)
        is UpdatePhase.SignatureMismatch -> stringResource(R.string.update_signature_title)
        is UpdatePhase.Failed -> stringResource(R.string.update_failed_title)
    }

@Composable
private fun UpdateFailure.text(): String =
    when (this) {
        UpdateFailure.Unreachable -> {
            stringResource(R.string.update_failed_unreachable)
        }

        is UpdateFailure.RateLimited -> {
            val at = retryAt?.atZone(ZoneId.systemDefault())?.toLocalTime()
            if (at != null) {
                stringResource(R.string.update_failed_rate_limited, at.toTimeText())
            } else {
                stringResource(R.string.update_failed_rate_limited_later)
            }
        }

        UpdateFailure.Invalid -> {
            stringResource(R.string.update_failed_invalid)
        }

        UpdateFailure.Checksum -> {
            stringResource(R.string.update_failed_checksum)
        }

        is UpdateFailure.Install -> {
            stringResource(R.string.update_failed_install)
        }
    }

@Composable
private fun Paragraph(text: String) {
    Text(
        text = text,
        style = CineTheme.typography.body,
        color = CineTheme.colors.text2,
        modifier = Modifier.width(CineSizes.formWidth),
    )
}

/** The version, its date and its changelog: ↑ and ↓ scroll it once it has the focus. */
@Composable
private fun Available(
    release: Release,
    state: UpdateState,
    ignored: Boolean,
) {
    val date = release.publishedAt?.atZone(ZoneId.systemDefault())?.toLocalDate()
    if (date != null) Paragraph(stringResource(R.string.update_published, date.toDateText(), state.versionName))
    if (ignored) Paragraph(stringResource(R.string.update_ignored))
    val blocks = remember(release.notes) { Changelog.parse(release.notes) }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val step = with(LocalDensity.current) { CineSizes.settingsRowHeight.toPx() }
    var notesFocused by remember { mutableStateOf(false) }
    Column(
        modifier =
            Modifier
                .width(CineSizes.settingsWidth)
                .heightIn(max = CineSizes.changelogHeight)
                .background(
                    if (notesFocused) CineTheme.colors.surface2 else CineTheme.colors.surface1,
                    NotesShape,
                ).onPreviewKeyEvent { event ->
                    val delta =
                        when (event.key) {
                            Key.DirectionDown -> step
                            Key.DirectionUp -> -step
                            else -> return@onPreviewKeyEvent false
                        }
                    val atEdge = (delta < 0 && scroll.value == 0) || (delta > 0 && scroll.value == scroll.maxValue)
                    if (atEdge) return@onPreviewKeyEvent false
                    if (event.type == KeyEventType.KeyDown) scope.launch { scroll.animateScrollBy(delta) }
                    true
                }.focusable()
                .onFocusChanged { notesFocused = it.isFocused }
                .verticalScroll(scroll)
                .padding(CineSpacing.m)
                .testTag(UpdatePreview.NOTES),
        verticalArrangement = Arrangement.spacedBy(CineSpacing.xs),
    ) {
        if (blocks.isEmpty()) Paragraph(stringResource(R.string.update_no_notes))
        blocks.forEach { block ->
            when (block) {
                is ChangelogBlock.Heading -> {
                    Text(block.text, style = CineTheme.typography.label, color = CineTheme.colors.text)
                }

                is ChangelogBlock.Bullet -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(CineSpacing.xs)) {
                        Text("•", style = CineTheme.typography.body, color = CineTheme.colors.accent)
                        Text(block.text, style = CineTheme.typography.body, color = CineTheme.colors.text2)
                    }
                }

                is ChangelogBlock.Paragraph -> {
                    Text(block.text, style = CineTheme.typography.body, color = CineTheme.colors.text2)
                }
            }
        }
    }
}

private val NotesShape = RoundedCornerShape(CineSizes.cardCorner)

@Composable
private fun Downloading(phase: UpdatePhase.Downloading) {
    Box(Modifier.width(CineSizes.formWidth)) {
        CardProgressBar(phase.progress)
    }
    Paragraph(
        stringResource(
            R.string.update_downloading_progress,
            (phase.progress * PERCENT).toInt(),
            phase.release.apk.size.sizeText(),
        ),
    )
}

@Composable
private fun Buttons(
    primary: FocusRequester,
    label: String,
    onPrimary: () -> Unit,
    later: (() -> Unit)? = null,
    ignore: (() -> Unit)? = null,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(CineSpacing.m)) {
        PrimaryButton(
            text = label,
            onClick = onPrimary,
            focusRequester = primary,
            focus = rememberCineFocus(UpdatePreview.PRIMARY),
            modifier = Modifier.testTag(UpdatePreview.PRIMARY),
        )
        if (later != null) {
            SecondaryButton(
                text = stringResource(R.string.update_later),
                onClick = later,
                focus = rememberCineFocus(UpdatePreview.LATER),
                modifier = Modifier.testTag(UpdatePreview.LATER),
            )
        }
        if (ignore != null) {
            SecondaryButton(
                text = stringResource(R.string.update_ignore),
                onClick = ignore,
                focus = rememberCineFocus(UpdatePreview.IGNORE),
                modifier = Modifier.testTag(UpdatePreview.IGNORE),
            )
        }
    }
}

private const val PERCENT = 100

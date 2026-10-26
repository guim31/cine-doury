package fr.guim.cinedoury.feature.settings

import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.guim.cinedoury.core.data.ConnectionMode
import fr.guim.cinedoury.core.data.Profile
import fr.guim.cinedoury.core.designsystem.component.CineIcons
import fr.guim.cinedoury.core.designsystem.component.ConfirmDialog
import fr.guim.cinedoury.core.designsystem.component.GeneratedArtwork
import fr.guim.cinedoury.core.designsystem.component.InitialFocus
import fr.guim.cinedoury.core.designsystem.component.StatusPill
import fr.guim.cinedoury.core.designsystem.component.rememberCineFocus
import fr.guim.cinedoury.core.designsystem.theme.CineMotion
import fr.guim.cinedoury.core.designsystem.theme.CineSizes
import fr.guim.cinedoury.core.designsystem.theme.CineSpacing
import fr.guim.cinedoury.core.ui.CollectEvents
import fr.guim.cinedoury.core.ui.RemoteIndicator
import fr.guim.cinedoury.feature.player.SubtitleSample
import fr.guim.cinedoury.feature.settings.page.SettingItem
import fr.guim.cinedoury.feature.settings.page.SettingsPageScreen
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel

/** What the settings ask the app to open. */
interface SettingsNavigation {
    /** A page of settings. */
    fun page(section: SettingsSection)

    fun customizeHome()

    fun pin(
        profile: Profile,
        remove: Boolean,
    )

    fun address(remote: Boolean)

    /** « Demandes (Seerr) » (J5). */
    fun seerr()

    /** « Nom de l'appareil » (J7). */
    fun deviceName()

    /** Looks for an update now, and shows the answer (J7). */
    fun checkUpdate()

    /** « Journaux de l'appli » (J7). */
    fun logs()

    /** « Rapport de diagnostic » (J7). */
    fun report()

    /** Empties the cache of the images of the app (J7). */
    fun clearImageCache()

    fun catalog()

    /** The playback profile of the device, in debug builds (J4). */
    fun deviceProfile()

    fun profileClosed()

    fun serverForgotten()
}

/** Tags of the settings, for tests and screenshots. */
object SettingsPreview {
    const val SEARCH = "search"
    const val FORGET_CANCEL = "forget-cancel"

    fun section(section: SettingsSection) = section.name

    /** The key of the line of [section] on the first page. */
    fun sectionRow(section: SettingsSection) = "setting-${section.name}"
}

@Composable
fun SettingsRoute(
    navigation: SettingsNavigation,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectEvents(viewModel.events) { event -> event.navigate(navigation) }
    SettingsScreen(state = state, navigation = navigation)
}

private fun SettingsEvent.navigate(navigation: SettingsNavigation) =
    when (this) {
        SettingsEvent.ProfileClosed -> navigation.profileClosed()
        SettingsEvent.ServerForgotten -> navigation.serverForgotten()
    }

/**
 * The first page of the settings (`FONCTIONNALITES.md` § 12): one line per theme, each saying what
 * is in it. « Recherche » leads to Seerr directly.
 */
@Composable
fun SettingsScreen(
    state: SettingsState,
    navigation: SettingsNavigation,
    modifier: Modifier = Modifier,
    focusSection: SettingsSection? = null,
) {
    val session = state.session ?: return
    val items =
        buildList {
            SettingsSection.entries.forEach { section ->
                add(
                    SettingItem.Action(
                        key = section.name,
                        title = section.title(),
                        description = section.description(),
                        icon = section.icon,
                        onClick = { navigation.page(section) },
                    ),
                )
                if (section == SettingsSection.Network) {
                    add(
                        SettingItem.Action(
                            key = SettingsPreview.SEARCH,
                            title = stringResource(R.string.theme_search),
                            description = stringResource(R.string.seerr_settings_description),
                            icon = CineIcons.Search,
                            onClick = navigation::seerr,
                        ),
                    )
                }
            }
        }
    SettingsPageScreen(
        title = stringResource(R.string.settings_title),
        subtitle =
            stringResource(
                R.string.settings_subtitle,
                session.profile.user.name,
                session.connection.server.name,
            ),
        items = items,
        focusKey = focusSection?.name,
        status = { if (session.connection.mode == ConnectionMode.Remote) RemoteIndicator() },
        modifier = modifier.testTag("settings"),
    )
}

@Composable
fun SettingsPageRoute(
    section: SettingsSection,
    navigation: SettingsNavigation,
    showDebug: Boolean,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectEvents(viewModel.events) { event -> event.navigate(navigation) }
    val context = LocalContext.current
    val systemReduced =
        remember {
            CineMotion.isReduced(
                asked = false,
                systemAnimatorScale =
                    Settings.Global.getFloat(
                        context.contentResolver,
                        Settings.Global.ANIMATOR_DURATION_SCALE,
                        1f,
                    ),
            )
        }
    SettingsPageScreen(
        section = section,
        state = state,
        actions = viewModel,
        navigation = navigation,
        showDebug = showDebug,
        systemReducedMotion = systemReduced,
        onDismissNotice = viewModel::dismissNotice,
    )
}

/**
 * A page of settings: the lines of [section] (`SettingsPages.kt`); « Sous-titres » shows the
 * subtitles as they will look, at the right; « Oublier ce serveur » asks first, focus on « Annuler ».
 *
 * @param previewChoice the choice whose panel is open (screenshots).
 * @param previewForget the confirmation of « Oublier ce serveur » shown (screenshots).
 */
@Composable
fun SettingsPageScreen(
    section: SettingsSection,
    state: SettingsState,
    actions: SettingsActions,
    navigation: SettingsNavigation,
    modifier: Modifier = Modifier,
    showDebug: Boolean = false,
    systemReducedMotion: Boolean = false,
    onDismissNotice: () -> Unit = {},
    previewChoice: String? = null,
    previewForget: Boolean = false,
) {
    val session = state.session ?: return
    var forget by rememberSaveable { mutableStateOf(previewForget) }
    val callbacks = PageCallbacks(navigation, onForget = { forget = true }, showDebug, systemReducedMotion)
    val items = pageItems(section, state, actions, callbacks)
    Box(modifier) {
        SettingsPageScreen(
            title = section.title(),
            subtitle =
                stringResource(
                    R.string.settings_subtitle,
                    session.profile.user.name,
                    session.connection.server.name,
                ),
            items = items,
            previewChoice = previewChoice,
            status = {
                if (section == SettingsSection.Subtitles) {
                    // The subtitles as they will look, beside their settings.
                    Box(
                        Modifier
                            .padding(top = CineSpacing.m)
                            .size(CineSizes.subtitlePreviewSmall)
                            .clip(PreviewShape),
                    ) {
                        GeneratedArtwork(title = "", compact = true)
                        SubtitleSample(state.player.subtitles, beside = false)
                    }
                }
                state.notice?.let { notice ->
                    StatusPill(text = notice.text(), modifier = Modifier.testTag("settings-notice"))
                    LaunchedEffect(notice) {
                        delay(NOTICE_MILLIS)
                        onDismissNotice()
                    }
                }
            },
            modifier = Modifier.testTag("settings-${section.name}"),
        )
        if (forget) {
            val cancel = remember { FocusRequester() }
            ConfirmDialog(
                title = stringResource(R.string.forget_title),
                message = stringResource(R.string.forget_message, session.connection.server.name),
                confirmLabel = stringResource(R.string.forget_confirm),
                cancelLabel = stringResource(R.string.forget_cancel),
                onConfirm = {
                    forget = false
                    actions.forgetServer()
                },
                onCancel = { forget = false },
                cancelFocusRequester = cancel,
                cancelFocus = rememberCineFocus(SettingsPreview.FORGET_CANCEL),
                modifier = Modifier.testTag("forget-dialog"),
            )
            InitialFocus(cancel)
            BackHandler { forget = false }
        }
    }
}

@Composable
private fun SettingsNotice.text(): String =
    stringResource(
        when (this) {
            SettingsNotice.CacheCleared -> R.string.notice_cache_cleared
            SettingsNotice.IndexRebuilding -> R.string.notice_index
            SettingsNotice.SaveFailed -> R.string.notice_save_failed
        },
    )

private val PreviewShape = RoundedCornerShape(CineSizes.cardCorner)
private const val NOTICE_MILLIS = 4_000L

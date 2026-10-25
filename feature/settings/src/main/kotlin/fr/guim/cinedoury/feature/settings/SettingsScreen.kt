package fr.guim.cinedoury.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import fr.guim.cinedoury.core.data.ConnectionMode
import fr.guim.cinedoury.core.data.Profile
import fr.guim.cinedoury.core.data.StartupMode
import fr.guim.cinedoury.core.designsystem.component.CineIcons
import fr.guim.cinedoury.core.designsystem.component.ConfirmDialog
import fr.guim.cinedoury.core.designsystem.component.InitialFocus
import fr.guim.cinedoury.core.designsystem.component.ListRow
import fr.guim.cinedoury.core.designsystem.component.ListSectionTitle
import fr.guim.cinedoury.core.designsystem.component.Wordmark
import fr.guim.cinedoury.core.designsystem.component.rememberCineFocus
import fr.guim.cinedoury.core.designsystem.theme.CineSizes
import fr.guim.cinedoury.core.designsystem.theme.CineSpacing
import fr.guim.cinedoury.core.designsystem.theme.CineTheme
import fr.guim.cinedoury.core.model.LibraryIcon
import fr.guim.cinedoury.core.model.LibraryId
import fr.guim.cinedoury.core.ui.CollectEvents
import fr.guim.cinedoury.core.ui.RemoteIndicator
import org.koin.androidx.compose.koinViewModel
import fr.guim.cinedoury.core.ui.R as UiR

/** What the settings ask the app to open. */
interface SettingsNavigation {
    fun customizeHome()

    fun pin(
        profile: Profile,
        remove: Boolean,
    )

    fun address(remote: Boolean)

    fun catalog()

    fun profileClosed()

    fun serverForgotten()
}

@Composable
fun SettingsRoute(
    navigation: SettingsNavigation,
    showCatalog: Boolean,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectEvents(viewModel.events) { event ->
        when (event) {
            SettingsEvent.ProfileClosed -> navigation.profileClosed()
            SettingsEvent.ServerForgotten -> navigation.serverForgotten()
        }
    }
    SettingsScreen(
        state = state,
        showCatalog = showCatalog,
        navigation = navigation,
        onToggleStartup = viewModel::toggleStartupMode,
        onSwitchProfile = viewModel::switchProfile,
        onSignOut = viewModel::signOut,
        onForgetServer = viewModel::forgetServer,
        onSetIcon = viewModel::setIcon,
        onRebuildIndex = viewModel::rebuildIndex,
    )
}

/** Tag of the first row, for tests and screenshots. */
const val PREVIEW_PIN_ROW = "pin-row"

/**
 * The settings of J1 (the full pages come with J7): one page, grouped by theme, each row with a
 * line of explanation (`FONCTIONNALITES.md` § 12). Forgetting the server asks first, the focus on
 * « Annuler » (`UI.md` § 8).
 */
@Composable
fun SettingsScreen(
    state: SettingsState,
    showCatalog: Boolean,
    navigation: SettingsNavigation,
    onToggleStartup: () -> Unit,
    onSwitchProfile: () -> Unit,
    onSignOut: () -> Unit,
    onForgetServer: () -> Unit,
    modifier: Modifier = Modifier,
    onSetIcon: (LibraryId, LibraryIcon?) -> Unit = { _, _ -> },
    onRebuildIndex: () -> Unit = {},
    confirmingForget: Boolean = false,
    previewIconOf: LibraryId? = null,
    previewFirstItem: Int = 0,
) {
    val session = state.session ?: return
    val profile = session.profile
    val server = session.connection.server
    var confirm by rememberSaveable { mutableStateOf(confirmingForget) }
    // The library whose icon is being chosen, in the side panel.
    var iconOf by rememberSaveable(stateSaver = LibraryIdSaver) { mutableStateOf(previewIconOf) }
    // The row of the library whose icon was chosen: the focus comes back to it.
    var chosenFor by remember { mutableStateOf<LibraryId?>(null) }
    val libraryRows = remember { mutableMapOf<LibraryId, FocusRequester>() }
    val first = remember { FocusRequester() }
    val cancel = remember { FocusRequester() }
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(CineTheme.colors.background),
    ) {
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
                    text = stringResource(R.string.settings_title),
                    style = CineTheme.typography.title1,
                    color = CineTheme.colors.text,
                    modifier = Modifier.padding(top = CineSpacing.l),
                )
                if (session.connection.mode == ConnectionMode.Remote) RemoteIndicator()
            }
            LazyColumn(
                state = rememberLazyListState(initialFirstVisibleItemIndex = previewFirstItem),
                modifier = Modifier.width(CineSizes.settingsWidth).fillMaxHeight().testTag("settings"),
                contentPadding =
                    PaddingValues(
                        top = CineSpacing.safeVertical + CineSpacing.m,
                        bottom = CineSpacing.safeVertical + CineSpacing.xxl,
                        start = CineSpacing.xs,
                        end = CineSpacing.xs,
                    ),
                verticalArrangement = Arrangement.spacedBy(CineSpacing.xs),
            ) {
                item { ListSectionTitle(stringResource(R.string.section_profile, profile.user.name)) }
                item {
                    ListRow(
                        title = stringResource(if (profile.hasPin) R.string.pin_change else R.string.pin_set),
                        description = stringResource(R.string.pin_description),
                        value = stringResource(if (profile.hasPin) R.string.pin_on else R.string.pin_off),
                        icon = CineIcons.Lock,
                        onClick = { navigation.pin(profile, remove = false) },
                        focusRequester = first,
                        focus = rememberCineFocus(PREVIEW_PIN_ROW),
                        modifier = Modifier.testTag(PREVIEW_PIN_ROW),
                    )
                }
                if (profile.hasPin) {
                    item {
                        ListRow(
                            title = stringResource(R.string.pin_remove),
                            onClick = { navigation.pin(profile, remove = true) },
                            modifier = Modifier.testTag("pin-remove"),
                        )
                    }
                }
                item {
                    ListRow(
                        title = stringResource(R.string.switch_profile),
                        icon = CineIcons.Profile,
                        onClick = onSwitchProfile,
                        modifier = Modifier.testTag("switch-profile"),
                    )
                }
                item {
                    ListRow(
                        title = stringResource(R.string.sign_out),
                        description = stringResource(R.string.sign_out_description),
                        onClick = onSignOut,
                        modifier = Modifier.testTag("sign-out"),
                    )
                }
                item { ListSectionTitle(stringResource(R.string.section_home)) }
                item {
                    ListRow(
                        title = stringResource(R.string.customize_home),
                        description = stringResource(R.string.customize_home_description),
                        icon = CineIcons.Home,
                        onClick = navigation::customizeHome,
                        modifier = Modifier.testTag("customize-home"),
                    )
                }
                libraryItems(
                    state,
                    onIcon = { iconOf = it },
                    onRebuildIndex = onRebuildIndex,
                    requesters = { libraryRows.requester(it) },
                )
                item { ListSectionTitle(stringResource(R.string.section_startup)) }
                item {
                    val last = state.startupMode == StartupMode.LastProfile
                    ListRow(
                        title = stringResource(R.string.startup_title),
                        description =
                            stringResource(
                                if (last) R.string.startup_last_description else R.string.startup_ask_description,
                            ),
                        value = stringResource(if (last) R.string.startup_last else R.string.startup_ask),
                        onClick = onToggleStartup,
                        modifier = Modifier.testTag("startup"),
                    )
                }
                item { ListSectionTitle(stringResource(R.string.section_server, server.name, server.version)) }
                item {
                    ListRow(
                        title = stringResource(R.string.address_local),
                        description = server.localAddress.url,
                        icon = CineIcons.Server,
                        onClick = { navigation.address(remote = false) },
                        modifier = Modifier.testTag("address-local"),
                    )
                }
                item {
                    ListRow(
                        title = stringResource(R.string.address_remote),
                        description = server.remoteAddress?.url ?: stringResource(R.string.address_remote_none),
                        icon = CineIcons.Remote,
                        onClick = { navigation.address(remote = true) },
                        modifier = Modifier.testTag("address-remote"),
                    )
                }
                item {
                    ListRow(
                        title = stringResource(R.string.forget_server),
                        description = stringResource(R.string.forget_server_description),
                        destructive = true,
                        onClick = { confirm = true },
                        modifier = Modifier.testTag("forget-server"),
                    )
                }
                if (showCatalog) {
                    item { ListSectionTitle(stringResource(R.string.section_debug)) }
                    item {
                        ListRow(
                            title = stringResource(R.string.catalog),
                            description = stringResource(R.string.catalog_description),
                            onClick = navigation::catalog,
                            modifier = Modifier.testTag("catalog"),
                        )
                    }
                }
            }
        }
        IconPanel(
            library = state.libraries.firstOrNull { it.id == iconOf },
            chosen = iconOf?.let { state.chosen[it] },
            onChoose = { library, icon ->
                onSetIcon(library, icon)
                chosenFor = library
                iconOf = null
            },
        )
        if (confirm) {
            ConfirmDialog(
                title = stringResource(R.string.forget_title),
                message = stringResource(R.string.forget_message, server.name),
                confirmLabel = stringResource(R.string.forget_confirm),
                cancelLabel = stringResource(R.string.forget_cancel),
                onConfirm = {
                    confirm = false
                    onForgetServer()
                },
                onCancel = { confirm = false },
                cancelFocusRequester = cancel,
                cancelFocus = rememberCineFocus("forget-cancel"),
                modifier = Modifier.testTag("forget-dialog"),
            )
        }
    }
    BackHandler(enabled = iconOf != null) {
        chosenFor = iconOf
        iconOf = null
    }
    val back = chosenFor?.let { libraryRows.requester(it) }
    InitialFocus(if (confirm) cancel else back ?: first, key = confirm to chosenFor, enabled = iconOf == null)
}

/** The requester of a row, made on first use: outside composition, where it may not be built. */
private fun MutableMap<LibraryId, FocusRequester>.requester(library: LibraryId): FocusRequester =
    getOrPut(library) { FocusRequester() }

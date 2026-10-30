package fr.guim.cinedoury.feature.settings.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.guim.cinedoury.core.data.ConnectionMode
import fr.guim.cinedoury.core.data.Reach
import fr.guim.cinedoury.core.data.SessionManager
import fr.guim.cinedoury.core.designsystem.component.CineIcons
import fr.guim.cinedoury.core.designsystem.image.ImageSource
import fr.guim.cinedoury.core.designsystem.image.RemoteImage
import fr.guim.cinedoury.core.designsystem.mobile.MobileAvatar
import fr.guim.cinedoury.core.designsystem.mobile.MobileFormScreen
import fr.guim.cinedoury.core.designsystem.mobile.MobileListRow
import fr.guim.cinedoury.core.designsystem.mobile.MobileMessage
import fr.guim.cinedoury.core.designsystem.mobile.MobilePrimaryButton
import fr.guim.cinedoury.core.designsystem.mobile.MobileSecondaryButton
import fr.guim.cinedoury.core.designsystem.mobile.MobileTextField
import fr.guim.cinedoury.core.designsystem.theme.CineSpacing
import fr.guim.cinedoury.core.designsystem.theme.CineTheme
import fr.guim.cinedoury.core.designsystem.theme.MobileSizes
import fr.guim.cinedoury.core.source.SourceException
import fr.guim.cinedoury.core.ui.CollectEvents
import fr.guim.cinedoury.core.ui.avatarSource
import fr.guim.cinedoury.core.ui.mobile.MobileSourceError
import fr.guim.cinedoury.feature.settings.ConnectDeviceState
import fr.guim.cinedoury.feature.settings.ConnectDeviceViewModel
import fr.guim.cinedoury.feature.settings.R
import fr.guim.cinedoury.feature.settings.SettingsEvent
import fr.guim.cinedoury.feature.settings.SettingsViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import fr.guim.cinedoury.core.ui.R as UiR

/** Tags of the profile tab of the phone, for tests and screenshots. */
object MobileProfileTags {
    const val SCREEN = "profile"
    const val CONNECTION = "profile-connection"
    const val CONNECT_DEVICE = "profile-connect-device"
    const val LOCAL_ADDRESS = "profile-local-address"
    const val REMOTE_ADDRESS = "profile-remote-address"
    const val SWITCH = "profile-switch"
    const val SIGN_OUT = "profile-sign-out"
    const val CODE = "connect-code"
    const val APPROVE = "connect-approve"
    const val DONE = "connect-done"
}

/** What the profile tab shows: who, on which server, through which address. */
data class MobileProfileState(
    val name: String,
    val serverName: String,
    val mode: ConnectionMode,
    val address: String,
    val avatar: ImageSource?,
    val version: String,
    /** The addresses of the server (F, M2); null when none is known. */
    val localAddress: String? = null,
    val remoteAddress: String? = null,
    /** Whether the server answers at one of them since the last change of network (F, M2). */
    val reach: Reach = Reach.Answers,
)

/** Where the profile tab leads. */
interface MobileProfileNavigation {
    fun connectDevice()

    /** The local or the remote address of the server (F, M2). */
    fun address(remote: Boolean)

    fun profileClosed()

    fun serverForgotten()
}

@Composable
fun MobileProfileRoute(
    navigation: MobileProfileNavigation,
    version: String,
    viewModel: SettingsViewModel = koinViewModel(),
    sessions: SessionManager = koinInject(),
    playback: @Composable () -> Unit = {},
) {
    val settings by viewModel.state.collectAsStateWithLifecycle()
    // The address follows the network of the phone (SessionManager.followNetwork): read as it changes.
    val connection by sessions.connection.collectAsStateWithLifecycle()
    val reach by sessions.reach.collectAsStateWithLifecycle()
    CollectEvents(viewModel.events) { event ->
        when (event) {
            SettingsEvent.ProfileClosed -> navigation.profileClosed()
            SettingsEvent.ServerForgotten -> navigation.serverForgotten()
        }
    }
    val session = settings.session ?: return
    val current = connection ?: session.connection
    MobileProfileScreen(
        state =
            MobileProfileState(
                name = session.profile.user.name,
                serverName = current.server.name,
                mode = current.mode,
                address = current.address.url,
                avatar = sessions.avatarSource(session.profile.user),
                version = version,
                localAddress = current.server.localAddress?.url,
                remoteAddress = current.server.remoteAddress?.url,
                reach = reach,
            ),
        onConnectDevice = navigation::connectDevice,
        onAddress = navigation::address,
        onSwitchProfile = viewModel::switchProfile,
        onSignOut = viewModel::signOut,
        playback = playback,
    )
}

/**
 * The profile tab (M1, `UI.md` § 9): the profile and its server, a discreet line that says which
 * address is in use (the house's, or the public one away from it), « Connecter un appareil », then
 * the playback settings of the phone ([playback], M2: the margin and the qualities), the profile
 * picker and the sign-out. The other settings come with M3.
 */
@Composable
fun MobileProfileScreen(
    state: MobileProfileState,
    onConnectDevice: () -> Unit,
    onSwitchProfile: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    onAddress: (remote: Boolean) -> Unit = {},
    playback: @Composable () -> Unit = {},
) {
    val colors = CineTheme.colors
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(colors.background)
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .testTag(MobileProfileTags.SCREEN),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = MobileSizes.formWidth).fillMaxWidth().padding(vertical = CineSpacing.m)) {
            Row(
                Modifier.padding(horizontal = MobileSizes.gutter),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CineSpacing.s),
            ) {
                MobileAvatar(
                    name = state.name,
                    size = MobileSizes.avatarMedium,
                    image =
                        state.avatar?.let { source ->
                            { RemoteImage(source, null, null, Modifier.size(MobileSizes.avatarMedium)) }
                        },
                )
                Column(verticalArrangement = Arrangement.spacedBy(CineSpacing.xs / 4)) {
                    Text(state.name, style = CineTheme.typography.title1)
                    Text(
                        stringResource(R.string.mobile_server_line, state.serverName),
                        style = CineTheme.typography.caption,
                        color = colors.text2,
                    )
                }
            }
            Row(
                Modifier
                    .padding(horizontal = MobileSizes.gutter, vertical = CineSpacing.s)
                    .testTag(MobileProfileTags.CONNECTION),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CineSpacing.xs),
            ) {
                val silent = state.reach as? Reach.Silent
                Icon(
                    when {
                        silent != null -> CineIcons.Warning
                        state.mode == ConnectionMode.Remote -> CineIcons.Remote
                        else -> CineIcons.Home
                    },
                    contentDescription = null,
                    tint = if (silent != null) colors.accent else colors.text3,
                    modifier = Modifier.size(CineSpacing.s),
                )
                Text(
                    when {
                        silent?.noRemote == true -> stringResource(R.string.mobile_connection_silent_no_remote)
                        silent != null -> stringResource(R.string.mobile_connection_silent)
                        state.mode == ConnectionMode.Remote -> stringResource(R.string.mobile_connection_remote)
                        else -> stringResource(R.string.mobile_connection_local)
                    },
                    style = CineTheme.typography.caption,
                    color = if (silent != null) colors.text else colors.text3,
                )
            }
            MobileListRow(
                title = stringResource(R.string.mobile_connect_device),
                subtitle = stringResource(R.string.mobile_connect_device_description),
                icon = CineIcons.Remote,
                onClick = onConnectDevice,
                modifier = Modifier.testTag(MobileProfileTags.CONNECT_DEVICE),
            )
            MobileListRow(
                title = stringResource(R.string.mobile_local_address),
                subtitle = state.localAddress ?: stringResource(R.string.mobile_address_none),
                icon = CineIcons.Home,
                onClick = { onAddress(false) },
                modifier = Modifier.testTag(MobileProfileTags.LOCAL_ADDRESS),
            )
            MobileListRow(
                title = stringResource(R.string.mobile_remote_address),
                subtitle = state.remoteAddress ?: stringResource(R.string.mobile_remote_address_none),
                icon = CineIcons.Remote,
                onClick = { onAddress(true) },
                modifier = Modifier.testTag(MobileProfileTags.REMOTE_ADDRESS),
            )
            playback()
            MobileListRow(
                title = stringResource(R.string.switch_profile),
                subtitle = stringResource(R.string.mobile_switch_profile_description),
                icon = CineIcons.Profile,
                onClick = onSwitchProfile,
                modifier = Modifier.testTag(MobileProfileTags.SWITCH),
            )
            MobileListRow(
                title = stringResource(R.string.sign_out),
                subtitle = stringResource(R.string.mobile_sign_out_description),
                icon = CineIcons.Exit,
                onClick = onSignOut,
                destructive = true,
                modifier = Modifier.testTag(MobileProfileTags.SIGN_OUT),
            )
            Text(
                stringResource(R.string.mobile_app_version, state.version),
                style = CineTheme.typography.minimum,
                color = colors.text3,
                modifier = Modifier.padding(horizontal = MobileSizes.gutter, vertical = CineSpacing.m),
            )
        }
    }
}

@Composable
fun MobileConnectDeviceRoute(
    onBack: () -> Unit,
    viewModel: ConnectDeviceViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MobileConnectDeviceScreen(state, viewModel::edit, viewModel::submit, viewModel::again, onBack)
}

/**
 * « Connecter un appareil » (M1): the six digits a TV shows on its Quick Connect screen, typed on the
 * number keyboard of the phone; the TV then signs in as this profile (`SPEC.md` § 4.26).
 */
@Composable
fun MobileConnectDeviceScreen(
    state: ConnectDeviceState,
    onEdit: (String) -> Unit,
    onSubmit: () -> Unit,
    onAgain: () -> Unit,
    onBack: (() -> Unit)?,
) {
    MobileFormScreen(
        brand = stringResource(UiR.string.brand_name),
        title = stringResource(R.string.mobile_connect_device),
        subtitle = stringResource(R.string.mobile_connect_device_instruction),
        onBack = onBack,
        backLabel = stringResource(UiR.string.action_back),
    ) {
        val approved = state.approved
        val error = state.error
        when {
            approved != null -> {
                MobileMessage(
                    title = stringResource(R.string.mobile_connect_device_done_title),
                    message = stringResource(R.string.mobile_connect_device_done_message, approved),
                    icon = CineIcons.Watched,
                    modifier = Modifier.testTag(MobileProfileTags.DONE),
                ) {
                    MobileSecondaryButton(stringResource(R.string.mobile_connect_device_again), onAgain)
                }
            }

            error is SourceException.QuickConnectExpired -> {
                MobileMessage(
                    title = stringResource(R.string.mobile_code_unknown_title),
                    message = stringResource(R.string.mobile_code_unknown_message),
                    icon = CineIcons.Warning,
                ) {
                    MobilePrimaryButton(stringResource(UiR.string.action_retry), onAgain)
                }
            }

            error != null -> {
                MobileSourceError(error, onSubmit)
            }

            else -> {
                MobileTextField(
                    value = state.code,
                    onValueChange = onEdit,
                    label = stringResource(R.string.mobile_connect_device_code),
                    placeholder = stringResource(R.string.mobile_connect_device_placeholder),
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                    onAction = onSubmit,
                    modifier = Modifier.testTag(MobileProfileTags.CODE),
                )
                MobilePrimaryButton(
                    stringResource(R.string.mobile_connect_device_approve),
                    onSubmit,
                    Modifier.fillMaxWidth().testTag(MobileProfileTags.APPROVE),
                    enabled = state.complete,
                    busy = state.busy,
                )
            }
        }
    }
}

package fr.guim.cinedoury.feature.auth.mobile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.guim.cinedoury.core.data.Profile
import fr.guim.cinedoury.core.data.security.PinHasher
import fr.guim.cinedoury.core.designsystem.component.CineIcons
import fr.guim.cinedoury.core.designsystem.image.ImageSource
import fr.guim.cinedoury.core.designsystem.image.RemoteImage
import fr.guim.cinedoury.core.designsystem.mobile.MobileAvatar
import fr.guim.cinedoury.core.designsystem.mobile.MobileFormScreen
import fr.guim.cinedoury.core.designsystem.mobile.MobileIconButton
import fr.guim.cinedoury.core.designsystem.mobile.MobileKey
import fr.guim.cinedoury.core.designsystem.mobile.MobileListRow
import fr.guim.cinedoury.core.designsystem.mobile.MobilePinDots
import fr.guim.cinedoury.core.designsystem.mobile.MobilePrimaryButton
import fr.guim.cinedoury.core.designsystem.mobile.MobileSecondaryButton
import fr.guim.cinedoury.core.designsystem.mobile.MobileTextButton
import fr.guim.cinedoury.core.designsystem.mobile.MobileTextField
import fr.guim.cinedoury.core.designsystem.theme.CineSpacing
import fr.guim.cinedoury.core.designsystem.theme.CineTheme
import fr.guim.cinedoury.core.designsystem.theme.MobileSizes
import fr.guim.cinedoury.core.source.ServerAddress
import fr.guim.cinedoury.core.source.SourceException
import fr.guim.cinedoury.core.ui.CollectEvents
import fr.guim.cinedoury.core.ui.mobile.MobileSourceError
import fr.guim.cinedoury.feature.auth.AddressMode
import fr.guim.cinedoury.feature.auth.AddressState
import fr.guim.cinedoury.feature.auth.AddressViewModel
import fr.guim.cinedoury.feature.auth.PasswordArgs
import fr.guim.cinedoury.feature.auth.PasswordState
import fr.guim.cinedoury.feature.auth.PasswordViewModel
import fr.guim.cinedoury.feature.auth.PinArgs
import fr.guim.cinedoury.feature.auth.PinMessage
import fr.guim.cinedoury.feature.auth.PinPurpose
import fr.guim.cinedoury.feature.auth.PinState
import fr.guim.cinedoury.feature.auth.PinViewModel
import fr.guim.cinedoury.feature.auth.ProfilesEvent
import fr.guim.cinedoury.feature.auth.ProfilesState
import fr.guim.cinedoury.feature.auth.ProfilesViewModel
import fr.guim.cinedoury.feature.auth.QuickConnectState
import fr.guim.cinedoury.feature.auth.QuickConnectViewModel
import fr.guim.cinedoury.feature.auth.R
import fr.guim.cinedoury.feature.auth.ServerSearchState
import fr.guim.cinedoury.feature.auth.ServerSearchViewModel
import fr.guim.cinedoury.feature.auth.SignInEvent
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import fr.guim.cinedoury.core.ui.R as UiR

/** Tags of the sign-in screens of the phone, for tests and screenshots. */
object MobileAuthTags {
    const val START = "auth-start"
    const val ENTER_ADDRESS = "auth-enter-address"
    const val ADDRESS = "auth-address"
    const val ADDRESS_SUBMIT = "auth-address-submit"
    const val USERNAME = "auth-username"
    const val PASSWORD = "auth-password"
    const val SIGN_IN = "auth-sign-in"
    const val USE_PASSWORD = "auth-use-password"
    const val CODE = "auth-code"
    const val OTHER_ACCOUNT = "auth-other-account"

    fun server(index: Int) = "auth-server-$index"

    fun profile(name: String) = "auth-profile-$name"

    fun key(digit: Char) = "auth-key-$digit"
}

/** The first launch on the phone: the name of the app, one line, « Commencer ». */
@Composable
fun MobileWelcomeScreen(onStart: () -> Unit) {
    MobileFormScreen(
        brand = stringResource(UiR.string.brand_name),
        title = stringResource(R.string.mobile_welcome_title),
        subtitle = stringResource(R.string.mobile_welcome_tagline),
    ) {
        MobilePrimaryButton(
            stringResource(R.string.welcome_start),
            onStart,
            Modifier.fillMaxWidth().testTag(MobileAuthTags.START),
        )
    }
}

@Composable
fun MobileServerSearchRoute(
    onConnected: () -> Unit,
    onEnterAddress: () -> Unit,
    onBack: () -> Unit,
    viewModel: ServerSearchViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectEvents(viewModel.connected) { onConnected() }
    MobileServerSearchScreen(
        state = state,
        onChoose = viewModel::choose,
        onEnterAddress = onEnterAddress,
        onSearchAgain = viewModel::search,
        onRetry = viewModel::retry,
        onDismissError = viewModel::dismissError,
        onBack = onBack,
    )
}

/** The servers of the local network as they answer, then « Saisir une adresse » (`SPEC.md` § 4.7). */
@Composable
fun MobileServerSearchScreen(
    state: ServerSearchState,
    onChoose: (ServerAddress) -> Unit,
    onEnterAddress: () -> Unit,
    onSearchAgain: () -> Unit,
    onRetry: () -> Unit,
    onDismissError: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val error = state.error
    val subtitle =
        when {
            error != null -> null
            state.searching -> stringResource(R.string.servers_searching)
            state.servers.isEmpty() -> stringResource(R.string.servers_none)
            else -> stringResource(R.string.servers_found)
        }
    MobileFormScreen(
        brand = stringResource(UiR.string.brand_name),
        title = stringResource(R.string.servers_title),
        subtitle = subtitle,
        onBack = onBack,
        backLabel = stringResource(UiR.string.action_back),
    ) {
        if (error != null) {
            MobileSourceError(
                error,
                onRetry,
                secondaryLabel = stringResource(UiR.string.action_back),
                onSecondary = onDismissError,
            )
            return@MobileFormScreen
        }
        Column(Modifier.fillMaxWidth()) {
            state.servers.forEachIndexed { index, server ->
                MobileListRow(
                    title = server.name,
                    subtitle =
                        if (state.checking == server.address) {
                            stringResource(R.string.servers_checking)
                        } else {
                            server.address.url
                        },
                    icon = CineIcons.Server,
                    onClick = { onChoose(server.address) },
                    modifier = Modifier.testTag(MobileAuthTags.server(index)),
                )
            }
        }
        if (state.searching) {
            CircularProgressIndicator(
                Modifier.size(MobileSizes.icon).align(Alignment.CenterHorizontally),
                color = CineTheme.colors.accent,
            )
        }
        MobilePrimaryButton(
            stringResource(R.string.servers_enter_address),
            onEnterAddress,
            Modifier.fillMaxWidth().testTag(MobileAuthTags.ENTER_ADDRESS),
        )
        if (!state.searching) MobileTextButton(stringResource(R.string.servers_search_again), onSearchAgain)
    }
}

@Composable
fun MobileAddressRoute(
    mode: AddressMode,
    onDone: () -> Unit,
    onBack: () -> Unit,
    viewModel: AddressViewModel = koinViewModel(key = "address-$mode") { parametersOf(mode) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectEvents(viewModel.done) { onDone() }
    MobileAddressScreen(state, viewModel::edit, viewModel::submit, viewModel::dismissError, onBack)
}

/** An address typed with the keyboard of the phone: `http://` and port 8096 assumed (`SPEC.md` § 4.7). */
@Composable
fun MobileAddressScreen(
    state: AddressState,
    onEdit: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismissError: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val (title, subtitle) =
        when (state.mode) {
            AddressMode.Add -> R.string.address_title to R.string.address_subtitle
            AddressMode.EditLocal -> R.string.address_local_title to R.string.address_local_subtitle
            AddressMode.EditRemote -> R.string.address_remote_title to R.string.address_remote_subtitle
        }
    MobileFormScreen(
        brand = stringResource(UiR.string.brand_name),
        title = stringResource(title),
        subtitle = stringResource(subtitle),
        onBack = onBack,
        backLabel = stringResource(UiR.string.action_back),
    ) {
        val error = state.error
        if (error != null) {
            MobileSourceError(
                error,
                onSubmit,
                secondaryLabel = stringResource(UiR.string.action_back),
                onSecondary = onDismissError,
            )
            return@MobileFormScreen
        }
        MobileTextField(
            value = state.text,
            onValueChange = onEdit,
            label = stringResource(R.string.address_label),
            placeholder =
                stringResource(
                    if (state.mode ==
                        AddressMode.EditRemote
                    ) {
                        R.string.address_remote_placeholder
                    } else {
                        R.string.address_placeholder
                    },
                ),
            error = stringResource(R.string.address_invalid).takeIf { state.invalid },
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Go,
            onAction = onSubmit,
            modifier = Modifier.testTag(MobileAuthTags.ADDRESS),
        )
        MobilePrimaryButton(
            stringResource(if (state.mode == AddressMode.Add) R.string.address_continue else R.string.address_save),
            onSubmit,
            Modifier.fillMaxWidth().testTag(MobileAuthTags.ADDRESS_SUBMIT),
            busy = state.busy,
        )
    }
}

/** At start, the saved server did not answer at either address: what happened, and what to do. */
@Composable
fun MobileUnreachableScreen(
    serverName: String,
    error: SourceException,
    onRetry: () -> Unit,
    onChangeServer: () -> Unit,
) {
    MobileFormScreen(brand = stringResource(UiR.string.brand_name), title = serverName) {
        MobileSourceError(
            error = error,
            onRetry = onRetry,
            secondaryLabel = stringResource(R.string.change_server),
            onSecondary = onChangeServer,
        )
    }
}

@Composable
fun MobileProfilesRoute(
    onOpened: () -> Unit,
    onSignIn: (username: String?) -> Unit,
    onPin: (Profile) -> Unit,
    onPassword: () -> Unit,
    viewModel: ProfilesViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectEvents(viewModel.events) { event ->
        when (event) {
            ProfilesEvent.Opened -> onOpened()
            is ProfilesEvent.SignIn -> onSignIn(event.username)
            is ProfilesEvent.Pin -> onPin(event.profile)
        }
    }
    MobileProfilesScreen(
        state,
        viewModel::avatarUrl,
        viewModel::choose,
        viewModel::otherAccount,
        onPassword,
        viewModel::refresh,
    )
}

/**
 * « Qui regarde ? » on the phone: the profiles in a grid of tiles a finger wide, a padlock on those
 * with a PIN, then « Autre compte ». When the server lists none, Quick Connect and the password.
 */
@Composable
fun MobileProfilesScreen(
    state: ProfilesState,
    avatarUrl: (Profile) -> String?,
    onChoose: (Profile) -> Unit,
    onOtherAccount: () -> Unit,
    onPassword: () -> Unit,
    onRetry: () -> Unit,
) {
    MobileFormScreen(
        brand = stringResource(UiR.string.brand_name),
        title = stringResource(R.string.profiles_title),
        subtitle = stringResource(R.string.profiles_hidden_hint),
    ) {
        val error = state.error
        when {
            error != null -> {
                MobileSourceError(
                    error,
                    onRetry,
                    secondaryLabel = stringResource(R.string.use_password),
                    onSecondary = onPassword,
                )
            }

            state.loading && state.profiles.isEmpty() -> {
                CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally), color = CineTheme.colors.accent)
            }

            else -> {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(CineSpacing.m),
                    verticalArrangement = Arrangement.spacedBy(CineSpacing.m),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    state.profiles.forEach { profile ->
                        ProfileTile(
                            name = profile.user.name,
                            caption = stringResource(R.string.profiles_to_sign_in).takeUnless { profile.isSignedIn },
                            locked = profile.hasPin,
                            imageUrl = avatarUrl(profile),
                            onClick = { onChoose(profile) },
                            modifier = Modifier.testTag(MobileAuthTags.profile(profile.user.name)),
                        )
                    }
                }
                MobileSecondaryButton(
                    stringResource(R.string.profiles_other),
                    onOtherAccount,
                    Modifier.fillMaxWidth().testTag(MobileAuthTags.OTHER_ACCOUNT),
                    icon = CineIcons.Add,
                )
                if (state.profiles.isEmpty()) {
                    MobileTextButton(stringResource(R.string.use_password), onPassword)
                }
            }
        }
    }
}

@Composable
private fun ProfileTile(
    name: String,
    caption: String?,
    locked: Boolean,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.width(MobileSizes.avatarLarge + CineSpacing.s),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CineSpacing.xs),
    ) {
        Box(
            Modifier.clip(CircleShape).clickable(role = Role.Button, onClick = onClick),
        ) {
            MobileAvatar(
                name = name,
                size = MobileSizes.avatarLarge,
                image =
                    imageUrl?.let { url ->
                        {
                            RemoteImage(
                                ImageSource { _, _ -> url },
                                blurHash = null,
                                contentDescription = null,
                                modifier = Modifier.size(MobileSizes.avatarLarge),
                            )
                        }
                    },
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement =
                Arrangement.spacedBy(CineSpacing.xs / 2),
        ) {
            if (locked) {
                Icon(
                    CineIcons.Lock,
                    contentDescription = null,
                    tint = CineTheme.colors.text2,
                    modifier = Modifier.size(CineSpacing.s),
                )
            }
            Text(name, style = CineTheme.typography.label, maxLines = 1, textAlign = TextAlign.Center)
        }
        caption?.let { Text(it, style = CineTheme.typography.minimum, color = CineTheme.colors.text2) }
    }
}

@Composable
fun MobileQuickConnectRoute(
    username: String?,
    onSignedIn: () -> Unit,
    onUsePassword: (instead: Boolean) -> Unit,
    onBack: () -> Unit,
    viewModel: QuickConnectViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectEvents(viewModel.events) { event ->
        when (event) {
            SignInEvent.SignedIn -> onSignedIn()
            SignInEvent.UsePassword -> onUsePassword(true)
        }
    }
    MobileQuickConnectScreen(state, username, viewModel::start, { onUsePassword(false) }, onBack)
}

/**
 * Quick Connect on the phone (`SPEC.md` § 4.7): the six digits in large type, where to type them (a
 * device already signed in: the TV, the web interface of Jellyfin), and the password as the other way.
 */
@Composable
fun MobileQuickConnectScreen(
    state: QuickConnectState,
    username: String?,
    onNewCode: () -> Unit,
    onUsePassword: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    MobileFormScreen(
        brand = stringResource(UiR.string.brand_name),
        title = stringResource(R.string.sign_in_title),
        subtitle = username?.let { stringResource(R.string.sign_in_for, it) },
        onBack = onBack,
        backLabel = stringResource(UiR.string.action_back),
    ) {
        when (state) {
            QuickConnectState.Loading -> {
                Text(
                    stringResource(R.string.quick_connect_loading),
                    style = CineTheme.typography.body,
                    color = CineTheme.colors.text2,
                )
            }

            is QuickConnectState.Waiting -> {
                Text(stringResource(R.string.mobile_quick_connect_instruction), style = CineTheme.typography.body)
                val code = state.code
                Text(
                    text = code.chunked(CODE_GROUP).joinToString(" "),
                    style = CineTheme.typography.display,
                    color = CineTheme.colors.accent,
                    modifier = Modifier.fillMaxWidth().padding(vertical = CineSpacing.s).testTag(MobileAuthTags.CODE),
                    textAlign = TextAlign.Center,
                )
                Text(
                    stringResource(R.string.mobile_quick_connect_waiting),
                    style = CineTheme.typography.caption,
                    color = CineTheme.colors.text2,
                )
            }

            QuickConnectState.Expired -> {
                MobileSourceError(SourceException.QuickConnectExpired(), onNewCode)
            }

            is QuickConnectState.Failed -> {
                MobileSourceError(state.error, onNewCode)
            }
        }
        MobileSecondaryButton(
            stringResource(R.string.use_password),
            onUsePassword,
            Modifier.fillMaxWidth().testTag(MobileAuthTags.USE_PASSWORD),
        )
    }
}

@Composable
fun MobilePasswordRoute(
    username: String?,
    onSignedIn: () -> Unit,
    onBack: () -> Unit,
    viewModel: PasswordViewModel = koinViewModel(key = "password-$username") { parametersOf(PasswordArgs(username)) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectEvents(viewModel.signedIn) { onSignedIn() }
    MobilePasswordScreen(
        state,
        viewModel::editUsername,
        viewModel::editPassword,
        viewModel::toggleShowPassword,
        viewModel::submit,
        viewModel::dismissError,
        onBack,
    )
}

/** Username and password, typed with the keyboard of the phone. */
@Composable
fun MobilePasswordScreen(
    state: PasswordState,
    onUsername: (String) -> Unit,
    onPassword: (String) -> Unit,
    onToggleShow: () -> Unit,
    onSubmit: () -> Unit,
    onDismissError: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    MobileFormScreen(
        brand = stringResource(UiR.string.brand_name),
        title = stringResource(R.string.sign_in_title),
        subtitle = stringResource(R.string.password_subtitle),
        onBack = onBack,
        backLabel = stringResource(UiR.string.action_back),
    ) {
        val error = state.error
        if (error != null) {
            MobileSourceError(
                error,
                onSubmit,
                secondaryLabel = stringResource(UiR.string.action_back),
                onSecondary = onDismissError,
            )
            return@MobileFormScreen
        }
        MobileTextField(
            value = state.username,
            onValueChange = onUsername,
            label = stringResource(R.string.password_username),
            imeAction = ImeAction.Next,
            modifier = Modifier.testTag(MobileAuthTags.USERNAME),
        )
        MobileTextField(
            value = state.password,
            onValueChange = onPassword,
            label = stringResource(R.string.password_password),
            password = !state.showPassword,
            error = stringResource(UiR.string.error_credentials_title).takeIf { state.refused },
            imeAction = ImeAction.Go,
            onAction = onSubmit,
            modifier = Modifier.testTag(MobileAuthTags.PASSWORD),
            trailing = {
                MobileIconButton(
                    if (state.showPassword) CineIcons.Hide else CineIcons.Show,
                    stringResource(if (state.showPassword) R.string.password_hide else R.string.password_show),
                    onToggleShow,
                )
            },
        )
        MobilePrimaryButton(
            stringResource(R.string.password_sign_in),
            onSubmit,
            Modifier.fillMaxWidth().testTag(MobileAuthTags.SIGN_IN),
            enabled = state.username.isNotBlank(),
            busy = state.busy,
        )
    }
}

@Composable
fun MobilePinRoute(
    profile: Profile,
    purpose: PinPurpose,
    onDone: () -> Unit,
    onBack: () -> Unit,
    viewModel: PinViewModel =
        koinViewModel(key = "pin-${profile.user.id.value}-$purpose") { parametersOf(PinArgs(profile, purpose)) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectEvents(viewModel.done) { onDone() }
    MobilePinScreen(state, viewModel::digit, viewModel::erase, onBack)
}

/** The PIN of a profile on a pad of keys a thumb wide; checked at the fourth digit. */
@Composable
fun MobilePinScreen(
    state: PinState,
    onDigit: (Char) -> Unit,
    onErase: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val title =
        when {
            state.purpose == PinPurpose.Unlock -> R.string.pin_unlock_title
            state.purpose == PinPurpose.Remove -> R.string.pin_remove_title
            state.confirming -> R.string.pin_confirm_title
            else -> R.string.pin_create_title
        }
    val message =
        when (state.message) {
            PinMessage.Wrong -> stringResource(R.string.pin_wrong)
            PinMessage.Mismatch -> stringResource(R.string.pin_mismatch)
            null -> state.profile.user.name
        }
    MobileFormScreen(
        brand = stringResource(UiR.string.brand_name),
        title = stringResource(title),
        subtitle = message,
        onBack = onBack,
        backLabel = stringResource(UiR.string.action_back),
    ) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(CineSpacing.m),
        ) {
            MobilePinDots(state.digits.length, PinHasher.PIN_DIGITS)
            listOf("123", "456", "789").forEach { line ->
                Row(horizontalArrangement = Arrangement.spacedBy(CineSpacing.m)) {
                    line.forEach { digit ->
                        MobileKey(digit.toString(), { onDigit(digit) }, Modifier.testTag(MobileAuthTags.key(digit)))
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(CineSpacing.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(MobileSizes.keypadKey))
                MobileKey("0", { onDigit('0') }, Modifier.testTag(MobileAuthTags.key('0')))
                MobileKey(stringResource(R.string.pin_erase), onErase, icon = CineIcons.Backspace)
            }
        }
    }
}

private const val CODE_GROUP = 3

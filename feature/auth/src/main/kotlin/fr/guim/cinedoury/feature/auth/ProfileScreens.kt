package fr.guim.cinedoury.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import fr.guim.cinedoury.core.data.Profile
import fr.guim.cinedoury.core.designsystem.component.CineIcons
import fr.guim.cinedoury.core.designsystem.component.FormScreen
import fr.guim.cinedoury.core.designsystem.component.InitialFocus
import fr.guim.cinedoury.core.designsystem.component.KeypadKey
import fr.guim.cinedoury.core.designsystem.component.PinDots
import fr.guim.cinedoury.core.designsystem.component.ProfileAvatar
import fr.guim.cinedoury.core.designsystem.component.ProfileTile
import fr.guim.cinedoury.core.designsystem.component.SecondaryButton
import fr.guim.cinedoury.core.designsystem.component.rememberCineFocus
import fr.guim.cinedoury.core.designsystem.image.ImageSource
import fr.guim.cinedoury.core.designsystem.image.RemoteImage
import fr.guim.cinedoury.core.designsystem.theme.CineSizes
import fr.guim.cinedoury.core.designsystem.theme.CineSpacing
import fr.guim.cinedoury.core.designsystem.theme.CineTheme
import fr.guim.cinedoury.core.ui.CollectEvents
import fr.guim.cinedoury.core.ui.RemoteIndicator
import fr.guim.cinedoury.core.ui.SourceErrorPanel
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import fr.guim.cinedoury.core.ui.R as UiR

@Composable
fun ProfilesRoute(
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
    ProfilesScreen(
        state = state,
        avatarUrl = viewModel::avatarUrl,
        onChoose = viewModel::choose,
        onOtherAccount = viewModel::otherAccount,
        onPassword = onPassword,
        onRetry = viewModel::refresh,
    )
}

/**
 * « Qui regarde ? » (`FONCTIONNALITES.md` § 1): the profiles of the server with their pictures (or
 * an initial on a coloured disc), a padlock on those with a PIN, and « Autre compte » for a user
 * the server does not list. Jellyfin hides new users, the administrator made by its startup wizard
 * included: the line under the title says so, and when no profile is listed at all, Quick Connect
 * and the password are offered right here.
 */
@Composable
fun ProfilesScreen(
    state: ProfilesState,
    avatarUrl: (Profile) -> String?,
    onChoose: (Profile) -> Unit,
    onOtherAccount: () -> Unit,
    onPassword: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val first = remember { FocusRequester() }
    val other = remember { FocusRequester() }
    val error = state.error
    val waiting = state.loading && state.profiles.isEmpty()
    FormScreen(
        brand = stringResource(UiR.string.brand_name),
        title = stringResource(R.string.profiles_title),
        // Hidden accounts (Jellyfin's default) are not listed: the line says where they are.
        subtitle = stringResource(R.string.profiles_hidden_hint).takeIf { error == null },
        topEnd = if (state.remote) ({ RemoteIndicator() }) else null,
        modifier = modifier,
    ) {
        if (error != null) {
            SourceErrorPanel(
                error = error,
                onRetry = onRetry,
                secondaryLabel = stringResource(R.string.profiles_other),
                onSecondary = onOtherAccount,
            )
        } else {
            ProfileRow(state.profiles, avatarUrl, onChoose, onOtherAccount, first, other, showOther = !waiting)
            if (!state.loading && state.profiles.isEmpty()) {
                DirectSignIn(onQuickConnect = onOtherAccount, onPassword = onPassword)
            }
        }
    }
    // The first profile, or « Autre compte » when none is listed. While the server is asked with no
    // profile known here, no focus yet: moving it from « Autre compte » to a tile that arrives
    // before it fails in a LazyRow (CLAUDE.md, « Pièges »).
    val target = if (state.profiles.isEmpty()) other else first
    InitialFocus(target, key = error == null to state.profiles.isEmpty(), enabled = error == null && !waiting)
}

@Composable
private fun ProfileRow(
    profiles: List<Profile>,
    avatarUrl: (Profile) -> String?,
    onChoose: (Profile) -> Unit,
    onOtherAccount: () -> Unit,
    first: FocusRequester,
    other: FocusRequester,
    showOther: Boolean,
) {
    val listState = rememberLazyListState()
    // Profiles that arrive go before « Autre compte », which the list keeps in place by its key:
    // back to the start, where the first profile is.
    LaunchedEffect(profiles.isEmpty()) {
        if (profiles.isNotEmpty()) listState.scrollToItem(0)
    }
    LazyRow(
        state = listState,
        // Room for the scale and the halo of the focused avatar.
        contentPadding = PaddingValues(vertical = CineSpacing.m, horizontal = CineSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(CineSpacing.l),
        modifier = Modifier.testTag("profiles"),
    ) {
        itemsIndexed(profiles, key = { _, profile -> profile.user.id.value }) { index, profile ->
            val url = avatarUrl(profile)
            ProfileTile(
                name = profile.user.name,
                onClick = { onChoose(profile) },
                locked = profile.hasPin,
                caption = stringResource(R.string.profiles_to_sign_in).takeUnless { profile.isSignedIn },
                focusRequester = if (index == 0) first else null,
                focus = rememberCineFocus("profile-$index"),
                modifier = Modifier.testTag("profile-$index"),
                image = url?.let { { AvatarImage(it) } },
            )
        }
        // Not while the server is asked with no profile known: alone on the screen, « Autre compte »
        // would get the focus Android gives back to the window (the keyboard of the address that
        // closes), and keep it when the profiles arrive before it (CLAUDE.md, « Pièges »).
        if (showOther) {
            item(key = "other") {
                ProfileTile(
                    name = stringResource(R.string.profiles_other),
                    onClick = onOtherAccount,
                    icon = CineIcons.Add,
                    focusRequester = other,
                    modifier = Modifier.testTag("profile-other"),
                )
            }
        }
    }
}

/** With no profile listed (hidden accounts only), the two ways to sign in, right here. */
@Composable
private fun DirectSignIn(
    onQuickConnect: () -> Unit,
    onPassword: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(CineSpacing.m)) {
        SecondaryButton(
            text = stringResource(R.string.profiles_quick_connect),
            onClick = onQuickConnect,
            focus = rememberCineFocus(AuthPreview.PROFILES_QUICK_CONNECT),
            modifier = Modifier.testTag(AuthPreview.PROFILES_QUICK_CONNECT),
        )
        SecondaryButton(
            text = stringResource(R.string.use_password),
            onClick = onPassword,
            modifier = Modifier.testTag("profiles-password"),
        )
    }
}

@Composable
private fun AvatarImage(url: String) {
    RemoteImage(
        source = { _, _ -> url },
        blurHash = null,
        contentDescription = null,
        modifier = Modifier.fillMaxSize(),
    )
}

/** For Koin. */
data class PinArgs(
    val profile: Profile,
    val purpose: PinPurpose,
)

@Composable
fun PinRoute(
    profile: Profile,
    purpose: PinPurpose,
    onDone: () -> Unit,
    avatar: ImageSource? = null,
    viewModel: PinViewModel =
        koinViewModel(key = "pin-${profile.user.id.value}-$purpose") { parametersOf(PinArgs(profile, purpose)) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectEvents(viewModel.done) { onDone() }
    PinScreen(state = state, onDigit = viewModel::digit, onErase = viewModel::erase, avatar = avatar)
}

/**
 * The PIN pad (`FONCTIONNALITES.md` § 1): four digits typed with the number keys of the remote,
 * or with the on-screen pad for remotes without them (the Shield's). The PIN is checked as soon
 * as the fourth digit is in.
 */
@Composable
fun PinScreen(
    state: PinState,
    onDigit: (Char) -> Unit,
    onErase: () -> Unit,
    modifier: Modifier = Modifier,
    avatar: ImageSource? = null,
) {
    val five = remember { FocusRequester() }
    val title =
        when {
            state.purpose == PinPurpose.Unlock -> R.string.pin_unlock_title
            state.purpose == PinPurpose.Remove -> R.string.pin_remove_title
            state.confirming -> R.string.pin_confirm_title
            else -> R.string.pin_create_title
        }
    val subtitle =
        when (state.purpose) {
            PinPurpose.Unlock -> null
            PinPurpose.Create -> stringResource(R.string.pin_create_subtitle)
            PinPurpose.Remove -> stringResource(R.string.pin_remove_subtitle)
        }
    FormScreen(
        brand = stringResource(UiR.string.brand_name),
        title = stringResource(title),
        subtitle = subtitle,
        modifier = modifier.onPreviewKeyEvent { event -> handleKey(event, onDigit, onErase) },
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(CineSpacing.xxl),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PinStatus(state, avatar)
            Keypad(onDigit, onErase, five)
        }
    }
    InitialFocus(five)
}

@Composable
private fun PinStatus(
    state: PinState,
    avatar: ImageSource?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(CineSpacing.m)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CineSpacing.s),
        ) {
            ProfileAvatar(
                name = state.profile.user.name,
                size = CineSizes.avatarMedium,
                image = avatar?.let { source -> { RemoteImage(source, null, null, Modifier.fillMaxSize()) } },
            )
            Text(text = state.profile.user.name, style = CineTheme.typography.title2, color = CineTheme.colors.text)
        }
        PinDots(filled = state.digits.length, error = state.message != null, modifier = Modifier.testTag("pin-dots"))
        val message =
            when (state.message) {
                PinMessage.Wrong -> stringResource(R.string.pin_wrong)
                PinMessage.Mismatch -> stringResource(R.string.pin_mismatch)
                null -> ""
            }
        Text(
            text = message,
            style = CineTheme.typography.caption,
            color = CineTheme.colors.danger,
            modifier = Modifier.testTag("pin-message"),
        )
    }
}

private val KeypadRows = listOf("123", "456", "789")

@Composable
private fun Keypad(
    onDigit: (Char) -> Unit,
    onErase: () -> Unit,
    five: FocusRequester,
) {
    Column(verticalArrangement = Arrangement.spacedBy(CineSpacing.s)) {
        KeypadRows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(CineSpacing.s)) {
                row.forEach { digit ->
                    KeypadKey(
                        onClick = { onDigit(digit) },
                        digit = digit,
                        focusRequester = if (digit == '5') five else null,
                        focus = rememberCineFocus("key-$digit"),
                        modifier = Modifier.testTag("key-$digit"),
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(CineSpacing.s)) {
            Spacer(Modifier.size(CineSizes.keypadKey))
            KeypadKey(onClick = { onDigit('0') }, digit = '0', modifier = Modifier.testTag("key-0"))
            KeypadKey(
                onClick = onErase,
                icon = CineIcons.Backspace,
                contentDescription = stringResource(R.string.pin_erase),
                modifier = Modifier.testTag("key-erase"),
            )
        }
    }
}

private val DigitKeys =
    mapOf(
        Key.Zero to '0',
        Key.One to '1',
        Key.Two to '2',
        Key.Three to '3',
        Key.Four to '4',
        Key.Five to '5',
        Key.Six to '6',
        Key.Seven to '7',
        Key.Eight to '8',
        Key.Nine to '9',
        Key.NumPad0 to '0',
        Key.NumPad1 to '1',
        Key.NumPad2 to '2',
        Key.NumPad3 to '3',
        Key.NumPad4 to '4',
        Key.NumPad5 to '5',
        Key.NumPad6 to '6',
        Key.NumPad7 to '7',
        Key.NumPad8 to '8',
        Key.NumPad9 to '9',
    )

/** The number keys of the remote type the PIN wherever the focus is. */
private fun handleKey(
    event: KeyEvent,
    onDigit: (Char) -> Unit,
    onErase: () -> Unit,
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    val digit = DigitKeys[event.key]
    return when {
        digit != null -> {
            onDigit(digit)
            true
        }

        event.key == Key.Backspace || event.key == Key.Delete -> {
            onErase()
            true
        }

        else -> {
            false
        }
    }
}

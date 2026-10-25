package fr.guim.cinedoury.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.tv.material3.Text
import fr.guim.cinedoury.core.data.Profile
import fr.guim.cinedoury.core.data.SessionManager
import fr.guim.cinedoury.core.data.StartupTarget
import fr.guim.cinedoury.core.designsystem.component.Wordmark
import fr.guim.cinedoury.core.designsystem.theme.CineSpacing
import fr.guim.cinedoury.core.designsystem.theme.CineTheme
import fr.guim.cinedoury.core.designsystem.theme.ThemeVariant
import fr.guim.cinedoury.core.ui.avatarSource
import fr.guim.cinedoury.feature.auth.AddressMode
import fr.guim.cinedoury.feature.auth.AddressRoute
import fr.guim.cinedoury.feature.auth.PasswordRoute
import fr.guim.cinedoury.feature.auth.PinPurpose
import fr.guim.cinedoury.feature.auth.PinRoute
import fr.guim.cinedoury.feature.auth.ProfilesRoute
import fr.guim.cinedoury.feature.auth.QuickConnectRoute
import fr.guim.cinedoury.feature.auth.ServerSearchRoute
import fr.guim.cinedoury.feature.auth.UnreachableScreen
import fr.guim.cinedoury.feature.auth.WelcomeScreen
import fr.guim.cinedoury.feature.home.HomeRoute
import fr.guim.cinedoury.feature.settings.SettingsNavigation
import fr.guim.cinedoury.feature.settings.SettingsRoute
import fr.guim.cinedoury.tv.catalog.CatalogScreen
import org.koin.compose.koinInject
import fr.guim.cinedoury.core.ui.R as UiR

/**
 * The app: one screen at a time from the [navigator]'s back stack, each with its own view models
 * and saved state. « Retour » goes back one screen.
 */
@Composable
fun CineApp(
    variant: ThemeVariant,
    onToggleVariant: () -> Unit,
    showCatalog: Boolean,
    navigator: Navigator = remember { Navigator(Route.Startup) },
    sessions: SessionManager = koinInject(),
) {
    val saveable = rememberSaveableStateHolder()
    navigator.onRemoved = { saveable.removeState(it.id) }
    val entry = navigator.current
    BackHandler(enabled = navigator.canGoBack) { navigator.pop() }
    CompositionLocalProvider(LocalViewModelStoreOwner provides entry) {
        saveable.SaveableStateProvider(entry.id) {
            Screen(entry.route, navigator, sessions, variant, onToggleVariant, showCatalog)
        }
    }
}

@Composable
private fun Screen(
    route: Route,
    navigator: Navigator,
    sessions: SessionManager,
    variant: ThemeVariant,
    onToggleVariant: () -> Unit,
    showCatalog: Boolean,
) {
    val home = { navigator.replaceAll(Route.Home) }
    when (route) {
        Route.Startup -> {
            StartupScreen(sessions) { target -> navigator.replaceAll(target.toRoute()) }
        }

        Route.Welcome -> {
            WelcomeScreen(onStart = { navigator.push(Route.ServerSearch) })
        }

        Route.ServerSearch -> {
            ServerSearchRoute(
                onConnected = { navigator.push(Route.Profiles) },
                onEnterAddress = { navigator.push(Route.Address(AddressMode.Add)) },
            )
        }

        is Route.Address -> {
            AddressRoute(
                mode = route.mode,
                onDone = {
                    if (route.mode == AddressMode.Add) navigator.replaceTop(Route.Profiles) else navigator.pop()
                },
            )
        }

        is Route.Unreachable -> {
            UnreachableScreen(
                serverName = route.server.name,
                error = route.error,
                onRetry = { navigator.replaceAll(Route.Startup) },
                onChangeServer = { navigator.replaceAll(Route.ServerSearch) },
            )
        }

        Route.Profiles -> {
            ProfilesRoute(
                onOpened = home,
                onSignIn = { username -> navigator.push(Route.QuickConnect(username)) },
                onPin = { profile -> navigator.push(Route.Pin(profile, PinPurpose.Unlock)) },
            )
        }

        is Route.QuickConnect -> {
            QuickConnectRoute(
                username = route.username,
                onSignedIn = home,
                onUsePassword = { instead ->
                    val password = Route.Password(route.username)
                    if (instead) navigator.replaceTop(password) else navigator.push(password)
                },
            )
        }

        is Route.Password -> {
            PasswordRoute(username = route.username, onSignedIn = home)
        }

        is Route.Pin -> {
            PinRoute(
                profile = route.profile,
                purpose = route.purpose,
                avatar = sessions.avatarSource(route.profile.user),
                onDone = { if (route.purpose == PinPurpose.Unlock) home() else navigator.pop() },
            )
        }

        Route.Home -> {
            HomeRoute(
                onSettings = { navigator.push(Route.Settings) },
                onProfiles = {
                    sessions.leaveProfile()
                    navigator.replaceAll(Route.Profiles)
                },
                onSessionEnded = { navigator.replaceAll(Route.Profiles) },
            )
        }

        Route.Settings -> {
            SettingsRoute(navigation = settingsNavigation(navigator), showCatalog = showCatalog)
        }

        Route.Catalog -> {
            CatalogScreen(variant = variant, onToggleVariant = onToggleVariant)
        }
    }
}

private fun settingsNavigation(navigator: Navigator) =
    object : SettingsNavigation {
        override fun pin(
            profile: Profile,
            remove: Boolean,
        ) = navigator.push(Route.Pin(profile, if (remove) PinPurpose.Remove else PinPurpose.Create))

        override fun address(remote: Boolean) =
            navigator.push(Route.Address(if (remote) AddressMode.EditRemote else AddressMode.EditLocal))

        override fun catalog() = navigator.push(Route.Catalog)

        override fun profileClosed() = navigator.replaceAll(Route.Profiles)

        override fun serverForgotten() = navigator.replaceAll(Route.Welcome)
    }

private fun StartupTarget.toRoute(): Route =
    when (this) {
        StartupTarget.NoServer -> Route.Welcome
        is StartupTarget.Unreachable -> Route.Unreachable(server, error)
        StartupTarget.Profiles -> Route.Profiles
        is StartupTarget.Pin -> Route.Pin(profile, PinPurpose.Unlock)
        StartupTarget.Home -> Route.Home
    }

/**
 * While the saved server is reached (local address, then remote): the name of the app and one
 * line, never a black screen (`FONCTIONNALITES.md` § 13).
 */
@Composable
fun StartupScreen(
    sessions: SessionManager,
    onTarget: (StartupTarget) -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(CineTheme.colors.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(CineSpacing.s),
        ) {
            Wordmark(stringResource(UiR.string.brand_name), large = true)
            Text(
                text = stringResource(R.string.startup_connecting),
                style = CineTheme.typography.body,
                color = CineTheme.colors.text2,
            )
        }
    }
    LaunchedEffect(Unit) { onTarget(sessions.start()) }
}

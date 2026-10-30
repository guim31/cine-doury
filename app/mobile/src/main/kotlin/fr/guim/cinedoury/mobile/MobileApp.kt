package fr.guim.cinedoury.mobile

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import fr.guim.cinedoury.core.data.SessionManager
import fr.guim.cinedoury.core.data.StartupTarget
import fr.guim.cinedoury.core.data.quick.QuickActions
import fr.guim.cinedoury.core.designsystem.component.CineIcons
import fr.guim.cinedoury.core.designsystem.mobile.MobileBottomBar
import fr.guim.cinedoury.core.designsystem.mobile.MobileTab
import fr.guim.cinedoury.core.designsystem.theme.CineSpacing
import fr.guim.cinedoury.core.designsystem.theme.CineTheme
import fr.guim.cinedoury.core.model.ItemId
import fr.guim.cinedoury.core.model.LibraryId
import fr.guim.cinedoury.core.model.MediaItem
import fr.guim.cinedoury.core.model.PlaybackRequest
import fr.guim.cinedoury.core.model.RemoteTrailer
import fr.guim.cinedoury.core.ui.LocalQuickActions
import fr.guim.cinedoury.core.ui.RailTarget
import fr.guim.cinedoury.feature.auth.AddressMode
import fr.guim.cinedoury.feature.auth.PinPurpose
import fr.guim.cinedoury.feature.auth.mobile.MobileAddressRoute
import fr.guim.cinedoury.feature.auth.mobile.MobilePasswordRoute
import fr.guim.cinedoury.feature.auth.mobile.MobilePinRoute
import fr.guim.cinedoury.feature.auth.mobile.MobileProfilesRoute
import fr.guim.cinedoury.feature.auth.mobile.MobileQuickConnectRoute
import fr.guim.cinedoury.feature.auth.mobile.MobileServerSearchRoute
import fr.guim.cinedoury.feature.auth.mobile.MobileUnreachableScreen
import fr.guim.cinedoury.feature.auth.mobile.MobileWelcomeScreen
import fr.guim.cinedoury.feature.details.DetailsNavigation
import fr.guim.cinedoury.feature.details.mobile.MobileDetailsRoute
import fr.guim.cinedoury.feature.home.HomeNavigation
import fr.guim.cinedoury.feature.home.mobile.MobileHomeRoute
import fr.guim.cinedoury.feature.library.LibraryNavigation
import fr.guim.cinedoury.feature.library.mobile.MobileLibrariesRoute
import fr.guim.cinedoury.feature.library.mobile.MobileLibraryRoute
import fr.guim.cinedoury.feature.player.mobile.MobilePlaybackSettingsRoute
import fr.guim.cinedoury.feature.player.mobile.MobilePlayerRoute
import fr.guim.cinedoury.feature.search.SearchNavigation
import fr.guim.cinedoury.feature.search.mobile.MobileSearchRoute
import fr.guim.cinedoury.feature.settings.mobile.MobileConnectDeviceRoute
import fr.guim.cinedoury.feature.settings.mobile.MobileProfileNavigation
import fr.guim.cinedoury.feature.settings.mobile.MobileProfileRoute
import org.koin.compose.koinInject
import org.koin.core.context.GlobalContext
import fr.guim.cinedoury.core.ui.R as UiR

/** Tags of the app's own parts, for tests. */
object MobileAppTags {
    const val STARTUP = "startup"

    fun tab(tab: Tab) = "tab-${tab.name.lowercase()}"
}

/**
 * The app on the phone and the tablet (M1, `SPEC.md` § 4.26): one screen at a time from the
 * [navigator]'s back stack, each with its own view models and saved state, and the bar of the tabs at
 * the bottom once a profile is open. « Retour », the key or the gesture of the system, goes back one
 * screen; on the home, it leaves the app.
 */
@Composable
fun MobileApp(
    version: String,
    navigator: Navigator = remember { Navigator(Route.Startup) },
    sessions: SessionManager = koinInject(),
) {
    val saveable = rememberSaveableStateHolder()
    navigator.onRemoved = { saveable.removeState(it.id) }
    val quickActions = remember { GlobalContext.getOrNull()?.getOrNull<QuickActions>() }
    BackHandler(enabled = navigator.canGoBack) { navigator.pop() }
    val entry = navigator.current
    val tab = navigator.currentTab()
    val tabs =
        listOf(
            MobileTab(stringResource(R.string.tab_home), CineIcons.Home, MobileAppTags.tab(Tab.Home)),
            MobileTab(stringResource(R.string.tab_search), CineIcons.Search, MobileAppTags.tab(Tab.Search)),
            MobileTab(stringResource(R.string.tab_libraries), CineIcons.Collections, MobileAppTags.tab(Tab.Libraries)),
            MobileTab(stringResource(R.string.tab_profile), CineIcons.Profile, MobileAppTags.tab(Tab.Profile)),
        )
    Scaffold(
        containerColor = CineTheme.colors.background,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (tab != null) {
                MobileBottomBar(tabs, tab.ordinal, { navigator.select(Tab.entries[it]) })
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            CompositionLocalProvider(
                LocalViewModelStoreOwner provides entry,
                LocalQuickActions provides quickActions,
            ) {
                saveable.SaveableStateProvider(entry.id) {
                    Screen(entry.route, navigator, sessions, version)
                }
            }
        }
    }
}

@Composable
private fun Screen(
    route: Route,
    navigator: Navigator,
    sessions: SessionManager,
    version: String,
) {
    val home = { navigator.replaceAll(Route.Home) }
    val back: () -> Unit = { navigator.pop() }
    when (route) {
        Route.Startup -> {
            StartupScreen(sessions) { target ->
                if (target is StartupTarget.Pin) {
                    navigator.replaceAll(Route.Profiles)
                    navigator.push(Route.Pin(target.profile, PinPurpose.Unlock))
                } else {
                    navigator.replaceAll(target.toRoute())
                }
            }
        }

        Route.Welcome -> {
            MobileWelcomeScreen(onStart = { navigator.push(Route.ServerSearch) })
        }

        Route.ServerSearch -> {
            MobileServerSearchRoute(
                // Found on the network of the house: the remote address is offered first (F, M2).
                onConnected = { navigator.push(Route.Address(AddressMode.EditRemote, offer = true)) },
                onEnterAddress = { navigator.push(Route.Address(AddressMode.Add)) },
                onBack = back,
            )
        }

        is Route.Address -> {
            MobileAddressRoute(
                mode = route.mode,
                onDone = {
                    if (route.mode == AddressMode.Add || route.offer) {
                        navigator.replaceTop(Route.Profiles)
                    } else {
                        navigator.pop()
                    }
                },
                onBack = back,
                onSkip = if (route.offer) ({ navigator.replaceTop(Route.Profiles) }) else null,
            )
        }

        is Route.Unreachable -> {
            MobileUnreachableScreen(
                serverName = route.server.name,
                error = route.error,
                onRetry = { navigator.replaceAll(Route.Startup) },
                onChangeServer = { navigator.replaceAll(Route.ServerSearch) },
            )
        }

        Route.Profiles -> {
            MobileProfilesRoute(
                onOpened = home,
                onSignIn = { username -> navigator.push(Route.QuickConnect(username)) },
                onPin = { profile -> navigator.push(Route.Pin(profile, PinPurpose.Unlock)) },
                onPassword = { navigator.push(Route.Password(null)) },
            )
        }

        is Route.QuickConnect -> {
            MobileQuickConnectRoute(
                username = route.username,
                onSignedIn = home,
                onUsePassword = { instead ->
                    val password = Route.Password(route.username)
                    if (instead) navigator.replaceTop(password) else navigator.push(password)
                },
                onBack = back,
            )
        }

        is Route.Password -> {
            MobilePasswordRoute(username = route.username, onSignedIn = home, onBack = back)
        }

        is Route.Pin -> {
            MobilePinRoute(
                profile = route.profile,
                purpose = route.purpose,
                onDone = { if (route.purpose == PinPurpose.Unlock) home() else navigator.pop() },
                onBack = back,
            )
        }

        else -> {
            SessionScreen(route, navigator, sessions, version)
        }
    }
}

/** The screens once a profile is open: the tabs and what opens over them. */
@Composable
private fun SessionScreen(
    route: Route,
    navigator: Navigator,
    sessions: SessionManager,
    version: String,
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    when (route) {
        Route.Home -> {
            MobileHomeRoute(navigation = navigation(navigator, sessions, context, activity))
        }

        Route.Search -> {
            MobileSearchRoute(navigation = navigation(navigator, sessions, context, activity))
        }

        Route.Libraries -> {
            MobileLibrariesRoute(onOpen = { navigator.push(Route.Library(it)) })
        }

        is Route.Library -> {
            MobileLibraryRoute(
                route.id,
                navigation = navigation(navigator, sessions, context, activity),
                onBack = { navigator.pop() },
            )
        }

        is Route.Details -> {
            MobileDetailsRoute(
                route.item,
                route.season,
                navigation = navigation(navigator, sessions, context, activity),
                onBack = { navigator.pop() },
            )
        }

        is Route.Player -> {
            MobilePlayerRoute(
                request = route.request,
                onClose = { navigator.pop() },
                onSessionEnded = { navigator.replaceAll(Route.Profiles) },
            )
        }

        Route.ProfileTab -> {
            MobileProfileRoute(
                navigation =
                    object : MobileProfileNavigation {
                        override fun connectDevice() = navigator.push(Route.ConnectDevice)

                        override fun address(remote: Boolean) =
                            navigator.push(Route.Address(if (remote) AddressMode.EditRemote else AddressMode.EditLocal))

                        override fun profileClosed() = navigator.replaceAll(Route.Profiles)

                        override fun serverForgotten() = navigator.replaceAll(Route.Welcome)
                    },
                version = version,
                playback = { MobilePlaybackSettingsRoute() },
            )
        }

        Route.ConnectDevice -> {
            MobileConnectDeviceRoute(onBack = { navigator.pop() })
        }

        else -> {}
    }
}

/** Where the screens of a session lead, one object for all their interfaces. */
private fun navigation(
    navigator: Navigator,
    sessions: SessionManager,
    context: Context,
    activity: Activity?,
) = PhoneNavigation(navigator, sessions, context, activity)

@Suppress("TooManyFunctions") // One function per destination the screens ask for.
private class PhoneNavigation(
    private val navigator: Navigator,
    private val sessions: SessionManager,
    private val context: Context,
    private val activity: Activity?,
) : HomeNavigation,
    LibraryNavigation,
    DetailsNavigation,
    SearchNavigation {
    override fun navigate(target: RailTarget) {
        when (target) {
            RailTarget.Home -> {
                navigator.select(Tab.Home)
            }

            RailTarget.Search -> {
                navigator.select(Tab.Search)
            }

            is RailTarget.Library -> {
                navigator.push(Route.Library(target.id))
            }

            RailTarget.Profiles -> {
                sessions.leaveProfile()
                navigator.replaceAll(Route.Profiles)
            }

            RailTarget.Settings -> {
                navigator.select(Tab.Profile)
            }
        }
    }

    override fun open(item: MediaItem) = navigator.push(Route.Details(item))

    override fun play(item: MediaItem) = navigator.push(Route.Player(PlaybackRequest(item)))

    override fun play(request: PlaybackRequest) = navigator.push(Route.Player(request))

    override fun openSeries(
        series: MediaItem,
        season: ItemId?,
    ) = navigator.push(Route.Details(series, season))

    override fun openTrailer(url: String) = openTrailerApp(context, url)

    override fun close() {
        navigator.pop()
    }

    override fun problems(
        library: LibraryId,
        name: String,
    ) = Unit

    // The personalisation of the home is the TV's (the same layout, kept per profile); its screen on
    // the phone comes with the settings, M3.
    override fun customize() = Unit

    override fun quit() {
        activity?.finish()
    }

    override fun sessionEnded() = navigator.replaceAll(Route.Profiles)
}

/** A trailer on the web: the YouTube app first, else whatever opens the address. */
private fun openTrailerApp(
    context: Context,
    url: String,
) {
    val youTube = RemoteTrailer(url).youTubeId?.let { Intent(Intent.ACTION_VIEW, "vnd.youtube:$it".toUri()) }
    val web = Intent(Intent.ACTION_VIEW, url.toUri())
    for (intent in listOfNotNull(youTube, web)) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: ActivityNotFoundException) {
            // No app for this link: the next one.
        }
    }
}

private fun StartupTarget.toRoute(): Route =
    when (this) {
        StartupTarget.NoServer -> Route.Welcome
        is StartupTarget.Unreachable -> Route.Unreachable(server, error)
        StartupTarget.Profiles -> Route.Profiles
        is StartupTarget.Pin -> Route.Pin(profile, PinPurpose.Unlock)
        StartupTarget.Home -> Route.Home
    }

/** While the saved server is reached: the name of the app and one line, never a black screen. */
@Composable
fun StartupScreen(
    sessions: SessionManager,
    onTarget: (StartupTarget) -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize().background(CineTheme.colors.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(CineSpacing.s),
        ) {
            Text(
                stringResource(UiR.string.brand_name),
                style = CineTheme.typography.display,
                color = CineTheme.colors.accent,
            )
            Text(
                stringResource(R.string.startup_connecting),
                style = CineTheme.typography.body,
                color = CineTheme.colors.text2,
            )
        }
    }
    LaunchedEffect(Unit) { onTarget(sessions.start()) }
}

package org.jetbrains.kotlinconf.navigation

import org.jetbrains.kotlinconf.event.ActiveEvent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.map
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.kotlinconf.ConferenceService
import org.jetbrains.kotlinconf.flags.LocalFlags
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.kotlinconf.generated.resources.Res
import org.jetbrains.kotlinconf.generated.resources.app_version
import org.jetbrains.kotlinconf.LocalAppGraph
import org.jetbrains.kotlinconf.LocalMapHandler
import org.jetbrains.kotlinconf.LocalNotificationId
import org.jetbrains.kotlinconf.SessionId
import org.jetbrains.kotlinconf.ThemeChangeAnimation
import org.jetbrains.kotlinconf.URLs
import org.jetbrains.kotlinconf.screens.AboutAppScreen
import org.jetbrains.kotlinconf.screens.AboutConference
import org.jetbrains.kotlinconf.screens.MyScheduleScreen
import org.jetbrains.kotlinconf.screens.NowScreen
import org.jetbrains.kotlinconf.screens.SessionizeSyncScreen
import org.jetbrains.kotlinconf.screens.MediaImportScreen
import org.jetbrains.kotlinconf.screens.TripReportScreen
import org.jetbrains.kotlinconf.screens.CompanyDetailScreen
import org.jetbrains.kotlinconf.screens.InfoScreen
import org.jetbrains.kotlinconf.screens.MapScreen
import org.jetbrains.kotlinconf.screens.NestedMapScreen
import org.jetbrains.kotlinconf.screens.ScheduleScreen
import org.jetbrains.kotlinconf.screens.SessionScreen
import org.jetbrains.kotlinconf.screens.SettingsScreen
import org.jetbrains.kotlinconf.screens.SpeakerDetailScreen
import org.jetbrains.kotlinconf.screens.SpeakersScreen
import org.jetbrains.kotlinconf.screens.StartNotificationsScreen
import org.jetbrains.kotlinconf.screens.licenses.LicensesScreen
import org.jetbrains.kotlinconf.screens.licenses.SingleLicenseScreen
import org.jetbrains.kotlinconf.ui.theme.KotlinConfDarkColors
import org.jetbrains.kotlinconf.ui.theme.KotlinConfLightColors
import org.jetbrains.kotlinconf.ui.theme.KotlinConfTheme
import org.jetbrains.kotlinconf.utils.getStoreUrl

fun navigateByLocalNotificationId(notificationId: String) {
    LocalNotificationId.parse(notificationId)?.let {
        when (it.type) {
            LocalNotificationId.Type.SessionStart, LocalNotificationId.Type.SessionEnd ->
                navigateToSession(SessionId(it.id))
        }
    }
}

fun navigateToSession(sessionId: SessionId) {
    notificationNavRequests.trySend(NavRequest(ScheduleScreen, SessionScreen(sessionId)))
}

data class NavRequest(val topLevelRoute: TopLevelRoute, val targetRoute: AppRoute)

private val notificationNavRequests = Channel<NavRequest>(capacity = 1)

@Composable
private fun NotificationHandler(navigator: Navigator) {
    LaunchedEffect(Unit) {
        while (true) {
            val request: NavRequest = notificationNavRequests.receive()
            navigator.activate(request.topLevelRoute, withReselection = false)
            navigator.add(request.targetRoute)
        }
    }
}

@Composable
internal fun NavHost(
    isOnboardingComplete: Boolean,
    isDarkTheme: Boolean,
    onThemeChange: ((Boolean) -> Unit)?,
) {
    val skipNotificationsOnboarding = LocalFlags.current.supportsNotifications.not()
    val startRoute = remember {
        when {
            isOnboardingComplete || skipNotificationsOnboarding -> NowScreen
            else -> StartNotificationsScreen
        }
    }

    val navState = rememberNavState(
        startRoute = startRoute,
        topLevelRoutes = setOf(
            NowScreen,
            MyScheduleScreen,
            ScheduleScreen,
            SpeakersScreen,
            MapScreen,
            InfoScreen,
        ),
        primaryTopLevelRoute = NowScreen,
    )

    val topLevelBackEnabled = LocalFlags.current.enableBackOnTopLevelScreens
    val navigator = remember(navState, topLevelBackEnabled) {
        Navigator(navState, topLevelBackEnabled)
    }

    BrowserIntegration(navState)

    NotificationHandler(navigator)

    val entryProvider = entryProvider {
        screens(
            navigator = navigator,
            onBack = { navigator.goBack() },
        )
    }

    val conferenceService = LocalAppGraph.current.conferenceService
    val showMap by remember { conferenceService.mapData.map { it == null || it.floors.isNotEmpty() } }
        .collectAsStateWithLifecycle(false)

    ThemeChangeAnimation(
        isDarkTheme = isDarkTheme,
        enabled = navState.currentBackstack.lastOrNull() is SettingsScreen,
    ) { appliedIsDarkTheme ->
        val colors = when {
            appliedIsDarkTheme -> ActiveEvent.theme?.dark ?: KotlinConfDarkColors
            else -> ActiveEvent.theme?.light ?: KotlinConfLightColors
        }

        if (onThemeChange != null) {
            LaunchedEffect(colors) { onThemeChange(colors.isDark) }
        }

        KotlinConfTheme(
            rippleEnabled = LocalFlags.current.rippleEnabled,
            colors = colors,
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(KotlinConfTheme.colors.mainBackground)
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
                    )
            ) {
                NavScaffold(
                    navState = navState,
                    navigator = navigator,
                    showMap = showMap,
                ) {
                    NavDisplay(
                        entries = navState.toDecoratedEntries(entryProvider),
                        onBack = navigator::goBack,
                    )
                }
            }
        }
    }
}

private val noAnimationTransition = EnterTransition.None togetherWith ExitTransition.None

private val noAnimationMetadata =
    NavDisplay.transitionSpec { noAnimationTransition } +
            NavDisplay.popTransitionSpec { noAnimationTransition } +
            NavDisplay.predictivePopTransitionSpec { noAnimationTransition }

private fun EntryProviderScope<AppRoute>.screens(
    navigator: Navigator,
    onBack: () -> Unit,
) {
    entry<StartNotificationsScreen> {
        StartNotificationsScreen(
            onDone = {
                navigator.set(NowScreen)
            }
        )
    }

    entry<ScheduleScreen>(metadata = noAnimationMetadata) {
        val service: ConferenceService = LocalAppGraph.current.conferenceService
        LaunchedEffect(Unit) {
            service.completeOnboarding()
        }
        ScheduleScreen(
            onSession = { navigator.add(SessionScreen(it)) },
            onNavigateToMap = { roomName -> navigator.add(NestedMapScreen(roomName)) },
            tabReselections = navigator.tabReselections(forRoute = ScheduleScreen),
        )
    }

    entry<NowScreen>(metadata = noAnimationMetadata) {
        val service: ConferenceService = LocalAppGraph.current.conferenceService
        LaunchedEffect(Unit) {
            service.completeOnboarding()
        }
        NowScreen(
            onSession = { navigator.add(SessionScreen(it)) },
            onTripReport = { navigator.add(TripReportScreen) },
        )
    }

    entry<SessionizeSyncScreen> {
        SessionizeSyncScreen(onBack = onBack)
    }

    entry<TripReportScreen> {
        TripReportScreen(
            onBack = onBack,
            onSession = { navigator.add(SessionScreen(it)) },
            onImport = { navigator.add(MediaImportScreen) },
        )
    }

    entry<MediaImportScreen> {
        MediaImportScreen(onDone = onBack)
    }

    entry<MyScheduleScreen>(metadata = noAnimationMetadata) {
        MyScheduleScreen(
            onSession = { navigator.add(SessionScreen(it)) },
            onSync = if (ActiveEvent.sessionizeAppUrl != null) { { navigator.add(SessionizeSyncScreen) } } else null,
        )
    }

    entry<SpeakersScreen>(metadata = noAnimationMetadata) {
        SpeakersScreen(
            onSpeaker = { navigator.add(SpeakerDetailScreen(it)) },
            onCompany = { navigator.add(CompanyScreen(it)) },
        )
    }

    entry<MapScreen>(metadata = noAnimationMetadata) {
        val mapHandler = LocalMapHandler.current
        MapScreen(
            onHowToFindVenue = { address -> mapHandler.openNavigation(address) },
        )
    }

    entry<InfoScreen>(metadata = noAnimationMetadata) {
        val uriHandler = LocalUriHandler.current
        val conferenceService = LocalAppGraph.current.conferenceService
        val mapHandler = LocalMapHandler.current
        val appVersion = stringResource(Res.string.app_version)
        InfoScreen(
            onAboutConf = { navigator.add(AboutConferenceScreen) },
            onHowToFindVenue = { address -> mapHandler.openNavigation(address) },
            onAboutApp = { navigator.add(AboutAppScreen) },
            onCodeOfConduct = ActiveEvent.codeOfConductUrl?.let { url -> { uriHandler.openUri(url) } },
            onTwitter = URLs.TWITTER?.let { url -> { uriHandler.openUri(url) } },
            onSlack = URLs.SLACK?.let { url -> { uriHandler.openUri(url) } },
            onBluesky = URLs.BLUESKY?.let { url -> { uriHandler.openUri(url) } },
            onSettings = { navigator.add(SettingsScreen) },
            onReportProblem = { uriHandler.openUri(URLs.reportProblem(appVersion)) },
            onSessionizeSync = if (ActiveEvent.sessionizeAppUrl != null) { { navigator.add(SessionizeSyncScreen) } } else null,
        )
    }

    entry<SpeakerDetailScreen> {
        SpeakerDetailScreen(
            speakerId = it.speakerId,
            onBack = onBack,
            onSession = { sessionId -> navigator.add(SessionScreen(sessionId)) },
            onCompany = { name -> navigator.add(CompanyScreen(name)) },
        )
    }
    entry<CompanyScreen> {
        CompanyDetailScreen(
            name = it.name,
            onBack = onBack,
            onSession = { sessionId -> navigator.add(SessionScreen(sessionId)) },
            onSpeaker = { speakerId -> navigator.add(SpeakerDetailScreen(speakerId)) },
        )
    }
    entry<SessionScreen> {
        val urlHandler = LocalUriHandler.current
        SessionScreen(
            sessionId = it.sessionId,
            onBack = onBack,
            onSpeaker = { speakerId -> navigator.add(SpeakerDetailScreen(speakerId)) },
            onWatchVideo = { videoUrl -> urlHandler.openUri(videoUrl) },
            onNavigateToMap = { roomName ->
                navigator.add(NestedMapScreen(roomName))
            },
        )
    }

    entry<AboutAppScreen> {
        val uriHandler = LocalUriHandler.current
        AboutAppScreen(
            onBack = onBack,
            onGitHubRepo = { uriHandler.openUri(URLs.GITHUB_REPO) },
            onRateApp = { getStoreUrl()?.let { uriHandler.openUri(it) } },
            onLicenses = { navigator.add(LicensesScreen) },
        )
    }
    entry<LicensesScreen> {
        LicensesScreen(
            onLicenseClick = { licenseName, licenseText ->
                navigator.add(SingleLicenseScreen(licenseName, licenseText))
            },
            onBack = onBack,
        )
    }
    entry<SingleLicenseScreen> {
        SingleLicenseScreen(
            licenseName = it.licenseName,
            licenseContent = it.licenseText,
            onBack = onBack,
        )
    }
    entry<AboutConferenceScreen> {
        val urlHandler = LocalUriHandler.current
        AboutConference(
            onWebsiteLink = URLs.CONFERENCE_HOMEPAGE?.let { url -> { urlHandler.openUri(url) } },
            onBack = onBack,
        )
    }
    entry<SettingsScreen> {
        SettingsScreen(onBack = onBack)
    }
    entry<NestedMapScreen> {
        NestedMapScreen(
            roomName = it.roomName,
            onBack = onBack,
        )
    }
}

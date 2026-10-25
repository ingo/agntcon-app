package org.jetbrains.kotlinconf.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.github.terrakok.navigation3.browser.ChronologicalBrowserNavigation
import com.github.terrakok.navigation3.browser.HierarchicalBrowserNavigation
import com.github.terrakok.navigation3.browser.buildBrowserHistoryFragment
import com.github.terrakok.navigation3.browser.getBrowserHistoryFragmentName
import com.github.terrakok.navigation3.browser.getBrowserHistoryFragmentParameters
import org.jetbrains.kotlinconf.SessionId
import org.jetbrains.kotlinconf.SpeakerId
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.js

@OptIn(ExperimentalWasmJsInterop::class)
private fun isMobileBrowser(): Boolean = js(
    """{
          if (navigator.userAgentData && navigator.userAgentData.mobile) {
            return navigator.userAgentData.mobile;
          }
          if (window.matchMedia && window.matchMedia('(pointer: coarse)').matches) {
            return window.innerWidth <= 820;
          }
          return /Mobi|Android|iPhone|iPad|iPod|IEMobile|Opera Mini|CriOS/i.test(navigator.userAgent);
      }"""
)

private fun AppRoute.toBrowserHistoryFragment() = when (val key = this) {
    AboutAppScreen -> buildBrowserHistoryFragment("about_app")
    AboutConferenceScreen -> buildBrowserHistoryFragment("about_conference")
    LicensesScreen -> buildBrowserHistoryFragment("licenses")
    SettingsScreen -> buildBrowserHistoryFragment("settings")
    StartNotificationsScreen -> buildBrowserHistoryFragment("notifications")
    ScheduleScreen -> buildBrowserHistoryFragment("schedule")
    NowScreen -> buildBrowserHistoryFragment("now")
    MyScheduleScreen -> buildBrowserHistoryFragment("my_schedule")
    SessionizeSyncScreen -> buildBrowserHistoryFragment("sessionize_sync")
    TripReportScreen -> buildBrowserHistoryFragment("trip_report")
    SpeakersScreen -> buildBrowserHistoryFragment("speakers")
    MapScreen -> buildBrowserHistoryFragment("map")
    InfoScreen -> buildBrowserHistoryFragment("info")
    is NestedMapScreen -> buildBrowserHistoryFragment(
        "map_detail",
        mapOf("roomName" to key.roomName)
    )

    is CompanyScreen -> buildBrowserHistoryFragment(
        "company",
        mapOf("name" to key.name)
    )

    is SessionScreen -> buildBrowserHistoryFragment(
        "session",
        mapOf("sessionId" to key.sessionId.id)
    )

    is SpeakerDetailScreen -> buildBrowserHistoryFragment(
        "speaker",
        mapOf("speakerId" to key.speakerId.id)
    )

    is SingleLicenseScreen -> buildBrowserHistoryFragment(
        "license",
        mapOf("licenseName" to key.licenseName, "licenseText" to key.licenseText)
    )
}

@Composable
actual fun BrowserIntegration(navState: NavState) {
    val isMobile = remember { isMobileBrowser() }
    if (isMobile) {
        HierarchicalBrowserNavigation {
            navState.currentBackstack.lastOrNull()?.toBrowserHistoryFragment()
        }
    } else {
        ChronologicalBrowserNavigation(
            backStack = navState.currentBackstack,
            saveKey = { key -> key.toBrowserHistoryFragment() },
            restoreKey = { fragment ->
                val name = getBrowserHistoryFragmentName(fragment)
                val params = getBrowserHistoryFragmentParameters(fragment)

                when (name) {
                    "about_app" -> AboutAppScreen
                    "about_conference" -> AboutConferenceScreen
                    "licenses" -> LicensesScreen
                    "settings" -> SettingsScreen
                    "notifications" -> StartNotificationsScreen
                    "schedule" -> ScheduleScreen
                    "now" -> NowScreen
                    "my_schedule" -> MyScheduleScreen
                    "sessionize_sync" -> SessionizeSyncScreen
                    "trip_report" -> TripReportScreen
                    "speakers" -> SpeakersScreen
                    "map" -> MapScreen
                    "info" -> InfoScreen
                    "map_detail" -> params["roomName"]?.let { NestedMapScreen(it) }
                    "company" -> params["name"]?.let { CompanyScreen(it) }
                    "session" -> params["sessionId"]?.let { SessionScreen(SessionId(it)) }
                    "speaker" -> params["speakerId"]?.let { SpeakerDetailScreen(SpeakerId(it)) }
                    "license" -> {
                        val licenseName = params["licenseName"]
                        val licenseText = params["licenseText"]
                        if (licenseName != null && licenseText != null) {
                            SingleLicenseScreen(licenseName, licenseText)
                        } else null
                    }

                    else -> null
                }
            }
        )
    }
}

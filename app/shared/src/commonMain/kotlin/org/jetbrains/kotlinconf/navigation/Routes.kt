package org.jetbrains.kotlinconf.navigation

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.kotlinconf.SessionId
import org.jetbrains.kotlinconf.SpeakerId

@Serializable
sealed interface AppRoute

@Serializable
sealed interface TopLevelRoute : AppRoute

@Serializable
@SerialName("Now")
data object NowScreen : AppRoute, TopLevelRoute

@Serializable
@SerialName("Schedule")
data object ScheduleScreen : AppRoute, TopLevelRoute

@Serializable
@SerialName("Speakers")
data object SpeakersScreen : AppRoute, TopLevelRoute

@Serializable
@SerialName("Map")
data object MapScreen : AppRoute, TopLevelRoute

@Serializable
@SerialName("Info")
data object InfoScreen : AppRoute, TopLevelRoute

@Serializable
@SerialName("AboutConference")
data object AboutConferenceScreen : AppRoute

@Serializable
@SerialName("AboutApp")
data object AboutAppScreen : AppRoute

@Serializable
@SerialName("WelcomeSetupNotifications")
data object StartNotificationsScreen : AppRoute

@Serializable
@SerialName("Settings")
data object SettingsScreen : AppRoute

@Serializable
@SerialName("Licenses")
data object LicensesScreen : AppRoute

@Serializable
@SerialName("License")
data class SingleLicenseScreen(
    val licenseName: String,
    val licenseText: String,
) : AppRoute

@Serializable
@SerialName("MySchedule")
data object MyScheduleScreen : AppRoute, TopLevelRoute

@Serializable
@SerialName("SessionizeSync")
data object SessionizeSyncScreen : AppRoute

@Serializable
@SerialName("Session")
data class SessionScreen(
    val sessionId: SessionId,
) : AppRoute

@Serializable
@SerialName("Speaker")
data class SpeakerDetailScreen(val speakerId: SpeakerId) : AppRoute

@Serializable
@SerialName("Company")
data class CompanyScreen(val name: String) : AppRoute

@Serializable
@SerialName("MapDetail")
data class NestedMapScreen(val roomName: String) : AppRoute

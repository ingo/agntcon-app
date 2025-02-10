package org.jetbrains.kotlinconf.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import org.jetbrains.kotlinconf.ConferenceService
import org.jetbrains.kotlinconf.NotificationSettings
import org.jetbrains.kotlinconf.Theme

class SettingsViewModel(
    private val service: ConferenceService,
) : ViewModel() {
    val theme: StateFlow<Theme> = service.getTheme()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Theme.SYSTEM)

    val notificationSettings: StateFlow<NotificationSettings> = service.getNotificationSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NotificationSettings())

    fun setTheme(theme: Theme) {
        service.setTheme(theme)
    }

    fun setNotificationSettings(settings: NotificationSettings) {
        service.setNotificationSettings(settings)
    }
}

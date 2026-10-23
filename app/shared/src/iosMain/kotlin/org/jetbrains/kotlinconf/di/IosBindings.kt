package org.jetbrains.kotlinconf.di

import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.ObservableSettings
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import platform.Foundation.NSUserDefaults

@BindingContainer
@ContributesTo(AppScope::class)
interface IosBindings {

    companion object {
        @Provides
        @SingleIn(AppScope::class)
        @OptIn(ExperimentalSettingsApi::class)
        fun provideSettings(): ObservableSettings =
            NSUserDefaultsSettings(NSUserDefaults.standardUserDefaults)
    }
}

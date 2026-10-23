package org.jetbrains.kotlinconf.di

import com.russhwolf.settings.ObservableSettings
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import org.jetbrains.kotlinconf.EmptyLocalNotificationService
import org.jetbrains.kotlinconf.LocalNotificationService
import org.jetbrains.kotlinconf.storage.createSettings

@BindingContainer
@ContributesTo(AppScope::class)
object JvmBindings {

    @Provides
    @SingleIn(AppScope::class)
    fun provideSettings(): ObservableSettings = createSettings()


    @Provides
    @SingleIn(AppScope::class)
    fun provideLocalNotificationService(): LocalNotificationService = EmptyLocalNotificationService()
}

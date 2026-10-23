package org.jetbrains.kotlinconf.di

import android.app.Application
import android.content.Context
import androidx.preference.PreferenceManager
import com.russhwolf.settings.ObservableSettings
import com.russhwolf.settings.SharedPreferencesSettings
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

@BindingContainer
@ContributesTo(AppScope::class)
object AndroidBindings {

    @Provides
    @SingleIn(AppScope::class)
    fun provideApplicationContext(application: Application): Context = application

    @Provides
    @SingleIn(AppScope::class)
    fun provideSettings(application: Application): ObservableSettings =
        SharedPreferencesSettings(PreferenceManager.getDefaultSharedPreferences(application))

}

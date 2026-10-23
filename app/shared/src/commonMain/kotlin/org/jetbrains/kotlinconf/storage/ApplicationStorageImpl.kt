package org.jetbrains.kotlinconf.storage

import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.ObservableSettings
import com.russhwolf.settings.coroutines.getBooleanFlow
import com.russhwolf.settings.coroutines.getStringOrNullFlow
import com.russhwolf.settings.set
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.jetbrains.kotlinconf.flags.Flags
import org.jetbrains.kotlinconf.Theme
import org.jetbrains.kotlinconf.utils.Logger
import org.jetbrains.kotlinconf.utils.tagged

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@OptIn(ExperimentalSettingsApi::class)
class ApplicationStorageImpl(
    private val settings: ObservableSettings,
    logger: Logger,
) : ApplicationStorage {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    private val taggedLogger = logger.tagged("ApplicationStorageImpl")

    private inline fun <reified T> String?.decodeOrNull(): T? {
        if (this == null) return null
        return try {
            json.decodeFromString<T>(this)
        } catch (_: SerializationException) {
            null
        }
    }

    override fun isOnboardingComplete(): Flow<Boolean> = settings.getBooleanFlow(Keys.ONBOARDING_COMPLETE, false)
    override suspend fun setOnboardingComplete(value: Boolean) = settings.set(Keys.ONBOARDING_COMPLETE, value)

    override fun getTheme(): Flow<Theme> = settings.getStringOrNullFlow(Keys.THEME).map { Theme.entries.firstOrNull { entry -> entry.name == it } ?: Theme.SYSTEM }
    override suspend fun setTheme(value: Theme) = settings.set(Keys.THEME, value.name)

    override fun getFlagsBlocking(): Flags? = settings.getStringOrNull(Keys.FLAGS)?.decodeOrNull<Flags>()
    override fun getFlags(): Flow<Flags?> = settings.getStringOrNullFlow(Keys.FLAGS).map { it.decodeOrNull<Flags>() }
    override suspend fun setFlags(value: Flags) = settings.set(Keys.FLAGS, json.encodeToString(value))

    override fun initialize() {
        val version = settings.getInt(Keys.STORAGE_VERSION, 0)
        taggedLogger.log { "Storage version is $version" }

        // A fresh install, or data from a version this app does not know: start clean
        if (version != LATEST_STORAGE_VERSION) destructiveUpgrade()
    }

    private fun destructiveUpgrade() {
        taggedLogger.log { "Performing destructive upgrade to $LATEST_STORAGE_VERSION" }
        settings.clear()
        settings.set(Keys.STORAGE_VERSION, LATEST_STORAGE_VERSION)
    }

    companion object {
        /**
         * Version of the stored data. Existing installs keep their notes, bookmarks and caches as long
         * as this stays the same; change it only together with a migration.
         */
        const val LATEST_STORAGE_VERSION: Int = 2026_001
    }

    private object Keys {
        const val STORAGE_VERSION = "storageVersion"
        const val ONBOARDING_COMPLETE = "onboardingComplete"
        const val THEME = "theme"
        const val FLAGS = "flags"
    }
}

package org.jetbrains.kotlinconf.storage

import kotlinx.coroutines.flow.Flow
import org.jetbrains.kotlinconf.flags.Flags
import org.jetbrains.kotlinconf.Theme

interface ApplicationStorage {
    fun isOnboardingComplete(): Flow<Boolean>
    suspend fun setOnboardingComplete(value: Boolean)

    fun getTheme(): Flow<Theme>
    suspend fun setTheme(value: Theme)

    fun getFlagsBlocking(): Flags?
    fun getFlags(): Flow<Flags?>
    suspend fun setFlags(value: Flags)

    fun initialize()
}

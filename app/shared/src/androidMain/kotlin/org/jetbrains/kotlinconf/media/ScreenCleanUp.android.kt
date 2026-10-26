package org.jetbrains.kotlinconf.media

import org.jetbrains.kotlinconf.personal.CleanUpMode

/** No clean-up here yet; photos are kept as they were taken. */
actual val screenCleanUpAvailable: Boolean = false

actual suspend fun cleanUpScreenPhoto(input: String, output: String, mode: CleanUpMode?): Boolean = false

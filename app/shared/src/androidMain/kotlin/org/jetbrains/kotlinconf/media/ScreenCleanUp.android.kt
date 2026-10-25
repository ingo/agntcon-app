package org.jetbrains.kotlinconf.media

/** No clean-up here yet; photos are kept as they were taken. */
actual val screenCleanUpAvailable: Boolean = false

actual suspend fun cleanUpScreenPhoto(input: String, output: String): Boolean = false

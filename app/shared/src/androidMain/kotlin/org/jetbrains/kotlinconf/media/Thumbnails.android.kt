package org.jetbrains.kotlinconf.media

/** The image loader here decodes photos at the size they are shown, so no separate preview is needed. */
actual fun makeThumbnail(input: String, output: String): Boolean = false

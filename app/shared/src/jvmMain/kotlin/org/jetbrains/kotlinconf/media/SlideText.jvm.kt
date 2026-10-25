package org.jetbrains.kotlinconf.media

/** No on-device text recognition here; the attendee picks from the photos. */
actual suspend fun recognizeText(path: String): String? = null

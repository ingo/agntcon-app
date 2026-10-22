package org.jetbrains.kotlinconf

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/** Hands text or a file to the platform's share sheet (or the closest thing a platform has). */
interface ShareHandler {
    /** Shares [text] as it is. */
    fun shareText(subject: String, text: String)

    /** Shares a small text file, e.g. a calendar file; the receiving app decides what to do with it. */
    fun shareFile(fileName: String, mimeType: String, content: String)
}

val LocalShareHandler = staticCompositionLocalOf<ShareHandler> {
    error("No ShareHandler provided")
}

@Composable
expect fun rememberShareHandler(): ShareHandler

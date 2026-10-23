@file:OptIn(ExperimentalWasmJsInterop::class)

package org.jetbrains.kotlinconf

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.window.ComposeViewport
import dev.zacsweers.metro.createGraphFactory
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.preloadFont
import org.jetbrains.kotlinconf.di.WebAppGraph
import org.jetbrains.kotlinconf.flags.Flags
import org.jetbrains.kotlinconf.ui.initCoil
import org.jetbrains.kotlinconf.utils.Logger
import org.jetbrains.kotlinconf.web.generated.resources.NotoColorEmoji
import org.jetbrains.kotlinconf.web.generated.resources.Res
import kotlinx.coroutines.delay
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.time.Duration.Companion.seconds

private val EMOJI_FONT_DELAY = 5.seconds

fun main() {
    initCoil()

    val appGraph = createGraphFactory<WebAppGraph.Factory>().create(
        // A web page cannot schedule reminders for when it is closed, so there is nothing to ask about
        platformFlags = Flags(supportsNotifications = false)
    )

    initApp(
        appGraph = appGraph,
        platformLogger = object : Logger {
            override fun log(tag: String, lazyMessage: () -> String) {
                println("[$tag] ${lazyMessage()}")
            }
        },
    )

    @OptIn(ExperimentalComposeUiApi::class)
    ComposeViewport {
        // The emoji font is 10 MB. Fetching it alongside the app makes the first visit much slower, and only
        // emoji need it, so it waits until the app has been on screen for a while.
        var loadEmojiFont by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            delay(EMOJI_FONT_DELAY)
            loadEmojiFont = true
        }
        @OptIn(ExperimentalResourceApi::class)
        val emojiFont = if (loadEmojiFont) preloadFont(Res.font.NotoColorEmoji).value else null
        val fontFamilyResolver = LocalFontFamilyResolver.current
        LaunchedEffect(fontFamilyResolver, emojiFont) {
            if (emojiFont != null) {
                fontFamilyResolver.preload(FontFamily(listOf(emojiFont)))
            }
        }

        App(appGraph)
    }
}
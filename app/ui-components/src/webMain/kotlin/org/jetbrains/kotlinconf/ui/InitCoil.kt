package org.jetbrains.kotlinconf.ui

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.util.DebugLogger

internal expect val platformContext: PlatformContext

fun initCoil() {
    SingletonImageLoader.setSafe {
        ImageLoader.Builder(platformContext)
            .logger(DebugLogger())
            .build()
    }
}

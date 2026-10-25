package org.jetbrains.kotlinconf.media

import androidx.compose.runtime.Composable

/** The web build keeps no files of its own, so it offers no photos or videos. */
@Composable
actual fun rememberMediaPicker(): MediaPicker? = null

package org.jetbrains.kotlinconf.media

/**
 * Raised whenever the clean-up gets better, so photos cleaned up (or passed over) by an older one are done again.
 * 2: finds screens from further back and slides that fill the frame, removes colour casts, no longer washes out.
 * 3: dark slides with little text stay dark instead of turning orange.
 * 4: the background of a dark slide turns black even when the projector's black is warmer than its white.
 */
const val SCREEN_CLEAN_UP_VERSION = 4

/** Whether this platform can straighten and clean up photos of screens and whiteboards. */
expect val screenCleanUpAvailable: Boolean

/**
 * Looks for a screen, slide or whiteboard filling a good part of the photo at [input]. If there is one, writes a
 * straightened, cropped and cleaned-up copy to [output] and returns true; a photo without one (people, a room) is
 * left alone and the result is false. Runs on the device.
 */
expect suspend fun cleanUpScreenPhoto(input: String, output: String): Boolean

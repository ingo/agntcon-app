package org.jetbrains.kotlinconf.media

/** Whether this platform can straighten and clean up photos of screens and whiteboards. */
expect val screenCleanUpAvailable: Boolean

/**
 * Looks for a screen, slide or whiteboard filling a good part of the photo at [input]. If there is one, writes a
 * straightened, cropped and cleaned-up copy to [output] and returns true; a photo without one (people, a room) is
 * left alone and the result is false. Runs on the device.
 */
expect suspend fun cleanUpScreenPhoto(input: String, output: String): Boolean

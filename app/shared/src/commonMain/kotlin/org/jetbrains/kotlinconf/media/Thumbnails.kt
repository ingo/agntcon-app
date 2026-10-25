package org.jetbrains.kotlinconf.media

/**
 * Writes a small JPEG preview (about 480 pixels on the long side) of the photo at [input] to [output]. Lists show
 * these instead of full photos, which are tens of megabytes each once decoded. False where the platform's image
 * loader shrinks photos well enough itself.
 */
expect fun makeThumbnail(input: String, output: String): Boolean

const val THUMBNAIL_PIXELS = 480

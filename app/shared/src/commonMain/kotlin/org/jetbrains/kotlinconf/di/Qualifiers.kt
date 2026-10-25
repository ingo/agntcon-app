package org.jetbrains.kotlinconf.di

import dev.zacsweers.metro.Qualifier

@Qualifier
annotation class Year

/** The folder where the app keeps files of its own (the attendee's photos and videos). */
@Qualifier
annotation class FileStorageDir



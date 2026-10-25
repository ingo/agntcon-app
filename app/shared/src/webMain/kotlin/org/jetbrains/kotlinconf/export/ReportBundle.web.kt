package org.jetbrains.kotlinconf.export

/** The web build cannot write files, so there is no report bundle there; the text report still works. */
actual val reportBundleBuilder: ReportBundleBuilder? = null

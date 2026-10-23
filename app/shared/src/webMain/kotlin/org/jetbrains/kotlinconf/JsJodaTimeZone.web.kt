@file:OptIn(ExperimentalWasmJsInterop::class)

package org.jetbrains.kotlinconf

import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsModule

/**
 * kotlinx-datetime reads time zones from js-joda's database, which exists only once
 * `@js-joda/timezone` has been loaded. Nothing else refers to it. Kotlin/Wasm initialises top-level properties at startup, but
 * Kotlin/JS (the fallback for browsers without WasmGC) only when something in the file is used, so the property is
 * marked eager or the first named zone ("America/Los_Angeles") fails with "js-joda timezone database is not available" and the app never starts.
 */
@JsModule("@js-joda/timezone")
external object JsJodaTimeZoneModule

@OptIn(ExperimentalStdlibApi::class)
@EagerInitialization
private val jsJodaTz = JsJodaTimeZoneModule

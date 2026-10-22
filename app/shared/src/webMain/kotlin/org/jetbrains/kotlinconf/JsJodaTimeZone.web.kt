@file:OptIn(ExperimentalWasmJsInterop::class)

package org.jetbrains.kotlinconf

import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsModule

@JsModule("@js-joda/timezone")
external object JsJodaTimeZoneModule

private val jsJodaTz = JsJodaTimeZoneModule
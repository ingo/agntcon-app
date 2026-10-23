package org.jetbrains.kotlinconf

import org.jetbrains.kotlinconf.event.ActiveEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.kotlinconf.di.AppGraph
import org.jetbrains.kotlinconf.flags.FlagsManager
import org.jetbrains.kotlinconf.utils.BufferedDelegatingLogger
import org.jetbrains.kotlinconf.utils.DebugLogger
import org.jetbrains.kotlinconf.utils.Logger
import org.jetbrains.kotlinconf.utils.NoopProdLogger

fun initApp(
    appGraph: AppGraph,
    platformLogger: Logger,
) {
    EVENT_TIME_ZONE = ActiveEvent.timeZone
    initFlagsAndLogging(
        appScope = appGraph.scope,
        platformLogger = platformLogger,
        bufferedDelegatingLogger = appGraph.bufferedDelegatingLogger,
        flagsManager = appGraph.flagsManager,
    )
}

private fun initFlagsAndLogging(
    appScope: CoroutineScope,
    platformLogger: Logger,
    bufferedDelegatingLogger: BufferedDelegatingLogger,
    flagsManager: FlagsManager,
) {
    appScope.launch {
        val flags = flagsManager.initAndGetFlags()
        bufferedDelegatingLogger.attach(
            when {
                flags.debugLogging -> DebugLogger(platformLogger)
                else -> NoopProdLogger()
            }
        )
    }
}

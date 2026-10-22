package org.jetbrains.kotlinconf.network

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.kotlinconf.sessionize.SessionizeData
import org.jetbrains.kotlinconf.sessionize.parseSessionizeData
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Reads a public Sessionize feed for events without a backend. The schedule and the conference
 * info are both built from the same feed, so one download serves both for a minute.
 */
@Inject
@SingleIn(AppScope::class)
class SessionizeFeed(
    private val client: HttpClient,
) {
    private val mutex = Mutex()
    private var cached: Pair<Instant, SessionizeData>? = null

    suspend fun get(url: String): SessionizeData = mutex.withLock {
        cached?.let { (fetchedAt, data) ->
            if (Clock.System.now() - fetchedAt < 1.minutes) return data
        }
        // A full feed is several hundred kilobytes, more than the default timeout allows on a slow network
        val body = client.get(url) { timeout { requestTimeoutMillis = 30.seconds.inWholeMilliseconds } }.bodyAsText()
        val data = parseSessionizeData(body)
        cached = Clock.System.now() to data
        data
    }
}

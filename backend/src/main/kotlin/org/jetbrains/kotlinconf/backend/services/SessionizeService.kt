package org.jetbrains.kotlinconf.backend.services

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.kotlinconf.Conference
import org.jetbrains.kotlinconf.backend.utils.ConferenceConfig
import org.jetbrains.kotlinconf.sessionize.parseSessionizeData
import org.jetbrains.kotlinconf.sessionize.toConference
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.minutes

class SessionizeService(
    private val client: HttpClient,
    scope: CoroutineScope,
    config: ConferenceConfig
) {
    private val conference = MutableSharedFlow<Conference>(replay = 1)

    private val sessionizeUrl: String = config.sessionizeUrl
    private val sessionizeInterval: Long = config.sessionizeInterval
    private val timeZone = config.timeZone
    private val rules = config.sessionizeRules
    private val log = LoggerFactory.getLogger("SessionizeService")

    init {
        scope.launch {
            log.info("Synchronizing each $sessionizeInterval minutes with $sessionizeUrl")
            while (true) {
                log.trace("Synchronizing to Sessionize…")
                runCatching {
                    synchronizeWithSessionize(sessionizeUrl)
                }.onFailure { cause ->
                    log.error("Failed to synchronize to Sessionize: ${cause.message}", cause)
                }

                log.trace("Finished loading data from Sessionize.")
                delay(sessionizeInterval.minutes)
            }
        }
    }

    suspend fun synchronizeWithSessionize(
        sessionizeUrl: String,
    ) {
        val updatedValue = parseSessionizeData(client.get(sessionizeUrl).bodyAsText())
            .toConference(timeZone, rules)

        conference.emit(updatedValue)
    }

    suspend fun fetchImage(
        imagesUrl: String,
        imageId: String
    ): ByteArray {
        return client.get("$imagesUrl/$imageId").body<ByteArray>()
    }

    suspend fun getConferenceData(): Conference = conference.first()
}

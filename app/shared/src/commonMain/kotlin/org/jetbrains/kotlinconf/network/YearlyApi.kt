package org.jetbrains.kotlinconf.network

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.encodedPath
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import org.jetbrains.kotlinconf.Conference
import org.jetbrains.kotlinconf.ConferenceInfo
import org.jetbrains.kotlinconf.GoldenKodeeData
import org.jetbrains.kotlinconf.FeedbackInfo
import org.jetbrains.kotlinconf.Score
import org.jetbrains.kotlinconf.SessionId
import org.jetbrains.kotlinconf.VoteInfo
import org.jetbrains.kotlinconf.Votes
import org.jetbrains.kotlinconf.di.Year
import org.jetbrains.kotlinconf.di.YearScope
import org.jetbrains.kotlinconf.event.ActiveEvent
import org.jetbrains.kotlinconf.event.toConferenceInfo
import org.jetbrains.kotlinconf.sessionize.toConference
import org.jetbrains.kotlinconf.storage.ApplicationStorage
import org.jetbrains.kotlinconf.utils.Logger
import org.jetbrains.kotlinconf.utils.tagged

@Inject
@SingleIn(YearScope::class)
class YearlyApi(
    @Year private val year: Int,
    private val client: HttpClient,
    private val sessionizeFeed: SessionizeFeed,
    applicationStorage: ApplicationStorage,
    logger: Logger,
) {
    private val taggedLogger = logger.tagged("YearlyApi")

    private val userId = applicationStorage.userId

    /** Without a backend the schedule comes straight from Sessionize and the other calls are unavailable. */
    private val event = ActiveEvent
    private val sessionizeUrl: String? = event.sessionizeUrl.takeUnless { event.hasBackend }

    suspend fun downloadConferenceData(): Conference? = safeApiCall {
        if (sessionizeUrl != null) {
            sessionizeFeed.get(sessionizeUrl).toConference(event.timeZone, event.sessionizeRules)
        } else {
            client.get { apiUrl("conference") }.body()
        }
    }

    suspend fun downloadConferenceInfo(): ConferenceInfo? = safeApiCall {
        if (sessionizeUrl != null) {
            event.toConferenceInfo(sessionizeFeed.get(sessionizeUrl))
        } else {
            client.get { apiUrl("conference-info") }.body()
        }
    }

    suspend fun downloadGoldenKodeeData(): GoldenKodeeData? = backendCall {
        client.get { apiUrl("golden-kodee") }.body()
    }

    suspend fun downloadAsset(path: String): String? = backendCall {
        client.get { apiUrl(path) }.bodyAsText()
    }

    suspend fun sign(userId: String): Boolean {
        return backendCall {
            client.post {
                apiUrl("sign")
                setBody(userId)
            }.status.isSuccess()
        } ?: false
    }

    suspend fun getPolicySigned(): Boolean? {
        return backendCall {
            client.get { apiUrl("sign") }.status.isSuccess()
        }
    }

    suspend fun vote(sessionId: SessionId, score: Score?): Boolean = backendCall {
        client.post {
            apiUrl("vote")
            json()
            setBody(VoteInfo(sessionId, score))
        }.status.isSuccess()
    } ?: false

    suspend fun sendFeedback(sessionId: SessionId, feedback: String): Boolean = backendCall {
        client.post {
            apiUrl("feedback")
            json()
            setBody(FeedbackInfo(sessionId, feedback))
        }.status.isSuccess()
    } ?: false

    suspend fun myVotes(): List<VoteInfo> = backendCall {
        client.get { apiUrl("vote") }.body<Votes>().votes
    } ?: emptyList()

    private suspend fun <T> backendCall(call: suspend () -> T): T? =
        if (event.hasBackend) safeApiCall(call) else null

    private suspend fun <T> safeApiCall(
        call: suspend () -> T,
    ): T? {
        return try {
            call()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            taggedLogger.log { "API call failed: ${e.message}" }
            null
        }
    }

    private fun HttpRequestBuilder.json() {
        contentType(ContentType.Application.Json)
    }

    private fun HttpRequestBuilder.apiUrl(path: String) {
        userId.value.takeIf { it.isNotBlank() }?.let {
            header(HttpHeaders.Authorization, "Bearer $it")
        }
        header(HttpHeaders.CacheControl, "no-cache")
        url {
            encodedPath = "$year/$path"
        }
    }
}

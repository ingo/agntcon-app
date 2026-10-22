package org.jetbrains.kotlinconf.sync

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.kotlinconf.SessionId

/*
 * Bookmark sync with an event's Sessionize web app (https://<event>.sessionize.com), using the same
 * calls as that app's "Sync favorites" feature:
 *
 * - every device has a random id; favorites are kept on the server per group of linked devices
 * - `favorites` sends the changes since the last call and returns the group's full list
 * - `sync-favorites-start` gives a 6-digit code for a device; another device links to it with
 *   `sync-favorites-use`; `sync-favorites-ping` tells the first device when its code was used
 *
 * The API is not documented by Sessionize and may change; the app keeps working from its own
 * bookmarks when sync fails.
 */

/** Sync state of this device, stored per conference. */
@Serializable
data class SessionizeSyncState(
    val deviceId: String,
    val linked: Boolean = false,
    /** Time of the last local change, in epoch milliseconds; sent as the web app does. */
    val changedAt: Long = 0,
    val toAdd: Set<String> = emptySet(),
    val toRemove: Set<String> = emptySet(),
    /** Epoch milliseconds of the last successful sync. */
    val lastSyncedAt: Long? = null,
)

/** Records a local bookmark change to send with the next sync, like the web app does. */
fun SessionizeSyncState.withChange(sessionId: SessionId, bookmarked: Boolean, now: Long): SessionizeSyncState {
    val id = sessionId.id
    return if (bookmarked) {
        copy(changedAt = now, toAdd = if (id in toRemove) toAdd else toAdd + id, toRemove = toRemove - id)
    } else {
        copy(changedAt = now, toRemove = if (id in toAdd) toRemove else toRemove + id, toAdd = toAdd - id)
    }
}

/**
 * The bookmarks to keep after a sync: the server's list, with local changes made while the request
 * was in flight (still pending in [pending]) applied on top.
 */
fun mergeAfterSync(serverItems: Set<String>, pending: SessionizeSyncState): Set<SessionId> =
    (serverItems + pending.toAdd - pending.toRemove).map(::SessionId).toSet()

enum class CodeStatus { Active, Used, Expired, Invalid }

interface SessionizeSyncApi {
    /** Sends changes and returns the full list of bookmarked session ids for the device's group. */
    suspend fun favorites(
        baseUrl: String,
        deviceId: String,
        changedAt: Long,
        first: Boolean,
        toAdd: Set<String>,
        toRemove: Set<String>,
    ): Set<String>

    /** Links this device to the device that showed [code]. */
    suspend fun useCode(baseUrl: String, deviceId: String, code: String): Boolean

    /** A code another device can enter to link to this one. */
    suspend fun startCode(baseUrl: String, deviceId: String): String

    suspend fun codeStatus(baseUrl: String, code: String): CodeStatus
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class SessionizeSyncClient(
    private val client: HttpClient,
) : SessionizeSyncApi {
    override suspend fun favorites(
        baseUrl: String,
        deviceId: String,
        changedAt: Long,
        first: Boolean,
        toAdd: Set<String>,
        toRemove: Set<String>,
    ): Set<String> {
        val fields = buildMap {
            put("id", deviceId)
            put("timestamp", changedAt.toString())
            put("first", first.toString())
            // The web app sends lists the way a browser form does: comma-separated
            if (toAdd.isNotEmpty()) put("toAdd", toAdd.joinToString(","))
            if (toRemove.isNotEmpty()) put("toRemove", toRemove.joinToString(","))
        }
        val response = Json.parseToJsonElement(post(baseUrl, "favorites", fields)).jsonObject
        val items = response["items"] as? JsonArray ?: error("No favorites in response")
        return items.map { it.jsonPrimitive.content }.toSet()
    }

    override suspend fun useCode(baseUrl: String, deviceId: String, code: String): Boolean {
        val response = Json.parseToJsonElement(post(baseUrl, "sync-favorites-use", mapOf("id" to deviceId, "code" to code)))
        return (response as? JsonObject)?.get("ok")?.jsonPrimitive?.booleanOrNull == true
    }

    override suspend fun startCode(baseUrl: String, deviceId: String): String {
        val response = Json.parseToJsonElement(post(baseUrl, "sync-favorites-start", mapOf("id" to deviceId)))
        return (response as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() } ?: error("No sync code in response")
    }

    override suspend fun codeStatus(baseUrl: String, code: String): CodeStatus {
        val status = Json.parseToJsonElement(post(baseUrl, "sync-favorites-ping", mapOf("code" to code))).jsonPrimitive.content
        return when (status) {
            "active" -> CodeStatus.Active
            "used" -> CodeStatus.Used
            "expired" -> CodeStatus.Expired
            else -> CodeStatus.Invalid
        }
    }

    private suspend fun post(baseUrl: String, path: String, fields: Map<String, String>): String =
        client.submitFormWithBinaryData(
            url = "${baseUrl.trimEnd('/')}/api/$path",
            formData = formData { fields.forEach { (key, value) -> append(key, value) } },
        ).bodyAsText()
}

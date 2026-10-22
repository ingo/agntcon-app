package org.jetbrains.kotlinconf.sync

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The requests the Sessionize web app makes, and its responses, as seen in its own code. */
class SessionizeSyncClientTest {
    private val baseUrl = "https://event.sessionize.com"

    private class Recorded(val path: String, val method: HttpMethod, val body: String)

    private fun client(responseJson: String, recorded: MutableList<Recorded>) = SessionizeSyncClient(
        HttpClient(MockEngine { request ->
            recorded += Recorded(request.url.encodedPath, request.method, request.body.toByteArray().decodeToString())
            respond(responseJson, headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        })
    )

    /** Value of a multipart form field in a recorded body. */
    private fun Recorded.field(name: String): String? =
        Regex("name=\"?$name\"?\\r\\n(?:[^\\r\\n]+\\r\\n)*\\r\\n([^\\r\\n]*)").find(body)?.groupValues?.get(1)

    @Test
    fun favoritesSendsChangesAsFormFieldsAndReadsTheList() = runTest {
        val recorded = mutableListOf<Recorded>()
        // Ids come back as numbers or strings
        val api = client("""{"error":null,"items":[1288844,"1288813"]}""", recorded)

        val items = api.favorites(baseUrl, "device-1", 1234, first = true, toAdd = setOf("1", "2"), toRemove = setOf("3"))

        assertEquals(setOf("1288844", "1288813"), items)
        val request = recorded.single()
        assertEquals("/api/favorites", request.path)
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("device-1", request.field("id"))
        assertEquals("1234", request.field("timestamp"))
        assertEquals("true", request.field("first"))
        assertEquals(setOf("1", "2"), request.field("toAdd")?.split(",")?.toSet())
        assertEquals("3", request.field("toRemove"))
    }

    @Test
    fun noChangesSendsNoChangeFields() = runTest {
        val recorded = mutableListOf<Recorded>()
        client("""{"error":null,"items":[]}""", recorded).favorites(baseUrl, "d", 0, false, emptySet(), emptySet())
        assertEquals(null, recorded.single().field("toAdd"))
        assertEquals(null, recorded.single().field("toRemove"))
    }

    @Test
    fun codes() = runTest {
        val recorded = mutableListOf<Recorded>()
        assertTrue(client("""{"ok":true}""", recorded).useCode(baseUrl, "d", "123456"))
        assertFalse(client("""{"ok":false}""", recorded).useCode(baseUrl, "d", "000000"))
        assertEquals("/api/sync-favorites-use", recorded.first().path)
        assertEquals("123456", recorded.first().field("code"))

        assertEquals("654321", client("654321", recorded).startCode(baseUrl, "d"))
        assertEquals(CodeStatus.Used, client("\"used\"", recorded).codeStatus(baseUrl, "654321"))
        assertEquals(CodeStatus.Active, client("\"active\"", recorded).codeStatus(baseUrl, "654321"))
        assertEquals(CodeStatus.Invalid, client("\"invalid\"", recorded).codeStatus(baseUrl, "1"))
    }
}

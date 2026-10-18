package com.dronebox.app

import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What the client puts on the wire when it has no key.
 *
 * The firmware's auth check is compiled out at the moment, so the dock answers
 * unsigned requests -- these pin the client to that, and to still signing when
 * a key is supplied, so turning the firmware's check back on needs no change
 * here.
 */
class DockClientAuthTest {

    private val statusJson = """{"dir":"s","speed":100,"level":2,"levels":5}"""

    private fun engineRecording(seen: MutableList<Map<String, String?>>) = MockEngine { request ->
        seen += mapOf(
            "path" to request.url.encodedPath,
            "nonce" to request.headers["X-Dock-Nonce"],
            "sig" to request.headers["X-Dock-Sig"],
        )
        respond(
            content = ByteReadChannel(statusJson),
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
        )
    }

    @Test
    fun withoutAKeyItSendsNoSignatureAndNeverFetchesANonce() = runTest {
        val seen = mutableListOf<Map<String, String?>>()
        val dock = DockClient(key = null, host = "dock.local", engine = engineRecording(seen))

        val status = dock.status()

        assertEquals(2, status.level)
        assertEquals(1, seen.size, "a keyless client must not call /api/nonce first")
        assertEquals("/api/status", seen[0]["path"])
        assertNull(seen[0]["nonce"], "no key means no nonce header")
        assertNull(seen[0]["sig"], "no key means no signature header")
    }

    @Test
    fun blankHexKeyMeansUnsigned() = runTest {
        val seen = mutableListOf<Map<String, String?>>()
        val dock = DockClient.fromHexKey("", "dock.local", engineRecording(seen))
        dock.status()
        assertNull(seen[0]["sig"])
    }

    @Test
    fun withAKeyItStillSigns() = runTest {
        val seen = mutableListOf<Map<String, String?>>()
        val key = ByteArray(32) { it.toByte() }
        val dock = DockClient(key = key, host = "dock.local", engine = MockEngine { request ->
            seen += mapOf(
                "path" to request.url.encodedPath,
                "nonce" to request.headers["X-Dock-Nonce"],
                "sig" to request.headers["X-Dock-Sig"],
            )
            val body = if (request.url.encodedPath == "/api/nonce") """{"nonce":7}""" else statusJson
            respond(
                content = ByteReadChannel(body),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        })

        dock.status()

        assertEquals("/api/nonce", seen[0]["path"], "a keyed client aligns its nonce first")
        assertEquals("/api/status", seen[1]["path"])
        assertEquals("8", seen[1]["nonce"], "must continue from the dock's high-water mark")
        assertTrue(seen[1]["sig"]!!.isNotEmpty())
    }

    @Test
    fun aNonAuthRefusalThrowsInsteadOfDecodingToABlankStatus() = runTest {
        // /api/scan answers 409 while the actuator is moving. Json ignores
        // unknown keys, so without the status check this would quietly become
        // a DockStatus of defaults and render as "stopped, not homed".
        val dock = DockClient(key = null, host = "dock.local", engine = MockEngine {
            respond(
                content = ByteReadChannel("""{"error":"moving","hint":"stop the actuator first"}"""),
                status = HttpStatusCode.Conflict,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        })

        val e = assertFailsWith<DockRefused> { dock.status() }
        assertEquals(409, e.status)
        assertTrue(e.body.contains("stop the actuator first"))
    }
}

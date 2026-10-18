package com.dronebox.app

import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * The endpoints beyond status and cmd, pinned to what webserver.c parses.
 */
class DockClientEndpointsTest {

    private fun json(body: String, status: HttpStatusCode = HttpStatusCode.OK) = MockEngine { _ ->
        respond(
            content = ByteReadChannel(body),
            status = status,
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
        )
    }

    @Test
    fun netDecodesTheDocksOwnFieldNames() = runTest {
        val dock = DockClient(null, "dock.local", json(
            """{"staJoined":true,"staIp":"172.20.10.4","apSsid":"drone-in-a-box","pairing":false}""",
        ))
        val net = dock.net()
        assertEquals(true, net.staJoined)
        assertEquals("172.20.10.4", net.staIp)
        assertEquals("drone-in-a-box", net.apSsid)
    }

    @Test
    fun scanReturnsTheNetworksList() = runTest {
        val dock = DockClient(null, "dock.local", json(
            """{"networks":[{"ssid":"hotspot","rssi":-50,"open":false,"auth":"WPA2"},""" +
                """{"ssid":"cafe","rssi":-80,"open":true,"auth":"OPEN"}]}""",
        ))
        val list = dock.scan()
        assertEquals(listOf("hotspot", "cafe"), list.map { it.ssid })
        assertEquals(4, list[0].bars)
        assertEquals(1, list[1].bars)
    }

    @Test
    fun scanRefusalCarriesTheDocksHint() = runTest {
        val dock = DockClient(null, "dock.local", json(
            """{"error":"moving","hint":"stop the actuator before scanning"}""",
            HttpStatusCode.Conflict,
        ))
        val e = assertFailsWith<DockRefused> { dock.scan() }
        assertEquals(409, e.status)
        assertEquals("stop the actuator before scanning", e.hint)
    }

    @Test
    fun joinWifiPutsPercentEncodedCredentialsInTheQuery() = runTest {
        // The firmware url-decodes both values; '&' and ' ' are the cases that
        // would split or truncate the password if they went out raw.
        var seen: String? = null
        var method: HttpMethod? = null
        val dock = DockClient(null, "dock.local", MockEngine { request ->
            seen = request.url.encodedQuery
            method = request.method
            respond(ByteReadChannel("""{"ok":true}"""), HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"))
        })
        dock.joinWifi("My Net", "a&b c")
        assertEquals(HttpMethod.Post, method)
        assertEquals("ssid=My%20Net&password=a%26b%20c", seen)
    }

    @Test
    fun pairReturnsTheKeyOrTheDocksReasonForRefusing() = runTest {
        val key = "ab".repeat(32)
        assertEquals(key, DockClient(null, "dock.local", json("""{"key":"$key"}""")).pair())

        val closed = DockClient(null, "dock.local", json(
            """{"error":"closed","hint":"press the BOOT button on the dock"}""",
            HttpStatusCode.Forbidden,
        ))
        val e = assertFailsWith<DockRefused> { closed.pair() }
        assertEquals("press the BOOT button on the dock", e.hint)
    }

    @Test
    fun throughAProxyItCallsTheProxyAndNamesTheDock() = runTest {
        // The browser build: same path and query, so the signature still
        // covers what the dock receives; only the host changes.
        var url: String? = null
        var target: String? = null
        val dock = DockClient(null, "dock.local", MockEngine { request ->
            url = request.url.toString()
            target = request.headers[DockClient.TARGET_HEADER]
            respond(ByteReadChannel("""{"dir":"s"}"""), HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"))
        }, proxy = "http://localhost:8080")

        dock.send('+')

        assertEquals("http://localhost:8080/api/cmd?c=%2B", url)
        assertEquals("http://dock.local", target)
    }

    @Test
    fun withoutAProxyNoTargetHeaderIsSent() = runTest {
        var target: String? = "unset"
        val dock = DockClient(null, "dock.local", MockEngine { request ->
            target = request.headers[DockClient.TARGET_HEADER]
            respond(ByteReadChannel("""{"dir":"s"}"""), HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"))
        })
        dock.status()
        assertEquals(null, target)
    }

    @Test
    fun baseUrlKeepsAnExplicitSchemeAndAddsHttpOtherwise() {
        assertEquals("http://192.168.4.1", DockClient.baseUrl("192.168.4.1"))
        assertEquals("http://10.0.2.2:8000", DockClient.baseUrl(" 10.0.2.2:8000/ "))
        assertEquals("https://localhost:8080", DockClient.baseUrl("https://localhost:8080"))
    }
}

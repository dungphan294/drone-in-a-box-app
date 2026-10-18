package com.dronebox.app

import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DockSessionTest {

    private val status = """{"dir":"s","speed":70,"level":2,"levels":5,"event":"ready"}"""
    private val net = """{"staJoined":false,"staIp":"","apSsid":"drone-in-a-box","pairing":false}"""

    private class FakeDock(var up: Boolean = true) {
        val paths = mutableListOf<String>()
        var scanReply: Pair<HttpStatusCode, String> = HttpStatusCode.OK to """{"networks":[]}"""
    }

    private fun TestScope.session(dock: FakeDock): DockSession {
        // On the test scheduler, so virtual time drives the HTTP calls as well
        // as the poll's delay().
        val config = MockEngineConfig()
        config.dispatcher = StandardTestDispatcher(testScheduler)
        config.addHandler { request ->
            val path = request.url.encodedPath
            dock.paths += path
            if (!dock.up) error("no route to host")
            val (code, body) = when (path) {
                "/api/net" -> HttpStatusCode.OK to net
                "/api/scan" -> dock.scanReply
                else -> HttpStatusCode.OK to status
            }
            respond(ByteReadChannel(body), code, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        return DockSession(DockClient(null, "dock.local", MockEngine(config)), "dock.local", signed = false, parent = backgroundScope)
    }

    @Test
    fun firstAnswerMakesTheLinkLiveAndShowsTheDocksEvent() = runTest {
        val s = session(FakeDock())
        assertEquals(LinkState.Connecting, s.state.value.link)

        s.start()
        runCurrent()

        assertEquals(LinkState.Live, s.state.value.link)
        assertEquals(2, s.state.value.status?.level)
        assertEquals("drone-in-a-box", s.state.value.net?.apSsid)
        assertEquals("ready", s.state.value.message)
    }

    @Test
    fun onlyARunOfMissedPollsCountsAsLost() = runTest {
        val dock = FakeDock()
        val s = session(dock)
        s.start()
        runCurrent()

        dock.up = false
        advanceTimeBy(DockSession.POLL_MS * 2 + 1)      // two misses
        assertEquals(LinkState.Live, s.state.value.link, "a single glitch must not read as a fault")

        advanceTimeBy(DockSession.POLL_MS)              // the third
        assertEquals(LinkState.Lost, s.state.value.link)

        dock.up = true
        advanceTimeBy(DockSession.POLL_MS)
        assertEquals(LinkState.Live, s.state.value.link)
        // The dock's event is unchanged, so it is not logged twice.
        assertEquals("Reconnected.", s.state.value.message)
    }

    @Test
    fun pauseStopsTheHeartbeat() = runTest {
        val dock = FakeDock()
        val s = session(dock)
        s.start()
        runCurrent()
        s.pause()
        val before = dock.paths.size

        advanceTimeBy(DockSession.POLL_MS * 10)

        assertEquals(before, dock.paths.size, "a paused session must not keep holding the dead-man")
        assertEquals(LinkState.Paused, s.state.value.link)
    }

    @Test
    fun aRefusedScanSaysWhyAndUnfreezesTheControls() = runTest {
        val dock = FakeDock()
        dock.scanReply = HttpStatusCode.Conflict to """{"error":"moving","hint":"stop the actuator first"}"""
        val s = session(dock)

        s.scan()
        assertTrue(s.state.value.controlsFrozen)
        runCurrent()

        assertEquals("Scan refused: stop the actuator first", s.state.value.message)
        assertEquals(false, s.state.value.controlsFrozen)
        assertNull(s.state.value.networks)
    }

    @Test
    fun aRepeatedTapWhileTheFirstIsInFlightIsDroppedButStopNeverIs() = runTest {
        val dock = FakeDock()
        val s = session(dock)

        s.send('t'); s.send('t'); s.send('t')
        s.send('s'); s.send('s')
        runCurrent()

        val sent = dock.paths.filter { it == "/api/cmd" }.size
        assertEquals(3, sent, "one 't' and both STOPs")

        s.send('t')                                     // the first has landed
        runCurrent()
        assertEquals(4, dock.paths.filter { it == "/api/cmd" }.size)
    }

    @Test
    fun keysAreEitherBlankOr64Hex() {
        assertNull(keyProblem(""))
        assertNull(keyProblem("  "))
        assertNull(keyProblem("0f".repeat(32)))
        assertTrue(keyProblem("abc") != null)
        assertTrue(keyProblem("zz".repeat(32)) != null)
    }
}

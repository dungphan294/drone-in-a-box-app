package com.dronebox.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LinkState {
    /** Polling has started and nothing has answered yet. */
    Connecting,
    Live,
    /** Several polls in a row went unanswered. */
    Lost,
    /** The app is in the background, so it is not polling -- and so not holding the dead-man. */
    Paused,
}

data class DockState(
    val host: String,
    val signed: Boolean,
    val link: LinkState = LinkState.Connecting,
    val status: DockStatus? = null,
    val net: DockNet? = null,
    /** Newest first: the dock's own events and the app's notices, interleaved. */
    val log: List<String> = emptyList(),
    val scanning: Boolean = false,
    /** Null until a scan has come back. */
    val networks: List<DockNetwork>? = null,
    val joining: Boolean = false,
) {
    val message: String get() = log.firstOrNull().orEmpty()

    /** Everything but STOP is held off while a scan has the radio off channel. */
    val controlsFrozen: Boolean get() = scanning
}

/**
 * One connection to one dock: what the UI shows, and the poll that keeps the
 * dock's dead-man switch held.
 *
 * The poll **is** the heartbeat. If it stops while the motor runs, the dock
 * stops the actuator by itself after ~1.5 s (SESSION_TIMEOUT_MS). So [pause]
 * is not cosmetic: it is how backgrounding the app stops the motor, which is
 * the safe outcome, and nothing here tries to keep polling behind the user's
 * back.
 */
class DockSession(
    private val client: DockClient,
    host: String,
    signed: Boolean,
    parent: CoroutineScope,
    private val periodMs: Long = POLL_MS,
) {
    // Its own child scope, so closing a session cancels its commands and
    // scans along with the poll, and a failure in one cannot cancel another.
    private val scope = CoroutineScope(parent.coroutineContext + SupervisorJob(parent.coroutineContext[Job]))

    private val _state = MutableStateFlow(DockState(host = host, signed = signed))
    val state: StateFlow<DockState> = _state.asStateFlow()

    private var poller: Job? = null
    private val inFlight = mutableSetOf<Char>()
    private var fails = 0
    private var lastEvent: String? = null

    fun start() {
        if (poller?.isActive == true) return
        if (_state.value.link == LinkState.Paused) {
            _state.update { it.copy(link = LinkState.Connecting) }
        }
        poller = scope.launch { pollLoop() }
    }

    fun pause() {
        poller?.cancel()
        poller = null
        _state.update { it.copy(link = LinkState.Paused) }
        note("Polling paused. The dock stops the actuator ~1.5 s after the last poll, by design.")
    }

    fun close() {
        scope.cancel()
        poller = null
        client.close()
    }

    private suspend fun pollLoop() {
        var tick = 0
        while (true) {
            // A scan has the radio off channel; polling through it would only
            // pile up failures that read as a fault.
            if (_state.value.scanning) {
                delay(periodMs)
                continue
            }
            try {
                val status = client.status()
                // Network state changes rarely; there is no need to double
                // the request rate for it.
                val net = if (tick % NET_EVERY == 0) client.net() else _state.value.net
                if (fails >= LOST_AFTER) note("Reconnected.")
                fails = 0
                _state.update { it.copy(link = LinkState.Live, status = status, net = net) }
                noteEvent(status.event)
            } catch (e: CancellationException) {
                throw e
            } catch (e: DockUnauthorized) {
                _state.update { it.copy(link = LinkState.Lost) }
                note(
                    if (_state.value.signed) "Refused: wrong key, or the dock was erased. Pair again."
                    else "The dock requires a signed request. Disconnect and pair to get its key."
                )
            } catch (e: Throwable) {
                // One missed poll proves nothing -- a hotspot glitches, a
                // scan takes the radio off channel. Only a run is reported.
                if (++fails == LOST_AFTER) {
                    _state.update { it.copy(link = LinkState.Lost) }
                    note("Lost contact with the dock, retrying. (${e.describe()})")
                }
            }
            tick++
            delay(periodMs)
        }
    }

    fun send(command: Char) {
        // A second tap of the same button while the first is still on its way
        // is a stutter, not an instruction: the dock's log showed "t t t t"
        // inside 200 ms, each one restarting the 13 s homing move. STOP is
        // never held back.
        if (command != 's' && command in inFlight) return
        inFlight += command
        scope.launch {
            try {
                val status = client.send(command)
                _state.update { it.copy(status = status) }
                // A command's reply is news even when it repeats the last one.
                lastEvent = null
                noteEvent(status.event)
            } catch (e: CancellationException) {
                throw e
            } catch (e: DockUnauthorized) {
                note("Refused: ${e.message}")
            } catch (e: DockRefused) {
                note("Dock refused: ${e.hint ?: e.body}")
            } catch (e: Throwable) {
                note("Command not delivered: ${e.describe()}")
            } finally {
                inFlight -= command
            }
        }
    }

    /**
     * Ask the dock for nearby networks.
     *
     * The poll goes quiet for the duration instead of piling up failures that
     * would read as a fault. That is safe because the dock refuses to scan at
     * all while the actuator is energised (409), and that refusal comes back
     * well inside the 1.5 s dead-man -- so pausing the poll can never leave a
     * moving motor unwatched.
     */
    fun scan() {
        if (_state.value.scanning) return
        _state.update { it.copy(scanning = true) }
        note("Scanning. The dock's radio sweeps the channels, so it goes quiet for a few seconds.")
        scope.launch {
            try {
                val list = client.scan().sortedByDescending { it.rssi }
                _state.update { it.copy(networks = list) }
                note(
                    if (list.isEmpty()) "No networks found. A hidden network never shows up: type its name."
                    else "${list.size} network${if (list.size == 1) "" else "s"} found."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: DockRefused) {
                // 409 is the dock declining on purpose; a 5xx is it failing.
                note((if (e.status == 409) "Scan refused: " else "Scan failed: ") + (e.hint ?: e.body))
            } catch (e: Throwable) {
                note("Lost contact during the scan, which is expected while the radio changes channel. Try again in a moment.")
            } finally {
                fails = 0
                _state.update { it.copy(scanning = false) }
            }
        }
    }

    fun join(ssid: String, password: String) {
        val name = ssid.trim()
        if (name.isEmpty()) {
            note("Enter a network name.")
            return
        }
        _state.update { it.copy(joining = true) }
        scope.launch {
            try {
                client.joinWifi(name, password)
                note("Joining \"$name\". The dock keeps its own access point up either way.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: DockRefused) {
                note("Could not save: ${e.hint ?: e.body}")
            } catch (e: Throwable) {
                note("Could not save: ${e.describe()}")
            } finally {
                _state.update { it.copy(joining = false) }
            }
        }
    }

    /** Show the dock's event only when it changes, so a 400 ms poll cannot bury the app's own notices. */
    private fun noteEvent(event: String) {
        if (event.isEmpty() || event == lastEvent) return
        lastEvent = event
        note(event)
    }

    private fun note(text: String) {
        _state.update { s ->
            if (s.log.firstOrNull() == text) s else s.copy(log = (listOf(text) + s.log).take(LOG_SIZE))
        }
    }

    companion object {
        const val POLL_MS = 400L
        const val NET_EVERY = 5
        const val LOST_AFTER = 3
        const val LOG_SIZE = 8
    }
}

internal fun Throwable.describe(): String = message ?: this::class.simpleName ?: "unknown error"

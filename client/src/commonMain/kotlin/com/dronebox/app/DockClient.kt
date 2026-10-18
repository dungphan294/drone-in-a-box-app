package com.dronebox.app

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@Serializable
data class DockStatus(
    val dir: String = "s",
    val speed: Int = 0,
    val dutyA: Long = 0,
    val dutyB: Long = 0,
    val level: Int = -1,
    val levels: Int = 5,
    val moving: Boolean = false,
    val slowDecay: Boolean = true,
    val levelText: String = "",
    val event: String = "",
) {
    /** The dock reports -1 when it has not been homed. Never show that as a number. */
    val levelKnown: Boolean get() = level >= 0
}

/** Where the dock is on the network, from GET /api/net. */
@Serializable
data class DockNet(
    val staJoined: Boolean = false,
    val staIp: String = "",
    val apSsid: String = "",
    val pairing: Boolean = false,
)

/** One access point from GET /api/scan. `ssid` is untrusted text from the air. */
@Serializable
data class DockNetwork(
    val ssid: String,
    val rssi: Int = 0,
    val open: Boolean = false,
    val auth: String = "",
) {
    /** Four coarse buckets at the usual phone-style thresholds, as the dock's own page uses. */
    val bars: Int get() = when {
        rssi >= -55 -> 4
        rssi >= -67 -> 3
        rssi >= -78 -> 2
        else -> 1
    }
}

class DockUnauthorized(message: String) : Exception(message)

/**
 * A non-401 refusal, carrying what the dock said. /api/scan answers 409 while
 * the actuator is moving, for instance, because a scan outlasts the dead-man.
 */
class DockRefused(val status: Int, val body: String) : Exception("dock returned $status: $body") {
    /**
     * The dock explains a refusal as {"error","hint"}, and the hint is written
     * for the operator. Null when the body is not that shape -- a 400 from
     * httpd_resp_send_err() is plain text, for instance.
     */
    val hint: String? get() = field("hint") ?: field("error")

    private fun field(name: String): String? = runCatching {
        Json.parseToJsonElement(body).jsonObject[name]?.jsonPrimitive?.content
    }.getOrNull()
}

/**
 * HTTP client for the dock.
 *
 * Reachable at 192.168.4.1 on the dock's own access point, or at dock.local
 * when both are on the same network -- joining the operator's hotspot is what
 * lets the phone keep its cellular data.
 *
 * `key` may be null, and then nothing is signed: no nonce is fetched and
 * neither header is sent. That mirrors the firmware, which has its whole auth
 * check compiled out at the moment (DOCK_REQUIRE_AUTH 0 in webserver.c) and
 * ignores both headers -- so demanding a key here only stopped the app from
 * talking to a dock that would have answered anyone.
 *
 * This is deliberately a property of the client rather than a build flag: the
 * same binary works against a dock with auth on and one with it off, and
 * turning the firmware's check back on needs no change here. With auth on and
 * no key the dock answers 401 and that surfaces as DockUnauthorized, which is
 * the truthful outcome.
 */
@OptIn(ExperimentalEncodingApi::class)
class DockClient(
    private val key: ByteArray? = null,
    host: String = "dock.local",
    engine: HttpClientEngine? = null,
    /**
     * Send every request here instead, naming the dock in [TARGET_HEADER].
     *
     * For the browser: the dock sends no CORS headers, so a page may only
     * read answers from its own origin. The dev server forwards /api to the
     * dock the header names -- see webApp/webpack.config.d/dock-proxy.js. Path
     * and query are unchanged, so a signature still covers exactly what the
     * dock receives.
     */
    proxy: String? = null,
) {
    private val dock = baseUrl(host)
    private val base = proxy?.let(::baseUrl) ?: dock
    private val viaProxy = proxy != null
    private var nonce: Long = 0

    companion object {
        /** Names the real dock when requests go through a proxy. */
        const val TARGET_HEADER = "X-Dock-Target"

        /**
         * How long one status poll may take. Under the dock's 1.5 s dead-man on
         * purpose: with the old 2 s timeout a single slow reply left a gap of
         * 2.4 s between polls, and the dock stopped a perfectly good move with
         * "web client stopped responding". Failing fast lets the next poll go.
         */
        const val POLL_TIMEOUT_MS = 1_000L

        /** How long a scan may take: a sweep plus a re-association. */
        const val SCAN_TIMEOUT_MS = 15_000L

        /**
         * `host` is usually a bare address -- 192.168.4.1, dock.local,
         * 10.0.2.2:8000 -- but the browser build passes its page origin, which
         * already carries a scheme, so keep one when it is there.
         */
        fun baseUrl(host: String): String {
            val h = host.trim().trimEnd('/')
            return if ("://" in h) h else "http://$h"
        }

        /**
         * Build a client from the 64-character hex key the dock prints with 'K',
         * or an unsigned one when `hexKey` is blank.
         *
         * Exists for Swift: Kotlin's ByteArray surfaces as KotlinByteArray,
         * which is awkward to assemble by hand on the call site. Taking blank
         * to mean "no key" keeps both platforms working against the current
         * open firmware without either having to special-case it.
         */
        fun fromHexKey(hexKey: String, host: String): DockClient =
            fromHexKey(hexKey, host, null)

        fun fromHexKey(hexKey: String, host: String, engine: HttpClientEngine?): DockClient =
            fromHexKey(hexKey, host, engine, null)

        /** As fromHexKey, through `proxy` when it is not null -- see the constructor. */
        fun fromHexKeyVia(hexKey: String, host: String, proxy: String?): DockClient =
            fromHexKey(hexKey, host, null, proxy)

        fun fromHexKey(hexKey: String, host: String, engine: HttpClientEngine?, proxy: String?): DockClient {
            val clean = hexKey.trim()
            if (clean.isEmpty()) return DockClient(null, host, engine, proxy)
            require(clean.length == 64) { "device key must be 64 hex characters" }
            return DockClient(
                ByteArray(32) { clean.substring(it * 2, it * 2 + 2).toInt(16).toByte() },
                host,
                engine,
                proxy,
            )
        }
    }

    private val http = run {
        val configure: HttpClientConfig<*>.() -> Unit = {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            install(HttpTimeout) { requestTimeoutMillis = 2_000 }
            if (viaProxy) defaultRequest { header(TARGET_HEADER, dock) }
        }
        // `engine` is for tests, which need to read the headers that actually
        // go out. Production callers leave it null and get the platform engine.
        if (engine != null) HttpClient(engine, configure) else HttpClient(configure)
    }

    /**
     * Align with the dock's replay high-water mark.
     *
     * Needed after a reinstall or when switching docks: the dock refuses any
     * nonce it has already seen, and the app's own counter means nothing to a
     * dock it has never spoken to. The value is not a secret -- knowing it
     * does not help anyone who cannot sign.
     */
    suspend fun syncNonce() {
        nonce = http.get("$base/api/nonce").body<NonceResponse>().nonce
    }

    @Serializable private data class NonceResponse(val nonce: Long)

    suspend fun status(): DockStatus = request(HttpMethod.Get, "/api/status", "", POLL_TIMEOUT_MS)

    suspend fun net(): DockNet = request(HttpMethod.Get, "/api/net", "", POLL_TIMEOUT_MS)

    /**
     * Ask the dock to sweep for networks. Takes several seconds, during which
     * the dock's radio leaves its own channel -- so expect to lose polls, and
     * do not call it while the actuator runs: the dock answers 409 then,
     * because a scan outlasts the dead-man timeout.
     */
    suspend fun scan(): List<DockNetwork> =
        request<ScanResponse>(HttpMethod.Get, "/api/scan", "", SCAN_TIMEOUT_MS).networks

    @Serializable private data class ScanResponse(val networks: List<DockNetwork> = emptyList())

    /**
     * Have the dock join a network. Credentials ride in the signed query, not
     * a body, because the signature covers the query and nothing else.
     */
    suspend fun joinWifi(ssid: String, password: String) {
        val query = "ssid=" + ssid.encodeURLParameter() + "&password=" + password.encodeURLParameter()
        request<OkResponse>(HttpMethod.Post, "/api/wifi", query)
    }

    @Serializable private data class OkResponse(val ok: Boolean = false)

    /**
     * Collect the device key during a pairing window, as 64 hex characters.
     *
     * Unsigned by design -- the caller has no key yet. The dock only answers
     * after its BOOT button was pressed, and only to a client on its own
     * access point; otherwise this throws DockRefused with the dock's hint.
     */
    suspend fun pair(): String {
        val response = http.get("$base/api/pair")
        if (!response.status.isSuccess()) {
            throw DockRefused(response.status.value, response.bodyAsText())
        }
        return response.body<PairResponse>().key
    }

    @Serializable private data class PairResponse(val key: String)

    fun close() = http.close()

    suspend fun send(command: Char): DockStatus {
        // Sign the query exactly as it goes on the wire: the firmware reads it
        // percent-encoded. '+' is the case that matters -- it encodes to %2B.
        val query = "c=" + command.toString().encodeURLParameter()
        return request(HttpMethod.Post, "/api/cmd", query)
    }

    /**
     * Poll for status. This **is** the heartbeat: if it stops while the motor
     * runs, the dock stops the actuator by itself after about 1.5 s. Do not
     * lengthen the interval casually, and do not stop collecting while a move
     * is in progress.
     */
    fun stream(periodMs: Long = 400): Flow<DockStatus> = flow {
        while (true) {
            emit(status())
            delay(periodMs)
        }
    }

    private suspend inline fun <reified T> request(
        method: HttpMethod, path: String, query: String, timeoutMs: Long? = null,
    ): T {
        // Without a key there is nothing to sign, and the nonce is only ever
        // used to build a signature -- so skip that round trip too.
        val signing = key
        var n = 0L
        var sig: String? = null
        if (signing != null) {
            if (nonce == 0L) syncNonce()
            n = ++nonce
            sig = Base64.encode(
                DockCrypto.hmacSha256(signing, DockCrypto.canonical(method.value, path, query, n))
            )
        }

        val response = http.request("$base$path" + if (query.isEmpty()) "" else "?$query") {
            this.method = method
            if (timeoutMs != null) timeout { requestTimeoutMillis = timeoutMs }
            if (sig != null) {
                header("X-Dock-Nonce", n.toString())
                header("X-Dock-Sig", sig)
            }
        }
        if (response.status == HttpStatusCode.Unauthorized) {
            throw DockUnauthorized("dock refused the signature or the nonce")
        }
        // Any other failure has to throw as well. The dock explains itself in
        // a JSON body -- {"error","hint"} -- and Json is configured to ignore
        // unknown keys, so letting one through here would quietly deserialize
        // a refusal into a DockStatus full of defaults and render it as if the
        // actuator were simply stopped at an unknown level.
        if (!response.status.isSuccess()) {
            throw DockRefused(response.status.value, response.bodyAsText())
        }
        return response.body()
    }
}

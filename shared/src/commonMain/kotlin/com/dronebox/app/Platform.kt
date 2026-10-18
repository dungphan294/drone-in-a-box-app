package com.dronebox.app

/**
 * The origin this app was served from, when it is a web page -- for instance
 * http://localhost:8080 under the dev server, which proxies /api to the dock.
 *
 * A browser may only read responses from its own origin, and the dock sends
 * no CORS headers, so on the web this is the address that works. Null on every
 * other platform, where any address will do.
 */
expect fun pageOrigin(): String?

/** True on the web build. */
val isBrowser: Boolean get() = pageOrigin() != null

/**
 * A client for the dock at `host`. In a browser, unless `host` is the page's
 * own origin, requests go through the page's server, which forwards them --
 * the browser may not read the dock's answers directly.
 */
fun dockClient(hexKey: String, host: String): DockClient {
    val origin = pageOrigin()
    val proxy = if (origin != null && DockClient.baseUrl(host) != DockClient.baseUrl(origin)) origin else null
    return DockClient.fromHexKeyVia(hexKey, host, proxy)
}

/**
 * Small, non-secret preferences that should survive a restart -- the theme.
 * Never the device key: that belongs in the platform keystore.
 */
expect fun readPref(key: String): String?

expect fun writePref(key: String, value: String)

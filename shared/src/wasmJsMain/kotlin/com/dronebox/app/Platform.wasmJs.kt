@file:OptIn(ExperimentalWasmJsInterop::class)

package com.dronebox.app

private fun locationOrigin(): String = js("window.location.origin")

// localStorage can be missing or throw (private windows, blocked storage);
// a preference that is not remembered is fine, a crash is not.
private fun storageGet(key: String): String? =
    js("(() => { try { return window.localStorage.getItem(key); } catch (e) { return null; } })()")

private fun storageSet(key: String, value: String): Unit =
    js("(() => { try { window.localStorage.setItem(key, value); } catch (e) {} })()")

actual fun pageOrigin(): String? = locationOrigin()

actual fun readPref(key: String): String? = storageGet(key)

actual fun writePref(key: String, value: String) = storageSet(key, value)

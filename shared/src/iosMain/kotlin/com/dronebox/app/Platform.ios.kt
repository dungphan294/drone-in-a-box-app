package com.dronebox.app

import platform.Foundation.NSUserDefaults

actual fun pageOrigin(): String? = null

actual fun readPref(key: String): String? = NSUserDefaults.standardUserDefaults.stringForKey(key)

actual fun writePref(key: String, value: String) = NSUserDefaults.standardUserDefaults.setObject(value, forKey = key)

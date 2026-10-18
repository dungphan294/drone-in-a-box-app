package com.dronebox.app

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
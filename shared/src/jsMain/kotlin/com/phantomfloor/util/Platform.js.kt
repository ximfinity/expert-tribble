package com.phantomfloor.util

actual fun nanoTime(): Long = (js("performance.now()") as Double * 1_000_000.0).toLong()

actual fun <T> locked(lock: Any, block: () -> T): T = block()

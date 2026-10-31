package com.phantomfloor.util

actual fun nanoTime(): Long = System.nanoTime()

actual fun <T> locked(lock: Any, block: () -> T): T = synchronized(lock) { block() }

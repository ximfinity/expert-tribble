package com.phantomfloor.util

/** Monotonic clock in nanoseconds (JVM: System.nanoTime; browser: performance.now). */
expect fun nanoTime(): Long

/** Runs [block] holding [lock] on platforms with threads; just runs it in the browser. */
expect fun <T> locked(lock: Any, block: () -> T): T

/** [v] with exactly [decimals] digits after the point, like "%.2f" (no locale). */
fun fmt(v: Float, decimals: Int): String {
    var scale = 1L
    repeat(decimals) { scale *= 10 }
    val neg = v < 0f
    val n = kotlin.math.round(kotlin.math.abs(v.toDouble()) * scale).toLong()
    val whole = n / scale
    val frac = n % scale
    val sign = if (neg && n != 0L) "-" else ""
    return if (decimals == 0) "$sign$whole" else "$sign$whole." + frac.toString().padStart(decimals, '0')
}

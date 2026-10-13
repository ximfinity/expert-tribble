package com.phantomfloor.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** In-place iterative radix-2 Cooley-Tukey FFT with precomputed twiddles and bit-reversal. */
class Fft(val size: Int) {

    private val cosTable = FloatArray(size / 2)
    private val sinTable = FloatArray(size / 2)
    private val reverse = IntArray(size)

    init {
        require(size >= 2 && size and (size - 1) == 0) { "FFT size must be a power of two" }
        for (i in 0 until size / 2) {
            cosTable[i] = cos(2.0 * PI * i / size).toFloat()
            sinTable[i] = sin(2.0 * PI * i / size).toFloat()
        }
        val bits = Integer.numberOfTrailingZeros(size)
        for (i in 0 until size) reverse[i] = Integer.reverse(i) ushr (32 - bits)
    }

    /** Forward transform: X[k] = sum x[n] e^(-2 pi i k n / N). */
    fun transform(re: FloatArray, im: FloatArray) {
        for (i in 0 until size) {
            val j = reverse[i]
            if (j > i) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
        }
        var len = 2
        while (len <= size) {
            val half = len / 2
            val step = size / len
            var start = 0
            while (start < size) {
                var k = 0
                for (j in start until start + half) {
                    val l = j + half
                    val tre = re[l] * cosTable[k] + im[l] * sinTable[k]
                    val tim = -re[l] * sinTable[k] + im[l] * cosTable[k]
                    re[l] = re[j] - tre
                    im[l] = im[j] - tim
                    re[j] += tre
                    im[j] += tim
                    k += step
                }
                start += len
            }
            len *= 2
        }
    }
}

package com.shake2flashlight

import kotlin.math.abs
import kotlin.math.sign

/**
 * Detects the chop-chop gesture from linear-acceleration samples (gravity removed).
 * The real gesture is a fast back-and-forth: acceleration on one axis keeps flipping between
 * strong positive and strong negative (recorded on an S23 Ultra: ±80 m/s², a flip every ~75 ms).
 * A slow move or a single swing gives far fewer strong flips, so it doesn't fire.
 * shortcut: calibrated on one recording; retune threshold/flips if it misfires.
 */
class ChopDetector(
    private val threshold: Float = 30f,         // m/s², how strong a swing must be to count
    private val flipsToFire: Int = 4,           // alternating strong swings needed
    private val maxFlipGapNs: Long = 250_000_000, // flips further apart start a new count
    private val quietNs: Long = 400_000_000,    // stillness needed before the next gesture counts
) {
    companion object {
        /** 0 low, 1 medium (calibrated default), 2 high. */
        fun forSensitivity(level: Int) = when (level) {
            0 -> ChopDetector(threshold = 40f, flipsToFire = 5)
            2 -> ChopDetector(threshold = 20f)
            else -> ChopDetector()
        }
    }

    private val lastSign = FloatArray(3)
    private val flips = IntArray(3)
    private val lastFlipNs = LongArray(3)
    private var lastAnyFlipNs = 0L
    private var locked = false

    /** Returns true when a chop-chop gesture completes on this sample. */
    fun onSample(tNs: Long, x: Float, y: Float, z: Float): Boolean {
        // After firing, ignore the rest of the same gesture until the phone goes still.
        if (locked && tNs - lastAnyFlipNs > quietNs) {
            locked = false
            flips.fill(0)
            lastSign.fill(0f)
        }
        var fired = false
        floatArrayOf(x, y, z).forEachIndexed { i, v ->
            if (abs(v) < threshold || sign(v) == lastSign[i]) return@forEachIndexed
            lastSign[i] = sign(v)
            lastAnyFlipNs = tNs
            if (locked) return@forEachIndexed
            flips[i] = if (tNs - lastFlipNs[i] > maxFlipGapNs) 1 else flips[i] + 1
            lastFlipNs[i] = tNs
            if (flips[i] >= flipsToFire) fired = true
        }
        if (fired) locked = true
        return fired
    }
}

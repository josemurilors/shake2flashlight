package com.shake2flashlight

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class ChopDetectorTest {

    private val ms = 1_000_000L

    /** Feeds an x-axis oscillation sampled at ~54 Hz; returns how many times it fired. */
    private fun ChopDetector.wave(startMs: Long, durMs: Long, periodMs: Long, amp: Float): Int =
        (startMs until startMs + durMs step 19).count {
            onSample(it * ms, amp * sin(2 * PI * it / periodMs).toFloat(), 0f, 0f)
        }

    private fun ChopDetector.still(startMs: Long, durMs: Long) =
        (startMs until startMs + durMs step 19).count { onSample(it * ms, 0.5f, 0f, 0f) }

    @Test fun fastStrongShakeFiresOnce() = assertEquals(1, ChopDetector().wave(1000, 600, 150, 80f))

    @Test fun longShakeStillFiresOnce() = assertEquals(1, ChopDetector().wave(1000, 1500, 150, 80f))

    @Test fun singleSwingDoesNotFire() = assertEquals(0, ChopDetector().wave(1000, 150, 150, 80f))

    @Test fun weakShakeDoesNotFire() = assertEquals(0, ChopDetector().wave(1000, 1000, 150, 20f))

    @Test fun slowSwingsDoNotFire() = assertEquals(0, ChopDetector().wave(1000, 3000, 1000, 80f))

    @Test fun sensitivityChangesWhatFires() {
        assertEquals(1, ChopDetector.forSensitivity(2).wave(1000, 600, 150, 25f))
        assertEquals(0, ChopDetector.forSensitivity(1).wave(1000, 600, 150, 25f))
        assertEquals(0, ChopDetector.forSensitivity(0).wave(1000, 600, 150, 35f))
        assertEquals(1, ChopDetector.forSensitivity(0).wave(1000, 600, 150, 80f))
    }

    @Test fun twoGesturesSeparatedByStillnessFireTwice() {
        val d = ChopDetector()
        val n = d.wave(1000, 600, 150, 80f) + d.still(1600, 800) + d.wave(2400, 600, 150, 80f)
        assertEquals(2, n)
    }

    /** Real recording from the S23 Ultra: 7 shake bursts; two are only 338 ms apart, so they count as one. */
    @Test fun realRecordingFiresOncePerBurst() {
        val d = ChopDetector()
        val fires = javaClass.getResource("/chop_recording.csv")!!.readText().lines()
            .filter { it.isNotBlank() }
            .count { line ->
                val (t, x, y, z) = line.split(",")
                d.onSample(t.toLong() * ms, x.toFloat(), y.toFloat(), z.toFloat())
            }
        assertEquals(6, fires)
    }
}

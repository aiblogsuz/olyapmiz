package com.example.lifedots.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IntervalSliderMathTest {

    @Test fun `msToSlider01 returns 0 for 1 second`() {
        assertEquals(0f, IntervalSliderMath.msToSlider01(1_000L), 0.001f)
    }

    @Test fun `msToSlider01 returns 1 for 1 hour`() {
        assertEquals(1f, IntervalSliderMath.msToSlider01(3_600_000L), 0.001f)
    }

    @Test fun `slider01ToMs round-trips with msToSlider01`() {
        listOf(1_000L, 5_000L, 30_000L, 300_000L, 1_800_000L, 3_600_000L).forEach { ms ->
            val slider = IntervalSliderMath.msToSlider01(ms)
            val back = IntervalSliderMath.slider01ToMs(slider)
            val tolerance = (ms * 0.01).toLong().coerceAtLeast(1L)
            assertTrue("$ms -> $slider -> $back exceeded tolerance",
                kotlin.math.abs(back - ms) <= tolerance)
        }
    }

    @Test fun `slider01ToSnappedMs snaps to nearest 1s below 1 minute`() {
        val ms = IntervalSliderMath.slider01ToSnappedMs(IntervalSliderMath.msToSlider01(7_300L))
        assertEquals(7_000L, ms)
    }

    @Test fun `slider01ToSnappedMs snaps to nearest 5s between 1 and 10 minutes`() {
        val ms = IntervalSliderMath.slider01ToSnappedMs(IntervalSliderMath.msToSlider01(123_000L))
        assertEquals(125_000L, ms)
    }

    @Test fun `slider01ToSnappedMs snaps to nearest 1 minute above 10 minutes`() {
        val ms = IntervalSliderMath.slider01ToSnappedMs(IntervalSliderMath.msToSlider01(1_350_000L))
        assertTrue(ms == 22 * 60_000L || ms == 23 * 60_000L)
    }

    @Test fun `tickIndexAt detects each anchor`() {
        IntervalSliderMath.TICK_POSITIONS.forEachIndexed { idx, pos ->
            assertEquals("anchor $idx at $pos", idx, IntervalSliderMath.tickIndexAt(pos))
        }
    }

    @Test fun `tickIndexAt returns -1 between anchors`() {
        val mid = (IntervalSliderMath.TICK_POSITIONS[1] + IntervalSliderMath.TICK_POSITIONS[2]) / 2f
        assertEquals(-1, IntervalSliderMath.tickIndexAt(mid))
    }

    @Test fun `formatLabel pluralizes correctly`() {
        assertEquals("Every 1 second", IntervalSliderMath.formatLabel(1_000L))
        assertEquals("Every 5 seconds", IntervalSliderMath.formatLabel(5_000L))
        assertEquals("Every 1 minute", IntervalSliderMath.formatLabel(60_000L))
        assertEquals("Every 5 minutes", IntervalSliderMath.formatLabel(300_000L))
        assertEquals("Every 1 hour", IntervalSliderMath.formatLabel(3_600_000L))
    }
    @Test fun `fast drags and reverse crossings give feedback without repeated ticks`() {
        val anchor = IntervalSliderMath.TICK_POSITIONS[2]
        assertTrue(IntervalSliderMath.crossedTick(anchor - 0.1f, anchor + 0.1f))
        assertTrue(IntervalSliderMath.crossedTick(anchor + 0.1f, anchor - 0.1f))
        assertTrue(!IntervalSliderMath.crossedTick(anchor, anchor + 0.001f))
        assertTrue(!IntervalSliderMath.crossedTick(anchor + 0.001f, anchor + 0.002f))
    }

    @Test fun `labels keep seconds for intervals between whole minutes`() {
        assertEquals("Every 1m 5s", IntervalSliderMath.formatLabel(65_000L))
        assertEquals("Every 2m 5s", IntervalSliderMath.formatLabel(125_000L))
    }

}

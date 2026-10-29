package com.fireworm.colourfun.ui.canvas

import android.view.MotionEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class PalmRejectionTest {

    private fun pointerUp(flags: Int): MotionEvent {
        val properties = arrayOf(
            MotionEvent.PointerProperties().apply { id = 0 },
            MotionEvent.PointerProperties().apply { id = 1 },
        )
        val coords = arrayOf(MotionEvent.PointerCoords(), MotionEvent.PointerCoords())
        return MotionEvent.obtain(
            0L, 10L, MotionEvent.ACTION_POINTER_UP or (0 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            2, properties, coords, 0, 0, 1f, 1f, 0, 0, 0, flags,
        )
    }

    @Test
    fun `a pointer lifted with FLAG_CANCELED is a palm`() {
        assertTrue(pointerUp(MotionEvent.FLAG_CANCELED).wasCanceled())
    }

    @Test
    fun `an ordinary lift is not`() {
        assertFalse(pointerUp(0).wasCanceled())
    }

    @Test
    @Config(sdk = [32])
    fun `before API 33 the flag is ignored`() {
        assertFalse(pointerUp(MotionEvent.FLAG_CANCELED).wasCanceled())
    }
}

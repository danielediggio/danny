package org.diggio.obdiggio.core.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PidsTest {

    @Test fun commandFormat() {
        assertEquals("010C", Pids[0x0C]!!.command())
        assertEquals("0105", Pids[0x05]!!.command())
    }

    @Test fun rpmDecode() {
        // ((0x1A*256)+0xF8)/4 = (6904)/4 = 1726
        val r = Pids[0x0C]!!.decode(intArrayOf(0x1A, 0xF8))
        assertEquals(1726.0, r.value!!, 0.001)
        assertEquals("rpm", r.unit)
    }

    @Test fun coolantTempOffset() {
        val r = Pids[0x05]!!.decode(intArrayOf(130)) // 130-40 = 90
        assertEquals(90.0, r.value!!, 0.001)
    }

    @Test fun speedDirect() {
        assertEquals(120.0, Pids[0x0D]!!.decode(intArrayOf(120)).value!!, 0.001)
    }

    @Test fun throttlePercent() {
        // 255 -> 100%
        assertEquals(100.0, Pids[0x11]!!.decode(intArrayOf(255)).value!!, 0.001)
        assertEquals(0.0, Pids[0x11]!!.decode(intArrayOf(0)).value!!, 0.001)
    }

    @Test fun insufficientBytesGivesNull() {
        assertNull(Pids[0x0C]!!.decode(intArrayOf(0x1A)).value) // servono 2 byte
    }

    @Test fun keyDerivation() {
        assertTrue(Pids[0x0C]!!.key == "rpm")
    }
}

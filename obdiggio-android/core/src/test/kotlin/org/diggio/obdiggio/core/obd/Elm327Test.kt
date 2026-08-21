package org.diggio.obdiggio.core.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Elm327Test {

    @Test fun parseHexBytesFiltersNoise() {
        val b = Elm327.parseHexBytes("SEARCHING... 41 0C 1A F8 >")
        assertEquals(listOf(0x41, 0x0C, 0x1A, 0xF8), b.toList())
    }

    @Test fun parseHexBytesSkipsIsoTpFrameMarkers() {
        val b = Elm327.parseHexBytes("0: 43 01 33 1: 04 20 00 00")
        assertEquals(listOf(0x43, 0x01, 0x33, 0x04, 0x20, 0x00, 0x00), b.toList())
    }

    @Test fun stripModeHeaderMode01() {
        val data = Elm327.stripModeHeader(intArrayOf(0x41, 0x0C, 0x1A, 0xF8), mode = 0x01, pid = 0x0C)
        assertEquals(listOf(0x1A, 0xF8), data!!.toList())
    }

    @Test fun stripModeHeaderWrongPidIsNull() {
        assertNull(Elm327.stripModeHeader(intArrayOf(0x41, 0x0D, 0x50), mode = 0x01, pid = 0x0C))
    }

    @Test fun endToEndViaMock() {
        val elm = Elm327(MockTransport())
        elm.connect()
        assertTrue(elm.isConnected)

        val rpm = elm.readPid(Pids[0x0C]!!)
        assertTrue("RPM plausibile", rpm.value!! in 700.0..1200.0)

        val coolant = elm.readPid(Pids[0x05]!!)
        assertTrue("Temp plausibile", coolant.value!! in 85.0..95.0)

        val dtcs = elm.readDtcs()
        assertEquals(listOf("P0133", "P0420"), dtcs.map { it.code })

        assertTrue(elm.clearDtcs())
        elm.close()
    }

    @Test fun mockWithoutDtcs() {
        val elm = Elm327(MockTransport(hasDtcs = false))
        elm.connect()
        assertTrue(elm.readDtcs().isEmpty())
    }
}

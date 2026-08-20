package org.diggio.obdiggio.core.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DtcTest {

    @Test fun decodePowertrain() {
        assertEquals("P0133", Dtc.decode(0x01, 0x33)!!.code)
        assertEquals("P0420", Dtc.decode(0x04, 0x20)!!.code)
    }

    @Test fun decodeSystemLetters() {
        assertEquals("C", Dtc.decode(0x40, 0x00)!!.code.substring(0, 1))
        assertEquals("B", Dtc.decode(0x80, 0x00)!!.code.substring(0, 1))
        assertEquals("U", Dtc.decode(0xC0, 0x00)!!.code.substring(0, 1))
    }

    @Test fun emptySlotIsNull() {
        assertNull(Dtc.decode(0x00, 0x00))
    }

    @Test fun decodeBytesSkipsEmpty() {
        val dtcs = Dtc.decodeBytes(intArrayOf(0x01, 0x33, 0x00, 0x00, 0x04, 0x20))
        assertEquals(listOf("P0133", "P0420"), dtcs.map { it.code })
    }

    @Test fun descriptionKnownAndFallback() {
        assertEquals("Sonda lambda risposta lenta (B1S1)", Dtc("P0133").description)
        assertEquals("Codice generico — consultare manuale del veicolo", Dtc("P9999").description)
    }
}

package org.diggio.obdiggio.core.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UdsTest {

    @Test fun parseTwoDtcs() {
        val dtcs = UdsDtc.parse("59 02 FF 20 15 11 08 04 01 00 08")
        assertEquals(listOf("P2015", "P0401"), dtcs.map { it.code })
        assertEquals(0x11, dtcs[0].failureType)
        assertEquals(0x08, dtcs[0].status)
        assertTrue(dtcs[0].confirmed)
        assertEquals("P2015-11", dtcs[0].fullCode)
    }

    @Test fun parseNoDtcs() {
        assertTrue(UdsDtc.parse("59 02 FF").isEmpty())
    }

    @Test fun parseIgnoresNonResponse() {
        assertTrue(UdsDtc.parse("NO DATA").isEmpty())
        assertTrue(UdsDtc.parse("7F 19 31").isEmpty()) // risposta negativa: nessun DTC
    }

    @Test fun descriptionCombinesCodeAndFailureType() {
        val d = UdsDtc("P2015", 0x11, 0x08)
        assertTrue("flap" in d.description || "collettore" in d.description)
        assertTrue("circuito" in d.description)
    }

    @Test fun clientReadsFromMockEngine() {
        val elm = Elm327(MockTransport())
        elm.connect()
        val client = UdsClient(elm)
        client.setup()
        val result = client.readModuleDtcs(UdsModules.VAG.first { it.name == "Motore" })
        assertTrue(result.responded)
        assertEquals(listOf("P2015", "P0401"), result.dtcs.map { it.code })
    }

    @Test fun clientClearReturnsTrueOnMock() {
        val elm = Elm327(MockTransport())
        elm.connect()
        val client = UdsClient(elm)
        assertTrue(client.clearModuleDtcs(UdsModules.VAG.first()))
    }

    @Test fun moduleHexFormatting() {
        val m = UdsModule(0x03, "ABS / ESP", 0x713, 0x77D)
        assertEquals("713", m.requestHex)
        assertEquals("77D", m.responseHex)
        assertFalse(m.autoResponse)
        assertTrue(UdsModule(0x01, "Motore", 0x7E0, 0x7E8).autoResponse)
    }
}

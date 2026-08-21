package org.diggio.obdiggio.core.obd

import kotlin.math.sin
import kotlin.random.Random

/**
 * Simulatore ELM327 come [Transport]: permette di sviluppare e testare
 * l'intera app (protocollo + UI) senza adattatore né veicolo. Alla scrittura di
 * un comando produce subito nel buffer la risposta corrispondente col prompt `>`.
 */
class MockTransport(private val hasDtcs: Boolean = true) : Transport() {

    private var opened = false
    private val t0 = System.nanoTime()

    override val isConnected: Boolean get() = opened

    override fun open() { opened = true }
    override fun close() { opened = false }

    override fun write(data: ByteArray) {
        check(opened) { "Trasporto simulato non aperto" }
        val command = String(data, Charsets.US_ASCII).trim().uppercase()
        feed((respond(command) + "\r\r>").toByteArray(Charsets.US_ASCII))
    }

    private fun elapsed(): Double = (System.nanoTime() - t0) / 1e9

    private fun respond(command: String): String = when {
        command.startsWith("AT") -> respondAt(command)
        // UDS: ReadDTCInformation (0x19/0x02) e ClearDiagnosticInformation (0x14).
        command.startsWith("1902") -> if (hasDtcs)
            "59 02 FF 20 15 11 08 04 01 00 08" else "59 02 FF"
        command.startsWith("14") -> "54"
        command.startsWith("02") -> respondMode02(command)
        command.startsWith("01") -> respondMode01(command)
        // Formato CAN (ISO 15765-4): "43 NN <coppie>", NN = numero di DTC.
        command == "03" -> if (hasDtcs) "43 02 01 33 04 20" else "43 00 00 00 00 00 00"
        command == "04" -> "44"
        else -> "NO DATA"
    }

    private fun respondAt(command: String): String = when (command) {
        "ATZ" -> "ELM327 v1.5"
        "ATRV" -> "%.1fV".format(12.2 + Random.nextDouble(-0.2, 0.4))
        else -> "OK"
    }

    // Freeze frame simulato (Mode 02): snapshot "al momento del guasto".
    private fun respondMode02(command: String): String {
        val pid = command.substring(2, 4).toIntOrNull(16) ?: return "NO DATA"
        fun frame(vararg d: Int) =
            (listOf("42", "%02X".format(pid), "00") + d.map { "%02X".format(it) }).joinToString(" ")
        return when (pid) {
            0x02 -> "42 02 00 01 04"                       // DTC che ha congelato: P0104
            0x04 -> frame((255 * 0.78).toInt())            // carico 78%
            0x05 -> frame(92 + 40)                         // refrigerante 92°C
            0x0B -> frame(158)                             // MAP 158 kPa
            0x0C -> { val r = 1850 * 4; frame((r shr 8) and 0xFF, r and 0xFF) } // 1850 rpm
            0x0D -> frame(62)                              // 62 km/h
            0x0E -> frame(128)                             // anticipo 0°
            0x0F -> frame(35 + 40)                         // aria aspirata 35°C
            0x10 -> { val r = 1500; frame((r shr 8) and 0xFF, r and 0xFF) }     // MAF 15 g/s
            0x11 -> frame((255 * 0.42).toInt())            // farfalla 42%
            0x1F -> { val r = 320; frame((r shr 8) and 0xFF, r and 0xFF) }      // 320 s dall'avvio
            0x33 -> frame(101)                             // barometrica
            else -> "NO DATA"
        }
    }

    private fun respondMode01(command: String): String {
        val pid = command.substring(2, 4).toIntOrNull(16) ?: return "NO DATA"
        val t = elapsed()
        fun frame(vararg dataBytes: Int): String =
            (listOf("41", "%02X".format(pid)) + dataBytes.map { "%02X".format(it) }).joinToString(" ")
        return when (pid) {
            // Maschere dei PID supportati (per il rilevamento automatico).
            0x00 -> "41 00 18 3B 80 01"
            0x20 -> "41 20 00 02 20 01"
            0x40 -> "41 40 44 80 00 00"
            0x04 -> frame((30 + 20 * (0.5 + 0.5 * sin(t))).toInt())
            0x05 -> frame(minOf(215, 90 + 40 + (3 * sin(t / 5)).toInt()))
            0x0B -> frame((104 + 18 * (0.5 + 0.5 * sin(t))).toInt()) // MAP (kPa)
            0x0C -> {
                val raw = ((800 + 300 * (0.5 + 0.5 * sin(t / 2))) * 4).toInt()
                frame((raw shr 8) and 0xFF, raw and 0xFF)
            }
            0x0D -> frame(0)
            0x0F -> frame(30 + 40)
            0x10 -> {
                val raw = ((2.0 + 0.5 * sin(t)) * 100).toInt()
                frame((raw shr 8) and 0xFF, raw and 0xFF)
            }
            0x11 -> frame((255 * 0.15).toInt())
            0x2F -> frame((255 * 0.62).toInt())
            0x33 -> frame(101)                                       // barometrica (kPa)
            0x42 -> {
                val raw = (12300 + Random.nextDouble(-100.0, 200.0)).toInt()
                frame((raw shr 8) and 0xFF, raw and 0xFF)
            }
            0x46 -> frame(22 + 40)
            0x49 -> frame((255 * 0.12).toInt())                      // pedale acceleratore
            else -> "NO DATA"
        }
    }
}

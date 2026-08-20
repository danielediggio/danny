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
        command.startsWith("01") -> respondMode01(command)
        command == "03" -> if (hasDtcs) "43 01 33 04 20" else "43 00 00"
        command == "04" -> "44"
        else -> "NO DATA"
    }

    private fun respondAt(command: String): String = when (command) {
        "ATZ" -> "ELM327 v1.5"
        "ATRV" -> "%.1fV".format(12.2 + Random.nextDouble(-0.2, 0.4))
        else -> "OK"
    }

    private fun respondMode01(command: String): String {
        val pid = command.substring(2, 4).toIntOrNull(16) ?: return "NO DATA"
        val t = elapsed()
        fun frame(vararg dataBytes: Int): String =
            (listOf("41", "%02X".format(pid)) + dataBytes.map { "%02X".format(it) }).joinToString(" ")
        return when (pid) {
            0x04 -> frame((30 + 20 * (0.5 + 0.5 * sin(t))).toInt())
            0x05 -> frame(minOf(215, 90 + 40 + (3 * sin(t / 5)).toInt()))
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
            0x42 -> {
                val raw = (12300 + Random.nextDouble(-100.0, 200.0)).toInt()
                frame((raw shr 8) and 0xFF, raw and 0xFF)
            }
            0x46 -> frame(22 + 40)
            else -> "NO DATA"
        }
    }
}

package org.diggio.obdiggio.core.obd

/** Valore decodificato di un PID. */
data class PidResult(
    val pid: Pid,
    val value: Double?,
    val unit: String,
) {
    val name: String get() = pid.name

    override fun toString(): String {
        val v = value ?: return "$name: n/d"
        val shown = if (v % 1.0 == 0.0) v.toInt().toString() else String.format("%.1f", v)
        return "$name: $shown $unit".trimEnd()
    }
}

/**
 * Definizione di un singolo PID Mode 01: sa costruire il comando ELM327 e
 * decodificare i byte dati (interi 0-255 nell'ordine A, B, C, D) in un valore
 * fisico, secondo le formule SAE J1979.
 */
data class Pid(
    val code: Int,
    val name: String,
    val unit: String,
    val numBytes: Int,
    val minValue: Double,
    val maxValue: Double,
    val decoder: (IntArray) -> Double,
) {
    /** Identificatore breve, es. "rpm". */
    val key: String get() = name.lowercase().replace(" ", "_")

    /** Comando ELM327 (Mode 01 + PID), es. "010C". */
    fun command(): String = "01%02X".format(code)

    fun decode(data: IntArray): PidResult {
        if (data.size < numBytes) return PidResult(this, null, unit)
        return try {
            PidResult(this, decoder(data), unit)
        } catch (e: IndexOutOfBoundsException) {
            PidResult(this, null, unit)
        } catch (e: ArithmeticException) {
            PidResult(this, null, unit)
        }
    }
}

/** Catalogo dei PID standard supportati. */
object Pids {

    // Decoder elementari (formule SAE J1979).
    private val percent: (IntArray) -> Double = { it[0] * 100.0 / 255.0 }
    private val temp: (IntArray) -> Double = { it[0] - 40.0 }
    private val rpm: (IntArray) -> Double = { ((it[0] * 256) + it[1]) / 4.0 }
    private val speed: (IntArray) -> Double = { it[0].toDouble() }
    private val timingAdvance: (IntArray) -> Double = { it[0] / 2.0 - 64.0 }
    private val maf: (IntArray) -> Double = { ((it[0] * 256) + it[1]) / 100.0 }
    private val intakePressure: (IntArray) -> Double = { it[0].toDouble() }
    private val controlModuleVoltage: (IntArray) -> Double = { ((it[0] * 256) + it[1]) / 1000.0 }
    private val fuelTrim: (IntArray) -> Double = { it[0] / 1.28 - 100.0 }
    private val runTime: (IntArray) -> Double = { (it[0] * 256 + it[1]).toDouble() }
    private val distance: (IntArray) -> Double = { (it[0] * 256 + it[1]).toDouble() }

    val all: List<Pid> = listOf(
        Pid(0x04, "Carico motore", "%", 1, 0.0, 100.0, percent),
        Pid(0x05, "Temp refrigerante", "°C", 1, -40.0, 215.0, temp),
        Pid(0x06, "Fuel trim breve B1", "%", 1, -100.0, 99.0, fuelTrim),
        Pid(0x07, "Fuel trim lungo B1", "%", 1, -100.0, 99.0, fuelTrim),
        Pid(0x0A, "Pressione carburante", "kPa", 1, 0.0, 765.0) { (it[0] * 3).toDouble() },
        Pid(0x0B, "Pressione aspirazione", "kPa", 1, 0.0, 255.0, intakePressure),
        Pid(0x0C, "RPM", "rpm", 2, 0.0, 8000.0, rpm),
        Pid(0x0D, "Velocita'", "km/h", 1, 0.0, 255.0, speed),
        Pid(0x0E, "Anticipo accensione", "°", 1, -64.0, 63.0, timingAdvance),
        Pid(0x0F, "Temp aria aspirata", "°C", 1, -40.0, 215.0, temp),
        Pid(0x10, "MAF", "g/s", 2, 0.0, 655.0, maf),
        Pid(0x11, "Posizione farfalla", "%", 1, 0.0, 100.0, percent),
        Pid(0x1F, "Tempo motore acceso", "s", 2, 0.0, 65535.0, runTime),
        Pid(0x21, "Distanza con MIL", "km", 2, 0.0, 65535.0, distance),
        Pid(0x2F, "Livello carburante", "%", 1, 0.0, 100.0, percent),
        Pid(0x31, "Distanza da azzeramento", "km", 2, 0.0, 65535.0, distance),
        Pid(0x42, "Tensione modulo", "V", 2, 0.0, 65.0, controlModuleVoltage),
        Pid(0x43, "Carico assoluto", "%", 2, 0.0, 25700.0) { (it[0] * 256 + it[1]) * 100.0 / 255.0 },
        Pid(0x46, "Temp ambiente", "°C", 1, -40.0, 215.0, temp),
        Pid(0x5C, "Temp olio motore", "°C", 1, -40.0, 215.0, temp),
    )

    private val byCode: Map<Int, Pid> = all.associateBy { it.code }

    operator fun get(code: Int): Pid? = byCode[code]
}

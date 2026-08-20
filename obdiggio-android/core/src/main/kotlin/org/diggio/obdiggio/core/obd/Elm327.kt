package org.diggio.obdiggio.core.obd

/**
 * Driver ELM327 agnostico rispetto al trasporto: init AT, invio comandi,
 * parsing risposte OBD (Mode 01 dati live, Mode 03 lettura DTC, Mode 04
 * cancellazione, ATRV tensione).
 */
class Elm327(
    val transport: Transport,
    private val timeoutMs: Long = 5000,
) {
    private var initialized = false

    val isConnected: Boolean get() = initialized && transport.isConnected

    fun connect() {
        if (!transport.isConnected) transport.open()
        initialize()
        initialized = true
        // Warm-up: la prima richiesta OBD fa negoziare il protocollo (SEARCHING…);
        // la eseguiamo qui così le letture successive sono già pronte.
        try { query("0100") } catch (_: Exception) {}
    }

    /** Interroga i PID supportati (0100) e ritorna la risposta grezza ripulita (diagnostica). */
    fun probeSupportedPids(): String = query("0100")

    /**
     * Rileva l'insieme dei PID Mode 01 supportati dal veicolo, interrogando le
     * maschere 0100/0120/0140/... (ognuna descrive 32 PID; l'ultimo bit indica
     * se esiste il gruppo successivo).
     */
    fun supportedPids(): Set<Int> {
        val supported = mutableSetOf<Int>()
        var base = 0x00
        while (base <= 0xC0) {
            val data = stripModeHeader(parseHexBytes(query("01%02X".format(base))), 0x01, base)
            if (data == null || data.size < 4) break
            val bits = (data[0].toLong() shl 24) or (data[1].toLong() shl 16) or
                (data[2].toLong() shl 8) or data[3].toLong()
            for (i in 0 until 32) {
                if ((bits shr (31 - i)) and 1L == 1L) supported.add(base + i + 1)
            }
            val next = base + 0x20
            if (!supported.contains(next)) break
            base = next
        }
        return supported
    }

    fun close() {
        initialized = false
        transport.close()
    }

    fun initialize() {
        transport.clear()
        for ((cmd, settle) in INIT_COMMANDS) {
            sendRaw(cmd)
            Thread.sleep(settle)
        }
    }

    private fun sendRaw(command: String): String {
        transport.write((command + "\r").toByteArray(Charsets.US_ASCII))
        val raw = transport.readUntil('>'.code.toByte(), timeoutMs)
        return String(raw, Charsets.US_ASCII)
    }

    /** Invia un comando e ritorna la risposta ripulita (senza prompt né echo). */
    fun query(command: String): String = clean(sendRaw(command))

    // --- Query di alto livello ---

    fun readPid(pid: Pid): PidResult {
        val bytes = parseHexBytes(query(pid.command()))
        val data = stripModeHeader(bytes, mode = 0x01, pid = pid.code)
            ?: return PidResult(pid, null, pid.unit)
        return pid.decode(data)
    }

    /** Codici memorizzati/confermati (Mode 03). */
    fun readDtcs(): List<Dtc> = readDtcsForMode("03", 0x03)

    /** Codici in sospeso — non ancora confermati (Mode 07). */
    fun readPendingDtcs(): List<Dtc> = readDtcsForMode("07", 0x07)

    /** Codici permanenti — non cancellabili con Mode 04 (Mode 0A). */
    fun readPermanentDtcs(): List<Dtc> = readDtcsForMode("0A", 0x0A)

    private fun readDtcsForMode(command: String, responseMode: Int): List<Dtc> {
        val bytes = parseHexBytes(query(command))
        val data = stripModeHeader(bytes, mode = responseMode, pid = null) ?: return emptyList()
        // Su CAN (ISO 15765-4, es. Nissan Qashqai) la risposta è "4X NN <coppie>"
        // con NN = numero di DTC; su ISO 9141/KWP il conteggio non c'è. I DTC
        // sono coppie di byte: se i byte dati sono in numero dispari, il primo è
        // il conteggio e va scartato.
        val payload = if (data.size % 2 == 1) data.copyOfRange(1, data.size) else data
        return Dtc.decodeBytes(payload)
    }

    // --- Freeze frame (Mode 02) ------------------------------------------
    // La risposta è "42 <PID> <frame> <dati>": come il Mode 01 ma con un byte
    // di indice frame (di solito 00) tra il PID e i dati.

    /** Legge un PID dallo snapshot congelato al momento del guasto (frame 0). */
    fun readFreezeFramePid(pid: Pid): PidResult {
        val bytes = parseHexBytes(query("02%02X00".format(pid.code)))
        val idx = bytes.indexOf(0x42)
        if (idx == -1 || idx + 2 >= bytes.size || bytes[idx + 1] != pid.code) {
            return PidResult(pid, null, pid.unit)
        }
        return pid.decode(bytes.copyOfRange(idx + 3, bytes.size)) // salta PID + frame
    }

    /** DTC che ha generato il freeze frame (Mode 02, PID 02). */
    fun readFreezeFrameDtc(): Dtc? {
        val bytes = parseHexBytes(query("020200"))
        val idx = bytes.indexOf(0x42)
        if (idx == -1 || idx + 4 >= bytes.size || bytes[idx + 1] != 0x02) return null
        return Dtc.decode(bytes[idx + 3], bytes[idx + 4])
    }

    /** Cancella i codici e spegne la spia MIL (Mode 04). True se l'ECU conferma (`44`). */
    fun clearDtcs(): Boolean = parseHexBytes(query("04")).contains(0x44)

    /** Tensione batteria letta dall'ELM327 (comando ATRV). */
    fun voltage(): Double? {
        val digits = query("ATRV").filter { it.isDigit() || it == '.' }
        return digits.toDoubleOrNull()
    }

    companion object {
        private val INIT_COMMANDS = listOf(
            "ATZ" to 1000L,   // reset
            "ATE0" to 300L,   // echo off
            "ATL0" to 300L,   // linefeed off
            "ATS1" to 300L,   // spazi ON: risposte "41 0C 1A F8" (il parser separa sugli spazi)
            "ATH0" to 300L,   // header off
            "ATSP0" to 300L,  // protocollo automatico
        )

        private val HEX = "0123456789ABCDEF".toSet()

        /** Rimuove prompt, echo e whitespace, normalizzando gli spazi. */
        fun clean(raw: String): String =
            raw.replace(">", " ")
                .replace(Regex("[\\r\\n\\t]"), " ")
                .split(" ").filter { it.isNotEmpty() }.joinToString(" ").trim()

        /**
         * Estrae i byte esadecimali (token a 2 cifre) da una risposta OBD,
         * unendo risposte multiriga/multi-frame e scartando i marcatori di frame
         * ISO-TP (`0:`, `1:`) e il testo (SEARCHING, OK, ...).
         */
        fun parseHexBytes(response: String): IntArray {
            val out = mutableListOf<Int>()
            // I marcatori di frame ISO-TP ("0:", "1:") vengono separati dai dati.
            val cleaned = response.uppercase().replace(":", " ")
            for (tok in cleaned.split(Regex("\\s+"))) {
                if (tok.isEmpty()) continue
                if (!tok.all { it in HEX }) continue
                // Token esadecimale a byte interi: lo spezziamo in coppie, così
                // funziona sia con spazi ("41 0C") sia senza ("410C1AF8").
                if (tok.length % 2 != 0) continue
                var i = 0
                while (i < tok.length) {
                    out.add(tok.substring(i, i + 2).toInt(16))
                    i += 2
                }
            }
            return out.toIntArray()
        }

        /**
         * Rimuove l'header `4X [PID]` e ritorna i soli byte dato; null se assente
         * (es. NO DATA). La risposta positiva al Mode N inizia con 0x40+N.
         */
        fun stripModeHeader(data: IntArray, mode: Int, pid: Int?): IntArray? {
            val responseMode = 0x40 + mode
            for (i in data.indices) {
                if (data[i] == responseMode) {
                    val start = i + 1
                    if (pid != null) {
                        if (start < data.size && data[start] == pid) {
                            return data.copyOfRange(start + 1, data.size)
                        }
                        continue
                    }
                    return data.copyOfRange(start, data.size)
                }
            }
            return null
        }
    }
}

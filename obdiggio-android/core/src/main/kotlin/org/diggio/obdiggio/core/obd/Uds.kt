package org.diggio.obdiggio.core.obd

/**
 * Diagnostica UDS (ISO 14229) su ISO-TP/CAN tramite ELM327.
 *
 * A differenza dell'OBD-II generico (che parla solo con la centralina motore
 * sul canale emissioni), l'UDS permette di interrogare **più centraline**
 * indirizzandole per ID CAN. Su piattaforme VAG con CAN a 11 bit (indicativamente
 * dal 2012+, ma motore/cambio rispondono anche prima) si leggono così gli errori
 * di ABS, airbag, quadro strumenti, servosterzo, ecc.
 *
 * NB: molte vetture VAG più vecchie (es. A6 C6 2007) usano il protocollo VW
 * TP2.0/KWP per i moduli non-powertrain: quelli **non** rispondono in UDS e la
 * scansione li segnala semplicemente come "non risponde". Motore e cambio, che
 * stanno sul canale diagnostico standard, sono i più probabili a rispondere.
 */

/** Una centralina indirizzabile via UDS, con i suoi ID CAN richiesta/risposta. */
data class UdsModule(val address: Int, val name: String, val requestId: Int, val responseId: Int) {
    val requestHex: String get() = "%03X".format(requestId)
    val responseHex: String get() = "%03X".format(responseId)
    /** Nei range OBD (0x7E0–0x7E7) l'ELM calcola da sé il filtro di risposta (req+8). */
    val autoResponse: Boolean get() = responseId == requestId + 8
}

object UdsModules {
    /**
     * Set di centraline VAG comuni (indirizzamento CAN 11 bit).
     * Motore/cambio usano gli ID diagnostici standard; gli altri seguono lo
     * schema VAG (risposta = richiesta + 0x6A).
     */
    val VAG: List<UdsModule> = listOf(
        UdsModule(0x01, "Motore", 0x7E0, 0x7E8),
        UdsModule(0x02, "Cambio", 0x7E1, 0x7E9),
        UdsModule(0x03, "ABS / ESP", 0x713, 0x77D),
        UdsModule(0x15, "Airbag", 0x715, 0x77F),
        UdsModule(0x17, "Quadro strumenti", 0x714, 0x77E),
        UdsModule(0x44, "Servosterzo", 0x712, 0x77C),
        UdsModule(0x09, "Elettronica centrale", 0x70E, 0x778),
    )
}

/**
 * Un DTC in formato UDS a 3 byte: i primi due danno il codice P/C/B/U (stessa
 * codifica del Mode 03), il terzo è il *failure type byte* (FTB, natura del
 * guasto secondo ISO 14229/J2012). Lo `status` è la maschera di stato del DTC.
 */
data class UdsDtc(val code: String, val failureType: Int, val status: Int) {

    /** Codice completo come mostrato dagli strumenti VAG, es. "P2015-11". */
    val fullCode: String get() = "%s-%02X".format(code, failureType)

    val description: String
        get() {
            val base = Dtc.describe(code)
            val ftb = FAILURE_TYPES[failureType]
            return if (ftb != null) "$base — $ftb" else base
        }

    /** True se il bit 0 della maschera di stato indica "test fallito". */
    val confirmed: Boolean get() = status and 0x08 != 0

    companion object {
        /** Nature del guasto (failure type byte) più comuni. */
        val FAILURE_TYPES: Map<Int, String> = mapOf(
            0x00 to "guasto generico",
            0x11 to "circuito: corrente bassa / massa",
            0x12 to "circuito: corrente alta / positivo",
            0x13 to "circuito aperto",
            0x14 to "cortocircuito a massa",
            0x15 to "cortocircuito al positivo",
            0x1C to "tensione fuori range",
            0x21 to "segnale troppo basso",
            0x22 to "segnale troppo alto",
            0x23 to "segnale bloccato",
            0x29 to "segnale non valido",
            0x2F to "segnale irregolare",
            0x31 to "nessun segnale",
            0x38 to "componente bloccato",
            0x62 to "valori non coerenti tra sensori",
            0x64 to "segnale non plausibile",
            0x68 to "condizione non corretta",
            0x81 to "dati seriali non validi",
            0x83 to "valore massimo superato",
            0x87 to "guasto non identificato",
        )

        /**
         * Estrae i DTC da una risposta UDS al servizio 0x19 sub-funzione 0x02
         * (ReadDTCInformation / reportDTCByStatusMask). Risposta positiva:
         * `59 02 <maschera disponibilità> [<hi> <mid> <ftb> <status>]...`.
         */
        fun parse(response: String): List<UdsDtc> {
            val bytes = Elm327.parseHexBytes(response)
            // Cerca l'header di risposta positiva 0x59 0x02.
            var i = 0
            while (i < bytes.size - 1) {
                if (bytes[i] == 0x59 && bytes[i + 1] == 0x02) break
                i++
            }
            if (i >= bytes.size - 1) return emptyList()
            // Salta 59, 02 e il byte di maschera disponibilità.
            var p = i + 3
            val out = mutableListOf<UdsDtc>()
            while (p + 3 < bytes.size + 1 && p + 2 < bytes.size) {
                if (p + 3 >= bytes.size) {
                    // record incompleto (senza status): usa 0.
                    val dtc = Dtc.decode(bytes[p], bytes[p + 1]) ?: break
                    out.add(UdsDtc(dtc.code, bytes[p + 2], 0))
                    break
                }
                val dtc = Dtc.decode(bytes[p], bytes[p + 1])
                if (dtc != null) out.add(UdsDtc(dtc.code, bytes[p + 2], bytes[p + 3]))
                p += 4
            }
            return out
        }
    }
}

/** Esito della scansione di una singola centralina. */
data class UdsModuleResult(val module: UdsModule, val responded: Boolean, val dtcs: List<UdsDtc>)

/**
 * Client UDS che riusa un [Elm327] già connesso come canale comandi.
 *
 * Per ogni centralina imposta l'header di richiesta (ATSH) e il filtro di
 * ricezione (ATCRA), poi invia i servizi UDS. L'ELM327 gestisce da sé il
 * framing ISO-TP (multi-frame) con l'auto-formatting attivo.
 */
class UdsClient(private val elm: Elm327) {

    /** Prepara l'ELM327 per la diagnostica UDS su CAN. */
    fun setup() {
        elm.query("ATCAF1")   // auto-formatting ISO-TP ON
        elm.query("ATH0")     // header off (filtriamo per ATCRA)
        elm.query("ATST20")   // timeout breve: i moduli assenti falliscono in fretta
    }

    private fun target(module: UdsModule) {
        elm.query("ATSH${module.requestHex}")
        // Filtro di ricezione: nei range OBD l'ELM lo deduce, altrimenti va imposto.
        if (module.autoResponse) elm.query("ATCRA") else elm.query("ATCRA${module.responseHex}")
    }

    /** Legge i DTC memorizzati (servizio 0x19/0x02, maschera di stato 0xFF). */
    fun readModuleDtcs(module: UdsModule): UdsModuleResult {
        target(module)
        val resp = elm.query("1902FF")
        val responded = looksLikeResponse(resp)
        return UdsModuleResult(module, responded, if (responded) UdsDtc.parse(resp) else emptyList())
    }

    /** Cancella i DTC di una centralina (servizio 0x14, gruppo 0xFFFFFF = tutti). */
    fun clearModuleDtcs(module: UdsModule): Boolean {
        target(module)
        return Elm327.parseHexBytes(elm.query("14FFFFFF")).contains(0x54)
    }

    /** Ripristina lo stato dell'ELM327 per l'OBD generico (dopo una scansione). */
    fun restoreObd() {
        try {
            elm.initialize()
            elm.query("0100")
        } catch (_: Exception) {}
    }

    private fun looksLikeResponse(resp: String): Boolean {
        val bytes = Elm327.parseHexBytes(resp)
        // Risposta positiva 0x59, oppure risposta negativa 0x7F 0x19 (il modulo
        // c'è ma rifiuta): in entrambi i casi il modulo "risponde".
        for (i in bytes.indices) {
            if (bytes[i] == 0x59) return true
            if (bytes[i] == 0x7F && i + 1 < bytes.size && bytes[i + 1] == 0x19) return true
        }
        return false
    }
}

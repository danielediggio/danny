package org.diggio.obdiggio.core.obd

/**
 * Un Diagnostic Trouble Code, con la sua descrizione.
 *
 * Un DTC è codificato su 2 byte: i 2 bit più alti del primo byte danno la
 * lettera di sistema (P/C/B/U), i 2 successivi la prima cifra, i restanti nibble
 * le altre tre cifre esadecimali. Es. `0x01 0x33` -> `P0133`.
 */
data class Dtc(val code: String) {
    val description: String
        get() = DESCRIPTIONS[code] ?: "Codice generico — consultare manuale del veicolo"

    override fun toString(): String = "$code — $description"

    companion object {
        private val SYSTEM_LETTERS = mapOf(0 to "P", 1 to "C", 2 to "B", 3 to "U")

        val DESCRIPTIONS: Map<String, String> = mapOf(
            "P0100" to "Malfunzionamento circuito portata aria (MAF)",
            "P0101" to "Portata aria/MAF fuori range",
            "P0102" to "Segnale MAF basso",
            "P0103" to "Segnale MAF alto",
            "P0104" to "Circuito MAF intermittente",
            "P0105" to "Sensore pressione collettore (MAP) circuito",
            "P0107" to "Sensore pressione collettore (MAP) basso",
            "P0108" to "Sensore pressione collettore (MAP) alto",
            "P0110" to "Sensore temperatura aria aspirata circuito",
            "P0112" to "Sensore temperatura aria aspirata basso",
            "P0115" to "Sensore temperatura refrigerante circuito",
            "P0116" to "Sensore temperatura refrigerante fuori range",
            "P0120" to "Sensore posizione farfalla/pedale circuito",
            "P0180" to "Sensore temperatura carburante circuito",
            "P0087" to "Pressione rail carburante troppo bassa",
            "P0088" to "Pressione rail carburante troppo alta",
            "P0089" to "Regolatore pressione carburante prestazioni",
            "P0234" to "Sovrapressione turbo (overboost)",
            "P0299" to "Sottopressione turbo (underboost)",
            "P0380" to "Circuito candelette di preriscaldo",
            "P0201" to "Circuito iniettore cilindro 1",
            "P0202" to "Circuito iniettore cilindro 2",
            "P0203" to "Circuito iniettore cilindro 3",
            "P0204" to "Circuito iniettore cilindro 4",
            "P0402" to "Flusso EGR eccessivo",
            "P0403" to "Circuito controllo valvola EGR",
            "P0404" to "Valvola EGR fuori range",
            "P0470" to "Sensore pressione gas di scarico circuito",
            "P0471" to "Sensore pressione gas di scarico fuori range",
            "P2002" to "Efficienza filtro antiparticolato (FAP/DPF) bassa",
            "P244A" to "Differenza pressione DPF troppo bassa",
            "P244B" to "Differenza pressione DPF troppo alta",
            "P0113" to "Sensore temperatura aria aspirata alto",
            "P0117" to "Sensore temperatura refrigerante basso",
            "P0118" to "Sensore temperatura refrigerante alto",
            "P0128" to "Refrigerante sotto temperatura di regolazione (termostato)",
            "P0130" to "Malfunzionamento sonda lambda (B1S1)",
            "P0133" to "Sonda lambda risposta lenta (B1S1)",
            "P0134" to "Sonda lambda nessuna attivita' (B1S1)",
            "P0171" to "Sistema troppo magro (Banco 1)",
            "P0172" to "Sistema troppo ricco (Banco 1)",
            "P0174" to "Sistema troppo magro (Banco 2)",
            "P0175" to "Sistema troppo ricco (Banco 2)",
            "P0300" to "Rilevati cilindri in cilecca casuali/multipli",
            "P0301" to "Cilecca cilindro 1",
            "P0302" to "Cilecca cilindro 2",
            "P0303" to "Cilecca cilindro 3",
            "P0304" to "Cilecca cilindro 4",
            "P0325" to "Malfunzionamento sensore di detonazione (Banco 1)",
            "P0335" to "Malfunzionamento sensore posizione albero motore",
            "P0340" to "Malfunzionamento sensore posizione albero a camme",
            "P0401" to "Flusso EGR insufficiente",
            "P0420" to "Efficienza catalizzatore sotto soglia (Banco 1)",
            "P0430" to "Efficienza catalizzatore sotto soglia (Banco 2)",
            "P0442" to "Piccola perdita sistema evaporativo (EVAP)",
            "P0455" to "Grande perdita sistema evaporativo (EVAP)",
            "P0500" to "Malfunzionamento sensore velocita' veicolo",
            "P0505" to "Malfunzionamento controllo minimo (IAC)",
            "P0562" to "Tensione sistema bassa",
            "P0563" to "Tensione sistema alta",
            "P0700" to "Malfunzionamento sistema controllo trasmissione",
            "U0100" to "Persa comunicazione con ECM/PCM",
        )

        /** Decodifica una coppia di byte; ritorna null per lo slot vuoto `00 00`. */
        fun decode(byteA: Int, byteB: Int): Dtc? {
            if (byteA == 0 && byteB == 0) return null
            val letter = SYSTEM_LETTERS[(byteA and 0xC0) shr 6]!!
            val d1 = (byteA and 0x30) shr 4
            val d2 = byteA and 0x0F
            val d3 = (byteB and 0xF0) shr 4
            val d4 = byteB and 0x0F
            return Dtc("%s%d%X%X%X".format(letter, d1, d2, d3, d4))
        }

        /** Decodifica una sequenza di byte (coppie A,B), ignorando gli slot vuoti. */
        fun decodeBytes(data: IntArray): List<Dtc> {
            val out = mutableListOf<Dtc>()
            var i = 0
            while (i < data.size - 1) {
                decode(data[i], data[i + 1])?.let { out.add(it) }
                i += 2
            }
            return out
        }
    }
}

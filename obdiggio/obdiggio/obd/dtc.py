"""Decodifica dei Diagnostic Trouble Code (DTC).

Un DTC OBD-II e' codificato su 2 byte. I 2 bit piu' alti del primo byte
indicano la lettera del sistema (P/C/B/U), i 2 bit successivi la prima cifra,
e i restanti nibble le altre tre cifre esadecimali.

Esempio: byte ``0x01 0x33`` -> ``P0133``.
"""

from __future__ import annotations

from typing import Dict, List, Optional

# Lettera di sistema dai 2 bit piu' significativi del primo byte.
_SYSTEM_LETTERS = {0b00: "P", 0b01: "C", 0b10: "B", 0b11: "U"}

# Descrizioni per un sottoinsieme di codici generici molto comuni.
# La lista completa e' enorme; qui copriamo i piu' frequenti e lasciamo
# un fallback generico per gli altri.
DTC_DESCRIPTIONS: Dict[str, str] = {
    "P0100": "Malfunzionamento circuito portata aria (MAF)",
    "P0101": "Portata aria/MAF fuori range",
    "P0102": "Segnale MAF basso",
    "P0113": "Sensore temperatura aria aspirata alto",
    "P0117": "Sensore temperatura refrigerante basso",
    "P0118": "Sensore temperatura refrigerante alto",
    "P0128": "Refrigerante sotto temperatura di regolazione (termostato)",
    "P0130": "Malfunzionamento sonda lambda (B1S1)",
    "P0133": "Sonda lambda risposta lenta (B1S1)",
    "P0134": "Sonda lambda nessuna attivita' (B1S1)",
    "P0171": "Sistema troppo magro (Banco 1)",
    "P0172": "Sistema troppo ricco (Banco 1)",
    "P0174": "Sistema troppo magro (Banco 2)",
    "P0175": "Sistema troppo ricco (Banco 2)",
    "P0300": "Rilevati cilindri in cilecca casuali/multipli",
    "P0301": "Cilecca cilindro 1",
    "P0302": "Cilecca cilindro 2",
    "P0303": "Cilecca cilindro 3",
    "P0304": "Cilecca cilindro 4",
    "P0325": "Malfunzionamento sensore di detonazione (Banco 1)",
    "P0335": "Malfunzionamento sensore posizione albero motore",
    "P0340": "Malfunzionamento sensore posizione albero a camme",
    "P0401": "Flusso EGR insufficiente",
    "P0420": "Efficienza catalizzatore sotto soglia (Banco 1)",
    "P0430": "Efficienza catalizzatore sotto soglia (Banco 2)",
    "P0442": "Piccola perdita sistema evaporativo (EVAP)",
    "P0455": "Grande perdita sistema evaporativo (EVAP)",
    "P0500": "Malfunzionamento sensore velocita' veicolo",
    "P0505": "Malfunzionamento controllo minimo (IAC)",
    "P0562": "Tensione sistema bassa",
    "P0563": "Tensione sistema alta",
    "P0700": "Malfunzionamento sistema controllo trasmissione",
    "U0100": "Persa comunicazione con ECM/PCM",
}


class DTC:
    """Un singolo codice diagnostico."""

    def __init__(self, code: str):
        self.code = code

    @property
    def description(self) -> str:
        return DTC_DESCRIPTIONS.get(self.code, "Codice generico — consultare manuale del veicolo")

    def __eq__(self, other: object) -> bool:
        return isinstance(other, DTC) and other.code == self.code

    def __hash__(self) -> int:
        return hash(self.code)

    def __repr__(self) -> str:
        return f"DTC({self.code})"

    def __str__(self) -> str:
        return f"{self.code} — {self.description}"


def decode_dtc(byte_a: int, byte_b: int) -> Optional[DTC]:
    """Decodifica una coppia di byte in un :class:`DTC`.

    Ritorna ``None`` per la coppia ``00 00`` (slot vuoto).
    """
    if byte_a == 0 and byte_b == 0:
        return None
    letter = _SYSTEM_LETTERS[(byte_a & 0xC0) >> 6]
    first_digit = (byte_a & 0x30) >> 4
    second_digit = byte_a & 0x0F
    third_digit = (byte_b & 0xF0) >> 4
    fourth_digit = byte_b & 0x0F
    code = f"{letter}{first_digit}{second_digit:X}{third_digit:X}{fourth_digit:X}"
    return DTC(code)


def decode_dtc_bytes(data: List[int]) -> List[DTC]:
    """Decodifica una sequenza di byte (coppie A,B) in una lista di DTC.

    Ignora gli slot vuoti (``00 00``) e le coppie incomplete finali.
    """
    dtcs: List[DTC] = []
    for i in range(0, len(data) - 1, 2):
        dtc = decode_dtc(data[i], data[i + 1])
        if dtc is not None:
            dtcs.append(dtc)
    return dtcs

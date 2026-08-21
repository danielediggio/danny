"""Driver ELM327: inizializzazione, invio comandi e parsing risposte.

Il driver e' agnostico rispetto al trasporto: riceve un :class:`Transport`
(BLE reale su Android, oppure un simulatore su desktop) e ci comunica con i
comandi AT dell'ELM327 e i comandi OBD-II standard.
"""

from __future__ import annotations

import logging
import time
from typing import Callable, List, Optional

from .dtc import DTC, decode_dtc_bytes
from .pids import Pid, PidResult
from .transport import Transport

log = logging.getLogger("obdiggio.elm327")

# Sequenza di inizializzazione consigliata per gli ELM327.
_INIT_COMMANDS = [
    ("ATZ", 1.0),    # reset
    ("ATE0", 0.3),   # echo off
    ("ATL0", 0.3),   # linefeed off
    ("ATS0", 0.3),   # spazi off (risposte piu' compatte)
    ("ATH0", 0.3),   # header off
    ("ATSP0", 0.3),  # protocollo automatico
]

_HEX = set("0123456789ABCDEF")


class ELM327Error(Exception):
    """Errore di comunicazione o risposta inattesa dall'ELM327."""


class ELM327:
    """Interfaccia di alto livello verso l'adattatore ELM327."""

    def __init__(self, transport: Transport, timeout: float = 5.0) -> None:
        self.transport = transport
        self.timeout = timeout
        self._connected = False

    # --- Ciclo di vita ----------------------------------------------------

    @property
    def is_connected(self) -> bool:
        return self._connected and self.transport.is_connected

    def connect(self) -> None:
        """Apre il trasporto ed esegue l'inizializzazione ELM327."""
        if not self.transport.is_connected:
            self.transport.open()
        self.initialize()
        self._connected = True

    def close(self) -> None:
        self._connected = False
        self.transport.close()

    def initialize(self) -> None:
        """Invia la sequenza di comandi AT di inizializzazione."""
        self.transport.clear()
        for cmd, settle in _INIT_COMMANDS:
            self._send_raw(cmd)
            time.sleep(settle)

    # --- Invio comandi ----------------------------------------------------

    def _send_raw(self, command: str) -> str:
        """Invia un comando (aggiungendo CR) e ritorna la risposta grezza."""
        self.transport.write((command + "\r").encode("ascii"))
        raw = self.transport.read_until(b">", timeout=self.timeout)
        return raw.decode("ascii", errors="ignore")

    def query(self, command: str) -> str:
        """Invia un comando e ritorna la risposta ripulita (senza prompt)."""
        raw = self._send_raw(command)
        return self._clean(raw)

    @staticmethod
    def _clean(raw: str) -> str:
        """Rimuove prompt, echo e whitespace di contorno."""
        text = raw.replace(">", " ")
        # Normalizza tutti i separatori di riga in spazi.
        for ch in ("\r", "\n", "\t"):
            text = text.replace(ch, " ")
        return " ".join(text.split()).strip()

    # --- Parsing risposte OBD --------------------------------------------

    @staticmethod
    def parse_hex_bytes(response: str) -> List[int]:
        """Estrae i byte esadecimali da una risposta OBD.

        Gestisce risposte multiriga e multi-frame ISO-TP: unisce tutti i token
        esadecimali a 2 cifre. Filtra i marcatori non-dato (``SEARCHING``,
        ``OK``, numeri di frame ISO-TP tipo ``0:`` ``1:``).
        """
        tokens = response.upper().split()
        out: List[int] = []
        for tok in tokens:
            # Scarta indicatori di frame ISO-TP ("0:", "1:") e testo.
            if tok.endswith(":"):
                continue
            if len(tok) == 2 and tok[0] in _HEX and tok[1] in _HEX:
                out.append(int(tok, 16))
        return out

    def _strip_mode_header(self, data: List[int], mode: int, pid: Optional[int]) -> Optional[List[int]]:
        """Rimuove l'header di risposta ``4X [PID]`` e ritorna i soli byte dato.

        La risposta positiva a un comando in Mode ``N`` inizia con ``0x40+N``.
        Ritorna ``None`` se l'header atteso non e' presente (es. ``NO DATA``).
        """
        response_mode = 0x40 + mode
        for i, b in enumerate(data):
            if b == response_mode:
                start = i + 1
                if pid is not None:
                    # Il byte successivo deve essere il PID richiesto.
                    if start < len(data) and data[start] == pid:
                        return data[start + 1:]
                    continue
                return data[start:]
        return None

    # --- Query di alto livello -------------------------------------------

    def read_pid(self, pid: Pid) -> PidResult:
        """Interroga un singolo PID Mode 01 e ritorna il valore decodificato."""
        response = self.query(pid.command())
        raw_bytes = self.parse_hex_bytes(response)
        data = self._strip_mode_header(raw_bytes, mode=0x01, pid=pid.code)
        if data is None:
            return PidResult(pid, None, pid.unit)
        return pid.decode(data)

    def read_dtcs(self) -> List[DTC]:
        """Legge i codici di errore memorizzati (Mode 03)."""
        response = self.query("03")
        raw_bytes = self.parse_hex_bytes(response)
        data = self._strip_mode_header(raw_bytes, mode=0x03, pid=None)
        if data is None:
            return []
        return decode_dtc_bytes(data)

    def clear_dtcs(self) -> bool:
        """Cancella i codici di errore e spegne la spia MIL (Mode 04).

        Ritorna ``True`` se l'ELM327 risponde positivamente (``44``).
        """
        response = self.query("04")
        raw_bytes = self.parse_hex_bytes(response)
        return 0x44 in raw_bytes

    def voltage(self) -> Optional[float]:
        """Legge la tensione della batteria letta dall'ELM327 (comando ``ATRV``)."""
        response = self.query("ATRV")
        # Risposta tipo "12.4V".
        digits = "".join(c for c in response if c.isdigit() or c == ".")
        try:
            return float(digits) if digits else None
        except ValueError:
            return None

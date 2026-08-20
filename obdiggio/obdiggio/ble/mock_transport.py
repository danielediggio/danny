"""Simulatore ELM327 come :class:`Transport`.

Permette di sviluppare e testare l'intera applicazione (protocollo + UI) su
desktop, senza adattatore e senza veicolo. Simula un motore al minimo con
qualche valore che oscilla, risponde ai comandi AT e restituisce due DTC di
esempio.
"""

from __future__ import annotations

import math
import random
import time

from ..obd.transport import Transport


class MockELM327Transport(Transport):
    """Adattatore ELM327 simulato.

    Alla ``write`` di un comando produce immediatamente nel buffer la risposta
    corrispondente, terminata dal prompt ``>``.
    """

    def __init__(self, dtcs: bool = True) -> None:
        super().__init__()
        self._open = False
        self._t0 = time.monotonic()
        self._has_dtcs = dtcs

    @property
    def is_connected(self) -> bool:
        return self._open

    def open(self) -> None:
        self._open = True

    def close(self) -> None:
        self._open = False

    # --- Simulazione ------------------------------------------------------

    def write(self, data: bytes) -> None:
        if not self._open:
            raise RuntimeError("Trasporto simulato non aperto")
        command = data.decode("ascii", errors="ignore").strip().upper()
        response = self._respond(command)
        # Emetti la risposta come farebbe un vero device (con prompt finale).
        self._feed((response + "\r\r>").encode("ascii"))

    def _respond(self, command: str) -> str:
        if command.startswith("AT"):
            return self._respond_at(command)
        if command.startswith("01"):
            return self._respond_mode01(command)
        if command == "03":
            return self._respond_dtcs()
        if command == "04":
            return "44"
        return "NO DATA"

    def _respond_at(self, command: str) -> str:
        if command == "ATZ":
            return "ELM327 v1.5"
        if command == "ATRV":
            return f"{12.2 + random.uniform(-0.2, 0.4):.1f}V"
        return "OK"

    def _elapsed(self) -> float:
        return time.monotonic() - self._t0

    def _respond_mode01(self, command: str) -> str:
        try:
            pid = int(command[2:4], 16)
        except (ValueError, IndexError):
            return "NO DATA"
        t = self._elapsed()

        def frame(*data_bytes: int) -> str:
            parts = ["41", f"{pid:02X}"] + [f"{b:02X}" for b in data_bytes]
            return " ".join(parts)

        if pid == 0x04:   # carico motore
            return frame(int(30 + 20 * (0.5 + 0.5 * math.sin(t))))
        if pid == 0x05:   # temp refrigerante -> ~90°C (offset +40)
            return frame(min(215, 90 + 40 + int(3 * math.sin(t / 5))))
        if pid == 0x0C:   # RPM ~ 800 al minimo
            rpm = 800 + 300 * (0.5 + 0.5 * math.sin(t / 2))
            raw = int(rpm * 4)
            return frame((raw >> 8) & 0xFF, raw & 0xFF)
        if pid == 0x0D:   # velocita'
            return frame(0)
        if pid == 0x0F:   # temp aria aspirata
            return frame(30 + 40)
        if pid == 0x10:   # MAF
            raw = int((2.0 + 0.5 * math.sin(t)) * 100)
            return frame((raw >> 8) & 0xFF, raw & 0xFF)
        if pid == 0x11:   # farfalla
            return frame(int(255 * 0.15))
        if pid == 0x2F:   # livello carburante
            return frame(int(255 * 0.62))
        if pid == 0x42:   # tensione modulo
            raw = int(12300 + random.uniform(-100, 200))
            return frame((raw >> 8) & 0xFF, raw & 0xFF)
        if pid == 0x46:   # temp ambiente
            return frame(22 + 40)
        # PID non simulato.
        return "NO DATA"

    def _respond_dtcs(self) -> str:
        if not self._has_dtcs:
            return "43 00 00 00"
        # Mode 03: "43" seguito dalle coppie DTC. P0133 (01 33) e P0420 (04 20).
        return "43 01 33 04 20"

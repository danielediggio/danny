"""Astrazione di trasporto tra il livello ELM327 e il canale fisico.

Un ELM327 su BLE espone (tipicamente) una caratteristica di *notify* da cui
arrivano i byte e una caratteristica di *write* su cui inviare i comandi. I dati
in ricezione arrivano quindi in modo asincrono, spezzettati in piu' notifiche.

:class:`Transport` normalizza tutto questo: le sottoclassi chiamano
``_feed(data)`` quando ricevono byte, e il livello ELM327 usa
``read_until()`` per leggere in modo bloccante fino a un terminatore
(il prompt ``>`` dell'ELM327).
"""

from __future__ import annotations

import threading
import time
from abc import ABC, abstractmethod


class Transport(ABC):
    """Canale byte bidirezionale con buffer di ricezione thread-safe."""

    def __init__(self) -> None:
        self._buffer = bytearray()
        self._lock = threading.Lock()

    # --- API che le sottoclassi devono implementare -----------------------

    @abstractmethod
    def open(self) -> None:
        """Apre/attiva il canale (connessione BLE, ecc.)."""

    @abstractmethod
    def close(self) -> None:
        """Chiude il canale."""

    @abstractmethod
    def write(self, data: bytes) -> None:
        """Invia byte sul canale fisico."""

    @property
    @abstractmethod
    def is_connected(self) -> bool:
        ...

    # --- API usata dal livello ELM327 -------------------------------------

    def _feed(self, data: bytes) -> None:
        """Le sottoclassi chiamano questo metodo quando arrivano byte."""
        with self._lock:
            self._buffer.extend(data)

    def clear(self) -> None:
        """Svuota il buffer di ricezione."""
        with self._lock:
            self._buffer.clear()

    def read_until(self, terminator: bytes = b">", timeout: float = 5.0) -> bytes:
        """Legge dal buffer finche' non trova ``terminator`` o scade il timeout.

        Ritorna i byte accumulati (terminatore incluso se trovato). In timeout
        ritorna comunque quanto ricevuto finora.
        """
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            with self._lock:
                idx = self._buffer.find(terminator)
                if idx != -1:
                    end = idx + len(terminator)
                    out = bytes(self._buffer[:end])
                    del self._buffer[:end]
                    return out
            time.sleep(0.01)
        # Timeout: restituisci quanto abbiamo.
        with self._lock:
            out = bytes(self._buffer)
            self._buffer.clear()
        return out

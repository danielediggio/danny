"""Service applicativo: sceglie il trasporto, gestisce la connessione e il
polling periodico dei PID in un thread di background, notificando la UI via
callback.
"""

from __future__ import annotations

import logging
import threading
import time
from typing import Callable, Dict, List, Optional

from .ble import ble_transport
from .ble.mock_transport import MockELM327Transport
from .obd.dtc import DTC
from .obd.elm327 import ELM327
from .obd.pids import Pid, PidResult, all_pids
from .obd.transport import Transport

log = logging.getLogger("obdiggio.service")

# PID mostrati sul cruscotto live (nell'ordine).
DASHBOARD_PIDS = [0x0C, 0x0D, 0x05, 0x11, 0x04, 0x0F, 0x10, 0x42]


def make_transport(force_mock: bool = False) -> Transport:
    """Ritorna il trasporto adatto: BLE su Android, simulatore altrove."""
    if not force_mock and ble_transport.is_available():
        return ble_transport.BLETransport()
    log.info("BLE non disponibile: uso il simulatore ELM327")
    return MockELM327Transport()


class OBDService:
    """Coordina ELM327 e il loop di polling dei dati live."""

    def __init__(self, transport: Optional[Transport] = None, poll_interval: float = 0.25) -> None:
        self.transport = transport or make_transport()
        self.elm = ELM327(self.transport)
        self.poll_interval = poll_interval

        self._pids: List[Pid] = [p for c in DASHBOARD_PIDS
                                 for p in [next((x for x in all_pids() if x.code == c), None)]
                                 if p is not None]
        self._thread: Optional[threading.Thread] = None
        self._running = False
        self.latest: Dict[str, PidResult] = {}

        # Callback UI (thread di background -> vanno reindirizzate al main thread).
        self.on_update: Optional[Callable[[PidResult], None]] = None
        self.on_status: Optional[Callable[[str], None]] = None

    # --- stato ------------------------------------------------------------

    @property
    def is_mock(self) -> bool:
        return isinstance(self.transport, MockELM327Transport)

    @property
    def is_connected(self) -> bool:
        return self.elm.is_connected

    def _status(self, message: str) -> None:
        log.info(message)
        if self.on_status:
            self.on_status(message)

    # --- connessione ------------------------------------------------------

    def connect(self) -> None:
        self._status("Connessione all'adattatore…")
        self.elm.connect()
        self._status("Connesso — inizializzazione OK")

    def disconnect(self) -> None:
        self.stop_polling()
        self.elm.close()
        self._status("Disconnesso")

    # --- polling live -----------------------------------------------------

    def start_polling(self) -> None:
        if self._running:
            return
        self._running = True
        self._thread = threading.Thread(target=self._poll_loop, name="obd-poll", daemon=True)
        self._thread.start()

    def stop_polling(self) -> None:
        self._running = False
        thread = self._thread
        if thread and thread.is_alive():
            thread.join(timeout=2.0)
        self._thread = None

    def _poll_loop(self) -> None:
        while self._running:
            for pid in self._pids:
                if not self._running:
                    break
                try:
                    result = self.elm.read_pid(pid)
                except Exception:
                    log.exception("Errore lettura PID %s", pid.name)
                    continue
                self.latest[pid.key] = result
                if self.on_update:
                    self.on_update(result)
                time.sleep(self.poll_interval)

    # --- diagnostica on-demand -------------------------------------------

    def read_dtcs(self) -> List[DTC]:
        return self.elm.read_dtcs()

    def clear_dtcs(self) -> bool:
        return self.elm.clear_dtcs()

    def battery_voltage(self) -> Optional[float]:
        return self.elm.voltage()

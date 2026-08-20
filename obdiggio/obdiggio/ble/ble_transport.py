"""Trasporto BLE per Android basato sulla libreria ``able``.

Pensato per adattatori ELM327 su Bluetooth Low Energy come il **Vgate iCar Pro
BLE 4.0**. Questi device espongono un servizio GATT con:

* una caratteristica di **notify** da cui l'app riceve le risposte;
* una caratteristica di **write** su cui l'app invia i comandi.

Per l'iCar Pro BLE i valori tipici sono il servizio ``FFF0`` con notify ``FFF1``
e write ``FFF2``, ma non tutti i cloni usano gli stessi UUID: per robustezza il
trasporto prova prima gli UUID noti e, in mancanza, li **rileva automaticamente**
dalle proprieta' delle caratteristiche (NOTIFY per la lettura, WRITE per la
scrittura).

Questo modulo importa ``able``/``jnius`` solo su Android; su desktop l'import
fallisce in modo controllato e si usa invece il simulatore.
"""

from __future__ import annotations

import logging
import time
from typing import List, Optional

from ..obd.transport import Transport

log = logging.getLogger("obdiggio.ble")

# UUID candidati noti per adattatori ELM327 BLE (in ordine di preferenza).
# iCar Pro BLE 4.0 (Vgate): servizio FFF0, notify FFF1, write FFF2.
KNOWN_SERVICE_UUIDS = [
    "0000fff0-0000-1000-8000-00805f9b34fb",
    "0000ffe0-0000-1000-8000-00805f9b34fb",
    "e7810a71-73ae-499d-8c15-faa9aef0c3f2",  # alcuni cloni
]
KNOWN_NOTIFY_UUIDS = [
    "0000fff1-0000-1000-8000-00805f9b34fb",
    "0000ffe1-0000-1000-8000-00805f9b34fb",
]
KNOWN_WRITE_UUIDS = [
    "0000fff2-0000-1000-8000-00805f9b34fb",
    "0000ffe1-0000-1000-8000-00805f9b34fb",
]

# Proprieta' GATT (bitmask Android BluetoothGattCharacteristic).
PROPERTY_WRITE = 0x08
PROPERTY_WRITE_NO_RESPONSE = 0x04
PROPERTY_NOTIFY = 0x10
PROPERTY_INDICATE = 0x20


def is_available() -> bool:
    """True se la libreria BLE Android e' importabile (siamo su Android)."""
    try:
        import able  # noqa: F401
        return True
    except Exception:
        return False


class BLETransport(Transport):
    """Trasporto ELM327 su BLE tramite ``able`` (solo Android).

    Uso tipico::

        t = BLETransport()
        t.scan(timeout=8)                 # popola t.devices
        t.select(t.devices[0])            # scegli l'adattatore
        elm = ELM327(t); elm.connect()
    """

    def __init__(
        self,
        service_uuid: Optional[str] = None,
        notify_uuid: Optional[str] = None,
        write_uuid: Optional[str] = None,
        connect_timeout: float = 20.0,
    ) -> None:
        super().__init__()
        self._service_uuid = service_uuid
        self._notify_uuid = notify_uuid
        self._write_uuid = write_uuid
        self._connect_timeout = connect_timeout

        self.devices: List["BLEDevice"] = []
        self._selected = None

        self._ble = None           # istanza able.BluetoothDispatcher
        self._write_char = None
        self._notify_char = None
        self._services_ready = False
        self._gatt_connected = False

    # --- API pubblica -----------------------------------------------------

    def scan(self, timeout: float = 8.0) -> List["BLEDevice"]:
        """Esegue una scansione BLE e ritorna i device trovati."""
        self._ensure_dispatcher()
        self.devices = []
        self._ble.start_scan()
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            time.sleep(0.1)
        self._ble.stop_scan()
        return self.devices

    def select(self, device: "BLEDevice") -> None:
        """Seleziona il device da usare per la connessione."""
        self._selected = device

    @property
    def is_connected(self) -> bool:
        return self._gatt_connected and self._write_char is not None

    def open(self) -> None:
        """Connette al device selezionato e configura le caratteristiche."""
        if self._selected is None:
            raise RuntimeError("Nessun device selezionato: chiama scan()/select() prima")
        self._ensure_dispatcher()
        self._services_ready = False
        self._ble.connect_gatt(self._selected.raw)

        deadline = time.monotonic() + self._connect_timeout
        while time.monotonic() < deadline and not self._services_ready:
            time.sleep(0.1)
        if not self._services_ready:
            raise RuntimeError("Timeout: servizi GATT non individuati")
        if self._notify_char is not None:
            self._ble.enable_notifications(self._notify_char)
        self._gatt_connected = True

    def close(self) -> None:
        self._gatt_connected = False
        if self._ble is not None:
            try:
                self._ble.close_gatt()
            except Exception:  # pragma: no cover - dipende dallo stato Android
                log.exception("Errore chiusura GATT")

    def write(self, data: bytes) -> None:
        if self._write_char is None or self._ble is None:
            raise RuntimeError("Caratteristica di scrittura non disponibile")
        # Alcuni adattatori accettano max ~20 byte per pacchetto: i comandi
        # ELM327 sono corti, ma spezziamo comunque per sicurezza.
        for chunk in _chunks(data, 20):
            self._ble.write_characteristic(self._write_char, chunk)

    # --- Integrazione con able -------------------------------------------

    def _ensure_dispatcher(self) -> None:
        if self._ble is not None:
            return
        from able import BluetoothDispatcher  # import Android-only

        transport = self

        class _Dispatcher(BluetoothDispatcher):
            def on_device(self, device, rssi, advertisement):
                name = None
                try:
                    name = device.getName()
                except Exception:
                    pass
                dev = BLEDevice(name or "?", _safe_address(device), device, rssi)
                if not any(d.address == dev.address for d in transport.devices):
                    transport.devices.append(dev)

            def on_connection_state_change(self, status, state):
                if state == 0:  # STATE_DISCONNECTED
                    transport._gatt_connected = False
                else:
                    # connesso: avvia la scoperta servizi
                    self.discover_services()

            def on_services(self, status, services):
                transport._bind_characteristics(services)

            def on_characteristic_changed(self, characteristic):
                try:
                    value = bytes(characteristic.getValue())
                except Exception:
                    value = b""
                if value:
                    transport._feed(value)

        self._ble = _Dispatcher()

    def _bind_characteristics(self, services) -> None:
        """Individua le caratteristiche notify/write dai servizi scoperti."""
        notify = write = None

        # 1) prova gli UUID espliciti / noti.
        wanted_service = _lower(self._service_uuid)
        for service in _iter_services(services):
            suuid = _lower(_uuid_of(service))
            if wanted_service and suuid != wanted_service:
                continue
            if not wanted_service and suuid not in KNOWN_SERVICE_UUIDS:
                # non e' un servizio noto: lo consideriamo comunque per l'auto-detect.
                pass
            for ch in _iter_characteristics(service):
                cuuid = _lower(_uuid_of(ch))
                props = _properties_of(ch)
                if notify is None and (
                    cuuid == _lower(self._notify_uuid)
                    or cuuid in KNOWN_NOTIFY_UUIDS
                    or props & (PROPERTY_NOTIFY | PROPERTY_INDICATE)
                ):
                    notify = ch
                if write is None and (
                    cuuid == _lower(self._write_uuid)
                    or cuuid in KNOWN_WRITE_UUIDS
                    or props & (PROPERTY_WRITE | PROPERTY_WRITE_NO_RESPONSE)
                ):
                    write = ch

        self._notify_char = notify
        self._write_char = write
        self._services_ready = write is not None
        if write is None:
            log.warning("Nessuna caratteristica di scrittura individuata")


class BLEDevice:
    """Device BLE individuato dalla scansione."""

    def __init__(self, name: str, address: str, raw, rssi: int = 0) -> None:
        self.name = name
        self.address = address
        self.raw = raw
        self.rssi = rssi

    def looks_like_obd(self) -> bool:
        """Euristica sul nome per riconoscere adattatori OBD."""
        n = (self.name or "").upper()
        return any(k in n for k in ("OBD", "ELM", "ICAR", "VGATE", "VIECAR", "VLINK"))

    def __str__(self) -> str:
        return f"{self.name} [{self.address}] {self.rssi} dBm"


# --- utility ----------------------------------------------------------------

def _chunks(data: bytes, size: int):
    for i in range(0, len(data), size):
        yield data[i:i + size]


def _lower(value: Optional[str]) -> Optional[str]:
    return value.lower() if isinstance(value, str) else value


def _safe_address(device) -> str:
    try:
        return device.getAddress()
    except Exception:
        return "?"


def _iter_services(services):
    """``services`` puo' essere una lista Java o Python: normalizza l'iterazione."""
    try:
        return list(services)
    except TypeError:
        return services


def _iter_characteristics(service):
    try:
        return list(service.getCharacteristics())
    except Exception:
        return []


def _uuid_of(obj) -> str:
    try:
        return str(obj.getUuid().toString())
    except Exception:
        return ""


def _properties_of(ch) -> int:
    try:
        return int(ch.getProperties())
    except Exception:
        return 0

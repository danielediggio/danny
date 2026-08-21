"""Definizioni PID OBD-II (Mode 01) e relativi decoder.

Ogni :class:`Pid` sa costruire il comando ELM327 da inviare e decodificare i
byte di risposta (gia' ripuliti dall'header ``41 XX``) in un valore fisico con
unita' di misura.

I decoder seguono le formule standard SAE J1979. I byte sono passati come lista
di interi 0-255, nell'ordine A, B, C, D (come da documentazione OBD-II).
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Callable, Dict, List, Optional


@dataclass(frozen=True)
class PidResult:
    """Valore decodificato di un PID."""

    pid: "Pid"
    value: Optional[float]
    unit: str

    @property
    def name(self) -> str:
        return self.pid.name

    def __str__(self) -> str:
        if self.value is None:
            return f"{self.name}: n/d"
        # Interi mostrati senza decimali, altrimenti una cifra.
        if float(self.value).is_integer():
            shown = str(int(self.value))
        else:
            shown = f"{self.value:.1f}"
        return f"{self.name}: {shown} {self.unit}".rstrip()


@dataclass(frozen=True)
class Pid:
    """Definizione di un singolo PID Mode 01."""

    code: int                      # es. 0x0C
    name: str                      # nome leggibile
    unit: str                      # unita' di misura
    num_bytes: int                 # byte dati attesi nella risposta
    decoder: Callable[[List[int]], Optional[float]]
    min_value: float = 0.0
    max_value: float = 100.0

    @property
    def key(self) -> str:
        """Identificatore breve (es. ``rpm``) derivato dal nome."""
        return self.name.lower().replace(" ", "_")

    def command(self) -> str:
        """Comando ELM327 (Mode 01 + PID), es. ``010C``."""
        return f"01{self.code:02X}"

    def decode(self, data: List[int]) -> PidResult:
        """Decodifica i byte dati in un :class:`PidResult`."""
        if len(data) < self.num_bytes:
            return PidResult(self, None, self.unit)
        try:
            value = self.decoder(data)
        except (IndexError, ValueError, ZeroDivisionError):
            value = None
        return PidResult(self, value, self.unit)


# --- Decoder elementari (formule SAE J1979) -------------------------------

def _percent(d: List[int]) -> float:
    """0-100 % da un singolo byte."""
    return d[0] * 100.0 / 255.0


def _temp(d: List[int]) -> float:
    """Temperatura in °C (offset -40)."""
    return d[0] - 40.0


def _rpm(d: List[int]) -> float:
    """Giri motore: ((A*256)+B)/4."""
    return ((d[0] * 256) + d[1]) / 4.0


def _speed(d: List[int]) -> float:
    """Velocita' in km/h (byte diretto)."""
    return float(d[0])


def _timing_advance(d: List[int]) -> float:
    """Anticipo accensione in gradi: A/2 - 64."""
    return d[0] / 2.0 - 64.0


def _maf(d: List[int]) -> float:
    """Portata aria (MAF) g/s: ((A*256)+B)/100."""
    return ((d[0] * 256) + d[1]) / 100.0


def _intake_pressure(d: List[int]) -> float:
    """Pressione collettore aspirazione kPa (byte diretto)."""
    return float(d[0])


def _fuel_level(d: List[int]) -> float:
    return d[0] * 100.0 / 255.0


def _control_module_voltage(d: List[int]) -> float:
    """Tensione modulo di controllo V: ((A*256)+B)/1000."""
    return ((d[0] * 256) + d[1]) / 1000.0


def _engine_load(d: List[int]) -> float:
    return d[0] * 100.0 / 255.0


def _fuel_trim(d: List[int]) -> float:
    """Correzione carburante %: A/1.28 - 100."""
    return d[0] / 1.28 - 100.0


def _run_time(d: List[int]) -> float:
    """Tempo dall'avvio motore in secondi: A*256 + B."""
    return float(d[0] * 256 + d[1])


def _distance(d: List[int]) -> float:
    """Distanza in km: A*256 + B."""
    return float(d[0] * 256 + d[1])


def _ambient_temp(d: List[int]) -> float:
    return d[0] - 40.0


# --- Catalogo PID ----------------------------------------------------------

_PID_LIST: List[Pid] = [
    Pid(0x04, "Carico motore", "%", 1, _engine_load, 0, 100),
    Pid(0x05, "Temp refrigerante", "°C", 1, _temp, -40, 215),
    Pid(0x06, "Fuel trim breve B1", "%", 1, _fuel_trim, -100, 99),
    Pid(0x07, "Fuel trim lungo B1", "%", 1, _fuel_trim, -100, 99),
    Pid(0x0A, "Pressione carburante", "kPa", 1, lambda d: float(d[0] * 3), 0, 765),
    Pid(0x0B, "Pressione aspirazione", "kPa", 1, _intake_pressure, 0, 255),
    Pid(0x0C, "RPM", "rpm", 2, _rpm, 0, 8000),
    Pid(0x0D, "Velocita'", "km/h", 1, _speed, 0, 255),
    Pid(0x0E, "Anticipo accensione", "°", 1, _timing_advance, -64, 63),
    Pid(0x0F, "Temp aria aspirata", "°C", 1, _temp, -40, 215),
    Pid(0x10, "MAF", "g/s", 2, _maf, 0, 655),
    Pid(0x11, "Posizione farfalla", "%", 1, _percent, 0, 100),
    Pid(0x1F, "Tempo motore acceso", "s", 2, _run_time, 0, 65535),
    Pid(0x21, "Distanza con MIL", "km", 2, _distance, 0, 65535),
    Pid(0x2F, "Livello carburante", "%", 1, _fuel_level, 0, 100),
    Pid(0x31, "Distanza da azzeramento", "km", 2, _distance, 0, 65535),
    Pid(0x42, "Tensione modulo", "V", 2, _control_module_voltage, 0, 65),
    Pid(0x43, "Carico assoluto", "%", 2, lambda d: (d[0] * 256 + d[1]) * 100.0 / 255.0, 0, 25700),
    Pid(0x46, "Temp ambiente", "°C", 1, _ambient_temp, -40, 215),
    Pid(0x5C, "Temp olio motore", "°C", 1, _temp, -40, 215),
]

# Indicizzato per codice PID per lookup veloce.
PIDS: Dict[int, Pid] = {p.code: p for p in _PID_LIST}
PIDS_BY_KEY: Dict[str, Pid] = {p.key: p for p in _PID_LIST}


def get_pid(code: int) -> Optional[Pid]:
    return PIDS.get(code)


def all_pids() -> List[Pid]:
    return list(_PID_LIST)

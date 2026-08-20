# obdiggio

App **Android** in **Python (Kivy)** per la diagnostica **OBD-II** di un veicolo
tramite adattatore **Bluetooth Low Energy** — pensata per il **Vgate iCar Pro
BLE 4.0** (ELM327 su BLE), ma compatibile con la maggior parte dei cloni ELM327
BLE grazie all'auto-rilevamento delle caratteristiche GATT.

## Cosa fa

- **Cruscotto live**: RPM, velocità, temperatura refrigerante, posizione
  farfalla, carico motore, temperatura aria aspirata, MAF, tensione modulo.
- **Codici errore (DTC)**: lettura (Mode 03) e cancellazione con spegnimento
  spia MIL (Mode 04), con descrizioni in italiano dei codici più comuni.
- **Modalità demo**: un simulatore ELM327 integrato permette di provare tutta
  l'app **senza adattatore e senza auto**, anche da desktop.

## Architettura

Il codice è diviso in livelli, così la logica OBD è testabile senza hardware:

```
obdiggio/
├── main.py                     # app Kivy (UI + navigazione)
├── obdiggio/
│   ├── service.py              # connessione + polling in background
│   ├── obd/
│   │   ├── transport.py        # canale byte astratto (buffer + read_until)
│   │   ├── elm327.py           # driver ELM327: init, comandi, parsing
│   │   ├── pids.py             # PID Mode 01 + decoder (formule SAE J1979)
│   │   └── dtc.py              # decodifica/descrizione DTC
│   └── ble/
│       ├── ble_transport.py    # BLE reale su Android (libreria `able`)
│       └── mock_transport.py   # simulatore ELM327 per desktop/test
└── tests/                      # test dei decoder e del driver
```

Il **`Transport`** è l'astrazione chiave: l'`ELM327` parla con un canale byte
generico. Su Android quel canale è il BLE (`BLETransport`), su desktop è il
simulatore (`MockELM327Transport`) — stesso codice OBD in entrambi i casi.

## Provare su desktop (modalità simulatore)

```bash
pip install -r requirements.txt
python main.py           # si apre l'app; premi "Modalità demo (simulatore)"
```

## Eseguire i test

```bash
pip install pytest
python -m pytest
```

## Compilare l'APK per Android

Serve [Buildozer](https://buildozer.readthedocs.io) (consigliato su Linux/WSL):

```bash
pip install buildozer cython
buildozer -v android debug
# l'APK finisce in bin/obdiggio-0.1.0-*-debug.apk
```

Installa l'APK sul telefono e assicurati di aver dato i permessi Bluetooth e
posizione (necessari su Android per la scansione BLE).

## Uso con il Vgate iCar Pro BLE 4.0

1. Inserisci l'adattatore nella presa OBD-II del veicolo.
2. Metti il quadro su "acceso" (o avvia il motore).
3. Attiva Bluetooth sul telefono (**non** serve accoppiarlo dalle impostazioni:
   l'app lo trova via scansione BLE).
4. Apri obdiggio → **Cerca e connetti**.

### UUID BLE

Gli iCar Pro BLE usano tipicamente il servizio `FFF0` con notifiche su `FFF1` e
scrittura su `FFF2`. L'app prova questi valori noti e, se non li trova, rileva
automaticamente le caratteristiche dalle loro proprietà GATT (NOTIFY per la
lettura, WRITE per la scrittura). Gli UUID sono comunque configurabili in
`obdiggio/ble/ble_transport.py`.

## Note e limiti

- La comunicazione BLE reale è verificabile solo su Android con l'adattatore: la
  logica OBD e il parsing sono coperti dai test; il layer `able` è documentato
  ma va provato sul dispositivo.
- L'elenco DTC con descrizione copre i codici generici più frequenti; gli altri
  sono decodificati correttamente ma mostrano una descrizione generica.
- Progetto a scopo diagnostico: non modifica centraline né esegue programmazioni.

## Roadmap

- Grafici storici dei parametri live.
- Lettura VIN (Mode 09) e stato monitor di prontezza (readiness).
- Salvataggio/log delle sessioni.
- Selezione manuale del device dalla lista scansionata.

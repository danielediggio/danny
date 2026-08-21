# obdiggio-android

Versione **Kotlin nativa** dell'app OBD-II per il **Vgate iCar Pro BLE 4.0**
(ELM327 su Bluetooth LE). UI in Jetpack Compose, BLE con le API native di
Android, concorrenza con le coroutine.

## Struttura (multi-modulo Gradle)

- **`core/`** — libreria **Kotlin/JVM pura**, riusabile in altri progetti:
  - `obd/Pids.kt` — PID standard e decoder (formule SAE J1979)
  - `obd/Dtc.kt` — decodifica codici errore con descrizioni
  - `obd/Elm327.kt` — driver ELM327 (init AT, Mode 01/03/04, parsing)
  - `obd/Transport.kt` — astrazione di trasporto con buffer thread-safe
  - `obd/MockTransport.kt` — simulatore ELM327 per test senza hardware
  - `src/test/…` — test JUnit della logica (girano su qualsiasi JVM)
- **`app/`** — app Android:
  - `ble/BleTransport.kt` — trasporto BLE nativo con auto-rilevamento
    delle caratteristiche notify/write (UUID noti iCar Pro + fallback)
  - `ObdViewModel.kt` — connessione e polling dei dati live (coroutine + StateFlow)
  - `MainActivity.kt` + `ui/` — UI Compose: cruscotto, errori, connessione

## Build

### In cloud (consigliato)
Workflow **Build Android APK** (GitHub Actions): `Run workflow` sul branch,
oppure push. L'APK esce come artifact e come **Release** (link diretto per il
telefono).

### In locale
Richiede Android SDK (platform 34). Poi:

```bash
./gradlew :core:test        # test della logica
./gradlew :app:assembleDebug
# APK in app/build/outputs/apk/debug/app-debug.apk
```

Il progetto si apre anche direttamente in **Android Studio**.

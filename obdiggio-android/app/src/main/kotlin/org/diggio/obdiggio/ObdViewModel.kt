package org.diggio.obdiggio

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.diggio.obdiggio.ble.BleTransport
import org.diggio.obdiggio.core.obd.Dtc
import org.diggio.obdiggio.core.obd.Elm327
import org.diggio.obdiggio.core.obd.MockTransport
import org.diggio.obdiggio.core.obd.Pid
import org.diggio.obdiggio.core.obd.PidResult
import org.diggio.obdiggio.core.obd.Pids
import org.diggio.obdiggio.core.obd.Transport

/** Ordine di preferenza dei PID sul cruscotto (i primi sono le lancette hero). */
private val CANDIDATE_CODES = listOf(
    0x0C, 0x0D, 0x0B, 0x33,          // RPM, velocità, MAP, barometrica (per il boost)
    0x05, 0x5C, 0x0F, 0x10, 0x04,    // temp refr., temp olio, IAT, MAF, carico
    0x11, 0x49, 0x2C, 0x2D, 0x23,    // farfalla, pedale, EGR comandata/errore, rail
    0x2F, 0x42, 0x46, 0x1F, 0x21, 0x5E,
)

/** Fallback se il rilevamento dei PID supportati non riesce. */
private val DEFAULT_CODES = listOf(0x0C, 0x0D, 0x05, 0x11, 0x04, 0x0F, 0x10, 0x42)

/** PID letti dallo snapshot freeze frame. */
private val FREEZE_CODES = listOf(0x0C, 0x0D, 0x04, 0x05, 0x0F, 0x10, 0x11, 0x0B, 0x0E, 0x33, 0x1F)

/** Un gruppo di DTC con la sua etichetta (Memorizzati / In sospeso / Permanenti). */
data class DtcGroup(val label: String, val codes: List<Dtc>)

/** Snapshot dei parametri congelati al momento del guasto (Mode 02). */
data class FreezeFrame(val dtc: Dtc?, val values: List<PidResult>)

data class UiState(
    val connecting: Boolean = false,
    val connected: Boolean = false,
    val usingMock: Boolean = false,
    val status: String = "Non connesso",
    val values: Map<String, PidResult> = emptyMap(),
    val boostKpa: Double? = null,       // sovralimentazione turbo = MAP - barometrica
    val dtcGroups: List<DtcGroup>? = null,
    val dtcBusy: Boolean = false,
    val freeze: FreezeFrame? = null,
    val freezeBusy: Boolean = false,
    val message: String? = null,
)

class ObdViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    /** PID effettivamente mostrati: impostato alla connessione in base al veicolo. */
    var dashboardPids: List<Pid> = DEFAULT_CODES.mapNotNull { Pids[it] }
        private set

    private var elm: Elm327? = null
    private var transport: Transport? = null
    private var pollJob: Job? = null

    fun connect(useMock: Boolean) {
        if (_state.value.connecting || _state.value.connected) return
        _state.update { it.copy(connecting = true, usingMock = useMock, status = "Connessione…", message = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val t: Transport = if (useMock) MockTransport() else {
                    val ble = BleTransport(getApplication())
                    setStatus("Scansione BLE…")
                    val devices = ble.scan(8_000)
                    val obd = devices.firstOrNull { it.looksLikeObd() }
                        ?: throw IllegalStateException("Nessun adattatore OBD trovato")
                    ble.select(obd)
                    ble
                }
                transport = t
                val e = Elm327(t)
                e.connect()
                elm = e
                // Rileva i PID supportati dal veicolo e costruisci il cruscotto.
                val supported = try { e.supportedPids() } catch (ex: Exception) { emptySet() }
                dashboardPids = CANDIDATE_CODES.filter { it in supported }.mapNotNull { Pids[it] }
                    .ifEmpty { DEFAULT_CODES.mapNotNull { Pids[it] } }
                _state.update {
                    it.copy(connecting = false, connected = true, status = "Connesso ✓",
                        message = "Veicolo connesso — ${dashboardPids.size} parametri disponibili")
                }
                startPolling()
            } catch (ex: Exception) {
                elm = null
                transport = null
                _state.update {
                    it.copy(connecting = false, connected = false, status = "Connessione fallita",
                        message = ex.message)
                }
            }
        }
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                val e = elm ?: break
                for (pid in dashboardPids) {
                    val result = try { e.readPid(pid) } catch (ex: Exception) { continue }
                    _state.update { st ->
                        val values = st.values + (pid.key to result)
                        st.copy(values = values, boostKpa = computeBoost(values))
                    }
                    delay(150)
                }
            }
        }
    }

    /** Sovralimentazione turbo (kPa relativi) = pressione collettore - barometrica. */
    private fun computeBoost(values: Map<String, PidResult>): Double? {
        val map = values[Pids[0x0B]?.key]?.value
        val baro = values[Pids[0x33]?.key]?.value ?: 101.3
        return if (map != null) (map - baro).coerceAtLeast(0.0) else null
    }

    fun readDtcs() {
        val e = elm ?: return
        _state.update { it.copy(dtcBusy = true, message = null) }
        viewModelScope.launch(Dispatchers.IO) {
            val groups = readAllDtcGroups(e)
            val total = groups.sumOf { it.codes.size }
            _state.update {
                it.copy(
                    dtcGroups = groups,
                    dtcBusy = false,
                    message = if (total == 0) "Nessun codice presente ✓"
                    else "Trovati $total codici (memorizzati/in sospeso/permanenti).",
                )
            }
        }
    }

    fun clearDtcs() {
        val e = elm ?: return
        _state.update { it.copy(dtcBusy = true, message = null) }
        viewModelScope.launch(Dispatchers.IO) {
            val ok = try { e.clearDtcs() } catch (ex: Exception) { false }
            if (ok) {
                // Rileggi per confermare l'effettiva cancellazione.
                val groups = readAllDtcGroups(e)
                val permanent = groups.firstOrNull { it.label.startsWith("Permanenti") }?.codes ?: emptyList()
                _state.update {
                    it.copy(
                        dtcBusy = false,
                        dtcGroups = groups,
                        message = if (permanent.isEmpty())
                            "Errori cancellati, spia MIL spenta ✓"
                        else "Cancellati i codici cancellabili. I permanenti restano finché il guasto non è risolto e il ciclo di guida non li azzera.",
                    )
                }
            } else {
                _state.update {
                    it.copy(
                        dtcBusy = false,
                        message = "Cancellazione rifiutata dall'ECU. Falla a QUADRO ACCESO e MOTORE SPENTO, poi riprova.",
                    )
                }
            }
        }
    }

    fun readFreezeFrame() {
        val e = elm ?: return
        _state.update { it.copy(freezeBusy = true, message = null) }
        viewModelScope.launch(Dispatchers.IO) {
            val dtc = try { e.readFreezeFrameDtc() } catch (ex: Exception) { null }
            val values = FREEZE_CODES.mapNotNull { Pids[it] }.mapNotNull { pid ->
                val r = try { e.readFreezeFramePid(pid) } catch (ex: Exception) { null }
                if (r?.value != null) r else null
            }
            _state.update {
                it.copy(
                    freezeBusy = false,
                    freeze = FreezeFrame(dtc, values),
                    message = if (dtc == null && values.isEmpty())
                        "Nessun freeze frame memorizzato (nessun errore congelato)." else null,
                )
            }
        }
    }

    private fun readAllDtcGroups(e: Elm327): List<DtcGroup> {
        fun safe(block: () -> List<Dtc>) = try { block() } catch (ex: Exception) { emptyList() }
        return listOf(
            DtcGroup("Memorizzati", safe { e.readDtcs() }),
            DtcGroup("In sospeso", safe { e.readPendingDtcs() }),
            DtcGroup("Permanenti", safe { e.readPermanentDtcs() }),
        ).filter { it.codes.isNotEmpty() }
    }

    fun disconnect() {
        pollJob?.cancel()
        viewModelScope.launch(Dispatchers.IO) {
            try { elm?.close() } catch (_: Exception) {}
            elm = null
            transport = null
            _state.update { UiState() }
        }
    }

    private suspend fun setStatus(s: String) = withContext(Dispatchers.Main) {
        _state.update { it.copy(status = s) }
    }

    override fun onCleared() {
        pollJob?.cancel()
        try { elm?.close() } catch (_: Exception) {}
    }
}

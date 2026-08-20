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
import org.diggio.obdiggio.core.obd.PidResult
import org.diggio.obdiggio.core.obd.Pids
import org.diggio.obdiggio.core.obd.Transport

/** PID mostrati sul cruscotto, nell'ordine. */
private val DASHBOARD_CODES = listOf(0x0C, 0x0D, 0x05, 0x11, 0x04, 0x0F, 0x10, 0x42)

data class UiState(
    val connecting: Boolean = false,
    val connected: Boolean = false,
    val usingMock: Boolean = false,
    val status: String = "Non connesso",
    val values: Map<String, PidResult> = emptyMap(),
    val dtcs: List<Dtc>? = null,
    val dtcBusy: Boolean = false,
    val message: String? = null,
)

class ObdViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    val dashboardPids = DASHBOARD_CODES.mapNotNull { Pids[it] }

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
                val probe = try { e.probeSupportedPids() } catch (ex: Exception) { "errore: ${ex.message}" }
                _state.update {
                    it.copy(connecting = false, connected = true, status = "Connesso ✓",
                        message = "Diagnostica 0100 → \"$probe\"")
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
                    _state.update { it.copy(values = it.values + (pid.key to result)) }
                    delay(200)
                }
            }
        }
    }

    fun readDtcs() {
        val e = elm ?: return
        _state.update { it.copy(dtcBusy = true) }
        viewModelScope.launch(Dispatchers.IO) {
            val dtcs = try { e.readDtcs() } catch (ex: Exception) { emptyList() }
            _state.update { it.copy(dtcs = dtcs, dtcBusy = false) }
        }
    }

    fun clearDtcs() {
        val e = elm ?: return
        _state.update { it.copy(dtcBusy = true) }
        viewModelScope.launch(Dispatchers.IO) {
            val ok = try { e.clearDtcs() } catch (ex: Exception) { false }
            _state.update {
                it.copy(dtcBusy = false, dtcs = if (ok) emptyList() else it.dtcs,
                    message = if (ok) "Errori cancellati, spia MIL spenta ✓" else "Cancellazione non confermata")
            }
        }
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

package org.diggio.obdiggio

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.diggio.obdiggio.core.obd.Pid
import org.diggio.obdiggio.core.obd.PidResult
import org.diggio.obdiggio.ui.ObdiggioTheme

class MainActivity : ComponentActivity() {

    private val viewModel: ObdViewModel by viewModels()

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            // Anche se non tutti concessi, tentiamo: connect() riporterà l'errore.
            viewModel.connect(useMock = false)
        }

    private fun requestBleAndConnect() {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        permissionLauncher.launch(perms)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ObdiggioTheme {
                Surface(Modifier.fillMaxSize()) {
                    AppRoot(
                        viewModel = viewModel,
                        onScanConnect = ::requestBleAndConnect,
                    )
                }
            }
        }
    }
}

private enum class Tab(val label: String) { DASHBOARD("Cruscotto"), DTC("Errori"), CONNECT("Connessione") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRoot(viewModel: ObdViewModel, onScanConnect: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(Tab.CONNECT) }

    // Passa al cruscotto appena connesso.
    LaunchedEffect(state.connected) { if (state.connected) tab = Tab.DASHBOARD }

    Scaffold(
        topBar = { TopAppBar(title = { Text("obdiggio · ${tab.label}") }) },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = {},
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                Tab.DASHBOARD -> DashboardScreen(viewModel, state)
                Tab.DTC -> DtcScreen(viewModel, state)
                Tab.CONNECT -> ConnectScreen(state, onScanConnect, onMock = { viewModel.connect(useMock = true) })
            }
        }
    }
}

@Composable
private fun DashboardScreen(viewModel: ObdViewModel, state: UiState) {
    if (!state.connected) {
        CenterHint("Non connesso. Vai su \"Connessione\".")
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize().padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(viewModel.dashboardPids) { pid ->
            GaugeTile(pid, state.values[pid.key])
        }
    }
}

@Composable
private fun GaugeTile(pid: Pid, result: PidResult?) {
    Card(Modifier.fillMaxWidth().height(112.dp)) {
        Column(
            Modifier.fillMaxSize().padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(pid.name, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                textAlign = TextAlign.Center)
            val shown = result?.value?.let {
                if (it % 1.0 == 0.0) it.toInt().toString() else String.format("%.1f", it)
            } ?: "—"
            Text(shown, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Text(pid.unit, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun DtcScreen(viewModel: ObdViewModel, state: UiState) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { viewModel.readDtcs() }, enabled = state.connected && !state.dtcBusy,
                modifier = Modifier.weight(1f)) { Text("Leggi errori") }
            Button(
                onClick = { viewModel.clearDtcs() },
                enabled = state.connected && !state.dtcBusy,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.weight(1f),
            ) { Text("Cancella errori") }
        }
        if (!state.connected) CenterHint("Prima connettiti a un adattatore.")
        when {
            state.dtcBusy -> Text("Operazione in corso…")
            state.dtcs == null -> Text("Premi \"Leggi errori\".")
            state.dtcs.isEmpty() -> Text("Nessun codice di errore memorizzato ✓",
                color = MaterialTheme.colorScheme.secondary)
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.dtcs) { dtc ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(dtc.code, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(dtc.description, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectScreen(state: UiState, onScanConnect: () -> Unit, onMock: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(state.status, style = MaterialTheme.typography.titleMedium)
        if (state.connecting) LinearProgressIndicator(Modifier.fillMaxWidth())

        Button(
            onClick = onScanConnect,
            enabled = !state.connecting && !state.connected,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
        ) { Text("Cerca e connetti (Vgate iCar Pro)") }

        OutlinedButton(
            onClick = onMock,
            enabled = !state.connecting && !state.connected,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("Modalità demo (simulatore)") }

        state.message?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        Text(
            "Accendi il quadro, inserisci il Vgate iCar Pro nella presa OBD e " +
                "attiva il Bluetooth, poi premi \"Cerca e connetti\".",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun CenterHint(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
    }
}

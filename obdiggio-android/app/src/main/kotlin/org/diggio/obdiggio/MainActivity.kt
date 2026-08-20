package org.diggio.obdiggio

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.diggio.obdiggio.core.obd.Pid
import org.diggio.obdiggio.core.obd.PidResult
import org.diggio.obdiggio.core.obd.Pids
import org.diggio.obdiggio.ui.Neon
import org.diggio.obdiggio.ui.NeonBar
import org.diggio.obdiggio.ui.NeonGauge
import org.diggio.obdiggio.ui.ObdiggioTheme

class MainActivity : ComponentActivity() {

    private val viewModel: ObdViewModel by viewModels()

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
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
                AppRoot(viewModel, onScanConnect = ::requestBleAndConnect)
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
    LaunchedEffect(state.connected) { if (state.connected) tab = Tab.DASHBOARD }

    val bg = Brush.verticalGradient(listOf(Neon.BgTop, Neon.Bg, Color(0xFF05070C)))

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("OBD", color = Neon.Cyan, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
                        Text("IGGIO", color = Neon.Magenta, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
                        Text("  ·  ${tab.label.uppercase()}", color = Neon.Muted, fontSize = 13.sp, letterSpacing = 2.sp)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Neon.Panel, tonalElevation = 0.dp) {
                Tab.entries.forEach { t ->
                    val selected = tab == t
                    NavigationBarItem(
                        selected = selected,
                        onClick = { tab = t },
                        icon = {},
                        label = { Text(t.label, letterSpacing = 1.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedTextColor = Neon.Cyan,
                            unselectedTextColor = Neon.Muted,
                            indicatorColor = Color.Transparent,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(
            Modifier.fillMaxSize().background(bg).padding(padding),
        ) {
            when (tab) {
                Tab.DASHBOARD -> DashboardScreen(viewModel, state)
                Tab.DTC -> DtcScreen(viewModel, state)
                Tab.CONNECT -> ConnectScreen(state, onScanConnect) { viewModel.connect(useMock = true) }
            }
        }
    }
}

@Composable
private fun DashboardScreen(viewModel: ObdViewModel, state: UiState) {
    if (!state.connected) {
        CenterHint("Non connesso.\nVai su \"Connessione\".")
        return
    }
    val rpm = state.values[Pids[0x0C]?.key]?.value
    val speed = state.values[Pids[0x0D]?.key]?.value
    val heroKeys = setOfNotNull(Pids[0x0C]?.key, Pids[0x0D]?.key, Pids[0x0B]?.key, Pids[0x33]?.key)
    val tiles = viewModel.dashboardPids.filter { it.key !in heroKeys }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                NeonGauge("Giri", rpm, 5000.0, "rpm", Neon.Cyan, Modifier.weight(1f))
                NeonGauge("Velocità", speed, 220.0, "km/h", Neon.Magenta, Modifier.weight(1f))
            }
        }
        if (state.boostKpa != null) {
            item {
                NeonPanel {
                    NeonBar("Turbo / Sovralimentazione", state.boostKpa / 100.0, 2.0, "bar", Neon.Lime)
                }
            }
        }
        items(tiles.chunked(2)) { rowPids ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                rowPids.forEach { pid ->
                    NeonTile(pid, state.values[pid.key], Modifier.weight(1f))
                }
                if (rowPids.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun NeonTile(pid: Pid, result: PidResult?, modifier: Modifier = Modifier) {
    val shown = result?.value?.let {
        if (it % 1.0 == 0.0) it.toInt().toString() else "%.1f".format(it)
    } ?: "—"
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Neon.Panel)
            .border(1.dp, Neon.PanelBorder, RoundedCornerShape(14.dp))
            .padding(vertical = 14.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(pid.name.uppercase(), color = Neon.Muted, fontSize = 10.sp, letterSpacing = 1.sp,
            textAlign = TextAlign.Center, maxLines = 1)
        Text(shown, color = Neon.Text, fontSize = 26.sp, fontWeight = FontWeight.Black)
        Text(pid.unit, color = Neon.Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun NeonPanel(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Neon.Panel)
            .border(1.dp, Neon.PanelBorder, RoundedCornerShape(14.dp))
            .padding(14.dp),
        content = content,
    )
}

@Composable
private fun NeonButton(text: String, color: Color, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color(0xFF04121A),
            disabledContainerColor = Neon.PanelBorder, disabledContentColor = Neon.Muted),
    ) { Text(text.uppercase(), fontWeight = FontWeight.Black, letterSpacing = 1.sp) }
}

@Composable
private fun DtcScreen(viewModel: ObdViewModel, state: UiState) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NeonButton("Leggi errori", Neon.Cyan, state.connected && !state.dtcBusy, Modifier.weight(1f)) {
                viewModel.readDtcs()
            }
            NeonButton("Cancella", Neon.Red, state.connected && !state.dtcBusy, Modifier.weight(1f)) {
                viewModel.clearDtcs()
            }
        }
        if (!state.connected) CenterHint("Prima connettiti a un adattatore.")
        state.message?.let { Text(it, color = Neon.Cyan, fontSize = 14.sp) }

        val groups = state.dtcGroups
        when {
            state.dtcBusy -> Text("Operazione in corso…", color = Neon.Muted)
            groups == null -> Text("Premi \"Leggi errori\".", color = Neon.Muted)
            groups.isEmpty() -> Text("Nessun codice presente ✓", color = Neon.Lime, fontWeight = FontWeight.Bold)
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                groups.forEach { group ->
                    item {
                        Text("${group.label.uppercase()} (${group.codes.size})",
                            color = Neon.Magenta, fontWeight = FontWeight.Black, fontSize = 13.sp,
                            letterSpacing = 1.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                    items(group.codes) { dtc ->
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .background(Neon.Panel).border(1.dp, Neon.PanelBorder, RoundedCornerShape(12.dp))
                                .padding(12.dp),
                        ) {
                            Text(dtc.code, color = Neon.Cyan, fontWeight = FontWeight.Black, fontSize = 17.sp)
                            Text(dtc.description, color = Neon.Text, fontSize = 14.sp)
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
        Spacer(Modifier.height(8.dp))
        Text(state.status.uppercase(), color = if (state.connected) Neon.Lime else Neon.Muted,
            fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        if (state.connecting) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Neon.Cyan,
            trackColor = Neon.PanelBorder)

        NeonButton("Cerca e connetti", Neon.Cyan, !state.connecting && !state.connected,
            Modifier.fillMaxWidth()) { onScanConnect() }
        NeonButton("Modalità demo (simulatore)", Neon.Purple, !state.connecting && !state.connected,
            Modifier.fillMaxWidth()) { onMock() }

        state.message?.let { Text(it, color = Neon.Cyan, fontSize = 13.sp) }

        NeonPanel {
            Text("VGATE iCAR PRO BLE 4.0", color = Neon.Magenta, fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                "Accendi il quadro, inserisci l'adattatore nella presa OBD e attiva il Bluetooth, " +
                    "poi premi \"Cerca e connetti\". Per cancellare gli errori: quadro acceso, motore spento.",
                color = Neon.Muted, fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun CenterHint(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = Neon.Muted, textAlign = TextAlign.Center)
    }
}

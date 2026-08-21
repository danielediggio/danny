package org.diggio.obdiggio

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.diggio.obdiggio.core.obd.Pid
import org.diggio.obdiggio.core.obd.PidResult
import org.diggio.obdiggio.core.obd.Pids
import org.diggio.obdiggio.ui.NavIcon
import org.diggio.obdiggio.ui.Neon
import org.diggio.obdiggio.ui.ObdiggioTheme
import org.diggio.obdiggio.ui.SevenSegment
import org.diggio.obdiggio.ui.Tachometer
import org.diggio.obdiggio.ui.TileIcon
import org.diggio.obdiggio.ui.carbonBackground
import org.diggio.obdiggio.ui.drawNavIcon
import org.diggio.obdiggio.ui.drawTileIcon
import org.diggio.obdiggio.ui.speedStreaks

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

private enum class Tab(val label: String) {
    DASHBOARD("Cruscotto"), DTC("Errori"), FREEZE("Freeze"), CONNECT("Connessione")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRoot(viewModel: ObdViewModel, onScanConnect: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(Tab.CONNECT) }
    LaunchedEffect(state.connected) { if (state.connected) tab = Tab.DASHBOARD }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = { HeaderBar(state.connected) },
        bottomBar = {
            NavigationBar(containerColor = Neon.Panel, tonalElevation = 0.dp) {
                Tab.entries.forEach { t ->
                    val selected = tab == t
                    val (navIcon, accent) = navMeta(t)
                    val tint = if (selected) accent else Neon.Muted
                    NavigationBarItem(
                        selected = selected,
                        onClick = { tab = t },
                        icon = {
                            Canvas(Modifier.size(26.dp)) {
                                if (selected) drawNavIcon(navIcon, Offset(size.width / 2f, size.height / 2f),
                                    size.minDimension * 1.15f, accent.copy(alpha = 0.25f))
                                drawNavIcon(navIcon, Offset(size.width / 2f, size.height / 2f),
                                    size.minDimension, tint)
                            }
                        },
                        label = { Text(t.label, letterSpacing = 1.sp, fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedTextColor = accent,
                            unselectedTextColor = Neon.Muted,
                            indicatorColor = Color.Transparent,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Canvas(Modifier.fillMaxSize()) {
                carbonBackground()
                if (tab == Tab.DASHBOARD) speedStreaks(Offset(size.width / 2f, size.height * 0.30f))
            }
            when (tab) {
                Tab.DASHBOARD -> DashboardScreen(viewModel, state)
                Tab.DTC -> DtcScreen(viewModel, state)
                Tab.FREEZE -> FreezeScreen(viewModel, state)
                Tab.CONNECT -> ConnectScreen(state, onScanConnect) { viewModel.connect(useMock = true) }
            }
        }
    }
}

private fun navMeta(t: Tab): Pair<NavIcon, Color> = when (t) {
    Tab.DASHBOARD -> NavIcon.GAUGE to Neon.Cyan
    Tab.DTC -> NavIcon.ENGINE to Neon.Red
    Tab.FREEZE -> NavIcon.SNAPSHOT to Neon.Magenta
    Tab.CONNECT -> NavIcon.LINK to Neon.Cyan
}

@Composable
private fun HeaderBar(connected: Boolean) {
    val chrome = Brush.verticalGradient(
        listOf(Color(0xFFFFFFFF), Color(0xFFC4CAD4), Color(0xFF767C86), Color(0xFFEAEDF2)))
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row {
            val chromeStyle = TextStyle(brush = chrome)
            Text("OBDIGGI", style = chromeStyle, fontSize = 30.sp, fontWeight = FontWeight.Black,
                fontStyle = FontStyle.Italic, letterSpacing = 2.sp)
            Text("O", color = Neon.Red, fontSize = 30.sp, fontWeight = FontWeight.Black,
                fontStyle = FontStyle.Italic, letterSpacing = 2.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Canvas(Modifier.size(9.dp)) { drawCircle(if (connected) Neon.Lime else Neon.Muted, size.minDimension / 2f) }
            Text(if (connected) "VGATE · CONNESSO" else "NON CONNESSO",
                color = if (connected) Neon.Lime else Neon.Muted, fontSize = 12.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        }
    }
}

@Composable
private fun DashboardScreen(viewModel: ObdViewModel, state: UiState) {
    if (!state.connected) {
        CenterHint("Non connesso.\nVai su \"Connessione\".")
        return
    }
    val v = state.values
    val rpm = v[Pids[0x0C]?.key]?.value
    val speed = v[Pids[0x0D]?.key]?.value
    val coolant = v[Pids[0x05]?.key]?.value
    val load = v[Pids[0x04]?.key]?.value
    val volt = v[Pids[0x42]?.key]?.value
    val heroKeys = setOfNotNull(
        Pids[0x0C]?.key, Pids[0x0D]?.key, Pids[0x0B]?.key, Pids[0x33]?.key,
        Pids[0x05]?.key, Pids[0x04]?.key, Pids[0x42]?.key,
    )
    val extra = viewModel.dashboardPids.filter { it.key !in heroKeys }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { RpmDial(rpm, Modifier.fillMaxWidth()) }
        item { SpeedHex(speed) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RacingTile(TileIcon.TEMP, "Temp", coolant, "°C", Neon.Red, Modifier.weight(1f))
                RacingTile(TileIcon.TURBO, "Turbo", state.boostKpa?.div(100.0), "bar", Neon.Lime, Modifier.weight(1f))
                RacingTile(TileIcon.LOAD, "Load", load, "%", Neon.Cyan, Modifier.weight(1f))
                RacingTile(TileIcon.VOLT, "Volt", volt, "V", Neon.Red, Modifier.weight(1f))
            }
        }
        if (extra.isNotEmpty()) {
            items(extra.chunked(2)) { rowPids ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    rowPids.forEach { pid -> NeonTile(pid, state.values[pid.key], Modifier.weight(1f)) }
                    if (rowPids.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun RpmDial(rpm: Double?, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Tachometer(rpm, 7000.0, Modifier.fillMaxWidth())
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.offset(y = 36.dp),
        ) {
            Text("RPM  x1000", color = Neon.Muted, fontSize = 10.sp, letterSpacing = 1.sp)
            SevenSegment(rpm?.let { it.toInt().toString() } ?: "0", Neon.Lime,
                Modifier.height(46.dp).width(170.dp))
            Text("RPM", color = Neon.Lime, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        }
    }
}

@Composable
private fun SpeedHex(speed: Double?) {
    Box(Modifier.fillMaxWidth().height(116.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val inset = h * 0.16f
            val chev = w * 0.08f
            val path = Path().apply {
                moveTo(chev, inset)
                lineTo(w - chev, inset)
                lineTo(w, h / 2f)
                lineTo(w - chev, h - inset)
                lineTo(chev, h - inset)
                lineTo(0f, h / 2f)
                close()
            }
            listOf(10f to 0.15f, 5f to 0.35f, 2.5f to 1f).forEach { (wd, a) ->
                drawPath(path, Neon.Lime.copy(alpha = a), style = Stroke(wd))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            SevenSegment(speed?.let { it.toInt().toString() } ?: "0", Neon.Lime,
                Modifier.height(56.dp).width(140.dp))
            Text("km/h", color = Neon.Lime, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        }
    }
}

@Composable
private fun RacingTile(
    icon: TileIcon, label: String, value: Double?, unit: String, color: Color, modifier: Modifier,
) {
    val shown = value?.let {
        if (it % 1.0 == 0.0 && unit != "bar" && unit != "V") it.toInt().toString() else "%.1f".format(it)
    } ?: "—"
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Neon.Panel)
            .border(1.5.dp, color.copy(alpha = 0.65f), RoundedCornerShape(12.dp))
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label.uppercase(), color = Neon.Muted, fontSize = 10.sp, letterSpacing = 1.sp)
        Canvas(Modifier.size(30.dp)) {
            drawTileIcon(icon, Offset(size.width / 2f, size.height / 2f), size.minDimension, color)
        }
        Text(shown, color = Neon.Text, fontSize = 22.sp, fontWeight = FontWeight.Black)
        Text(unit, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
private fun FreezeScreen(viewModel: ObdViewModel, state: UiState) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        NeonButton("Leggi freeze frame", Neon.Cyan, state.connected && !state.freezeBusy,
            Modifier.fillMaxWidth()) { viewModel.readFreezeFrame() }
        Text("Fotografia dei parametri nell'istante in cui l'ECU ha registrato l'errore.",
            color = Neon.Muted, fontSize = 12.sp)
        if (!state.connected) CenterHint("Prima connettiti a un adattatore.")
        state.message?.let { Text(it, color = Neon.Cyan, fontSize = 14.sp) }

        val ff = state.freeze
        when {
            state.freezeBusy -> Text("Lettura in corso…", color = Neon.Muted)
            ff == null -> Text("Premi \"Leggi freeze frame\".", color = Neon.Muted)
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ff.dtc?.let { dtc ->
                    item {
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .background(Neon.Panel).border(1.5.dp, Neon.Magenta.copy(alpha = 0.7f),
                                    RoundedCornerShape(12.dp)).padding(12.dp),
                        ) {
                            Text("ERRORE CHE HA CONGELATO I DATI", color = Neon.Magenta,
                                fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 1.sp)
                            Text(dtc.code, color = Neon.Cyan, fontWeight = FontWeight.Black, fontSize = 18.sp)
                            Text(dtc.description, color = Neon.Text, fontSize = 14.sp)
                        }
                    }
                }
                if (ff.values.isEmpty() && ff.dtc == null) {
                    item { Text("Nessun dato congelato disponibile.", color = Neon.Muted) }
                } else {
                    items(ff.values.chunked(2)) { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { r -> NeonTile(r.pid, r, Modifier.weight(1f)) }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
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

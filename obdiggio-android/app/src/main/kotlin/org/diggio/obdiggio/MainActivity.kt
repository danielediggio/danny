package org.diggio.obdiggio

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.diggio.obdiggio.ui.*
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    private val viewModel: ObdViewModel by viewModels()
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            viewModel.connect(useMock = false)
        }

    private fun requestBleAndConnect() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        permissionLauncher.launch(permissions)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ObdiggioTheme {
                ObdiggioApp(viewModel, ::requestBleAndConnect)
            }
        }
    }
}

private enum class Screen(val title: String, val symbol: String) {
    DASHBOARD("CRUSCOTTO", "◴"), DTC("ERRORI", "▣"), FREEZE("FREEZE", "❄"), CONNECT("CONNESSIONE", "⛓")
}

private fun Screen.accent(): Color = when (this) {
    Screen.DASHBOARD -> NeonGreen
    Screen.DTC -> NeonPink
    Screen.FREEZE -> NeonCyan
    Screen.CONNECT -> NeonGreen
}

@Composable
private fun ObdiggioApp(viewModel: ObdViewModel, onConnect: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var screen by remember { mutableStateOf(Screen.CONNECT) }
    LaunchedEffect(state.connected) { if (state.connected) screen = Screen.DASHBOARD }

    Scaffold(
        containerColor = Color.Black,
        topBar = { RacingHeader(state) },
        bottomBar = { NeonNavigation(screen) { screen = it } },
    ) { padding ->
        Box(
            Modifier.fillMaxSize().padding(padding).background(
                Brush.radialGradient(listOf(Color(0xFF092329), Color.Black), radius = 900f)
            )
        ) {
            when (screen) {
                Screen.DASHBOARD -> Dashboard(state)
                Screen.DTC -> DtcScreen(viewModel, state)
                Screen.FREEZE -> FreezeScreen(viewModel, state)
                Screen.CONNECT -> ConnectScreen(state, onConnect, { viewModel.connect(true) }, viewModel::disconnect)
            }
        }
    }
}

@Composable
private fun RacingHeader(state: UiState) {
    Column(
        Modifier.fillMaxWidth().background(
            Brush.verticalGradient(listOf(Color(0xFF151A1D), Color(0xFF050708)))
        ).padding(top = 13.dp, bottom = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("OBDIGGI", fontSize = 37.sp, fontWeight = FontWeight.Black,
                fontStyle = FontStyle.Italic, letterSpacing = (-2).sp, color = Color(0xFFE7ECEF))
            Text("O", fontSize = 37.sp, fontWeight = FontWeight.Black,
                fontStyle = FontStyle.Italic, color = NeonPink)
        }
        val statusColor = if (state.connected) NeonGreen else NeonPink
        Text(
            if (state.connected) "●  VGATE · CONNESSO" else "●  VGATE · ${state.status.uppercase()}",
            color = statusColor, fontSize = 14.sp, fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun Dashboard(state: UiState) {
    val rpm = state.number("rpm")
    val speed = state.number("velocita'")
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Tachometer(rpm = rpm, modifier = Modifier.fillMaxWidth().aspectRatio(1f))
        SpeedDisplay(speed)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            MetricTile("TEMP", "♨", state.number("temp_refrigerante"), "°C", NeonPink, Modifier.weight(1f))
            MetricTile("TURBO", "◎", turboBar(state.number("pressione_aspirazione")), "bar", NeonGreen, Modifier.weight(1f))
            MetricTile("LOAD", "◴", state.number("carico_motore"), "%", NeonCyan, Modifier.weight(1f))
            MetricTile("VOLT", "▣", state.number("tensione_modulo"), "V", NeonPink, Modifier.weight(1f))
        }
        if (!state.connected) Text("CONNETTI IL VGATE O USA LA MODALITÀ DEMO", color = NeonPink, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun Tachometer(rpm: Double?, modifier: Modifier = Modifier) {
    val target = ((rpm ?: 0.0) / 8000.0).coerceIn(0.0, 1.0).toFloat()
    val progress by animateFloatAsState(target, tween(450), label = "rpm")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val radius = size.minDimension * .43f
            drawCircle(Color.Black, radius * 1.1f, c)
            drawCircle(Brush.sweepGradient(listOf(NeonCyan, Color.White, NeonPink, NeonCyan), c), radius * 1.06f, c, style = Stroke(radius * .035f))
            drawCircle(Color(0xFF20272B), radius, c, style = Stroke(radius * .025f))
            val start = 140f
            val sweep = 260f
            drawArc(NeonGreen.copy(.22f), start, sweep * .72f, false,
                topLeft = c - Offset(radius, radius), size = Size(radius * 2, radius * 2),
                style = Stroke(radius * .025f))
            drawArc(NeonPink.copy(.55f), start + sweep * .72f, sweep * .28f, false,
                topLeft = c - Offset(radius, radius), size = Size(radius * 2, radius * 2),
                style = Stroke(radius * .035f))
            for (i in 0..40) {
                val angle = Math.toRadians((start + sweep * i / 40f).toDouble())
                val long = i % 5 == 0
                val r1 = radius * if (long) .82f else .88f
                val p1 = Offset(c.x + cos(angle).toFloat() * r1, c.y + sin(angle).toFloat() * r1)
                val p2 = Offset(c.x + cos(angle).toFloat() * radius * .97f, c.y + sin(angle).toFloat() * radius * .97f)
                drawLine(if (i >= 34) NeonPink else Color(0xFFE9EEF0), p1, p2, if (long) 6f else 2f)
            }
            rotate(start + sweep * progress, c) {
                drawLine(NeonGreen.copy(.25f), c, Offset(c.x + radius * .78f, c.y), 20f)
                drawLine(NeonGreen, c, Offset(c.x + radius * .78f, c.y), 8f)
            }
            drawCircle(Color(0xFF050708), radius * .12f, c)
            drawCircle(Color(0xFF536068), radius * .12f, c, style = Stroke(3f))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.offset(y = 50.dp)) {
            Text(format(rpm, 0), color = NeonGreen, fontSize = 42.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
            Text("RPM", color = Steel, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Row(Modifier.fillMaxWidth(.68f), horizontalArrangement = Arrangement.SpaceBetween) {
            for (i in 0..7) Text(i.toString(), color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        }
        Text("RPM\nX1000", textAlign = TextAlign.Center, color = Steel, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.offset(y = (-35).dp))
    }
}

@Composable
private fun SpeedDisplay(speed: Double?) {
    Box(
        Modifier.fillMaxWidth(.86f).clip(RoundedCornerShape(22.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xFF07130B), Color(0xFF111614), Color(0xFF07130B))))
            .border(2.dp, NeonGreen.copy(.75f), RoundedCornerShape(22.dp)).padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(format(speed, 0), color = NeonGreen, fontSize = 67.sp, lineHeight = 68.sp,
                fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
            Text("km/h", color = Steel, fontSize = 16.sp, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MetricTile(title: String, symbol: String, value: Double?, unit: String, color: Color, modifier: Modifier) {
    Column(
        modifier.height(145.dp).clip(RoundedCornerShape(15.dp)).background(Color(0xE6101416))
            .border(1.dp, color.copy(.8f), RoundedCornerShape(15.dp)).padding(7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text(symbol, color = color, fontSize = 30.sp, fontWeight = FontWeight.Black)
        Text(format(value, if (unit == "bar" || unit == "V") 1 else 0), color = Color.White,
            fontSize = 27.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
        Text(unit, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
    }
}

@Composable
private fun NeonNavigation(selected: Screen, onSelect: (Screen) -> Unit) {
    Row(Modifier.fillMaxWidth().height(78.dp).background(Color(0xFF070A0C))) {
        Screen.entries.forEach { screen ->
            val active = screen == selected
            val color = screen.accent()
            Column(
                Modifier.weight(1f).fillMaxHeight().clickable { onSelect(screen) }
                    .background(if (active) color.copy(.08f) else Color.Transparent).padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(screen.symbol, color = if (active) color else Steel, fontSize = 25.sp)
                Text(screen.title, color = if (active) color else Steel, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                if (active) Box(Modifier.fillMaxWidth(.9f).height(3.dp).background(color))
            }
        }
    }
}

@Composable
private fun DtcScreen(viewModel: ObdViewModel, state: UiState) {
    val groups = state.dtcGroups
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("DIAGNOSTICA MOTORE", color = NeonPink, fontSize = 23.sp, fontWeight = FontWeight.Black)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NeonButton("LEGGI ERRORI", NeonCyan, state.connected && !state.dtcBusy, Modifier.weight(1f)) { viewModel.readDtcs() }
            NeonButton("CANCELLA", NeonPink, state.connected && !state.dtcBusy, Modifier.weight(1f)) { viewModel.clearDtcs() }
        }
        if (!state.connected) Hint("Prima connettiti a un adattatore.")
        state.message?.let { Text(it, color = NeonCyan, fontSize = 14.sp) }
        when {
            state.dtcBusy -> LinearProgressIndicator(Modifier.fillMaxWidth(), color = NeonPink)
            groups == null -> Hint("Premi LEGGI ERRORI per interrogare la centralina")
            groups.isEmpty() -> Hint("NESSUN ERRORE MEMORIZZATO ✓", NeonGreen)
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                groups.forEach { group ->
                    item {
                        Text("${group.label.uppercase()} (${group.codes.size})", color = NeonCyan,
                            fontSize = 13.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 6.dp))
                    }
                    items(group.codes) { dtc ->
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(PanelDark)
                            .border(1.dp, NeonPink, RoundedCornerShape(12.dp)).padding(13.dp)) {
                            Text(dtc.code, color = NeonPink, fontSize = 21.sp, fontWeight = FontWeight.Black)
                            Text(dtc.description, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FreezeScreen(viewModel: ObdViewModel, state: UiState) {
    val ff = state.freeze
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("FREEZE FRAME", color = NeonCyan, fontSize = 23.sp, fontWeight = FontWeight.Black)
        NeonButton("LEGGI FREEZE FRAME", NeonCyan, state.connected && !state.freezeBusy, Modifier.fillMaxWidth()) {
            viewModel.readFreezeFrame()
        }
        Text("Fotografia dei parametri nell'istante in cui l'ECU ha registrato l'errore.",
            color = Steel, fontSize = 12.sp)
        if (!state.connected) Hint("Prima connettiti a un adattatore.")
        state.message?.let { Text(it, color = NeonCyan, fontSize = 14.sp) }
        when {
            state.freezeBusy -> LinearProgressIndicator(Modifier.fillMaxWidth(), color = NeonCyan)
            ff == null -> Hint("Premi LEGGI FREEZE FRAME.")
            ff.dtc == null && ff.values.isEmpty() -> Hint("Nessun dato congelato disponibile.")
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ff.dtc?.let { dtc ->
                    item {
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(PanelDark)
                            .border(1.dp, NeonPink, RoundedCornerShape(12.dp)).padding(13.dp)) {
                            Text("ERRORE CHE HA CONGELATO I DATI", color = NeonPink, fontSize = 12.sp,
                                fontWeight = FontWeight.Black)
                            Text(dtc.code, color = NeonCyan, fontSize = 20.sp, fontWeight = FontWeight.Black)
                            Text(dtc.description, color = Color.White)
                        }
                    }
                }
                items(ff.values) { r ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(PanelDark)
                        .border(1.dp, NeonCyan.copy(.5f), RoundedCornerShape(12.dp)).padding(13.dp),
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(r.name, color = Steel, fontSize = 14.sp)
                        Text(format(r.value, if (r.value != null && r.value!! % 1.0 == 0.0) 0 else 1) + " " + r.unit,
                            color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectScreen(state: UiState, onConnect: () -> Unit, onMock: () -> Unit, onDisconnect: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("VGATE iCAR PRO BLE", color = NeonCyan, fontSize = 25.sp, fontWeight = FontWeight.Black)
        Text(state.status.uppercase(), color = if (state.connected) NeonGreen else NeonPink, fontSize = 17.sp)
        if (state.connecting) LinearProgressIndicator(Modifier.fillMaxWidth(), color = NeonCyan)
        if (!state.connected) {
            NeonButton("CERCA E CONNETTI", NeonGreen, !state.connecting, Modifier.fillMaxWidth()) { onConnect() }
            NeonButton("MODALITÀ DEMO", NeonCyan, !state.connecting, Modifier.fillMaxWidth()) { onMock() }
        } else NeonButton("DISCONNETTI", NeonPink, true, Modifier.fillMaxWidth()) { onDisconnect() }
        state.message?.let { Text(it, color = NeonPink, textAlign = TextAlign.Center) }
        Hint("Accendi il quadro, inserisci il Vgate nella presa OBD e attiva il Bluetooth. Per cancellare gli errori: quadro acceso, motore spento.")
    }
}

@Composable
private fun NeonButton(text: String, color: Color, enabled: Boolean, modifier: Modifier, action: () -> Unit) {
    Button(onClick = action, enabled = enabled, modifier = modifier.height(52.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color.copy(.18f), contentColor = color),
        border = androidx.compose.foundation.BorderStroke(1.dp, color)) {
        Text(text, fontWeight = FontWeight.Black)
    }
}

@Composable private fun Hint(text: String, color: Color = Steel) =
    Text(text, color = color, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(16.dp))

private fun UiState.number(key: String): Double? = values[key]?.value
private fun turboBar(kpa: Double?): Double? = kpa?.let { ((it - 100.0) / 100.0).coerceIn(-1.0, 2.5) }
private fun format(value: Double?, decimals: Int): String = when {
    value == null -> "—"
    decimals == 0 -> value.toInt().toString()
    else -> String.format("%.${decimals}f", value)
}

package com.wifizone.mikhmon.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wifizone.mikhmon.Services
import com.wifizone.mikhmon.router.Traffic
import com.wifizone.mikhmon.tickets.gfx.Icons
import kotlinx.coroutines.delay

/** Trafic en direct : courbe des 90 dernières secondes, débits, plus gros consommateurs. */
@Composable
fun TrafficScreen() {
    val repo = Services.router
    val ifaces by polling(30000) { repo.interfaces() }
    val main by polling(120000) { repo.mainInterface() }
    var iface by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(main) { if (iface == null) iface = main?.getOrNull()?.ifBlank { null } }
    val samples = remember { mutableStateListOf<Traffic>() }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(iface) {
        samples.clear()
        val name = iface ?: return@LaunchedEffect
        while (true) {
            runCatching { repo.traffic(name) }
                .onSuccess { samples.add(it); if (samples.size > 90) samples.removeAt(0); error = null }
                .onFailure { error = it.message }
            delay((1000 * com.wifizone.mikhmon.remote.RemoteAccess.pollFactor()).toLong())
        }
    }
    val active by polling(3000) { repo.active() }
    val prev = remember { mutableStateMapOf<String, Pair<Long, Long>>() }
    val rates = remember { mutableStateMapOf<String, Long>() }
    LaunchedEffect(active) {
        val now = System.currentTimeMillis()
        active?.getOrNull()?.forEach { a ->
            prev[a.id]?.let { (b, t) -> if (now - t > 1000) rates[a.id] = ((a.bytesOut - b).coerceAtLeast(0) * 8000 / (now - t)) }
            prev[a.id] = a.bytesOut to now
        }
    }

    Column(Modifier.fillMaxSize().background(Brand.Bg).verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Trafic en direct", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ifaces?.getOrNull()?.filter { it.running }?.forEach { f -> SelectChip(f.name, iface == f.name) { iface = f.name } }
        }
        Spacer(Modifier.height(12.dp))
        val last = samples.lastOrNull()
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("Réception (↓)", last?.let { Fmt.rate(it.rxBps) } ?: "–", Icons.DOWNLOAD, Brand.Blue, Modifier.weight(1f),
                sub = samples.maxOfOrNull { it.rxBps }?.let { "pointe ${Fmt.rate(it)}" })
            StatTile("Envoi (↑)", last?.let { Fmt.rate(it.txBps) } ?: "–", Icons.DATA, Brand.Violet, Modifier.weight(1f),
                sub = samples.maxOfOrNull { it.txBps }?.let { "pointe ${Fmt.rate(it)}" })
        }
        Spacer(Modifier.height(12.dp))
        Panel(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(iface ?: "…", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Legend(Brand.Blue, "Réception"); Spacer(Modifier.width(12.dp)); Legend(Brand.Violet, "Envoi")
            }
            Spacer(Modifier.height(10.dp))
            Chart(samples, Modifier.fillMaxWidth().height(200.dp))
            Text("90 dernières secondes · une mesure par seconde", color = Brand.Muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 6.dp))
        }
        error?.let { Spacer(Modifier.height(10.dp)); ErrorNote("Mesure impossible : $it") }

        SectionTitle("Plus gros consommateurs")
        val list = active?.getOrNull().orEmpty().sortedByDescending { rates[it.id] ?: 0 }
        if (list.isEmpty()) Panel(Modifier.fillMaxWidth()) { Text("Aucun client connecté.", color = Brand.Muted) }
        else Panel(Modifier.fillMaxWidth(), padding = 14.dp) {
            val top = (list.maxOfOrNull { rates[it.id] ?: 0 } ?: 0).coerceAtLeast(1)
            val mono = rememberMono()
            list.take(10).forEachIndexed { i, a ->
                if (i > 0) Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(a.user, fontFamily = mono, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(Fmt.rate(rates[a.id] ?: 0), fontWeight = FontWeight.SemiBold, color = Brand.Blue)
                }
                Text("${a.device.ifBlank { a.mac }} · ${Fmt.bytes(a.bytesOut)} reçus", color = Brand.Muted, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(5.dp))
                LinearProgressIndicator(progress = { (rates[a.id] ?: 0).toFloat() / top }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = Brand.Blue, trackColor = Color(0xFFE8EDF8))
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun Legend(c: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.size(9.dp).clip(CircleShape).background(c)); Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = Brand.Muted)
    }
}

@Composable
private fun Chart(samples: List<Traffic>, modifier: Modifier) {
    val max = (samples.maxOfOrNull { maxOf(it.rxBps, it.txBps) } ?: 1L).coerceAtLeast(1_000_000L) * 1.15f
    Column {
        Text(Fmt.rate(max.toLong()), style = MaterialTheme.typography.labelSmall, color = Brand.Muted, fontSize = 10.sp)
        Canvas(modifier) {
            val w = size.width; val h = size.height
            for (i in 0..3) {
                val y = h * i / 3f
                drawLine(Color(0xFFE9EDF5), Offset(0f, y), Offset(w, y), 1f)
            }
            if (samples.size < 2) return@Canvas
            val n = 90
            val step = w / (n - 1)
            val start = n - samples.size
            fun series(sel: (Traffic) -> Long): Path {
                val p = Path()
                samples.forEachIndexed { i, s ->
                    val x = (start + i) * step
                    val y = h - h * sel(s) / max
                    if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                }
                return p
            }
            val rx = series { it.rxBps }
            val area = Path().apply { addPath(rx); lineTo((start + samples.size - 1) * step, h); lineTo(start * step, h); close() }
            drawPath(area, Brush.verticalGradient(listOf(Brand.Blue.copy(alpha = 0.35f), Brand.Blue.copy(alpha = 0.02f))))
            drawPath(rx, Brand.Blue, style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(series { it.txBps }, Brand.Violet, style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

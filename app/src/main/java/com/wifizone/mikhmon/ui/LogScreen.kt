package com.wifizone.mikhmon.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wifizone.mikhmon.Services
import com.wifizone.mikhmon.router.LogLine
import com.wifizone.mikhmon.router.RouterRepo
import com.wifizone.mikhmon.tickets.gfx.Icons

@Composable
fun BackHeader(title: String, onBack: () -> Unit, subtitle: String? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("‹", style = MaterialTheme.typography.headlineMedium, color = Brand.Indigo,
            modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onBack).padding(horizontal = 14.dp, vertical = 2.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1)
            if (subtitle != null) Text(subtitle, color = Brand.Muted, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
    }
}

private fun kind(l: LogLine): Int {
    val m = l.message.lowercase()
    return when {
        "logged in" in m && "hotspot" in l.topics -> 1
        "logged out" in m -> 2
        "failed" in m || "error" in l.topics || "invalid" in m -> 3
        else -> 4
    }
}

@Composable
fun LogScreen(nav: Nav) {
    val repo = Services.router
    val logs by polling(5000) { repo.logs() }
    val sales by polling(30000) { repo.sales() }
    var filter by remember { mutableIntStateOf(0) }
    var q by remember { mutableStateOf("") }
    val cur = Services.settings.load()?.currency?.ifBlank { null } ?: "F"
    Column(Modifier.fillMaxSize().background(Brand.Bg)) {
        BackHeader("Journal du routeur", { nav.overlay = null }, "Connexions, échecs et ventes")
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                val list = sales?.getOrNull().orEmpty()
                val today = RouterRepo.todayKeys(); val month = RouterRepo.monthKeys()
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("Ventes du jour", Fmt.money(list.filter { it.date.lowercase() in today }.sumOf { it.price }, cur), Icons.TICKET, Brand.Green, Modifier.weight(1f))
                    StatTile("Ce mois-ci", Fmt.money(list.filter { s -> month.any { s.date.lowercase().startsWith(it) } }.sumOf { it.price }, cur), Icons.CALENDAR, Brand.Indigo, Modifier.weight(1f),
                        sub = "${list.size} vente(s) enregistrée(s)")
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(q, { q = it }, placeholder = { Text("Rechercher (code, IP, message…)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
                Spacer(Modifier.height(8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Tout", "Connexions", "Déconnexions", "Échecs", "Système").forEachIndexed { i, s -> SelectChip(s, filter == i) { filter = i } }
                }
            }
            val all = logs?.getOrNull()
            if (all == null) item { if (logs?.isFailure == true) ErrorNote("Journal indisponible : ${logs?.exceptionOrNull()?.message}") else Loading() }
            else {
                val list = all.filter { l -> (filter == 0 || kind(l) == filter) && (q.isBlank() || "${l.message} ${l.topics}".contains(q.trim(), true)) }
                if (list.isEmpty()) item { EmptyState(Icons.CHAT, "Rien à afficher", "Aucun message ne correspond.") }
                items(list.take(400), key = { it.id + it.time }) { l ->
                    val k = kind(l)
                    val (ic, color) = when (k) { 1 -> Icons.CHECK to Brand.Green; 2 -> Icons.DOWNLOAD to Brand.Muted; 3 -> Icons.LOCK to Brand.Red; else -> Icons.SIGNAL to Brand.Indigo }
                    Panel(Modifier.fillMaxWidth(), padding = 12.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(ic, color, 34.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(l.message, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text("${l.time} · ${l.topics}", color = Brand.Muted, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

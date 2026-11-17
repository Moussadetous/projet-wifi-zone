@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.wifizone.mikhmon.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wifizone.mikhmon.Services
import com.wifizone.mikhmon.router.ActiveUser
import com.wifizone.mikhmon.router.HsUser
import com.wifizone.mikhmon.tickets.gfx.Icons
import kotlinx.coroutines.launch

@Composable
fun ClientsScreen(nav: Nav) {
    var tab by remember { mutableIntStateOf(0) }
    var refresh by remember { mutableIntStateOf(0) }
    val repo = Services.router
    val active by polling(3000, refresh) { repo.active() }
    val users by polling(20000, refresh) { repo.users() }
    val prev = remember { mutableStateMapOf<String, Triple<Long, Long, Long>>() }
    val rates = remember { mutableStateMapOf<String, Pair<Long, Long>>() }
    // Débit de chaque client : différence d'octets entre deux lectures (calculé après l'affichage).
    LaunchedEffect(active) {
        val list = active?.getOrNull() ?: return@LaunchedEffect
        val now = System.currentTimeMillis()
        for (a in list) {
            val p = prev[a.id]
            if (p != null && now - p.third >= 1500) {
                val dt = (now - p.third) / 1000.0
                rates[a.id] = (((a.bytesOut - p.first).coerceAtLeast(0) * 8 / dt).toLong()) to (((a.bytesIn - p.second).coerceAtLeast(0) * 8 / dt).toLong())
                prev[a.id] = Triple(a.bytesOut, a.bytesIn, now)
            } else if (p == null) prev[a.id] = Triple(a.bytesOut, a.bytesIn, now)
        }
    }
    var selActive by remember { mutableStateOf<ActiveUser?>(null) }
    var selUser by remember { mutableStateOf<HsUser?>(null) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize().background(Brand.Bg)) {
        Column(Modifier.padding(16.dp)) {
            Text("Clients", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(12.dp))
            Segmented(listOf("Connectés (${active?.getOrNull()?.size ?: "…"})", "Tickets (${users?.getOrNull()?.size ?: "…"})"), tab, { tab = it })
        }
        if (tab == 0) {
            val list = active?.getOrNull()
            when {
                list == null && active?.isFailure == true -> ErrorNote("Routeur injoignable : ${active?.exceptionOrNull()?.message}", Modifier.padding(16.dp))
                list == null -> Loading()
                list.isEmpty() -> EmptyState(Icons.USER, "Personne n'est connecté", "Les clients apparaissent ici dès qu'ils se connectent au hotspot.")
                else -> LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val total = list.sumOf { rates[it.id]?.first ?: 0L }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatTile("Débit total", Fmt.rate(total), Icons.GAUGE, Brand.Blue, Modifier.weight(1f))
                            StatTile("Consommé (sessions)", Fmt.bytes(list.sumOf { it.bytesOut + it.bytesIn }), Icons.DATA, Brand.Violet, Modifier.weight(1f))
                        }
                    }
                    items(list.sortedByDescending { rates[it.id]?.first ?: 0L }, key = { it.id }) { a -> ActiveRow(a, rates[a.id]) { selActive = a } }
                }
            }
        } else {
            val all = users?.getOrNull()
            Column(Modifier.padding(horizontal = 16.dp)) {
                OutlinedTextField(query, { query = it }, placeholder = { Text("Rechercher un code, un profil, un lot…") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
                Spacer(Modifier.height(8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Tous", "Non utilisés", "Utilisés", "Désactivés").forEachIndexed { i, s -> SelectChip(s, filter == i) { filter = i } }
                }
                Spacer(Modifier.height(8.dp))
            }
            when {
                all == null && users?.isFailure == true -> ErrorNote("Routeur injoignable : ${users?.exceptionOrNull()?.message}", Modifier.padding(16.dp))
                all == null -> Loading()
                else -> {
                    val q = query.trim().lowercase()
                    val list = all.filter { u ->
                        (q.isEmpty() || "${u.name} ${u.profile} ${u.comment}".lowercase().contains(q)) && when (filter) {
                            1 -> !u.used && !u.disabled; 2 -> u.used; 3 -> u.disabled; else -> true
                        }
                    }
                    if (list.isEmpty()) EmptyState(Icons.TICKET, "Aucun ticket", "Rien ne correspond à votre recherche.")
                    else LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { Text("${list.size} ticket(s)", color = Brand.Muted, style = MaterialTheme.typography.labelMedium) }
                        items(list.take(500), key = { it.id }) { u -> UserRow(u) { selUser = u } }
                    }
                }
            }
        }
    }

    selActive?.let { a -> ActiveSheet(a, rates[a.id], onClose = { selActive = null }, onChanged = { refresh++; selActive = null }) }
    selUser?.let { u -> UserSheet(u, nav, onClose = { selUser = null }, onChanged = { refresh++; selUser = null }) }
}

@Composable
private fun ActiveRow(a: ActiveUser, rate: Pair<Long, Long>?, onClick: () -> Unit) {
    val mono = rememberMono()
    Panel(Modifier.fillMaxWidth(), padding = 14.dp, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(if (a.device.contains("iphone", true) || a.device.contains("android", true) || a.device.contains("galaxy", true) || a.device.contains("redmi", true)) Icons.PHONE else Icons.WIFI, Brand.Blue, 42.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(a.user, fontFamily = mono, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                Text(a.device.ifBlank { a.mac }, color = Brand.Muted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("↓ " + Fmt.rate(rate?.first ?: 0), fontWeight = FontWeight.Bold, color = Brand.Blue)
                Text("↑ " + Fmt.rate(rate?.second ?: 0), color = Brand.Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill(a.address, Brand.Indigo)
            Pill("Connecté ${Fmt.ros(a.uptime)}", Brand.Green)
            if (a.timeLeft.isNotBlank()) Pill("Reste ${Fmt.ros(a.timeLeft)}", Brand.Amber)
        }
    }
}

@Composable
private fun UserRow(u: HsUser, onClick: () -> Unit) {
    val mono = rememberMono()
    Panel(Modifier.fillMaxWidth(), padding = 12.dp, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(u.name, fontFamily = mono, fontWeight = FontWeight.ExtraBold)
                Text(listOf(u.profile, if (u.used) "${Fmt.ros(u.uptime)} · ${Fmt.bytes(u.bytesIn + u.bytesOut)}" else null).filterNotNull().joinToString(" · "),
                    color = Brand.Muted, style = MaterialTheme.typography.bodySmall)
            }
            when {
                u.disabled -> Pill("Désactivé", Brand.Red)
                u.used -> Pill("Utilisé", Brand.Muted)
                else -> Pill("Libre", Brand.Green)
            }
        }
    }
}

@Composable
private fun Field(k: String, v: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
        Text(k, color = Brand.Muted, modifier = Modifier.weight(1f))
        Text(v.ifBlank { "—" }, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ActiveSheet(a: ActiveUser, rate: Pair<Long, Long>?, onClose: () -> Unit, onChanged: () -> Unit) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var busy by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onClose, containerColor = Color.White) {
        Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 28.dp)) {
            Text(a.user, style = MaterialTheme.typography.headlineSmall, fontFamily = rememberMono())
            Text(a.device.ifBlank { "Appareil inconnu" }, color = Brand.Muted)
            Spacer(Modifier.height(14.dp))
            Field("Adresse IP", a.address)
            Field("Adresse MAC", a.mac)
            Field("Connecté depuis", Fmt.ros(a.uptime))
            Field("Temps restant", if (a.timeLeft.isBlank()) "illimité" else Fmt.ros(a.timeLeft))
            Field("Inactif depuis", Fmt.ros(a.idle))
            Field("Téléchargé", Fmt.bytes(a.bytesOut))
            Field("Envoyé", Fmt.bytes(a.bytesIn))
            Field("Débit actuel", "↓ ${Fmt.rate(rate?.first ?: 0)}  ↑ ${Fmt.rate(rate?.second ?: 0)}")
            Field("Méthode", a.loginBy)
            Field("Serveur", a.server)
            Spacer(Modifier.height(16.dp))
            BigButton("Déconnecter ce client", loading = busy, brush = androidx.compose.ui.graphics.Brush.linearGradient(listOf(Brand.Red, Brand.Orange))) {
                busy = true
                scope.launch {
                    runCatching { Services.router.disconnect(a.id) }
                        .onSuccess { Toast.makeText(ctx, "${a.user} déconnecté", Toast.LENGTH_SHORT).show(); onChanged() }
                        .onFailure { Toast.makeText(ctx, it.message, Toast.LENGTH_LONG).show() }
                    busy = false
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Le ticket reste valable : le client peut se reconnecter. Pour l'en empêcher, désactivez le ticket dans l'onglet « Tickets ».",
                color = Brand.Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun UserSheet(u: HsUser, nav: Nav, onClose: () -> Unit, onChanged: () -> Unit) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    fun act(msg: String, block: suspend () -> Unit) = scope.launch {
        runCatching { block() }.onSuccess { Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show(); onChanged() }
            .onFailure { Toast.makeText(ctx, it.message, Toast.LENGTH_LONG).show() }
    }
    ModalBottomSheet(onDismissRequest = onClose, containerColor = Color.White) {
        Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 28.dp)) {
            Text(u.name, style = MaterialTheme.typography.headlineSmall, fontFamily = rememberMono())
            Text(u.profile, color = Brand.Muted)
            Spacer(Modifier.height(14.dp))
            Field("Mot de passe", if (u.password == u.name) "identique au code" else u.password)
            Field("État", if (u.disabled) "Désactivé" else if (u.used) "Utilisé" else "Jamais utilisé")
            Field("Temps utilisé", Fmt.ros(u.uptime))
            Field("Consommé", Fmt.bytes(u.bytesIn + u.bytesOut))
            Field("Limite de temps", u.limitUptime.ifBlank { "aucune" })
            Field("Limite de données", u.limitBytes.toLongOrNull()?.let { Fmt.bytes(it) } ?: "aucune")
            Field("Commentaire", u.comment)
            Spacer(Modifier.height(16.dp))
            BigButton("Imprimer ce ticket (premium)", brush = Brand.Premium, icon = Icons.SPARKLE) { onClose(); nav.open(Overlay.Studio("Ticket ${u.name}", users = listOf(u), templateId = "premium")) }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton({ act(if (u.disabled) "Ticket réactivé" else "Ticket désactivé") { Services.router.setDisabled(u.id, !u.disabled) } }, Modifier.weight(1f)) {
                    Text(if (u.disabled) "Réactiver" else "Désactiver")
                }
                OutlinedButton({ if (confirmDelete) act("Ticket supprimé") { Services.router.removeUser(u.id) } else confirmDelete = true }, Modifier.weight(1f)) {
                    Text(if (confirmDelete) "Confirmer ?" else "Supprimer", color = Brand.Red)
                }
            }
        }
    }
}

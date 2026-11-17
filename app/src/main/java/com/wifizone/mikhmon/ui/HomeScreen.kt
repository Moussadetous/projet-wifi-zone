package com.wifizone.mikhmon.ui

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wifizone.mikhmon.Services
import com.wifizone.mikhmon.remote.RemoteAccess
import com.wifizone.mikhmon.router.RouterRepo
import com.wifizone.mikhmon.tickets.gfx.Icons

@Composable
fun HomeScreen(activity: Activity, nav: Nav) {
    val repo = Services.router
    val res by polling(5000) { repo.resource() }
    val active by polling(5000) { repo.active() }
    val iface by polling(60000) { repo.mainInterface() }
    val traffic by polling(2000, iface?.getOrNull()) { iface?.getOrNull()?.takeIf { it.isNotBlank() }?.let { repo.traffic(it) } }
    val users by polling(30000) { repo.users() }
    val sales by polling(30000) { repo.sales() }
    val account by Services.account.account.collectAsState()
    val scope = rememberCoroutineScope()
    val purchase = remember { Purchase(activity, scope) }
    val cur = Services.settings.load()?.currency?.ifBlank { null } ?: "F"

    LazyColumn(Modifier.fillMaxSize().background(Brand.Bg), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 14.dp, top = 4.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Tableau de bord", style = MaterialTheme.typography.labelLarge, color = Brand.Muted)
                    Text(res?.getOrNull()?.identity?.ifBlank { null } ?: "Mon routeur", style = MaterialTheme.typography.headlineMedium, maxLines = 1)
                }
                val online = res?.isSuccess == true
                Pill(if (online) "En ligne" else if (res == null) "…" else "Hors ligne", if (online) Brand.Green else Brand.Red)
                val remoteUp by RemoteAccess.up.collectAsState()
                if (remoteUp) { Spacer(Modifier.width(6.dp)); Pill("À distance", Brand.Violet) }
                Spacer(Modifier.width(10.dp))
                Box(Modifier.size(42.dp).clip(CircleShape).background(Brand.Hero).clickable { nav.open(Overlay.Account) }, contentAlignment = Alignment.Center) {
                    Text(account?.name?.split(" ")?.take(2)?.joinToString("") { it.take(1) }?.uppercase()?.ifBlank { null } ?: "?", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
        // Routeur injoignable : pourquoi et quoi faire (hors du Wi-Fi du routeur notamment).
        res?.exceptionOrNull()?.let { err ->
            item {
                val lastRemote by RemoteAccess.lastError.collectAsState()
                val cfg = remember { Services.settings.load() }
                val onWifi = cfg?.let { RemoteAccess.onRouterWifi(it) } ?: false
                val supported = RemoteAccess.support.value?.supported == true
                val (title, text, action) = when {
                    RemoteAccess.ready -> Triple("Accès à distance : routeur injoignable", lastRemote ?: err.message.orEmpty(), "Diagnostiquer")
                    RemoteAccess.paid && !onWifi -> Triple("Accès à distance payé ✓",
                        "Connectez le téléphone une fois au Wi-Fi du routeur : l'activation se fera toute seule.", null)
                    !onWifi && supported -> Triple("Vous n'êtes pas sur le Wi-Fi du routeur",
                        "Pour le joindre d'ici (4G, autre Wi-Fi), activez l'accès à distance : il se paie puis s'active une fois depuis le Wi-Fi du routeur.", "Accès à distance")
                    !onWifi -> Triple("Vous n'êtes pas sur le Wi-Fi du routeur", "Connectez le téléphone au Wi-Fi de votre MikroTik.", null)
                    else -> Triple("Routeur injoignable", err.message.orEmpty(), null)
                }
                Column(Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(18.dp)).background(Brand.Red.copy(alpha = 0.08f)).padding(14.dp)) {
                    Text(title, fontWeight = FontWeight.Bold, color = Brand.Red)
                    Spacer(Modifier.height(4.dp))
                    Text(text, style = MaterialTheme.typography.bodySmall)
                    if (action != null) Text(action, color = Brand.Indigo, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp).clip(RoundedCornerShape(10.dp)).clickable { nav.open(Overlay.Remote) }.padding(6.dp))
                }
            }
        }
        // Accès à distance : mis en avant si le routeur le permet et qu'il n'est pas encore pris ; sinon son état.
        item {
            val support by RemoteAccess.support.collectAsState()
            val up by RemoteAccess.up.collectAsState()
            val until = RemoteAccess.activeUntil(account?.items)
            if (support?.supported == true && until == null) {
                val (price, perMonth) = remotePrice(account?.itemPrice)
                RemoteHero(price, perMonth, Modifier.padding(bottom = 12.dp)) { nav.open(Overlay.Remote) }
            } else if (until != null) {
                Row(Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(18.dp)).background(Color.White).clickable { nav.open(Overlay.Remote) }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.GLOBE, if (up) Brand.Green else Brand.Indigo, 36.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (up) "Connecté à distance" else "Accès à distance actif", fontWeight = FontWeight.Bold)
                        Text("Jusqu'au ${until.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))}", color = Brand.Muted, style = MaterialTheme.typography.bodySmall)
                    }
                    Text("›", color = Brand.Muted, style = MaterialTheme.typography.titleLarge)
                }
            }
        }
        account?.takeIf { it.status == "trial" }?.let { a ->
            item {
                Row(Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(18.dp)).background(Brand.Premium).clickable { purchase.buy("lifetime") }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    VIcon(Icons.CROWN, Color.White, size = 26.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Essai gratuit : ${a.daysLeft} jour${if (a.daysLeft > 1) "s" else ""} restant${if (a.daysLeft > 1) "s" else ""}", color = Color.White, fontWeight = FontWeight.Bold)
                        Text("Activez l'accès à vie pour ${Fmt.money(a.lifetimePrice, "F")}", color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodySmall)
                    }
                    Text("Activer", color = Color.White, fontWeight = FontWeight.Black)
                }
            }
        }
        item { Hero(res?.getOrNull(), active?.getOrNull()?.size, traffic?.getOrNull()) { nav.tab = Tab.CLIENTS } }
        item {
            SectionTitle("Raccourcis")
            Panel(Modifier.fillMaxWidth(), padding = 10.dp) {
                Row(Modifier.fillMaxWidth()) {
                    Shortcut(Icons.TICKET, "Générer\ndes tickets", listOf(Color(0xFF4F46E5), Color(0xFF7C3AED)), Modifier.weight(1f)) { nav.tab = Tab.TICKETS }
                    Shortcut(Icons.SPARKLE, "Tickets\npremium", listOf(Color(0xFFF59E0B), Color(0xFFEC4899)), Modifier.weight(1f)) { nav.tab = Tab.TICKETS }
                    Shortcut(Icons.USER, "Clients\nconnectés", listOf(Color(0xFF0EA5E9), Color(0xFF2563EB)), Modifier.weight(1f),
                        badge = active?.getOrNull()?.size?.takeIf { it > 0 }?.toString()) { nav.tab = Tab.CLIENTS }
                }
                Row(Modifier.fillMaxWidth()) {
                    Shortcut(Icons.GAUGE, "Trafic\nen direct", listOf(Color(0xFF10B981), Color(0xFF0D9488)), Modifier.weight(1f)) { nav.tab = Tab.TRAFFIC }
                    Shortcut(Icons.CHAT, "Journal\ndu routeur", listOf(Color(0xFF64748B), Color(0xFF334155)), Modifier.weight(1f)) { nav.open(Overlay.Logs) }
                    Shortcut(Icons.GLOBE, "Mikhmon\ncomplet", listOf(Color(0xFFEF4444), Color(0xFFF97316)), Modifier.weight(1f)) { nav.tab = Tab.MIKHMON }
                }
            }
        }
        item {
            SectionTitle("Aujourd'hui")
            val today = RouterRepo.todayKeys()
            val month = RouterRepo.monthKeys()
            val list = sales?.getOrNull().orEmpty()
            val todaySales = list.filter { it.date.lowercase() in today }
            val monthTotal = list.filter { s -> month.any { s.date.lowercase().startsWith(it) } }.sumOf { it.price }
            val u = users?.getOrNull().orEmpty()
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Ventes du jour", Fmt.money(todaySales.sumOf { it.price }, cur), Icons.TICKET, Brand.Green, Modifier.weight(1f), sub = "${todaySales.size} ticket(s) · mois ${Fmt.money(monthTotal, cur)}")
                StatTile("Tickets libres", u.count { !it.used && !it.disabled }.toString(), Icons.KEY, Brand.Indigo, Modifier.weight(1f), sub = "${u.size} au total") { nav.tab = Tab.TICKETS }
            }
            if (todaySales.isNotEmpty()) {
                SectionTitle("Dernières ventes")
                Panel(Modifier.fillMaxWidth(), padding = 4.dp) {
                    todaySales.takeLast(6).reversed().forEach { s ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(Icons.TICKET, Brand.Green, 34.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(s.user, fontWeight = FontWeight.SemiBold, fontFamily = rememberMono())
                                Text("${s.time} · ${s.profile} · ${s.ip}", color = Brand.Muted, style = MaterialTheme.typography.bodySmall)
                            }
                            Text(Fmt.money(s.price, cur), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
    PurchaseDialogs(purchase)
}

@Composable
private fun Hero(r: com.wifizone.mikhmon.router.Resource?, activeCount: Int?, t: com.wifizone.mikhmon.router.Traffic?, onClients: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Brand.Hero).clickable(onClick = onClients).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Connectés maintenant", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelLarge)
                Text(activeCount?.toString() ?: "–", color = Color.White, fontSize = 52.sp, fontWeight = FontWeight.Black, lineHeight = 56.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    VIcon(Icons.DOWNLOAD, Color.White, size = 16.dp); Spacer(Modifier.width(4.dp))
                    Text(t?.let { Fmt.rate(it.rxBps) } ?: "–", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                Text("↑ " + (t?.let { Fmt.rate(it.txBps) } ?: "–"), color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Meter("Processeur", (r?.cpu ?: 0) / 100f, "${r?.cpu ?: 0} %", Modifier.weight(1f))
            val mem = if (r != null && r.memTotal > 0) 1f - r.memFree.toFloat() / r.memTotal else 0f
            Meter("Mémoire", mem, "${(mem * 100).toInt()} %", Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Text(listOfNotNull(r?.board?.ifBlank { null }, r?.version?.ifBlank { null }?.let { "RouterOS $it" }, r?.uptime?.let { "allumé depuis ${Fmt.ros(it)}" })
            .joinToString(" · ").ifBlank { "Connexion au routeur…" }, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun Meter(label: String, v: Float, text: String, modifier: Modifier) {
    Column(modifier) {
        Row { Text(label, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f)); Text(text, color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(5.dp))
        LinearProgressIndicator(progress = { v.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = Color.White, trackColor = Color.White.copy(alpha = 0.25f))
    }
}

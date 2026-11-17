package com.wifizone.mikhmon.ui

import android.app.Activity
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wifizone.mikhmon.BuildConfig
import com.wifizone.mikhmon.MikhmonServer
import com.wifizone.mikhmon.MikhmonService
import com.wifizone.mikhmon.Services
import com.wifizone.mikhmon.remote.RemoteAccess
import com.wifizone.mikhmon.tickets.Catalog
import com.wifizone.mikhmon.tickets.gfx.Icon
import com.wifizone.mikhmon.tickets.gfx.Icons
import kotlinx.coroutines.launch

@Composable
fun AccountScreen(activity: Activity, nav: Nav) {
    val acc by Services.account.account.collectAsState()
    val scope = rememberCoroutineScope()
    val p = remember { Purchase(activity, scope) }
    var report by remember { mutableStateOf<String?>(null) }
    val settings = remember { Services.settings.load() }
    Column(Modifier.fillMaxSize().background(Brand.Bg).verticalScroll(rememberScrollState())) {
        BackHeader("Compte et réglages", { nav.overlay = null })
        Column(Modifier.padding(horizontal = 16.dp)) {
            Panel(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(54.dp).clip(CircleShape).background(Brand.Hero), contentAlignment = Alignment.Center) {
                        Text(acc?.name?.split(" ")?.take(2)?.joinToString("") { it.take(1) }?.uppercase() ?: "?", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(acc?.name ?: "Non connecté", style = MaterialTheme.typography.titleLarge)
                        Text(acc?.email ?: "", color = Brand.Muted)
                    }
                }
                Spacer(Modifier.height(14.dp))
                val a = acc
                if (a != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        when (a.status) {
                            "lifetime" -> Pill("Accès à vie ✓", Brand.Green, filled = true)
                            "trial" -> Pill("Essai : ${a.daysLeft} jour(s) restant(s)", Brand.Amber, filled = true)
                            else -> Pill("Essai terminé", Brand.Red, filled = true)
                        }
                        Spacer(Modifier.width(8.dp))
                        Pill("Solde : ${Fmt.money(a.balance, "F")}", Brand.Indigo)
                    }
                    if (a.status != "lifetime") {
                        Spacer(Modifier.height(14.dp))
                        BuyArea(p, a, "lifetime", "", "Activer l'accès à vie — ${Fmt.money(a.lifetimePrice, "F")}", icon = Icons.CROWN)
                    }
                    Spacer(Modifier.height(10.dp))
                    val owned = Catalog.premium.count { it.id in a.items }
                    Text("Modèles de tickets premium : $owned / ${Catalog.premium.size} débloqués", color = Brand.Muted, style = MaterialTheme.typography.bodySmall)
                }
            }
            SectionTitle("Mon compte MPro")
            Panel(Modifier.fillMaxWidth(), padding = 4.dp) {
                Line(Icons.GLOBE, "Ouvrir mon compte sur la boutique", "Recharges, achats, factures") { Services.account.openBrowser(activity, Uri.parse(Services.account.shopUrl)) }
                Line(Icons.USER, "Changer de compte", null) { scope.launch { Services.account.logout(); Services.account.startLogin(activity, chooseAccount = true) } }
                Line(Icons.LOCK, "Se déconnecter", null, Brand.Red) { scope.launch { Services.account.logout(); nav.overlay = null } }
            }
            SectionTitle("Accès à distance")
            Panel(Modifier.fillMaxWidth(), padding = 4.dp) {
                val support by RemoteAccess.support.collectAsState()
                val until = RemoteAccess.activeUntil(acc?.items)
                val s = support
                when {
                    s == null -> Line(Icons.GLOBE, "Accès à distance", "Vérification au prochain passage sur le Wi-Fi du routeur") { nav.open(Overlay.Remote) }
                    !s.supported -> Line(Icons.GLOBE, "Non disponible sur votre routeur", s.reason, Brand.Muted) { nav.open(Overlay.Remote) }
                    until != null -> Line(Icons.GLOBE, "Actif jusqu'au ${until.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))}", "Gérer, tester, prolonger") { nav.open(Overlay.Remote) }
                    else -> {
                        val (price, perMonth) = remotePrice(acc?.itemPrice)
                        Line(Icons.GLOBE, "Accédez à votre routeur partout", "${Fmt.money(perMonth, "F")} / mois · payé par 2 mois (${Fmt.money(price, "F")})") { nav.open(Overlay.Remote) }
                    }
                }
            }
            SectionTitle("Routeur")
            Panel(Modifier.fillMaxWidth(), padding = 4.dp) {
                Line(Icons.WIFI, "Modifier la connexion au routeur", settings?.let { "${it.routerUser}@${it.routerIp}" }) { nav.open(Overlay.Setup) }
                Line(Icons.SHIELD, "Rapport de diagnostic", "À envoyer au support en cas de problème") { report = MikhmonServer.report() }
                Line(Icons.BOLT, "Redémarrer le serveur Mikhmon", null) { MikhmonServer.restart(activity); Toast.makeText(activity, "Redémarrage…", Toast.LENGTH_SHORT).show() }
                Line(Icons.DOWNLOAD, "Arrêter Mikhmon et quitter", null, Brand.Red) { MikhmonService.stop(activity); activity.finishAffinity() }
            }
            Spacer(Modifier.height(16.dp))
            Text("Mikhmon Android ${BuildConfig.VERSION_NAME} · boutique : ${Services.account.base.removePrefix("https://")}", color = Brand.Muted,
                style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.CenterHorizontally))
            Text("Polices : Inter, JetBrains Mono (OFL) · Caladea (Apache 2.0) · Mikhmon (GPL v2)", color = Brand.Muted,
                style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 24.dp))
        }
    }
    report?.let { r ->
        AlertDialog(onDismissRequest = { report = null }, title = { Text("Diagnostic") }, text = { Text(r, style = MaterialTheme.typography.bodySmall) },
            confirmButton = { TextButton({
                val cm = activity.getSystemService(android.content.ClipboardManager::class.java)
                cm.setPrimaryClip(android.content.ClipData.newPlainText("Mikhmon", r)); report = null
                Toast.makeText(activity, "Rapport copié", Toast.LENGTH_SHORT).show()
            }) { Text("Copier") } },
            dismissButton = { TextButton({ report = null }) { Text("Fermer") } })
    }
    PurchaseDialogs(p)
}

@Composable
private fun Line(icon: Icon, title: String, sub: String?, color: Color = Brand.Ink, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon, if (color == Brand.Ink) Brand.Indigo else color, 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = color, fontWeight = FontWeight.SemiBold)
            if (sub != null) Text(sub, color = Brand.Muted, style = MaterialTheme.typography.bodySmall)
        }
        Text("›", color = Brand.Muted, style = MaterialTheme.typography.titleLarge)
    }
}

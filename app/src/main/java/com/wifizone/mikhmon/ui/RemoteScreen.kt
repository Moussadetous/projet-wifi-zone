package com.wifizone.mikhmon.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.sp
import com.wifizone.mikhmon.Services
import com.wifizone.mikhmon.remote.RemoteAccess
import com.wifizone.mikhmon.remote.RemotePlan
import com.wifizone.mikhmon.tickets.gfx.Icon
import com.wifizone.mikhmon.tickets.gfx.Icons
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy")

/** Prix affiché : l'article de la boutique (2 mois) et son équivalent par mois. */
fun remotePrice(itemPrice: Long?): Pair<Long, Long> { val p = itemPrice ?: 1000L; return p to p / RemotePlan.MONTHS }

/**
 * Accès au routeur depuis n'importe où (« Back To Home » de MikroTik) : compatibilité du routeur, abonnement de
 * 2 mois, activation sur le routeur (une fois, depuis son Wi-Fi), test, prolongation.
 */
@Composable
fun RemoteScreen(activity: Activity, nav: Nav) {
    val acc by Services.account.account.collectAsState()
    val support by RemoteAccess.support.collectAsState()
    val configured by RemoteAccess.configured.collectAsState()
    val up by RemoteAccess.up.collectAsState()
    val scope = rememberCoroutineScope()
    val purchase = remember { Purchase(activity, scope) }
    val settings = remember { Services.settings.load() }
    var checking by remember { mutableStateOf(support == null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var importing by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }

    val until = RemoteAccess.activeUntil(acc?.items)
    val (price, perMonth) = remotePrice(acc?.itemPrice)
    val start = RemotePlan.nextStart(acc?.items.orEmpty(), LocalDate.now())
    val onWifi = settings?.let { RemoteAccess.onRouterWifi(it) } ?: false

    LaunchedEffect(Unit) { RemoteAccess.refreshSupport(); checking = false }

    // Autorisation « VPN » d'Android, demandée une seule fois.
    val vpn = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val action = pending
        pending = null
        if (r.resultCode == Activity.RESULT_OK) action?.invoke()
        else error = "Sans cette autorisation, l'application ne peut pas ouvrir la connexion sécurisée vers votre routeur."
    }
    fun withVpn(action: () -> Unit) {
        val i = RemoteAccess.permissionIntent()
        if (i == null) action() else { pending = action; vpn.launch(i) }
    }
    fun act(kind: String, ok: (String) -> String, block: suspend () -> String) {
        busy = kind
        scope.launch {
            runCatching { block() }.onSuccess { message = ok(it) }.onFailure { error = it.message ?: "Opération impossible" }
            busy = null
        }
    }

    Column(Modifier.fillMaxSize().background(Brand.Bg).verticalScroll(rememberScrollState())) {
        BackHeader("Accès à distance", { nav.overlay = null }, "Back To Home de MikroTik")
        Column(Modifier.padding(horizontal = 16.dp)) {
            RemoteHero(price, perMonth, active = until)
            Spacer(Modifier.height(14.dp))
            val s = support
            when {
                s == null -> Panel(Modifier.fillMaxWidth()) {
                    if (checking) Loading()
                    Text("Connectez le téléphone au Wi-Fi de votre routeur : l'application vérifie s'il sait faire l'accès à distance.", color = Brand.Muted)
                }
                !s.supported -> Panel(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.LOCK, Brand.Red, 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Non disponible sur votre routeur", style = MaterialTheme.typography.titleMedium, color = Brand.Red)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(s.reason)
                    Spacer(Modifier.height(8.dp))
                    Text("L'accès à distance utilise « Back To Home », intégré à RouterOS 7.12 et plus récent sur la plupart des modèles. " +
                        "Après une mise à jour de RouterOS (Winbox : System › Packages › Check For Updates), revenez sur cet écran.",
                        color = Brand.Muted, style = MaterialTheme.typography.bodySmall)
                }
                else -> {
                    Panel(Modifier.fillMaxWidth(), padding = 6.dp) {
                        Step(Icons.CALENDAR, "Abonnement",
                            if (until != null) "Actif jusqu'au ${until.format(DAY)}" else "Inactif : ${Fmt.money(price, "F")} pour 2 mois", until != null)
                        Step(Icons.SHIELD, "Accès créé sur le routeur",
                            if (configured) "Prêt · ${RemoteAccess.deviceName}" else "À activer une fois, depuis le Wi-Fi du routeur", configured)
                        Step(Icons.SIGNAL, "Connexion en ce moment",
                            when {
                                up -> "À distance, par le tunnel sécurisé"
                                onWifi -> "Wi-Fi du routeur (directe)"
                                else -> "Hors du Wi-Fi du routeur"
                            }, up || onWifi)
                        Step(Icons.BOLT, "Routeur", "RouterOS ${s.version.substringBefore(' ')}" + if (s.status.isNotBlank()) " · ${s.status}" else "", true)
                    }
                    Spacer(Modifier.height(14.dp))
                    when {
                        until == null -> BuyArea(purchase, acc, RemotePlan.item(start), RemotePlan.label(start), "Payer ${Fmt.money(price, "F")} · 2 mois",
                            icon = Icons.GLOBE, match = { it.startsWith(RemotePlan.PREFIX) })
                        !configured -> {
                            BigButton("Activer sur le routeur", enabled = onWifi, loading = busy == "activate", icon = Icons.SHIELD) {
                                withVpn { act("activate", { "Accès à distance activé ✓\nVous pouvez maintenant joindre votre routeur depuis la 4G ou un autre Wi-Fi." }) { RemoteAccess.activate(); "" } }
                            }
                            if (!onWifi) Text("Connectez-vous au Wi-Fi du routeur pour l'activer (une seule fois).", color = Brand.Amber,
                                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                        }
                        else -> BigButton("Tester l'accès à distance", loading = busy == "test", icon = Icons.GLOBE) {
                            withVpn { act("test", { "Routeur « $it » joint à distance ✓" }) { RemoteAccess.test() } }
                        }
                    }
                    if (until != null) {
                        Spacer(Modifier.height(10.dp))
                        BuyArea(purchase, acc, RemotePlan.item(start), RemotePlan.label(start),
                            "Prolonger de 2 mois · ${Fmt.money(price, "F")} (dès le ${start.format(DAY)})", brush = Brand.Hero,
                            icon = Icons.CALENDAR, match = { it.startsWith(RemotePlan.PREFIX) })
                    }
                    SectionTitle("Comment ça marche")
                    Panel(Modifier.fillMaxWidth()) {
                        Text("• Votre routeur se connecte lui-même au relais de MikroTik : il est joignable même derrière une box ou une clé 4G.", style = MaterialTheme.typography.bodySmall)
                        Text("• Hors du Wi-Fi du routeur, l'application passe automatiquement par une connexion chiffrée (WireGuard). " +
                            "Seule l'application l'utilise, et seulement pour joindre votre routeur : votre téléphone n'est pas ralenti.", style = MaterialTheme.typography.bodySmall)
                        Text("• Tableau de bord, clients, tickets, impression et Mikhmon complet fonctionnent comme sur place.", style = MaterialTheme.typography.bodySmall)
                        Text("• ${Fmt.money(perMonth, "F")} par mois, payé par périodes de 2 mois (${Fmt.money(price, "F")}). Rien n'est renouvelé sans vous.", style = MaterialTheme.typography.bodySmall)
                    }
                    SectionTitle("Avancé")
                    Panel(Modifier.fillMaxWidth(), padding = 4.dp) {
                        if (until != null) RemoteLine(Icons.KEY, "Importer une configuration Back To Home", "Copiée depuis Winbox (IP › Cloud › Back To Home)") { importing = true }
                        if (configured) RemoteLine(Icons.LOCK, "Désactiver sur ce téléphone", "Retire l'accès de ce téléphone du routeur", Brand.Red) {
                            act("off", { "Accès à distance désactivé sur ce téléphone." }) { RemoteAccess.deactivate(); "" }
                        }
                        RemoteLine(Icons.SIGNAL, "Vérifier à nouveau le routeur", null) { scope.launch { checking = true; RemoteAccess.refreshSupport(); checking = false } }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (importing) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { importing = false },
            title = { Text("Configuration Back To Home") },
            text = {
                Column {
                    Text("Collez la configuration WireGuard ([Interface] … [Peer] …) d'un utilisateur Back To Home de votre routeur.", color = Brand.Muted)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(text, { text = it }, minLines = 6, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton({
                    runCatching { RemoteAccess.importConfig(text) }.onSuccess { message = "Configuration importée ✓"; importing = false }.onFailure { error = it.message }
                }) { Text("Importer") }
            },
            dismissButton = { TextButton({ importing = false }) { Text("Annuler") } },
        )
    }
    message?.let { m -> AlertDialog(onDismissRequest = { message = null }, title = { Text("Accès à distance") }, text = { Text(m) },
        confirmButton = { TextButton({ message = null }) { Text("OK") } }) }
    error?.let { e -> AlertDialog(onDismissRequest = { error = null }, title = { Text("Accès à distance") }, text = { Text(e) },
        confirmButton = { TextButton({ error = null }) { Text("OK") } }) }
    PurchaseDialogs(purchase)
}

/** Bandeau publicitaire de l'accès à distance (accueil, écran dédié). */
@Composable
fun RemoteHero(price: Long, perMonth: Long, modifier: Modifier = Modifier, active: LocalDate? = null, onClick: (() -> Unit)? = null) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Brand.Hero)
        .let { if (onClick != null) it.clickable(onClick = onClick) else it }.padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                VIcon(Icons.GLOBE, Color.White, size = 28.dp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(if (active != null) "ACTIF JUSQU'AU ${active.format(DAY)}" else "NOUVEAU", color = Color.White.copy(alpha = 0.85f),
                    fontWeight = FontWeight.Black, letterSpacing = 2.sp, fontSize = 11.sp)
                Text("Votre routeur, où que vous soyez", color = Color.White, fontWeight = FontWeight.Black, fontSize = 19.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("Ventes, clients, tickets et Mikhmon complet depuis la 4G ou n'importe quel Wi-Fi.", color = Color.White.copy(alpha = 0.92f))
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(Fmt.money(perMonth, "F"), color = Color.White, fontWeight = FontWeight.Black, fontSize = 28.sp)
            Text("/ mois", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
            Spacer(Modifier.weight(1f))
            Text("payé par 2 mois · ${Fmt.money(price, "F")}", color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(bottom = 6.dp))
        }
    }
}

@Composable
private fun Step(icon: Icon, title: String, text: String, ok: Boolean) {
    Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon, if (ok) Brand.Green else Brand.Muted, 38.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(text, color = Brand.Muted, style = MaterialTheme.typography.bodySmall)
        }
        if (ok) VIcon(Icons.CHECK, Brand.Green, size = 20.dp)
    }
}

@Composable
private fun RemoteLine(icon: Icon, title: String, sub: String?, color: Color = Brand.Ink, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon, if (color == Brand.Ink) Brand.Indigo else color, 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = color, fontWeight = FontWeight.SemiBold)
            if (sub != null) Text(sub, color = Brand.Muted, style = MaterialTheme.typography.bodySmall)
        }
        Text("›", color = Brand.Muted, style = MaterialTheme.typography.titleLarge)
    }
}

package com.wifizone.mikhmon.ui

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.telephony.TelephonyManager
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wifizone.mikhmon.Services
import com.wifizone.mikhmon.account.MproClient
import com.wifizone.mikhmon.tickets.gfx.Icons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Achat en un geste : solde suffisant = immédiat ; sinon page de paiement, et l'achat se termine tout seul. */
class Purchase(private val activity: Activity, private val scope: CoroutineScope) {
    var busy by mutableStateOf(false)
    var waiting by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var askPhone by mutableStateOf<Pair<String, String>?>(null)
    var onDone: (() -> Unit)? = null

    fun buy(item: String, itemName: String = "", phone: String? = null) {
        if (busy) return
        busy = true
        scope.launch {
            runCatching { Services.account.checkout(item, itemName, phone) }
                .onSuccess { r ->
                    when (r) {
                        com.wifizone.mikhmon.account.MproCore.Checkout.Done -> { Toast.makeText(activity, "Achat activé ✓", Toast.LENGTH_SHORT).show(); onDone?.invoke() }
                        is com.wifizone.mikhmon.account.MproCore.Checkout.Pay -> { waiting = true; Services.account.openBrowser(activity, Uri.parse(r.url)) }
                        is com.wifizone.mikhmon.account.MproCore.Checkout.NeedPhone -> askPhone = item to itemName
                        is com.wifizone.mikhmon.account.MproCore.Checkout.NeedEmail -> error = r.message
                    }
                }
                .onFailure { error = it.message ?: "Paiement impossible pour le moment." }
            busy = false
        }
    }

    /** « J'ai payé » : la boutique vérifie et termine l'achat. */
    fun check() {
        scope.launch {
            busy = true
            repeat(6) {
                val a = runCatching { Services.account.checkoutStatus() }.getOrNull()
                if (a?.pending == null) { waiting = false; busy = false; return@launch }
                delay(2500)
            }
            busy = false
            error = "Le paiement n'est pas encore confirmé. S'il a été fait, il sera pris en compte automatiquement dans quelques minutes."
        }
    }
}

private val DIAL = mapOf("bf" to "+226", "ci" to "+225", "sn" to "+221", "ml" to "+223", "tg" to "+228", "bj" to "+229", "ne" to "+227",
    "cm" to "+237", "gn" to "+224", "ga" to "+241", "cg" to "+242", "cd" to "+243", "td" to "+235", "cf" to "+236", "mr" to "+222", "gh" to "+233", "ng" to "+234")

fun dialPrefix(ctx: Context): String {
    val tm = ctx.getSystemService(TelephonyManager::class.java)
    val iso = (tm?.simCountryIso?.ifBlank { null } ?: tm?.networkCountryIso?.ifBlank { null } ?: java.util.Locale.getDefault().country).lowercase()
    return DIAL[iso] ?: "+"
}

@Composable
fun PurchaseDialogs(p: Purchase) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    p.askPhone?.let { (item, name) ->
        var phone by remember { mutableStateOf(dialPrefix(ctx) + " ") }
        AlertDialog(
            onDismissRequest = { p.askPhone = null },
            title = { Text("Numéro Mobile Money") },
            text = {
                Column {
                    Text("Le numéro qui va payer (Orange Money, Moov, Wave…), avec l'indicatif du pays. Il n'est demandé qu'une fois.", color = Brand.Muted)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(phone, { phone = it }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = { TextButton({ p.askPhone = null; p.buy(item, name, phone.trim()) }) { Text("Continuer") } },
            dismissButton = { TextButton({ p.askPhone = null }) { Text("Annuler") } },
        )
    }
    p.error?.let { msg ->
        AlertDialog(onDismissRequest = { p.error = null }, title = { Text("Paiement") }, text = { Text(msg) },
            confirmButton = { TextButton({ p.error = null }) { Text("OK") } })
    }
}

/** Accès à l'application : compte obligatoire, puis essai gratuit ou accès à vie. */
@Composable
fun Gate(activity: Activity, acc: com.wifizone.mikhmon.account.MproCore.Account?, onRouterSettings: () -> Unit, onSkip: (() -> Unit)? = null) {
    val scope = rememberCoroutineScope()
    val p = remember { Purchase(activity, scope) }
    var connecting by remember { mutableStateOf(false) }
    var discoverError by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().background(Brand.Bg).verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(30.dp))
        Box(Modifier.size(96.dp).clip(CircleShape).background(Brand.Hero), contentAlignment = Alignment.Center) {
            VIcon(if (acc == null) Icons.USER else if (acc.status == "blocked") Icons.LOCK else Icons.CROWN, Color.White, size = 46.dp)
        }
        Spacer(Modifier.height(22.dp))
        when {
            acc == null -> {
                Text("Connectez votre compte", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text("Votre routeur est bien connecté ✓\nConnectez-vous avec votre compte MPro Digital (Google ou e-mail). Si vous êtes déjà connecté à la boutique sur ce téléphone, il est reconnu tout seul.",
                    style = MaterialTheme.typography.bodyLarge, color = Brand.Muted, textAlign = TextAlign.Center)
                Spacer(Modifier.height(20.dp))
                Perks(listOf("7 jours d'essai gratuit, sans engagement", "Votre accès suit votre compte, même si vous changez de téléphone", "Paiement simple par Mobile Money depuis la boutique"))
                Spacer(Modifier.height(24.dp))
                BigButton("Continuer avec mon compte", loading = connecting, icon = Icons.USER) {
                    connecting = true
                    scope.launch {
                        val ok = runCatching { Services.account.discover() }.getOrDefault(false)
                        connecting = false
                        if (ok) Services.account.startLogin(activity)
                        else discoverError = "La boutique est injoignable. Une connexion internet est nécessaire une seule fois pour activer votre compte (données mobiles ou Wi-Fi avec internet)."
                    }
                }
                discoverError?.let { Spacer(Modifier.height(12.dp)); ErrorNote(it) }
                val grace = remember { Services.account.graceDaysLeft() }
                if (onSkip != null && grace > 0) {
                    TextButton(onSkip) { Text("Continuer sans compte pour l'instant (encore $grace jour${if (grace > 1) "s" else ""})", color = Brand.Muted, textAlign = TextAlign.Center) }
                }
            }
            acc.status == "blocked" -> {
                Text("Accès suspendu", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(8.dp))
                Text("L'accès de ce compte (${acc.email}) est suspendu. Contactez le support de la boutique.", color = Brand.Muted, textAlign = TextAlign.Center)
                Spacer(Modifier.height(20.dp))
                BigButton("Utiliser un autre compte") { scope.launch { Services.account.logout(); Services.account.startLogin(activity, chooseAccount = true) } }
            }
            else -> {
                Text(if (acc.status == "trial") "Votre essai est en cours" else "Votre essai gratuit est terminé", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text("Activez l'accès à vie : vous payez une seule fois et il reste lié à votre compte, sur tous vos appareils.",
                    style = MaterialTheme.typography.bodyLarge, color = Brand.Muted, textAlign = TextAlign.Center)
                Spacer(Modifier.height(18.dp))
                Panel(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.CROWN, Brand.Amber, 48.dp, Brand.Premium)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Accès à vie", style = MaterialTheme.typography.titleLarge)
                            Text("Tableau de bord, clients, trafic, tickets, Mikhmon", color = Brand.Muted, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(Fmt.money(acc.lifetimePrice, "F"), fontWeight = FontWeight.Black, fontSize = 22.sp)
                    }
                    if (acc.balance > 0) Text("Solde de votre compte : ${Fmt.money(acc.balance, "F")}", color = Brand.Green, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 10.dp))
                }
                Spacer(Modifier.height(18.dp))
                if (p.waiting || acc.pending != null) {
                    Text("Paiement en cours de vérification…", color = Brand.Indigo, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))
                    BigButton("J'ai payé : vérifier", loading = p.busy) { p.check() }
                    TextButton({ p.buy("lifetime") }) { Text("Rouvrir la page de paiement") }
                } else {
                    BigButton("Activer l'accès à vie — ${Fmt.money(acc.lifetimePrice, "F")}", brush = Brand.Premium, loading = p.busy, icon = Icons.CROWN) { p.buy("lifetime") }
                }
                Spacer(Modifier.height(8.dp))
                Text("Connecté : ${acc.name} · ${acc.email}", color = Brand.Muted, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                TextButton({ scope.launch { Services.account.logout() } }) { Text("Changer de compte") }
            }
        }
        Spacer(Modifier.height(14.dp))
        TextButton(onRouterSettings) { Text("Paramètres du routeur", color = Brand.Muted) }
    }
    PurchaseDialogs(p)
}

@Composable
fun Perks(items: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        items.forEach {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(26.dp).clip(CircleShape).background(Brand.Green.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                    VIcon(Icons.CHECK, Brand.Green, size = 15.dp)
                }
                Spacer(Modifier.width(10.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun Loading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Brand.Indigo) }
}

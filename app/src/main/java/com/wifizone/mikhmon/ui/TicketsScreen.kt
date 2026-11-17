@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.wifizone.mikhmon.ui

import android.app.Activity
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wifizone.mikhmon.Defaults
import com.wifizone.mikhmon.Services
import com.wifizone.mikhmon.router.HsUser
import com.wifizone.mikhmon.router.Profile
import com.wifizone.mikhmon.tickets.Vouchers
import com.wifizone.mikhmon.tickets.gfx.Icons
import kotlinx.coroutines.launch

/** Lot de tickets Mikhmon (même commentaire). */
data class Batch(val comment: String, val users: List<HsUser>) {
    private val parts = comment.split("-")
    val date get() = parts.getOrNull(2)?.let { d -> d.split(".").let { if (it.size == 3) "${it[1]}/${it[0]}/20${it[2]}" else d } }.orEmpty()
    val note get() = parts.drop(3).joinToString("-").trim()
    val used get() = users.count { it.used }
    val profile get() = users.groupingBy { it.profile }.eachCount().maxByOrNull { it.value }?.key.orEmpty()
}

fun mikhmonPrintPath(comment: String, qr: Boolean): String {
    val session = Defaults.sessionName(Services.settings.load()?.sessionName ?: "")
    return "/voucher/print.php?id=${Uri.encode(comment)}&qr=${if (qr) "yes" else "no"}&session=${Uri.encode(session)}"
}

@Composable
fun TicketsScreen(activity: Activity, nav: Nav) {
    val repo = Services.router
    var reload by remember { mutableIntStateOf(0) }
    val profiles by polling(60000, reload) { repo.profiles() }
    val users by polling(30000, reload) { repo.users() }
    val scope = rememberCoroutineScope()

    var profile by remember { mutableStateOf<Profile?>(null) }
    LaunchedEffect(profiles) { if (profile == null) profile = profiles?.getOrNull()?.firstOrNull { it.price != null } ?: profiles?.getOrNull()?.firstOrNull() }
    var qty by remember { mutableIntStateOf(20) }
    var userPass by remember { mutableStateOf(false) }
    var length by remember { mutableIntStateOf(6) }
    var charset by remember { mutableStateOf(Vouchers.Charset.MIX_UPPER) }
    var prefix by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var advanced by remember { mutableStateOf(false) }
    var limitUptime by remember { mutableStateOf("") }
    var limitData by remember { mutableStateOf("") }
    var progress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var created by remember { mutableStateOf<Batch?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun generate() {
        val p = profile ?: run { error = "Choisissez un profil."; return }
        error = null
        scope.launch {
            runCatching {
                val existing = repo.users().map { it.name }.toSet()
                val list = Vouchers.generate(Vouchers.Spec(qty, userPass, length, charset, prefix.trim()), existing)
                val comment = Vouchers.mikhmonComment(userPass, note)
                progress = 0 to list.size
                val bytes = limitData.trim().replace(',', '.').toDoubleOrNull()?.let { (it * 1024 * 1024 * 1024).toLong() } ?: 0L
                repo.addUsers(list, p.name, comment, limitUptime.trim(), bytes) { n -> progress = n to list.size }
                Batch(comment, repo.users(comment))
            }.onSuccess { created = it; reload++ }.onFailure { error = it.message ?: "Création impossible" }
            progress = null
        }
    }

    Column(Modifier.fillMaxSize().background(Brand.Bg).verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Tickets", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionCard("Studio premium", "46 modèles, PDF", Icons.SPARKLE, Brand.Premium, Modifier.weight(1f)) {
                val b = created ?: batches(users?.getOrNull()).firstOrNull()
                if (b != null) nav.open(Overlay.Studio(b.note.ifBlank { "Lot du ${b.date}" }, b.users))
                else Toast.makeText(activity, "Générez d'abord des tickets (ci-dessous).", Toast.LENGTH_LONG).show()
            }
            ActionCard("Mikhmon standard", "Impression d'origine", Icons.TICKET, androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF475569), Color(0xFF1E293B))), Modifier.weight(1f)) {
                val b = created ?: batches(users?.getOrNull()).firstOrNull()
                if (b != null) nav.open(Overlay.MikhmonUrl(mikhmonPrintPath(b.comment, qr = true))) else nav.tab = Tab.MIKHMON
            }
        }

        created?.let { b ->
            Spacer(Modifier.height(14.dp))
            Panel(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(Brand.Green.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) { VIcon(Icons.CHECK, Brand.Green, size = 22.dp) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${b.users.size} tickets créés", style = MaterialTheme.typography.titleMedium)
                        Text("Profil ${b.profile} · ${b.comment}", color = Brand.Muted, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    }
                }
                Spacer(Modifier.height(12.dp))
                BigButton("Imprimer en premium", brush = Brand.Premium, icon = Icons.SPARKLE) { nav.open(Overlay.Studio(b.note.ifBlank { "Nouveau lot" }, b.users)) }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ nav.open(Overlay.MikhmonUrl(mikhmonPrintPath(b.comment, qr = false))) }, Modifier.weight(1f)) { Text("Mikhmon standard") }
                    OutlinedButton({ nav.open(Overlay.MikhmonUrl(mikhmonPrintPath(b.comment, qr = true))) }, Modifier.weight(1f)) { Text("Mikhmon + QR") }
                }
            }
        }

        SectionTitle("Générer des tickets")
        Panel(Modifier.fillMaxWidth()) {
            Text("Profil", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            val list = profiles?.getOrNull()
            if (list == null) { if (profiles?.isFailure == true) ErrorNote("Profils indisponibles : ${profiles?.exceptionOrNull()?.message}") else Loading() }
            else FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                list.filter { it.name != "default" || list.size == 1 }.forEach { p ->
                    val label = listOfNotNull(p.name, p.price?.let { Vouchers.price(it) + " F" }, Vouchers.duration(p.validity)).joinToString(" · ")
                    SelectChip(label, profile?.name == p.name) { profile = p }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Quantité", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Stepper("−") { qty = (qty - if (qty > 20) 10 else 1).coerceAtLeast(1) }
                Text("$qty", fontSize = 26.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(78.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Stepper("+") { qty = (qty + if (qty >= 20) 10 else 1).coerceAtMost(500) }
                Spacer(Modifier.width(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(10, 50, 100).forEach { n -> SelectChip("$n", qty == n) { qty = n } } }
            }
            Spacer(Modifier.height(16.dp))
            Segmented(listOf("Code unique", "Utilisateur + mot de passe"), if (userPass) 1 else 0, { userPass = it == 1 })
            Spacer(Modifier.height(14.dp))
            Text("Longueur du code : $length caractères", fontWeight = FontWeight.SemiBold)
            Slider(length.toFloat(), { length = it.toInt() }, valueRange = 4f..10f, steps = 5)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Vouchers.Charset.values().forEach { c -> SelectChip(c.label, charset == c) { charset = c } }
            }
            Spacer(Modifier.height(10.dp))
            Text("Exemple : " + (prefix.trim() + Vouchers.generate(Vouchers.Spec(1, false, length, charset), emptySet()).first().first.removePrefix(prefix.trim())),
                color = Brand.Muted, fontFamily = rememberMono())
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(prefix, { prefix = it.filter { ch -> ch.isLetterOrDigit() || ch == '-' }.take(6) }, label = { Text("Préfixe") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(note, { note = it.take(30) }, label = { Text("Nom du lot") }, singleLine = true, modifier = Modifier.weight(1.4f))
            }
            Text(if (advanced) "Limites ▴" else "Limites (facultatif) ▾", color = Brand.Indigo, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 10.dp).clip(RoundedCornerShape(8.dp)).clickable { advanced = !advanced }.padding(6.dp))
            if (advanced) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(limitUptime, { limitUptime = it.lowercase().filter { ch -> ch.isDigit() || ch in "wdhms" } }, label = { Text("Temps (ex. 3h, 1d)") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(limitData, { limitData = it.filter { ch -> ch.isDigit() || ch == ',' || ch == '.' } }, label = { Text("Données (Go)") }, singleLine = true,
                    modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            }
            Spacer(Modifier.height(16.dp))
            progress?.let { (n, total) ->
                Text("Création sur le routeur : $n / $total", color = Brand.Indigo, fontWeight = FontWeight.SemiBold)
                LinearProgressIndicator(progress = { if (total == 0) 0f else n / total.toFloat() }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
            }
            error?.let { ErrorNote(it); Spacer(Modifier.height(8.dp)) }
            BigButton("Créer $qty ticket${if (qty > 1) "s" else ""}", loading = progress != null, icon = Icons.TICKET) { generate() }
        }

        SectionTitle("Lots récents")
        val bs = batches(users?.getOrNull())
        if (users == null) Loading()
        else if (bs.isEmpty()) Panel(Modifier.fillMaxWidth()) { Text("Aucun lot pour l'instant. Les tickets créés par l'application ou par Mikhmon apparaissent ici.", color = Brand.Muted) }
        else bs.take(30).forEach { b ->
            Panel(Modifier.fillMaxWidth().padding(bottom = 10.dp), padding = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.TICKET, Brand.Indigo, 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(b.note.ifBlank { "Lot du ${b.date}" }, fontWeight = FontWeight.Bold)
                        Text("${b.users.size} tickets · ${b.used} utilisé(s) · ${b.profile} · ${b.date}", color = Brand.Muted, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(Brand.Premium).clickable { nav.open(Overlay.Studio(b.note.ifBlank { "Lot du ${b.date}" }, b.users)) }.padding(10.dp), contentAlignment = Alignment.Center) {
                        Text("Premium ✦", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton({ nav.open(Overlay.MikhmonUrl(mikhmonPrintPath(b.comment, qr = true))) }, Modifier.weight(1f)) { Text("Standard") }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

fun batches(users: List<HsUser>?): List<Batch> =
    users.orEmpty().filter { it.batch.isNotEmpty() }.groupBy { it.batch }.map { Batch(it.key, it.value) }
        .sortedByDescending { b -> b.comment.split("-").getOrNull(2)?.split(".")?.let { "${it.getOrNull(2)}${it.getOrNull(0)}${it.getOrNull(1)}" } ?: "" }

@Composable
private fun Stepper(label: String, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFEEF1F7)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(label, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Brand.Indigo)
    }
}

@Composable
private fun ActionCard(title: String, sub: String, icon: com.wifizone.mikhmon.tickets.gfx.Icon, brush: androidx.compose.ui.graphics.Brush, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(22.dp)).background(brush).clickable(onClick = onClick).padding(16.dp)) {
        VIcon(icon, Color.White, size = 30.dp)
        Spacer(Modifier.height(12.dp))
        Text(title, color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp)
        Text(sub, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall)
    }
}

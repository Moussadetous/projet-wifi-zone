package com.wifizone.mikhmon.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.wifizone.mikhmon.Services
import com.wifizone.mikhmon.account.MproCore
import com.wifizone.mikhmon.tickets.gfx.Icons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Partage de l'application avec le code de parrainage : le fichier de l'application (WhatsApp, Bluetooth…) ou le lien. */
object Share {
    fun message(r: MproCore.Referral?): String = buildString {
        append("📶 Je gère ma Wi-Fi Zone MikroTik avec Mikhmon Android : tickets, ventes, clients connectés… et même à distance.\n")
        if (!r?.shareUrl.isNullOrBlank()) append("Téléchargez-la : ${r!!.shareUrl}\n")
        if (r != null) append("Après la connexion, entrez mon code de parrainage : ${r.code} (Compte › Parrainage).")
    }

    fun text(ctx: Context, r: MproCore.Referral?) {
        val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, message(r))
        ctx.startActivity(Intent.createChooser(i, "Partager Mikhmon Android").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Envoie l'application elle-même (fichier .apk installé sur ce téléphone) avec le message et le code. */
    suspend fun apk(ctx: Context, r: MproCore.Referral?) {
        val file = withContext(Dispatchers.IO) {
            val dir = File(ctx.cacheDir, "partage").apply { mkdirs() }
            File(ctx.applicationInfo.sourceDir).copyTo(File(dir, "Mikhmon-Android.apk"), overwrite = true)
        }
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
        val i = Intent(Intent.ACTION_SEND).setType("application/vnd.android.package-archive")
            .putExtra(Intent.EXTRA_STREAM, uri).putExtra(Intent.EXTRA_TEXT, message(r))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(i, "Partager l'application").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }
}

/**
 * Parrainage : chaque compte a un code. Tous les [MproCore.Referral.goal] amis validés (ils saisissent le code et
 * connectent LEUR routeur), [MproCore.Referral.months] mois d'accès à distance offert, ajouté tout seul.
 */
@Composable
fun ReferralScreen(activity: Activity, nav: Nav) {
    val acc by Services.account.account.collectAsState()
    val scope = rememberCoroutineScope()
    var sharing by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { runCatching { Services.account.refresh() }; refreshed = true }
    val r = acc?.referral
    val goal = r?.goal ?: 4
    val months = r?.months ?: 1
    val monthsText = if (months > 1) "$months mois" else "1 mois"

    Column(Modifier.fillMaxSize().background(Brand.Bg).verticalScroll(rememberScrollState())) {
        BackHeader("Parrainage", { nav.overlay = null }, "Partagez l'application, gagnez l'accès à distance")
        Column(Modifier.padding(horizontal = 16.dp)) {
            // Promesse
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Brand.Premium).padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    VIcon(Icons.GIFT, Color.White, size = 30.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("$goal amis = $monthsText offert", color = Color.White, fontWeight = FontWeight.Black, fontSize = 24.sp)
                }
                Spacer(Modifier.height(6.dp))
                Text("Partagez Mikhmon Android avec votre code. Chaque fois que $goal amis l'utilisent avec leur routeur, " +
                    "vous gagnez $monthsText d'accès à distance gratuit. Sans limite.", color = Color.White.copy(alpha = 0.95f))
            }
            Spacer(Modifier.height(14.dp))

            if (acc == null) {
                Panel(Modifier.fillMaxWidth()) { Text("Connectez-vous à votre compte pour obtenir votre code de parrainage.", color = Brand.Muted) }
            } else if (r == null) {
                Panel(Modifier.fillMaxWidth()) {
                    if (!refreshed) Loading()
                    else Text("Le parrainage n'est pas encore disponible. Vérifiez votre connexion Internet, puis revenez sur cet écran.", color = Brand.Muted)
                }
            } else {
                // Code + partage
                Panel(Modifier.fillMaxWidth()) {
                    Text("Votre code de parrainage", color = Brand.Muted, style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(r.code, fontFamily = rememberMono(), fontWeight = FontWeight.Bold, fontSize = 34.sp, letterSpacing = 4.sp,
                            color = Brand.Indigo, modifier = Modifier.weight(1f))
                        OutlinedButton({
                            activity.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Code", r.code))
                            Toast.makeText(activity, "Code copié", Toast.LENGTH_SHORT).show()
                        }) { Text("Copier") }
                    }
                    Spacer(Modifier.height(12.dp))
                    BigButton("Partager l'application", icon = Icons.DOWNLOAD, loading = sharing) {
                        sharing = true
                        scope.launch {
                            runCatching { Share.apk(activity, r) }.onFailure { Share.text(activity, r) }
                            sharing = false
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton({ Share.text(activity, r) }, Modifier.fillMaxWidth()) { Text("Envoyer seulement le message et le code") }
                    Text("« Partager l'application » envoie le fichier de l'application (WhatsApp, Bluetooth, Telegram…) avec votre code.",
                        color = Brand.Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                }

                // Progression
                SectionTitle("Vos filleuls")
                Panel(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        repeat(goal.coerceAtMost(10)) { i ->
                            val on = i < r.progress
                            Box(Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(12.dp))
                                .background(if (on) Brand.Green else Brand.Line), contentAlignment = Alignment.Center) {
                                if (on) VIcon(Icons.CHECK, Color.White, size = 22.dp)
                                else Text("${i + 1}", color = Brand.Muted, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    val left = goal - r.progress
                    Text("${r.progress} / $goal amis validés · encore $left pour votre prochain mois offert", fontWeight = FontWeight.SemiBold)
                    if (r.rewarded > 0) Text("Déjà gagné : ${r.rewarded * months} mois d'accès à distance ✓ (${r.valid} filleuls validés)",
                        color = Brand.Green, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                    if (r.children.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        r.children.forEach { c ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(32.dp).clip(CircleShape).background(if (c.valid) Brand.Green else Brand.Amber), contentAlignment = Alignment.Center) {
                                    Text(c.name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(c.name, fontWeight = FontWeight.SemiBold)
                                    Text(if (c.valid) "Validé ✓" else "En attente : il doit connecter son routeur MikroTik à l'application",
                                        color = if (c.valid) Brand.Green else Brand.Muted, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    } else Text("Personne n'a encore utilisé votre code. Partagez-le !", color = Brand.Muted,
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                }

                // Comment ça marche
                SectionTitle("Comment ça marche")
                Panel(Modifier.fillMaxWidth()) {
                    HowStep(1, "Partagez l'application", "Touchez « Partager l'application » et envoyez-la à vos amis promoteurs de Wi-Fi Zone, avec votre code.")
                    HowStep(2, "Votre ami saisit votre code", "Il installe Mikhmon Android, se connecte avec son compte, puis saisit votre code dans Compte › Parrainage " +
                        "(dans les 30 jours qui suivent sa première connexion).")
                    HowStep(3, "Il connecte SON routeur", "Dès que l'application voit son propre routeur MikroTik, il devient un filleul validé. " +
                        "Un même routeur ne compte qu'une fois (le vôtre ne compte pas).")
                    HowStep(4, "Vous gagnez $monthsText", "À chaque groupe de $goal filleuls validés, $monthsText d'accès à distance est ajouté tout seul à votre compte, " +
                        "à la suite de votre abonnement s'il est en cours. Il s'active comme un abonnement payé.", last = true)
                    Text("L'accès à distance demande un routeur MikroTik compatible « Back To Home » (RouterOS 7.12 ou plus récent).",
                        color = Brand.Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                }

                // Code d'un parrain
                SectionTitle("Un ami vous a invité ?")
                Panel(Modifier.fillMaxWidth()) {
                    when {
                        r.referredBy != null -> {
                            Text("Parrainé par ${r.referredBy} ✓", fontWeight = FontWeight.Bold)
                            Text(if (r.referredValid) "Votre routeur est connecté : votre parrain a bien gagné son filleul. Merci !"
                                else "Connectez votre routeur MikroTik à l'application pour que votre parrain gagne son filleul.",
                                color = Brand.Muted, style = MaterialTheme.typography.bodySmall)
                        }
                        r.canUseCode -> {
                            Text("Saisissez le code de la personne qui vous a fait découvrir l'application.", color = Brand.Muted, style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(code, { code = it.uppercase().filter { ch -> ch.isLetterOrDigit() }.take(6); error = null },
                                label = { Text("Code du parrain (6 caractères)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters))
                            error?.let { ErrorNote(it, Modifier.padding(top = 6.dp)) }
                            Spacer(Modifier.height(8.dp))
                            BigButton("Valider le code", enabled = code.length == 6, loading = sending) {
                                sending = true
                                scope.launch {
                                    runCatching { Services.account.useReferralCode(code) }
                                        .onSuccess { Toast.makeText(activity, "Code enregistré ✓ Merci !", Toast.LENGTH_LONG).show(); code = "" }
                                        .onFailure { error = it.message ?: "Code refusé" }
                                    sending = false
                                }
                            }
                        }
                        else -> Text("Le code d'un parrain se saisit dans les 30 jours qui suivent la première connexion : ce délai est passé pour ce compte.",
                            color = Brand.Muted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun HowStep(n: Int, title: String, text: String, last: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(bottom = if (last) 0.dp else 12.dp)) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(Brand.Hero), contentAlignment = Alignment.Center) {
            Text("$n", color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(text, color = Brand.Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Bandeau compact (accueil, accès à distance) qui mène au parrainage. */
@Composable
fun ReferralBanner(modifier: Modifier = Modifier, onClick: () -> Unit) {
    val acc by Services.account.account.collectAsState()
    val r = acc?.referral ?: return
    val months = if (r.months > 1) "${r.months} mois" else "1 mois"
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White).clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        IconBadge(Icons.GIFT, Brand.Pink, 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(if (r.canUseCode && r.referredBy == null) "Invité par un ami ? Saisissez son code" else "Partagez l'application : ${r.goal} amis = $months offert",
                fontWeight = FontWeight.Bold)
            Text(if (r.progress > 0 || r.rewarded > 0) "${r.progress} / ${r.goal} amis validés · votre code : ${r.code}" else "Votre code : ${r.code} · accès à distance gratuit",
                color = Brand.Muted, style = MaterialTheme.typography.bodySmall)
        }
        Text("›", color = Brand.Muted, style = MaterialTheme.typography.titleLarge)
    }
}

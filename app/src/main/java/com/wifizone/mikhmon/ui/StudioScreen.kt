@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.wifizone.mikhmon.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.wifizone.mikhmon.Services
import com.wifizone.mikhmon.print.PdfExporter
import com.wifizone.mikhmon.print.Previews
import com.wifizone.mikhmon.router.HsUser
import com.wifizone.mikhmon.tickets.Branding
import com.wifizone.mikhmon.tickets.Catalog
import com.wifizone.mikhmon.tickets.Category
import com.wifizone.mikhmon.tickets.PaperFormat
import com.wifizone.mikhmon.tickets.PrintSettings
import com.wifizone.mikhmon.tickets.Sheet
import com.wifizone.mikhmon.tickets.TicketOptions
import com.wifizone.mikhmon.tickets.TicketTemplate
import com.wifizone.mikhmon.tickets.Voucher
import com.wifizone.mikhmon.tickets.Vouchers
import com.wifizone.mikhmon.tickets.gfx.Icons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val SWATCHES = listOf(0xFF2563EB, 0xFF4338CA, 0xFF7C3AED, 0xFFDB2777, 0xFFE11D48, 0xFFEA580C, 0xFFD97706, 0xFF9A6B12,
    0xFF16A34A, 0xFF0D9488, 0xFF0891B2, 0xFF0F172A, 0xFF78350F, 0xFF4A0E1C).map { it.toInt() }

/** Studio premium : choix du modèle, personnalisation, aperçu et export PDF natif. */
@Composable
fun StudioScreen(activity: Activity, nav: Nav, studio: Overlay.Studio) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val design = Services.design
    val settings = remember { Services.settings.load() }
    val acc by Services.account.account.collectAsState()
    val purchase = remember { Purchase(activity, scope) }

    val profiles by produceState<List<com.wifizone.mikhmon.router.Profile>?>(null) { value = runCatching { Services.router.profiles() }.getOrNull() ?: emptyList() }
    val dnsRouter by produceState(settings?.dnsName.orEmpty()) { if (value.isBlank()) value = runCatching { Services.router.hotspotDns() }.getOrDefault("") }

    var templateId by remember { mutableStateOf(design.templateId.takeIf { Catalog.byId(it) != null } ?: "std-standard") }
    val template = Catalog.byId(templateId) ?: Catalog.all.first()
    var options by remember(templateId) { mutableStateOf(design.options(templateId)) }
    var branding by remember(dnsRouter) { mutableStateOf(design.branding(settings?.hotspotName.orEmpty(), dnsRouter, settings?.currency?.ifBlank { null } ?: "F")) }
    var print by remember { mutableStateOf(design.print) }
    var category by remember { mutableStateOf<Category?>(null) }
    var exporting by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<PdfExporter.Result?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val vouchers = remember(studio.users, profiles) { toVouchers(studio.users, profiles.orEmpty()) }
    val owned = !template.premium || (acc?.items?.contains(template.id) == true)

    LaunchedEffect(templateId, options, branding, print) {
        design.templateId = templateId; design.saveOptions(templateId, options); design.saveBranding(branding); design.print = print
    }

    val logoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { withContext(Dispatchers.IO) { design.setLogo(uri) }; branding = branding.copy(logo = design.logo()) }
    }
    fun doExport() {
        exporting = true
        scope.launch {
            runCatching { PdfExporter.export(ctx, template, vouchers, branding, options, print) }
                .onSuccess { result = it }.onFailure { error = it.message ?: "Export impossible" }
            exporting = false
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) doExport() else error = "Sans l'autorisation de stockage, le PDF ne peut pas être enregistré dans Téléchargements."
    }

    Column(Modifier.fillMaxSize().background(Brand.Bg)) {
        BackHeader("Studio premium", { nav.overlay = null }, "${studio.title} · ${vouchers.size} ticket(s)")
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            // Aperçu du modèle choisi, avec le premier ticket du lot.
            val sample = vouchers.firstOrNull() ?: Voucher("K7M4Q2", null, "", "500", "24 h", "2 Go", serial = 1)
            val preview by produceState<Bitmap?>(null, templateId, options, branding) {
                delay(60)
                value = withContext(Dispatchers.Default) { Previews.ticket(ctx, template, sample, branding, options, 1100) }
            }
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color(0xFFE6EAF2)).padding(18.dp), contentAlignment = Alignment.Center) {
                val bmp = preview
                if (bmp != null) Image(bmp.asImageBitmap(), contentDescription = template.name,
                    modifier = Modifier.fillMaxWidth().aspectRatio(template.aspect).shadow(10.dp, RoundedCornerShape(12.dp)))
                else Box(Modifier.fillMaxWidth().aspectRatio(template.aspect))
                if (!owned) Text("APERÇU", color = Color.White, fontWeight = FontWeight.Black, letterSpacing = 3.sp,
                    modifier = Modifier.align(Alignment.TopStart).clip(RoundedCornerShape(8.dp)).background(Color(0xAA111827)).padding(horizontal = 8.dp, vertical = 3.dp))
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(template.name, style = MaterialTheme.typography.titleLarge)
                    Text(template.description, color = Brand.Muted, style = MaterialTheme.typography.bodySmall)
                }
                when {
                    !template.premium -> Pill("Gratuit", Brand.Green)
                    owned -> Pill("Débloqué ✓", Brand.Green)
                    else -> Pill(Fmt.money(acc?.priceOf(template.id) ?: 1000, "F"), Brand.Pink, filled = true)
                }
            }

            SectionTitle("Modèles (${Catalog.all.size})")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectChip("Tous", category == null) { category = null }
                Category.values().forEach { c -> SelectChip(c.label, category == c) { category = c } }
            }
            Spacer(Modifier.height(10.dp))
            val list = Catalog.all.filter { category == null || it.category == category }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(list, key = { it.id }) { t ->
                    Thumb(t, t.id == templateId, !t.premium || acc?.items?.contains(t.id) == true, branding, sample) { templateId = t.id }
                }
            }

            SectionTitle("Couleurs")
            Panel(Modifier.fillMaxWidth()) {
                Text("Couleur principale", fontWeight = FontWeight.SemiBold)
                Swatches(options.primary) { options = options.copy(primary = it) }
                Spacer(Modifier.height(10.dp))
                Text("Couleur d'accent", fontWeight = FontWeight.SemiBold)
                Swatches(options.accent) { options = options.copy(accent = it) }
            }

            SectionTitle("Textes")
            Panel(Modifier.fillMaxWidth()) {
                OutlinedTextField(branding.business, { branding = branding.copy(business = it.take(40)) }, label = { Text("Nom de votre Wi-Fi") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(branding.tagline, { branding = branding.copy(tagline = it.take(50)) }, label = { Text("Slogan (facultatif)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(branding.contact, { branding = branding.copy(contact = it.take(30)) }, label = { Text("Contact") }, singleLine = true, modifier = Modifier.weight(1.5f))
                    OutlinedTextField(branding.currency, { branding = branding.copy(currency = it.take(6)) }, label = { Text("Devise") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(branding.dns, { branding = branding.copy(dns = it.trim().take(60)) }, label = { Text("Adresse de connexion") }, singleLine = true, modifier = Modifier.weight(1.5f))
                    OutlinedTextField(options.badge, { options = options.copy(badge = it.take(18)) }, label = { Text("Badge") }, singleLine = true, modifier = Modifier.weight(1f))
                }
            }

            SectionTitle("Motif et logo")
            Panel(Modifier.fillMaxWidth()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icons.MOTIFS.forEach { ic ->
                        val on = options.motif == ic.id
                        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(if (on) Brand.Indigo else Color(0xFFEEF1F7)).clickable { options = options.copy(motif = ic.id) },
                            contentAlignment = Alignment.Center) { VIcon(ic, if (on) Color.White else Brand.Ink, size = 24.dp) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (branding.logo != null) "Votre logo est utilisé à la place du motif." else "Ajoutez votre logo (il remplace le motif).", color = Brand.Muted, modifier = Modifier.weight(1f))
                    TextButton({ logoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text(if (branding.logo != null) "Changer" else "Choisir") }
                    if (branding.logo != null) TextButton({ design.setLogo(null); branding = branding.copy(logo = null) }) { Text("Retirer", color = Brand.Red) }
                }
            }

            SectionTitle("Afficher sur le ticket")
            Panel(Modifier.fillMaxWidth(), padding = 8.dp) {
                Toggle("QR code de connexion automatique", options.showQr) { options = options.copy(showQr = it) }
                Toggle("Prix", options.showPrice) { options = options.copy(showPrice = it) }
                Toggle("Icônes (durée, volume, débit)", options.showIcons) { options = options.copy(showIcons = it) }
                Toggle("Adresse de connexion", options.showDns) { options = options.copy(showDns = it) }
                Toggle("Contact", options.showContact) { options = options.copy(showContact = it) }
                Toggle("Numéro du ticket", options.showSerial) { options = options.copy(showSerial = it) }
                Toggle("Logo", options.showLogo) { options = options.copy(showLogo = it) }
            }

            SectionTitle("Papier")
            Panel(Modifier.fillMaxWidth()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PaperFormat.values().forEach { f -> SelectChip(f.label, print.format == f) { print = print.copy(format = f, columns = 0) } }
                }
                if (!print.format.roll) {
                    Spacer(Modifier.height(10.dp))
                    Text("Tickets par ligne", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SelectChip("Auto", print.columns == 0) { print = print.copy(columns = 0) }
                        (1..5).forEach { n -> SelectChip("$n", print.columns == n) { print = print.copy(columns = n) } }
                    }
                    Toggle("Traits de coupe", print.cutMarks) { print = print.copy(cutMarks = it) }
                }
                val plan = Sheet.plan(template, vouchers.size, print)
                Text(Sheet.summary(plan, vouchers.size), color = Brand.Indigo, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
            }
            Spacer(Modifier.height(100.dp))
        }

        // Barre d'action fixe en bas, qui ne cache rien.
        Column(Modifier.fillMaxWidth().background(Color.White).padding(16.dp)) {
            if (owned) {
                BigButton("Exporter en PDF (${vouchers.size} tickets)", loading = exporting, icon = Icons.DOWNLOAD) {
                    if (PdfExporter.needsStoragePermission() && ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                        permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    } else doExport()
                }
            } else if (acc?.pending != null || purchase.waiting) {
                BigButton("J'ai payé : vérifier", loading = purchase.busy) { purchase.check() }
            } else {
                BigButton("Débloquer « ${template.name} » — ${Fmt.money(acc?.priceOf(template.id) ?: 1000, "F")}", brush = Brand.Premium, loading = purchase.busy, icon = Icons.SPARKLE) {
                    purchase.buy(template.id, template.name)
                }
                Text("Modèle acheté une fois, gardé à vie sur votre compte. Les modèles Standard et Mini sont gratuits.", color = Brand.Muted,
                    style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }

    result?.let { r ->
        AlertDialog(
            onDismissRequest = { result = null },
            title = { Text("PDF enregistré ✓") },
            text = { Text("${r.name}\n\n${r.pages} page(s) · ${Fmt.bytes(r.bytes)}\nDossier : ${r.location}") },
            confirmButton = {
                TextButton({
                    val i = Intent(Intent.ACTION_VIEW).setDataAndType(r.uri, "application/pdf").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    runCatching { activity.startActivity(i) }.onFailure { error = "Aucune application ne peut ouvrir les PDF sur ce téléphone." }
                }) { Text("Ouvrir") }
            },
            dismissButton = {
                TextButton({
                    val i = Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, r.uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    activity.startActivity(Intent.createChooser(i, "Partager les tickets"))
                }) { Text("Partager") }
            },
        )
    }
    error?.let { e -> AlertDialog(onDismissRequest = { error = null }, title = { Text("Export") }, text = { Text(e) }, confirmButton = { TextButton({ error = null }) { Text("OK") } }) }
    PurchaseDialogs(purchase)
}

fun toVouchers(users: List<HsUser>, profiles: List<com.wifizone.mikhmon.router.Profile>): List<Voucher> {
    val byName = profiles.associateBy { it.name }
    return users.mapIndexed { i, u ->
        val p = byName[u.profile]
        Voucher(
            username = u.name, password = u.password.takeIf { it.isNotBlank() && it != u.name }, profile = u.profile,
            price = Vouchers.price(p?.price), validity = Vouchers.duration(p?.validity),
            data = Vouchers.bytes(u.limitBytes.toLongOrNull()), speed = Vouchers.rate(p?.rateLimit), time = Vouchers.duration(u.limitUptime), serial = i + 1,
        )
    }
}

@Composable
private fun Thumb(t: TicketTemplate, selected: Boolean, owned: Boolean, b: Branding, sample: Voucher, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val bmp by produceState<Bitmap?>(null, t.id, b.business, b.logo) {
        value = withContext(Dispatchers.Default) { Previews.ticket(ctx, t, sample, b, TicketOptions(showQr = true), 420, key = "thumb-${t.id}-${b.business}-${b.logo != null}") }
    }
    Column(Modifier.width(158.dp).clip(RoundedCornerShape(16.dp)).background(Color.White)
        .border(if (selected) 2.5.dp else 1.dp, if (selected) Brand.Indigo else Brand.Line, RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(8.dp)) {
        Box(Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.Center) {
            bmp?.let { Image(it.asImageBitmap(), t.name, Modifier.fillMaxWidth().aspectRatio(t.aspect)) }
            if (t.premium && !owned) Box(Modifier.align(Alignment.TopEnd).size(22.dp).clip(CircleShape).background(Color(0xCC111827)), contentAlignment = Alignment.Center) {
                VIcon(Icons.LOCK, Color.White, size = 12.dp)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(t.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
        Text(if (!t.premium) "Gratuit" else if (owned) "Débloqué" else "Premium", color = if (!t.premium || owned) Brand.Green else Brand.Pink, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Swatches(current: Int?, onPick: (Int?) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(Color.White).border(if (current == null) 2.5.dp else 1.dp, if (current == null) Brand.Indigo else Brand.Line, CircleShape)
            .clickable { onPick(null) }, contentAlignment = Alignment.Center) { Text("A", fontWeight = FontWeight.Bold, color = Brand.Muted) }
        SWATCHES.forEach { c ->
            Box(Modifier.size(34.dp).clip(CircleShape).background(Color(c)).border(if (current == c) 3.dp else 0.dp, if (current == c) Brand.Ink else Color.Transparent, CircleShape)
                .clickable { onPick(c) })
        }
    }
}

@Composable
private fun Toggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onChange(!value) }.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(value, onChange)
    }
}

@Suppress("unused")
private fun Color.argb() = this.toArgb()

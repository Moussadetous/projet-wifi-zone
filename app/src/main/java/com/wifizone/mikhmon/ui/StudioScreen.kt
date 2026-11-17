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
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.wifizone.mikhmon.Services
import com.wifizone.mikhmon.print.PdfExporter
import com.wifizone.mikhmon.print.Previews
import com.wifizone.mikhmon.router.HsUser
import com.wifizone.mikhmon.router.Profile
import com.wifizone.mikhmon.tickets.Branding
import com.wifizone.mikhmon.tickets.Catalog
import com.wifizone.mikhmon.tickets.Category
import com.wifizone.mikhmon.tickets.PaperFormat
import com.wifizone.mikhmon.tickets.Sheet
import com.wifizone.mikhmon.tickets.SheetPlan
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

/** Modèle à l'ouverture : celui demandé (boutons de Mikhmon), le dernier premium (« premium »), sinon le dernier utilisé. */
private fun initialTemplate(preset: String?, owned: Set<String>): String {
    val design = Services.design
    if (preset != null && preset != "premium" && Catalog.byId(preset) != null) return preset
    if (preset == "premium") {
        design.lastPremium?.takeIf { Catalog.byId(it)?.premium == true }?.let { return it }
        Catalog.premium.firstOrNull { it.id in owned }?.let { return it.id }
        return Catalog.premium.first().id
    }
    return design.templateId.takeIf { Catalog.byId(it) != null } ?: "mk-default"
}

/**
 * Impression des tickets : formats de Mikhmon (Default, QR, Small), modèles gratuits et premium, nombre de tickets
 * par feuille (la mise en page s'adapte), aperçu de chaque page, choix des pages, puis impression ou PDF.
 */
@Composable
fun StudioScreen(activity: Activity, nav: Nav, studio: Overlay.Studio) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val design = Services.design
    val repo = Services.router
    val settings = remember { Services.settings.load() }
    val acc by Services.account.account.collectAsState()
    val purchase = remember { Purchase(activity, scope) }

    val profiles by produceState<List<Profile>?>(null) { value = runCatching { repo.profiles() }.getOrNull() ?: emptyList() }
    val dnsRouter by produceState(settings?.dnsName.orEmpty()) { if (value.isBlank()) value = runCatching { repo.hotspotDns() }.getOrDefault("") }

    // Tickets à imprimer : donnés, ou lus sur le routeur (lot Mikhmon, profil, ticket seul).
    var reload by remember { mutableStateOf(0) }
    val loaded by produceState<Result<List<HsUser>>?>(null, studio, reload) {
        value = null
        value = studio.users?.let { Result.success(it) } ?: runCatching {
            when {
                studio.userName != null -> repo.users(name = studio.userName)
                studio.comment != null -> repo.users(comment = studio.comment)
                studio.profile != null -> repo.users().filter { it.profile == studio.profile }
                else -> emptyList()
            }
        }
    }
    val pool = loaded?.getOrNull().orEmpty()
    val usedCount = pool.count { it.used }
    // Comme Mikhmon : un lot ou un profil s'imprime sans les tickets déjà utilisés.
    var unusedOnly by remember(studio) { mutableStateOf(studio.unusedOnly ?: (studio.users == null && studio.userName == null)) }
    val chosen = remember(pool, unusedOnly) { if (unusedOnly) pool.filter { !it.used } else pool }
    val vouchers = remember(chosen, profiles) { toVouchers(chosen, profiles.orEmpty()) }

    var templateId by remember { mutableStateOf(initialTemplate(studio.templateId, acc?.items.orEmpty())) }
    val template = Catalog.byId(templateId) ?: Catalog.all.first()
    var options by remember(templateId) { mutableStateOf(design.options(templateId)) }
    var branding by remember(dnsRouter) { mutableStateOf(design.branding(settings?.hotspotName.orEmpty(), dnsRouter, settings?.currency?.ifBlank { null } ?: "F")) }
    var print by remember(templateId) { mutableStateOf(design.print(templateId)) }
    var category by remember { mutableStateOf<Category?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<PdfExporter.Result?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var customize by remember { mutableStateOf(false) }
    var zoom by remember { mutableStateOf<Int?>(null) }
    var askCount by remember { mutableStateOf(false) }
    val owned = !template.premium || (acc?.items?.contains(template.id) == true)

    val plan = remember(template, vouchers.size, print) { Sheet.plan(template, vouchers.size, print) }
    // Pages choisies (indices à partir de 0) ; null = toutes.
    var selection by remember(templateId, print, vouchers.size) { mutableStateOf<Set<Int>?>(null) }
    var pagesText by remember(templateId, print, vouchers.size) { mutableStateOf("") }
    val printedPages = selection?.size ?: plan.pages
    val printedTickets = remember(plan, selection) { (selection ?: (0 until plan.pages).toSet()).sumOf { plan.ticketsOn(it).count() } }
    // Clé des aperçus : tout ce qui change le dessin.
    val renderKey = remember(templateId, options, branding, print, vouchers) {
        listOf(templateId, options, branding.copy(logo = null), branding.logo?.let { System.identityHashCode(it) }, print, vouchers.map { it.username }).hashCode().toString()
    }

    LaunchedEffect(templateId, options, branding, print) {
        design.templateId = templateId
        if (template.premium) design.lastPremium = templateId
        design.saveOptions(templateId, options); design.saveBranding(branding); design.savePrint(templateId, print)
    }
    LaunchedEffect(selection) {
        val sel = selection
        if (sel != null && Sheet.parsePages(pagesText, plan.pages)?.toSet() != sel) pagesText = Sheet.formatPages(sel)
    }

    val logoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { withContext(Dispatchers.IO) { design.setLogo(uri) }; branding = branding.copy(logo = design.logo()) }
    }
    val only = selection?.sorted()
    fun go(kind: String) {
        if (vouchers.isEmpty() || printedPages == 0) return
        busy = kind
        scope.launch {
            runCatching {
                if (kind == "pdf") {
                    result = PdfExporter.export(ctx, template, vouchers, branding, options, print, only)
                } else {
                    PdfExporter.print(activity, template, vouchers, branding, options, print, only)
                }
                Unit
            }.onFailure { error = it.message ?: "Impression impossible" }
            busy = null
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) go("pdf") else error = "Sans l'autorisation de stockage, le PDF ne peut pas être enregistré dans Téléchargements."
    }
    fun toggle(p: Int) {
        val cur = selection ?: (0 until plan.pages).toSet()
        val next = if (p in cur) cur - p else cur + p
        selection = if (next.size == plan.pages) null else next
    }

    Column(Modifier.fillMaxSize().background(Brand.Bg)) {
        val sub = when {
            loaded == null -> "${studio.title} · chargement…"
            else -> "${studio.title} · ${vouchers.size} ticket(s)"
        }
        BackHeader("Impression des tickets", { nav.overlay = null }, sub)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            val l = loaded
            when {
                l == null -> Loading()
                l.isFailure -> Column(Modifier.padding(16.dp)) {
                    ErrorNote("Tickets indisponibles : ${l.exceptionOrNull()?.message}")
                    Spacer(Modifier.height(10.dp))
                    BigButton("Réessayer") { reload++ }
                }
                pool.isEmpty() -> EmptyState(Icons.TICKET, "Aucun ticket", "Ce lot ou ce profil ne contient aucun ticket sur le routeur.")
                vouchers.isEmpty() -> Column(Modifier.padding(16.dp)) {
                    Panel(Modifier.fillMaxWidth()) {
                        Text("Les ${pool.size} tickets ont déjà été utilisés.", fontWeight = FontWeight.Bold)
                        Text("Mikhmon n'imprime que les tickets jamais utilisés. Vous pouvez quand même les réimprimer.", color = Brand.Muted)
                        Spacer(Modifier.height(10.dp))
                        BigButton("Afficher aussi les tickets utilisés") { unusedOnly = false }
                    }
                }
                else -> {
                    // ---------------------------------------------------------------- aperçu des pages
                    val ratio = plan.pageW / plan.pageH
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items((0 until plan.pages).toList(), key = { "$renderKey-$it" }) { page ->
                            PageCard(page, plan.pages, selection?.contains(page) ?: true, !owned, ratio, renderKey,
                                render = { w -> Previews.page(ctx, template, vouchers, branding, options, print, w, page, key = "$renderKey-$page-$w") },
                                onToggle = { toggle(page) }, onOpen = { zoom = page })
                        }
                    }
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        Text(summary(plan, vouchers.size), color = Brand.Indigo, fontWeight = FontWeight.SemiBold)
                        if (!print.format.roll && plan.ticketWmm < 32f) Text(
                            "Petits tickets (${Sheet.sizeLabel(plan)}) : vérifiez qu'ils restent lisibles" + if (options.showQr && template.id != "mk-default") ", le QR code peut être difficile à scanner." else ".",
                            color = Brand.Amber, style = MaterialTheme.typography.bodySmall)

                        // ---------------------------------------------------------------- tickets par feuille
                        SectionTitle("Tickets par feuille")
                        Panel(Modifier.fillMaxWidth()) {
                            if (print.format.roll) Text("Rouleau : un ticket par découpe, à la largeur du papier.", color = Brand.Muted)
                            else {
                                val natural = remember(template, print.format) { Sheet.naturalPerPage(template, print) }
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    SelectChip("Taille réelle · $natural", print.perPage == 0) { print = print.copy(perPage = 0) }
                                    Sheet.densityChoices(template, print).filter { it != natural || print.perPage == natural }.forEach { n ->
                                        SelectChip("$n", print.perPage == n) { print = print.copy(perPage = n) }
                                    }
                                    val custom = print.perPage > 0 && print.perPage !in Sheet.densityChoices(template, print)
                                    SelectChip(if (custom) "${print.perPage} ✎" else "Autre…", custom) { askCount = true }
                                }
                                Text("Choisissez combien de tickets tiennent sur une feuille : l'application calcule la grille et la taille des tickets.",
                                    color = Brand.Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                            }
                            Spacer(Modifier.height(12.dp))
                            Text("Papier", fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(6.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                PaperFormat.values().forEach { f -> SelectChip(f.label, print.format == f) { print = print.copy(format = f) } }
                            }
                            if (!print.format.roll) Toggle("Traits de coupe", print.cutMarks) { print = print.copy(cutMarks = it) }
                        }

                        // ---------------------------------------------------------------- pages à imprimer
                        SectionTitle("Pages à imprimer")
                        Panel(Modifier.fillMaxWidth()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SelectChip("Toutes (${plan.pages})", selection == null) { selection = null; pagesText = "" }
                                SelectChip("Choisir", selection != null) { if (selection == null) selection = setOf(0) }
                            }
                            if (selection != null) {
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(pagesText, { t -> pagesText = t; Sheet.parsePages(t, plan.pages)?.let { selection = it.toSet() } },
                                    label = { Text("Pages (ex. 1-3, 5)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                Text("Vous pouvez aussi toucher le rond des pages de l'aperçu pour les cocher.", color = Brand.Muted, style = MaterialTheme.typography.bodySmall)
                            }
                            if (usedCount > 0) Toggle("Seulement les tickets non utilisés (${pool.size - usedCount} sur ${pool.size})", unusedOnly) { unusedOnly = it }
                        }

                        // ---------------------------------------------------------------- modèle
                        SectionTitle("Modèle · ${template.name}")
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SelectChip("Tous (${Catalog.all.size})", category == null) { category = null }
                            Category.values().filter { c -> Catalog.all.any { it.category == c } }.forEach { c -> SelectChip(c.label, category == c) { category = c } }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    val sample = vouchers.first()
                    val list = Catalog.all.filter { category == null || it.category == category }
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(list, key = { it.id }) { t ->
                            Thumb(t, t.id == templateId, !t.premium || acc?.items?.contains(t.id) == true, branding, sample) { templateId = t.id }
                        }
                    }
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(template.description, color = Brand.Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            when {
                                !template.premium -> Pill("Gratuit", Brand.Green)
                                owned -> Pill("Débloqué ✓", Brand.Green)
                                else -> Pill(Fmt.money(acc?.priceOf(template.id) ?: 1000, "F"), Brand.Pink, filled = true)
                            }
                        }

                        // ---------------------------------------------------------------- personnalisation
                        SectionTitle("Personnaliser", action = if (customize) "Masquer" else "Afficher") { customize = !customize }
                        if (!customize) Text("Couleurs, nom du Wi-Fi, contact, logo, QR code, prix…", color = Brand.Muted,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { customize = true }.padding(4.dp))
                        else Customize(template, sample, owned, options, { options = it }, branding, { branding = it },
                            onLogo = { logoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            onLogoRemove = { design.setLogo(null); branding = branding.copy(logo = null) })
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }

        // Barre d'action fixe en bas, qui ne cache rien.
        if (vouchers.isNotEmpty()) Column(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text("$printedPages page(s) · $printedTickets ticket(s) · ${print.format.label}", color = Brand.Muted,
                style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 6.dp))
            if (owned) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BigButton("Imprimer", Modifier.weight(1f), enabled = printedPages > 0, loading = busy == "print", icon = Icons.PRINT) { go("print") }
                    BigButton("PDF", Modifier.weight(1f), brush = Brush.linearGradient(listOf(Color(0xFF334155), Color(0xFF0F172A))),
                        enabled = printedPages > 0, loading = busy == "pdf", icon = Icons.DOWNLOAD) {
                        if (PdfExporter.needsStoragePermission() && ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                            permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        } else go("pdf")
                    }
                }
            } else if (acc?.pending != null || purchase.waiting) {
                BigButton("J'ai payé : vérifier", loading = purchase.busy) { purchase.check() }
            } else {
                BigButton("Débloquer « ${template.name} » — ${Fmt.money(acc?.priceOf(template.id) ?: 1000, "F")}", brush = Brand.Premium, loading = purchase.busy, icon = Icons.SPARKLE) {
                    purchase.buy(template.id, template.name)
                }
                Text("Modèle acheté une fois, gardé à vie. Les formats Mikhmon, Standard et Mini sont gratuits.", color = Brand.Muted,
                    style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }

    zoom?.let { page ->
        PageViewer(page, plan.pages, selection?.contains(page) ?: true, !owned, plan.pageW / plan.pageH,
            render = { p, w -> Previews.page(ctx, template, vouchers, branding, options, print, w, p, key = "$renderKey-$p-$w") },
            onToggle = { toggle(it) }, onPage = { zoom = it }, onClose = { zoom = null })
    }
    if (askCount) CountDialog(print.perPage.takeIf { it > 0 } ?: 24, onDismiss = { askCount = false }) { n -> print = print.copy(perPage = n); askCount = false }
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
    error?.let { e -> AlertDialog(onDismissRequest = { error = null }, title = { Text("Impression") }, text = { Text(e) }, confirmButton = { TextButton({ error = null }) { Text("OK") } }) }
    PurchaseDialogs(purchase)
}

private fun summary(plan: SheetPlan, count: Int): String =
    if (plan.columns == 1 && plan.rows == 1) "$count ticket(s) · un par page · ticket ${Sheet.sizeLabel(plan)}"
    else "${plan.pages} page(s) · ${plan.perPage} par feuille (${plan.columns} × ${plan.rows}) · ticket ${Sheet.sizeLabel(plan)}"

/** Une page de l'aperçu, avec son numéro et sa case « à imprimer ». */
@Composable
private fun PageCard(page: Int, total: Int, selected: Boolean, locked: Boolean, ratio: Float, renderKey: String,
                     render: (Int) -> Bitmap?, onToggle: () -> Unit, onOpen: () -> Unit) {
    val bmp by produceState<Bitmap?>(null, renderKey, page) {
        delay(60)
        value = withContext(Dispatchers.Default) { runCatching { render(560) }.getOrNull() }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.width(196.dp).aspectRatio(ratio).shadow(6.dp, RoundedCornerShape(6.dp)).clip(RoundedCornerShape(6.dp)).background(Color.White)
            .border(if (selected) 0.dp else 2.dp, if (selected) Color.Transparent else Brand.Line, RoundedCornerShape(6.dp)).clickable(onClick = onOpen)) {
            val b = bmp
            if (b != null) Image(b.asImageBitmap(), "Page ${page + 1}", Modifier.fillMaxSize().graphicsLayer(alpha = if (selected) 1f else 0.35f))
            else Loading(Modifier.fillMaxSize())
            if (locked) Text("APERÇU", color = Color.White, fontWeight = FontWeight.Black, letterSpacing = 3.sp, fontSize = 11.sp,
                modifier = Modifier.align(Alignment.Center).clip(RoundedCornerShape(8.dp)).background(Color(0xAA111827)).padding(horizontal = 8.dp, vertical = 3.dp))
            Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(30.dp).clip(CircleShape)
                .background(if (selected) Brand.Indigo else Color.White).border(2.dp, Brand.Indigo, CircleShape).clickable(onClick = onToggle),
                contentAlignment = Alignment.Center) { if (selected) VIcon(Icons.CHECK, Color.White, size = 16.dp) }
        }
        Text("Page ${page + 1} / $total", style = MaterialTheme.typography.labelMedium, color = if (selected) Brand.Ink else Brand.Muted,
            fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
    }
}

/** Page en grand (zoom avec deux doigts), page précédente / suivante, inclure ou non. */
@Composable
private fun PageViewer(page: Int, total: Int, selected: Boolean, locked: Boolean, ratio: Float,
                       render: (Int, Int) -> Bitmap?, onToggle: (Int) -> Unit, onPage: (Int) -> Unit, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        var scale by remember(page) { mutableFloatStateOf(1f) }
        var offset by remember(page) { mutableStateOf(Offset.Zero) }
        val bmp by produceState<Bitmap?>(null, page) { value = withContext(Dispatchers.Default) { runCatching { render(page, 1500) }.getOrNull() } }
        Column(Modifier.fillMaxSize().background(Color(0xF20F172A))) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Page ${page + 1} / $total", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClose) { Text("Fermer", color = Color.White) }
            }
            Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(4.dp))
                .pointerInput(page) { detectTransformGestures { _, pan, z, _ -> scale = (scale * z).coerceIn(1f, 6f); offset = if (scale == 1f) Offset.Zero else offset + pan } },
                contentAlignment = Alignment.Center) {
                val b = bmp
                Box(Modifier.padding(12.dp).fillMaxWidth().aspectRatio(ratio).background(Color.White)
                    .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y)) {
                    if (b != null) Image(b.asImageBitmap(), "Page ${page + 1}", Modifier.fillMaxSize()) else Loading(Modifier.fillMaxSize())
                    if (locked) Text("APERÇU", color = Color.White, fontWeight = FontWeight.Black, letterSpacing = 3.sp,
                        modifier = Modifier.align(Alignment.Center).clip(RoundedCornerShape(8.dp)).background(Color(0xAA111827)).padding(horizontal = 10.dp, vertical = 4.dp))
                }
            }
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton({ onPage(page - 1) }, enabled = page > 0) { Text("‹ Précédente", color = if (page > 0) Color.White else Color.Gray) }
                Row(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { onToggle(page) }.padding(8.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center) {
                    Box(Modifier.size(24.dp).clip(CircleShape).background(if (selected) Brand.Indigo else Color.Transparent).border(2.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center) { if (selected) VIcon(Icons.CHECK, Color.White, size = 14.dp) }
                    Spacer(Modifier.width(8.dp))
                    Text("À imprimer", color = Color.White)
                }
                TextButton({ onPage(page + 1) }, enabled = page < total - 1) { Text("Suivante ›", color = if (page < total - 1) Color.White else Color.Gray) }
            }
        }
    }
}

/** Nombre de tickets par feuille saisi à la main. */
@Composable
private fun CountDialog(initial: Int, onDismiss: () -> Unit, onOk: (Int) -> Unit) {
    var text by remember { mutableStateOf(initial.toString()) }
    val n = text.toIntOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tickets par feuille") },
        text = {
            Column {
                Text("Entre 1 et 300. Plus il y en a, plus les tickets sont petits.", color = Brand.Muted)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(text, { text = it.filter(Char::isDigit).take(3) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton({ n?.let { onOk(it.coerceIn(1, 300)) } }, enabled = n != null && n > 0) { Text("Appliquer") } },
        dismissButton = { TextButton(onDismiss) { Text("Annuler") } },
    )
}

/** Couleurs, textes, motif ou logo, éléments affichés ; avec l'aperçu d'un ticket. */
@Composable
private fun Customize(
    template: TicketTemplate, sample: Voucher, owned: Boolean,
    options: TicketOptions, setOptions: (TicketOptions) -> Unit, branding: Branding, setBranding: (Branding) -> Unit,
    onLogo: () -> Unit, onLogoRemove: () -> Unit,
) {
    val ctx = LocalContext.current
    val preview by produceState<Bitmap?>(null, template.id, options, branding) {
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
    SectionTitle("Couleurs")
    Panel(Modifier.fillMaxWidth()) {
        Text("Couleur principale", fontWeight = FontWeight.SemiBold)
        Swatches(options.primary) { setOptions(options.copy(primary = it)) }
        Spacer(Modifier.height(10.dp))
        Text("Couleur d'accent", fontWeight = FontWeight.SemiBold)
        Swatches(options.accent) { setOptions(options.copy(accent = it)) }
    }
    SectionTitle("Textes")
    Panel(Modifier.fillMaxWidth()) {
        OutlinedTextField(branding.business, { setBranding(branding.copy(business = it.take(40))) }, label = { Text("Nom de votre Wi-Fi") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(branding.tagline, { setBranding(branding.copy(tagline = it.take(50))) }, label = { Text("Slogan (facultatif)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(branding.contact, { setBranding(branding.copy(contact = it.take(30))) }, label = { Text("Contact") }, singleLine = true, modifier = Modifier.weight(1.5f))
            OutlinedTextField(branding.currency, { setBranding(branding.copy(currency = it.take(6))) }, label = { Text("Devise") }, singleLine = true, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(branding.dns, { setBranding(branding.copy(dns = it.trim().take(60))) }, label = { Text("Adresse de connexion") }, singleLine = true, modifier = Modifier.weight(1.5f))
            OutlinedTextField(options.badge, { setOptions(options.copy(badge = it.take(18))) }, label = { Text("Badge") }, singleLine = true, modifier = Modifier.weight(1f))
        }
    }
    SectionTitle("Motif et logo")
    Panel(Modifier.fillMaxWidth()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icons.MOTIFS.forEach { ic ->
                val on = options.motif == ic.id
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(if (on) Brand.Indigo else Color(0xFFEEF1F7)).clickable { setOptions(options.copy(motif = ic.id)) },
                    contentAlignment = Alignment.Center) { VIcon(ic, if (on) Color.White else Brand.Ink, size = 24.dp) }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (branding.logo != null) "Votre logo est utilisé à la place du motif." else "Ajoutez votre logo (il remplace le motif).", color = Brand.Muted, modifier = Modifier.weight(1f))
            TextButton(onLogo) { Text(if (branding.logo != null) "Changer" else "Choisir") }
            if (branding.logo != null) TextButton(onLogoRemove) { Text("Retirer", color = Brand.Red) }
        }
    }
    SectionTitle("Afficher sur le ticket")
    Panel(Modifier.fillMaxWidth(), padding = 8.dp) {
        Toggle("QR code de connexion automatique", options.showQr) { setOptions(options.copy(showQr = it)) }
        Toggle("Prix", options.showPrice) { setOptions(options.copy(showPrice = it)) }
        Toggle("Icônes (durée, volume, débit)", options.showIcons) { setOptions(options.copy(showIcons = it)) }
        Toggle("Adresse de connexion", options.showDns) { setOptions(options.copy(showDns = it)) }
        Toggle("Contact", options.showContact) { setOptions(options.copy(showContact = it)) }
        Toggle("Numéro du ticket", options.showSerial) { setOptions(options.copy(showSerial = it)) }
        Toggle("Logo", options.showLogo) { setOptions(options.copy(showLogo = it)) }
    }
}

fun toVouchers(users: List<HsUser>, profiles: List<Profile>): List<Voucher> {
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
            .clickable { onPick(null) }, contentAlignment = Alignment.Center) { Text("A", fontWeight = FontWeight.Bold, color = Brand.Muted, textAlign = TextAlign.Center) }
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

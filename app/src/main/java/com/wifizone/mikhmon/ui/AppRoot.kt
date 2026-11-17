package com.wifizone.mikhmon.ui

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.wifizone.mikhmon.MikhmonServer
import com.wifizone.mikhmon.MikhmonService
import com.wifizone.mikhmon.Services
import com.wifizone.mikhmon.remote.RemoteAccess
import com.wifizone.mikhmon.router.HsUser
import com.wifizone.mikhmon.tickets.gfx.Icon
import com.wifizone.mikhmon.tickets.gfx.Icons
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class Tab(val label: String, val icon: Icon) {
    HOME("Accueil", Icons.HOME), CLIENTS("Clients", Icons.USER), TRAFFIC("Trafic", Icons.GAUGE),
    TICKETS("Tickets", Icons.TICKET), MIKHMON("Mikhmon", Icons.GLOBE)
}

/** Écrans posés par-dessus les onglets. */
sealed interface Overlay {
    object Account : Overlay
    object Logs : Overlay
    object Setup : Overlay
    /** Accès au routeur depuis n'importe où (Back To Home). */
    object Remote : Overlay
    /**
     * Impression de tickets (générateur PDF avec aperçu). Les tickets sont donnés ([users]) ou lus sur le routeur :
     * un lot Mikhmon ([comment]), un profil ([profile]) ou un seul ticket ([userName]).
     * [templateId] : modèle présélectionné (« mk-default », « mk-qr », « mk-small » pour les boutons de Mikhmon,
     * « premium » pour le dernier modèle premium utilisé). [unusedOnly] : null = comme Mikhmon (lots et profils :
     * seulement les tickets jamais utilisés).
     */
    data class Studio(
        val title: String,
        val users: List<HsUser>? = null,
        val comment: String? = null,
        val profile: String? = null,
        val userName: String? = null,
        val templateId: String? = null,
        val unusedOnly: Boolean? = null,
    ) : Overlay
    data class MikhmonUrl(val path: String) : Overlay
}

class Nav {
    var tab by mutableStateOf(Tab.HOME)
    var overlay by mutableStateOf<Overlay?>(null)
    var mikhmonPath by mutableStateOf<String?>(null)
    /** Navigateur de Mikhmon gardé ouvert : on retrouve la même page en revenant de l'impression. */
    val web = WebHolder()
    fun open(o: Overlay) { overlay = o }
    fun back(): Boolean {
        if (overlay != null) { overlay = null; return true }
        if (tab != Tab.HOME) { tab = Tab.HOME; return true }
        return false
    }
}

@Composable
fun AppRoot(activity: Activity) {
    var configured by remember { mutableStateOf(Services.settings.load() != null) }
    if (!configured) {
        SetupScreen(onDone = { configured = true })
        return
    }
    val nav = remember { Nav() }
    val account by Services.account.account.collectAsState()
    val scope = rememberCoroutineScope()
    var gateChecked by rememberSaveable { mutableStateOf(false) }
    var skipGate by rememberSaveable { mutableStateOf(false) }

    // Serveur Mikhmon (PHP) toujours prêt en arrière-plan.
    LaunchedEffect(Unit) { MikhmonService.start(activity) }

    // Compte : découverte de la boutique, relecture du compte, signal d'ouverture pour l'administrateur.
    LaunchedEffect(Unit) {
        runCatching { Services.account.discover() }
        runCatching { Services.account.refresh() }
        val id = runCatching { Services.router.resource() }.getOrNull()
        Services.account.ping(listOfNotNull(id?.identity, id?.board, id?.version).joinToString(" · "))
        gateChecked = true
        // Le routeur sait-il faire l'accès à distance ? (l'offre n'est proposée que s'il le peut)
        if (id != null) RemoteAccess.refreshSupport()
    }

    // Retours de la boutique : connexion (…://auth) et paiement (…://paiement).
    LaunchedEffect(Unit) {
        Services.links.collect { uri ->
            Services.links.resetReplayCache()
            when (uri.host) {
                "auth" -> runCatching { Services.account.completeLogin(uri) }
                    .onSuccess { Toast.makeText(activity, "Bienvenue ${it.name.substringBefore(' ')} !", Toast.LENGTH_SHORT).show() }
                    .onFailure { Toast.makeText(activity, it.message ?: "Connexion impossible", Toast.LENGTH_LONG).show() }
                "paiement" -> repeat(10) { if (runCatching { Services.account.checkoutStatus() }.getOrNull()?.pending == null) return@repeat; delay(3000) }
            }
        }
    }

    // Raccourcis de l'icône (et tests) : ouvrir directement un écran.
    val open by Services.openRequest.collectAsState()
    LaunchedEffect(open) {
        when (open) {
            "clients" -> nav.tab = Tab.CLIENTS
            "trafic" -> nav.tab = Tab.TRAFFIC
            "tickets" -> nav.tab = Tab.TICKETS
            "mikhmon" -> nav.tab = Tab.MIKHMON
            "journal" -> nav.open(Overlay.Logs)
            "distance" -> nav.open(Overlay.Remote)
            "compte" -> nav.open(Overlay.Account)
            "accueil" -> nav.tab = Tab.HOME
            "studio" -> scope.launch {
                val b = runCatching { batches(Services.router.users()).firstOrNull() }.getOrNull()
                if (b != null) nav.open(Overlay.Studio(b.title, comment = b.comment, templateId = "premium")) else nav.tab = Tab.TICKETS
            }
            else -> {
                // « lot:<commentaire> », « profil:<nom> » (raccourcis internes et tests).
                val o = open.orEmpty()
                when {
                    o.startsWith("lot:") -> nav.open(Overlay.Studio(o.removePrefix("lot:").let { Batch(it, emptyList()).title }, comment = o.removePrefix("lot:")))
                    o.startsWith("profil:") -> nav.open(Overlay.Studio("Profil ${o.removePrefix("profil:")}", profile = o.removePrefix("profil:")))
                }
            }
        }
        if (open != null) Services.openRequest.value = null
    }

    // Retour dans l'application (après la page de paiement) : on revérifie le compte.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME && Services.account.loggedIn) scope.launch {
                runCatching { if (Services.account.account.value?.pending != null) Services.account.checkoutStatus() else Services.account.refresh() }
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    BackHandler(enabled = nav.overlay != null || nav.tab != Tab.HOME) { nav.back() }

    // Ouvrir une page précise de Mikhmon (impression standard d'un lot…).
    LaunchedEffect(nav.overlay) {
        (nav.overlay as? Overlay.MikhmonUrl)?.let { nav.mikhmonPath = it.path; nav.overlay = null; nav.tab = Tab.MIKHMON }
    }

    // Accès : connexion au compte obligatoire, puis essai ou accès à vie.
    val acc = account
    val locked = gateChecked && !(skipGate && acc == null) && (acc == null || !acc.unlocked)
    if (locked && nav.overlay !is Overlay.Setup) {
        Gate(activity, acc, onRouterSettings = { nav.open(Overlay.Setup) }, onSkip = { skipGate = true })
        return
    }

    when (val o = nav.overlay) {
        is Overlay.Account -> { AccountScreen(activity, nav); return }
        is Overlay.Logs -> { LogScreen(nav); return }
        is Overlay.Setup -> { SetupScreen(onDone = { MikhmonServer.restart(activity); Services.router.reset(); nav.overlay = null }, onCancel = { nav.overlay = null }); return }
        is Overlay.Studio -> { StudioScreen(activity, nav, o); return }
        is Overlay.Remote -> { RemoteScreen(activity, nav); return }
        is Overlay.MikhmonUrl, null -> {}
    }

    val support by RemoteAccess.support.collectAsState()
    var promo by remember { mutableStateOf(false) }
    LaunchedEffect(support, acc) {
        if (support?.supported == true && acc?.unlocked == true && !RemoteAccess.paid && !RemoteAccess.promoShown) { promo = true; RemoteAccess.promoShown = true }
    }
    if (promo) {
        val (price, perMonth) = remotePrice(acc?.itemPrice)
        androidx.compose.ui.window.Dialog(onDismissRequest = { promo = false }) {
            Column(Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(26.dp)).background(Color.White).padding(10.dp)) {
                RemoteHero(price, perMonth)
                Text("Votre routeur sait le faire : activez-le en une minute, depuis son Wi-Fi.", color = Brand.Muted,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp))
                BigButton("Découvrir", icon = Icons.GLOBE) { promo = false; nav.open(Overlay.Remote) }
                androidx.compose.material3.TextButton({ promo = false }, Modifier.align(androidx.compose.ui.Alignment.CenterHorizontally)) { Text("Plus tard", color = Brand.Muted) }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
                Tab.values().forEach { t ->
                    val sel = nav.tab == t
                    NavigationBarItem(
                        selected = sel, onClick = { nav.tab = t },
                        icon = { VIcon(t.icon, if (sel) Brand.Indigo else Brand.Muted, size = 22.dp) },
                        label = { Text(t.label, fontSize = 11.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = Color(0xFFE0E7FF)),
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            AnimatedContent(nav.tab, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "onglet") { t ->
                when (t) {
                    Tab.HOME -> HomeScreen(activity, nav)
                    Tab.CLIENTS -> ClientsScreen(nav)
                    Tab.TRAFFIC -> TrafficScreen()
                    Tab.TICKETS -> TicketsScreen(activity, nav)
                    Tab.MIKHMON -> MikhmonWebScreen(activity, nav)
                }
            }
        }
    }
}

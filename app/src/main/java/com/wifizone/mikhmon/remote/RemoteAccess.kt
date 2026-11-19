package com.wifizone.mikhmon.remote

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import com.wifizone.mikhmon.MikhmonSettings
import com.wifizone.mikhmon.RouterOs
import com.wifizone.mikhmon.RouterOsException
import com.wifizone.mikhmon.Services
import com.wifizone.mikhmon.WifiNetwork
import com.wireguard.android.backend.BackendException
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import com.wireguard.config.Interface
import com.wireguard.config.Peer
import com.wireguard.crypto.Key
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.Socket
import java.time.LocalDate

/**
 * Accès au routeur depuis n'importe où, par « Back To Home » de MikroTik.
 *  - Sur le Wi-Fi du routeur, rien ne change : connexion directe.
 *  - Ailleurs (4G, autre Wi-Fi), si l'accès est payé et activé : tunnel WireGuard intégré à l'application. Seule cette
 *    application l'utilise, et seulement pour joindre le routeur ; le reste du téléphone n'est pas touché.
 *  - L'accès s'achète par périodes de 2 mois (articles « distance-AAAAMMJJ » de la boutique, voir RemotePlan).
 */
object RemoteAccess {
    private val ctx: Context get() = Services.app
    private val prefs by lazy { ctx.getSharedPreferences("remote", Context.MODE_PRIVATE) }
    private val backend by lazy { GoBackend(ctx) }
    @Volatile private var started = false
    /** Adresse du routeur qui a répondu la dernière fois par le tunnel (passerelle BTH ou adresse du réseau local). */
    @Volatile private var remoteHost: String? = null

    private val tunnel = object : Tunnel {
        override fun getName() = "mikhmon"
        override fun onStateChange(newState: Tunnel.State) { up.value = newState == Tunnel.State.UP }
    }

    /** Tunnel actif en ce moment. */
    val up = MutableStateFlow(false)
    /** Compatibilité du routeur avec « Back To Home » (null : pas encore vérifiée). */
    val support by lazy { MutableStateFlow(loadSupport()) }
    /** Accès créé sur le routeur et enregistré sur ce téléphone. */
    val configured by lazy { MutableStateFlow(config() != null) }

    // ------------------------------------------------------------------ réglages
    private fun loadSupport(): BackToHome.Support? = prefs.getString("support", null)?.let { s ->
        runCatching {
            val j = JSONObject(s)
            BackToHome.Support(j.getBoolean("supported"), j.optString("version"), j.optString("reason"), j.optBoolean("enabled"), j.optString("status"))
        }.getOrNull()
    }

    private fun saveSupport(s: BackToHome.Support) {
        prefs.edit().putString("support", JSONObject().put("supported", s.supported).put("version", s.version).put("reason", s.reason)
            .put("enabled", s.enabled).put("status", s.status).toString()).apply()
        support.value = s
    }

    fun config(): WgConf? = prefs.getString("wg", null)?.let { WgConf.parse(it) }

    private fun saveConfig(c: WgConf?) {
        prefs.edit().apply { if (c == null) remove("wg") else putString("wg", c.text()) }.apply()
        configured.value = c != null
    }

    /** Nom de ce téléphone sur le routeur (un accès « Back To Home » par appareil). */
    val deviceName: String
        get() = prefs.getString("device", null) ?: run {
            val model = Build.MODEL.orEmpty().lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(16).ifEmpty { "android" }
            "mikhmon-$model-${(1000..9999).random()}".also { prefs.edit().putString("device", it).apply() }
        }

    /** Dernier jour d'accès payé (inclus), ou null. */
    fun activeUntil(items: Collection<String>? = Services.account.account.value?.items): LocalDate? =
        RemotePlan.activeUntil(items.orEmpty(), LocalDate.now())

    val paid: Boolean get() = activeUntil() != null
    val ready: Boolean get() = paid && config() != null

    /** La publicité de l'accès à distance a-t-elle déjà été montrée en grand ? */
    var promoShown: Boolean
        get() = prefs.getBoolean("promoShown", false)
        set(v) { prefs.edit().putBoolean("promoShown", v).apply() }

    // ------------------------------------------------------------------ vitesse d'actualisation à distance
    /** Rythme des relectures des écrans quand on passe par le tunnel (en Wi-Fi local, rien ne change). */
    enum class Speed(val label: String, val factor: Float, val hint: String) {
        AUTO("Automatique", 2.5f, "Recommandé : un peu moins souvent qu'en Wi-Fi, pour rester fluide à travers le relais."),
        FAST("Rapide", 1f, "Aussi souvent qu'en Wi-Fi : plus réactif, mais peut ralentir si le relais est chargé."),
        ECO("Économe", 5f, "Le moins de données possible : idéal en 4G avec un petit forfait."),
    }

    val speed by lazy { MutableStateFlow(runCatching { Speed.valueOf(prefs.getString("speed", "AUTO")!!) }.getOrDefault(Speed.AUTO)) }

    fun setSpeed(v: Speed) { prefs.edit().putString("speed", v.name).apply(); speed.value = v }

    /** Facteur appliqué aux relectures des écrans : 1 en Wi-Fi local, selon le réglage à distance. */
    fun pollFactor(): Float = if (up.value) speed.value.factor else 1f

    // ------------------------------------------------------------------ activation automatique après l'achat
    /** Activation automatique : null (rien en cours), "running", ou le message d'erreur de la dernière tentative. */
    val activation = MutableStateFlow<String?>(null)
    /** L'autorisation « VPN » d'Android est à demander (après l'activation, ou quand le tunnel en a besoin). */
    val askPermission = MutableStateFlow(false)
    private val activating = java.util.concurrent.atomic.AtomicBoolean(false)

    /**
     * Accès payé mais pas encore créé sur le routeur : l'application le crée toute seule dès que le téléphone est sur
     * le Wi-Fi du routeur, sans bouton à chercher. Renvoie true si l'activation vient d'être faite.
     */
    suspend fun autoActivate(): Boolean {
        if (!paid || config() != null) return false
        val s = Services.settings.load() ?: return false
        if (!onRouterWifi(s)) return false
        if (!activating.compareAndSet(false, true)) return false
        try {
            if (support.value?.supported != true) refreshSupport()
            if (support.value?.supported != true) return false
            activation.value = "running"
            val ok = runCatching { activate() }
            activation.value = ok.exceptionOrNull()?.let { it.message ?: "Activation impossible pour le moment." }
            if (ok.isSuccess && permissionIntent() != null) askPermission.value = true
            return ok.isSuccess
        } finally {
            activating.set(false)
        }
    }

    /** Autorisation « VPN » d'Android (demandée une seule fois) : null si déjà accordée. */
    fun permissionIntent(): Intent? = VpnService.prepare(ctx)

    // ------------------------------------------------------------------ Wi-Fi local ou distance
    private fun sameLan(ip: String?, router: String) =
        ip != null && WgConf.isIpv4(ip) && WgConf.isIpv4(router) && ip.substringBeforeLast('.') == router.substringBeforeLast('.')

    /** Le téléphone est-il sur le réseau du routeur ? */
    fun onRouterWifi(s: MikhmonSettings): Boolean {
        if (s.routerIp.startsWith("127.")) return true // routeur sur le téléphone lui-même (tests)
        val w = WifiNetwork.detect(ctx)
        if (!w.connected) return false
        if (!WgConf.isIpv4(s.routerIp)) return true
        return w.gateway == s.routerIp || sameLan(w.ownIp, s.routerIp)
    }

    fun remoteMode(s: MikhmonSettings): Boolean = ready && !onRouterWifi(s) || directFailedHere()

    /**
     * Wi-Fi sur lequel la connexion directe a échoué (autre MikroTik à la même adresse…) : on y reste à distance
     * sans réessayer à chaque lecture. Oublié dès que le téléphone change de réseau.
     */
    @Volatile private var directFailedOn: String? = null
    private fun currentWifi(): String? = WifiNetwork.detect(ctx).network?.toString()
    private fun directFailedHere(): Boolean = ready && directFailedOn != null && directFailedOn == currentWifi()

    /**
     * La connexion ouverte ne correspond plus au réseau : passée par le tunnel alors que le téléphone est revenu
     * sur le Wi-Fi du routeur (le direct est bien plus rapide), ou directe alors qu'il l'a quitté.
     */
    fun modeChanged(s: MikhmonSettings, viaTunnel: Boolean): Boolean = viaTunnel != remoteMode(s)

    /** Surveille le Wi-Fi : dès le retour sur celui du routeur, la connexion repasse en direct (et inversement). */
    fun watchNetwork() {
        val cm = ctx.getSystemService(android.net.ConnectivityManager::class.java) ?: return
        // Vrai Wi-Fi seulement (la demande exclut les VPN par défaut), avec ou sans Internet.
        val req = android.net.NetworkRequest.Builder().addTransportType(android.net.NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
        val cb = object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) = recheck()
            override fun onLost(network: android.net.Network) = recheck()
            override fun onLinkPropertiesChanged(network: android.net.Network, lp: android.net.LinkProperties) = recheck()
        }
        runCatching { cm.registerNetworkCallback(req, cb) }
    }

    private val rechecking = java.util.concurrent.atomic.AtomicBoolean(false)
    /** Test ou diagnostic en cours : ils passent volontairement par le tunnel, même sur le Wi-Fi du routeur. */
    private val forcing = java.util.concurrent.atomic.AtomicInteger(0)
    private suspend fun <T> forced(block: suspend () -> T): T { forcing.incrementAndGet(); try { return block() } finally { forcing.decrementAndGet() } }
    private fun recheck() {
        if (!rechecking.compareAndSet(false, true)) return
        Thread {
            try {
                Thread.sleep(1500) // laisse le Wi-Fi obtenir son adresse et sa passerelle
                val s = Services.settings.load() ?: return@Thread
                if (directFailedOn != null && directFailedOn != currentWifi()) directFailedOn = null
                kotlinx.coroutines.runBlocking { Services.router.recheck() }
                if (up.value && !remoteMode(s) && forcing.get() == 0) down()
            } catch (_: Exception) {
            } finally {
                rechecking.set(false)
            }
        }.start()
    }

    // ------------------------------------------------------------------ tunnel
    private fun build(c: WgConf, s: MikhmonSettings): Config {
        // Le tunnel ne sert qu'à joindre le routeur : passerelle Back To Home et adresse du routeur sur son réseau.
        // Android refuse les adresses locales (127.x, 169.254.x, 0.x) dans les routes d'un VPN.
        fun routable(ip: String?) = ip != null && WgConf.isIpv4(ip) && !ip.startsWith("127.") && !ip.startsWith("169.254.") && !ip.startsWith("0.")
        val targets = listOfNotNull(c.routerAddress, s.routerIp).filter { routable(it) }.distinct().joinToString(", ") { "$it/32" }
        val iface = Interface.Builder()
            .parsePrivateKey(c.privateKey)
            .parseAddresses(c.addresses.joinToString(", "))
            .includeApplication(ctx.packageName)
        c.mtu?.let { iface.setMtu(it) }
        val peer = Peer.Builder()
            .parsePublicKey(c.peerPublicKey)
            .parseEndpoint(c.endpoint)
            .parseAllowedIPs(targets)
            .setPersistentKeepalive(c.keepalive ?: 25)
        c.presharedKey?.let { peer.parsePreSharedKey(it) }
        return Config.Builder().setInterface(iface.build()).addPeer(peer.build()).build()
    }

    /** Ouvre le tunnel si besoin (appel bloquant, hors du fil de l'interface). */
    @Synchronized
    fun ensureUp(s: MikhmonSettings) {
        val c = config() ?: throw IllegalStateException("L'accès à distance n'est pas activé sur ce téléphone.")
        if (!paid) throw IllegalStateException("Votre accès à distance a expiré : renouvelez-le (Compte › Accès à distance).")
        if (permissionIntent() != null) {
            askPermission.value = true
            throw IllegalStateException("Autorisez la connexion sécurisée (VPN) de l'application : touchez « Autoriser » dans la fenêtre qui s'affiche.")
        }
        if (started && runCatching { backend.getState(tunnel) }.getOrNull() == Tunnel.State.UP) return
        backend.setState(tunnel, Tunnel.State.UP, build(c, s))
        started = true
        up.value = true
    }

    /** Ferme le tunnel (de retour sur le Wi-Fi du routeur, ou accès désactivé). */
    @Synchronized
    fun down() {
        if (!started) return
        runCatching { backend.setState(tunnel, Tunnel.State.DOWN, null) }
        started = false
        up.value = false
    }

    // ------------------------------------------------------------------ connexions au routeur
    /** Dernière erreur de l'accès à distance (affichée sur l'accueil), null quand tout va bien. */
    val lastError = MutableStateFlow<String?>(null)

    /** Numéro de série du routeur : reconnaît SON Wi-Fi (beaucoup de MikroTik répondent sur 192.168.88.1). */
    private var routerSerial: String
        get() = prefs.getString("serial", "").orEmpty()
        set(v) { prefs.edit().putString("serial", v).apply() }

    private fun remoteHosts(s: MikhmonSettings): List<String> =
        listOfNotNull(remoteHost, config()?.routerAddress, s.routerIp).distinct()

    /** Heure du dernier échange WireGuard réussi avec le routeur (0 : jamais). */
    private fun handshakeAt(c: WgConf): Long = runCatching {
        backend.getStatistics(tunnel).peer(Key.fromBase64(c.peerPublicKey))?.latestHandshakeEpochMillis() ?: 0L
    }.getOrDefault(0L)

    /** Attend un échange avec le routeur par le relais Back To Home (preuve que le tunnel fonctionne vraiment). */
    private fun waitHandshake(c: WgConf, timeoutMs: Long): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (handshakeAt(c) > System.currentTimeMillis() - 180_000L) return true
            Thread.sleep(300)
        }
        return false
    }

    /** Message clair pour une erreur d'ouverture du tunnel. */
    fun explain(e: Throwable): String = when ((e as? BackendException)?.reason) {
        BackendException.Reason.VPN_NOT_AUTHORIZED -> "Autorisez la connexion sécurisée (VPN) : Compte › Accès à distance › Tester."
        BackendException.Reason.DNS_RESOLUTION_FAILURE -> "Adresse du relais Back To Home introuvable : le téléphone a-t-il Internet ?"
        BackendException.Reason.UNABLE_TO_START_VPN, BackendException.Reason.TUN_CREATION_ERROR ->
            "Android a refusé d'ouvrir le tunnel : une autre application VPN est peut-être active (désactivez-la)."
        else -> e.message ?: "Tunnel impossible à ouvrir."
    }

    /** Le routeur joint est-il bien le nôtre (et pas un autre MikroTik à la même adresse) ? */
    private fun sameRouter(r: RouterOs): Boolean {
        val known = routerSerial
        if (known.isEmpty()) return true
        val now = runCatching { BackToHome.serial(r) }.getOrDefault("")
        return now.isEmpty() || now == known
    }

    /**
     * Connexion à l'API du routeur : directe sur son Wi-Fi ; sinon (accès payé et activé) par le tunnel. Si la
     * connexion directe échoue pour une raison quelconque (autre Wi-Fi, autre MikroTik à la même adresse…), le tunnel
     * prend le relais.
     */
    fun connect(s: MikhmonSettings, timeoutMs: Int = 8000, forceRemote: Boolean = false): RouterOs {
        if (!forceRemote && !remoteMode(s)) {
            down()
            val local = try {
                val r = RouterOs.connect(s.routerIp, s.routerUser, s.routerPass, s.routerPort, timeoutMs) { WifiNetwork.socketFor(ctx, s.routerIp) }
                if (sameRouter(r)) { lastError.value = null; directFailedOn = null; return r }
                r.close()
                RouterOsException(RouterOsException.Kind.UNREACHABLE, "Ce Wi-Fi n'est pas celui de votre routeur : un autre MikroTik répond à l'adresse ${s.routerIp}.")
            } catch (e: RouterOsException) { e }
            if (!ready) throw local
            directFailedOn = currentWifi()
        }
        return try {
            remote(s).also { lastError.value = null }
        } catch (e: RouterOsException) {
            lastError.value = e.message
            throw e
        }
    }

    private fun remote(s: MikhmonSettings): RouterOs {
        val c = config() ?: throw RouterOsException(RouterOsException.Kind.UNREACHABLE, "L'accès à distance n'est pas activé sur ce téléphone.")
        try { ensureUp(s) } catch (e: Exception) { throw RouterOsException(RouterOsException.Kind.UNREACHABLE, explain(e)) }
        if (!waitHandshake(c, 9000)) throw RouterOsException(RouterOsException.Kind.UNREACHABLE,
            "Le routeur ne répond pas par le relais Back To Home. Vérifiez qu'il est allumé et qu'il a Internet " +
                "(Winbox : IP › Cloud › « BTH VPN is ready »). Certains Wi-Fi publics bloquent aussi ces connexions : essayez en 4G.")
        var last: Exception? = null
        for (h in remoteHosts(s)) {
            try {
                return RouterOs.connect(h, s.routerUser, s.routerPass, s.routerPort, 12000) { Socket() }.also { remoteHost = h }
            } catch (e: RouterOsException) {
                if (e.kind == RouterOsException.Kind.BAD_LOGIN) throw e
                last = e
            }
        }
        throw RouterOsException(RouterOsException.Kind.UNREACHABLE,
            "Le tunnel fonctionne, mais le routeur refuse l'accès à son API (port ${s.routerPort}) : son pare-feu le bloque. " +
                "Ouvrez l'application une fois sur le Wi-Fi du routeur : elle le règle toute seule. (${last?.message ?: "aucune réponse"})")
    }

    /** Cible du pont de Mikhmon (PHP), à chaque nouvelle connexion. */
    fun bridgeTarget(s: MikhmonSettings): Pair<String, Int> {
        if (!remoteMode(s)) return s.routerIp to s.routerPort
        ensureUp(s)
        return (remoteHost ?: config()?.routerAddress ?: s.routerIp) to s.routerPort
    }

    fun bridgeSocket(host: String, s: MikhmonSettings): Socket = if (remoteMode(s)) Socket() else WifiNetwork.socketFor(ctx, host)

    // ------------------------------------------------------------------ actions de l'écran « Accès à distance »
    /** Vérifie (sur place) si le routeur sait faire « Back To Home ». */
    suspend fun refreshSupport(): BackToHome.Support? = runCatching { Services.router.call { BackToHome.check(it) } }
        .onSuccess { saveSupport(it) }.getOrNull()

    /** Active « Back To Home » sur le routeur, ouvre son API au tunnel et enregistre l'accès de ce téléphone. */
    suspend fun activate(): WgConf {
        val s = Services.settings.load() ?: throw IllegalStateException("Routeur non configuré")
        val c = Services.router.call { r ->
            val conf = BackToHome.setup(r, deviceName)
            routerSerial = BackToHome.serial(r)
            BackToHome.subnet(conf)?.let { BackToHome.openApi(r, it, s.routerPort) }
            conf
        }
        saveConfig(c)
        refreshSupport()
        return c
    }

    /**
     * Sur le Wi-Fi du routeur, une fois l'accès créé : règle le routeur pour l'accès à distance (pare-feu, service API)
     * et retient son numéro de série. Sans effet si tout est déjà en place. Renvoie les changements faits.
     */
    suspend fun prepareRouter(): List<String> {
        val c = config() ?: return emptyList()
        val s = Services.settings.load() ?: return emptyList()
        if (!onRouterWifi(s)) return emptyList()
        return runCatching {
            Services.router.call { r ->
                if (routerSerial.isEmpty()) routerSerial = BackToHome.serial(r)
                BackToHome.subnet(c)?.let { BackToHome.openApi(r, it, s.routerPort) }.orEmpty()
            }
        }.getOrDefault(emptyList())
    }

    /** Routeur reconfiguré (autre adresse, autre routeur) : son numéro de série sera relu sur place. */
    fun forgetRouter() { prefs.edit().remove("serial").apply(); remoteHost = null }

    /** Une étape du diagnostic : réussie (true), en échec (false) ou sans objet (null). */
    data class Step(val title: String, val ok: Boolean?, val detail: String)

    private fun describeNetwork(s: MikhmonSettings): String {
        val w = WifiNetwork.detect(ctx)
        return when {
            !w.connected -> "données mobiles (pas de Wi-Fi)"
            onRouterWifi(s) -> "Wi-Fi du routeur (passerelle ${w.gateway ?: "?"})"
            else -> "autre Wi-Fi (passerelle ${w.gateway ?: "?"}, adresse ${w.ownIp ?: "?"})"
        }
    }

    /**
     * Diagnostic de l'accès à distance, étape par étape. Passe toujours par le tunnel, même sur le Wi-Fi du routeur :
     * il montre précisément ce qui bloque (abonnement, activation, autorisation VPN, relais, pare-feu du routeur).
     */
    suspend fun diagnose(): List<Step> = forced { diagnoseNow() }

    private suspend fun diagnoseNow(): List<Step> = withContext(Dispatchers.IO) {
        val out = ArrayList<Step>()
        val s = Services.settings.load() ?: return@withContext listOf(Step("Routeur", false, "routeur non configuré"))
        val sup = support.value
        out += Step("Routeur compatible", sup?.supported, sup?.let { "RouterOS ${it.version}" + if (it.status.isNotBlank()) " · ${it.status}" else "" } ?: "pas encore vérifié")
        val until = activeUntil()
        out += Step("Abonnement", until != null, until?.let { "actif jusqu'au $it" } ?: "inactif : payez l'accès à distance")
        val c = config()
        out += Step("Accès créé sur le routeur", c != null, c?.let { "$deviceName · relais ${it.endpoint}" } ?: "touchez « Activer sur le routeur » depuis son Wi-Fi")
        out += Step("Réseau du téléphone", null, describeNetwork(s))
        if (until == null || c == null) return@withContext out
        val allowed = permissionIntent() == null
        out += Step("Autorisation VPN d'Android", allowed, if (allowed) "accordée" else "à accorder : touchez « Tester l'accès à distance »")
        if (!allowed) return@withContext out
        val host = c.endpoint.substringBeforeLast(':').trim('[', ']')
        val ips = runCatching { java.net.InetAddress.getAllByName(host).mapNotNull { it.hostAddress } }
        out += Step("Adresse du relais", ips.isSuccess, ips.getOrNull()?.joinToString() ?: "introuvable : ${ips.exceptionOrNull()?.message}")
        val opened = runCatching { ensureUp(s) }
        out += Step("Tunnel WireGuard", opened.isSuccess, if (opened.isSuccess) "ouvert" else explain(opened.exceptionOrNull()!!))
        if (opened.isFailure) return@withContext out
        val hs = waitHandshake(c, 10_000)
        out += Step("Réponse du routeur par le relais", hs,
            if (hs) "échange WireGuard réussi" else "aucune réponse en 10 s : routeur éteint, sans Internet, ou Back To Home pas prêt")
        if (hs) for (h in listOfNotNull(c.routerAddress, s.routerIp).distinct()) {
            val t0 = System.currentTimeMillis()
            val r = runCatching {
                RouterOs.connect(h, s.routerUser, s.routerPass, s.routerPort, 10_000) { Socket() }
                    .use { it.command("/system/identity/print").firstOrNull()?.get("name").orEmpty() }
            }
            out += Step("API du routeur ($h:${s.routerPort})", r.isSuccess,
                r.getOrNull()?.let { "routeur « $it » joint en ${System.currentTimeMillis() - t0} ms" }
                    ?: "${r.exceptionOrNull()?.message} — pare-feu du routeur ? (ouvrez l'application sur son Wi-Fi pour le régler)")
            if (r.isSuccess) { remoteHost = h; lastError.value = null; break }
        }
        if (!remoteMode(s)) down()
        out
    }

    /** Rapport du diagnostic, à copier pour le support. */
    fun report(steps: List<Step>): String = steps.joinToString("\n") { st ->
        (when (st.ok) { true -> "✓"; false -> "✗"; null -> "•" }) + " " + st.title + " : " + st.detail
    }

    /** Teste la connexion à distance (même sur le Wi-Fi du routeur) : renvoie le nom du routeur. */
    suspend fun test(): String = forced {
        withContext(Dispatchers.IO) {
            val s = Services.settings.load() ?: throw IllegalStateException("Routeur non configuré")
            val name = connect(s, forceRemote = true).use { r -> r.command("/system/identity/print").firstOrNull()?.get("name").orEmpty() }
            if (!remoteMode(s)) down() // sur le Wi-Fi du routeur, on repasse en direct après le test
            name
        }
    }

    /** Retire l'accès de ce téléphone (sur le routeur si possible) et ferme le tunnel. */
    suspend fun deactivate() {
        runCatching { Services.router.call { BackToHome.remove(it, deviceName) } }
        withContext(Dispatchers.IO) { down() }
        saveConfig(null)
        Services.router.reset()
    }

    /** Importe une configuration « Back To Home » copiée depuis Winbox ou l'application MikroTik. */
    fun importConfig(text: String): WgConf {
        val c = WgConf.parse(text) ?: throw IllegalArgumentException("Configuration WireGuard non reconnue.")
        saveConfig(c)
        return c
    }
}

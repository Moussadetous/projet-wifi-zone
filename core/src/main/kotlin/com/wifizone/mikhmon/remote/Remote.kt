package com.wifizone.mikhmon.remote

import com.wifizone.mikhmon.RouterOs
import com.wifizone.mikhmon.RouterOsException
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Configuration WireGuard au format « wg-quick », celle que donne « Back To Home » (BTH) de MikroTik.
 * Lue avec tolérance : sauts de ligne ou non, majuscules, listes séparées par des virgules.
 */
data class WgConf(
    val privateKey: String,
    val addresses: List<String>,
    val dns: List<String>,
    val mtu: Int?,
    val peerPublicKey: String,
    val presharedKey: String?,
    val endpoint: String,
    val allowedIps: List<String>,
    val keepalive: Int?,
) {
    /** Adresse du routeur dans le tunnel : son serveur DNS (passerelle BTH), sinon x.y.z.1 de notre adresse. */
    val routerAddress: String?
        get() = dns.firstOrNull { isIpv4(it) }
            ?: addresses.map { it.substringBefore('/') }.firstOrNull { isIpv4(it) }?.let { it.substringBeforeLast('.') + ".1" }

    /** Texte wg-quick (pour l'enregistrer ou l'importer ailleurs). */
    fun text(): String = buildString {
        append("[Interface]\nPrivateKey = ").append(privateKey).append('\n')
        if (addresses.isNotEmpty()) append("Address = ").append(addresses.joinToString(", ")).append('\n')
        if (dns.isNotEmpty()) append("DNS = ").append(dns.joinToString(", ")).append('\n')
        mtu?.let { append("MTU = ").append(it).append('\n') }
        append("\n[Peer]\nPublicKey = ").append(peerPublicKey).append('\n')
        presharedKey?.let { append("PresharedKey = ").append(it).append('\n') }
        append("Endpoint = ").append(endpoint).append('\n')
        if (allowedIps.isNotEmpty()) append("AllowedIPs = ").append(allowedIps.joinToString(", ")).append('\n')
        keepalive?.let { append("PersistentKeepalive = ").append(it).append('\n') }
    }

    companion object {
        private val KEYS = listOf("PrivateKey", "Address", "DNS", "MTU", "ListenPort", "PublicKey", "PresharedKey", "AllowedIPs", "Endpoint", "PersistentKeepalive")

        fun isIpv4(s: String) = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$").matches(s.trim())

        fun parse(raw: String): WgConf? {
            // Remet chaque clé sur sa ligne (certaines versions de RouterOS renvoient tout sur une seule ligne).
            var text = raw.replace("\r", "\n")
            text = text.replace(Regex("\\s*(\\[(?:Interface|Peer)])\\s*", RegexOption.IGNORE_CASE), "\n$1\n")
            text = text.replace(Regex("\\s+(?=(${KEYS.joinToString("|")})\\s*=)", RegexOption.IGNORE_CASE), "\n")
            var section = ""
            val iface = HashMap<String, String>()
            val peer = HashMap<String, String>()
            for (line in text.lines()) {
                val l = line.trim()
                if (l.isEmpty() || l.startsWith("#")) continue
                if (l.startsWith("[")) { section = l.trim('[', ']').lowercase(); continue }
                val eq = l.indexOf('=')
                if (eq <= 0) continue
                val k = l.substring(0, eq).trim().lowercase()
                val v = l.substring(eq + 1).trim()
                when (section) {
                    "interface" -> iface[k] = v
                    "peer" -> if (k !in peer) peer[k] = v
                }
            }
            fun list(v: String?) = v.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
            val pk = iface["privatekey"]?.takeIf { it.length >= 40 } ?: return null
            val pub = peer["publickey"]?.takeIf { it.length >= 40 } ?: return null
            val ep = peer["endpoint"]?.takeIf { it.contains(':') } ?: return null
            return WgConf(
                privateKey = pk, addresses = list(iface["address"]), dns = list(iface["dns"]), mtu = iface["mtu"]?.toIntOrNull(),
                peerPublicKey = pub, presharedKey = peer["presharedkey"], endpoint = ep, allowedIps = list(peer["allowedips"]),
                keepalive = peer["persistentkeepalive"]?.toIntOrNull(),
            )
        }
    }
}

/**
 * Abonnement « accès à distance » vendu par la boutique sans rien y changer : chaque achat est un article à 1000 F
 * nommé « distance-AAAAMMJJ » (date de début), valable 2 mois. Plusieurs achats s'enchaînent.
 */
object RemotePlan {
    const val PREFIX = "distance-"
    const val MONTHS = 2L
    private val FMT = DateTimeFormatter.BASIC_ISO_DATE

    fun item(start: LocalDate): String = PREFIX + start.format(FMT)

    /** Périodes payées : début (inclus) et fin (exclue). */
    fun periods(items: Collection<String>): List<Pair<LocalDate, LocalDate>> =
        items.filter { it.startsWith(PREFIX) }
            .mapNotNull { runCatching { LocalDate.parse(it.removePrefix(PREFIX), FMT) }.getOrNull() }
            .distinct().sorted().map { it to it.plusMonths(MONTHS) }

    /** Dernier jour d'accès (inclus) si l'accès est ouvert aujourd'hui, sinon null. */
    fun activeUntil(items: Collection<String>, today: LocalDate): LocalDate? {
        val ps = periods(items)
        var day = today
        var covered = false
        while (true) {
            val p = ps.firstOrNull { (a, b) -> !day.isBefore(a) && day.isBefore(b) } ?: break
            covered = true
            day = p.second
        }
        return if (covered) day.minusDays(1) else null
    }

    /** Début du prochain achat : le lendemain de la fin de l'accès en cours, sinon aujourd'hui. */
    fun nextStart(items: Collection<String>, today: LocalDate): LocalDate = activeUntil(items, today)?.plusDays(1) ?: today

    fun label(start: LocalDate): String {
        val f = DateTimeFormatter.ofPattern("dd/MM/yyyy")
        return "Accès à distance · 2 mois (${start.format(f)} → ${start.plusMonths(MONTHS).minusDays(1).format(f)})"
    }
}

/**
 * « Back To Home » de MikroTik : VPN WireGuard intégré à RouterOS 7.12+, joignable même derrière une box 4G
 * (relais de MikroTik). L'application s'y connecte avec son propre accès (utilisateur BTH).
 */
object BackToHome {
    data class Support(val supported: Boolean, val version: String, val reason: String, val enabled: Boolean, val status: String = "")

    fun version(v: String): Pair<Int, Int>? =
        Regex("^\\s*(\\d+)\\.(\\d+)").find(v)?.let { it.groupValues[1].toInt() to it.groupValues[2].toInt() }

    /** Le routeur sait-il faire « Back To Home » ? (à vérifier sur place, en Wi-Fi) */
    fun check(r: RouterOs): Support {
        val ver = r.command("/system/resource/print").firstOrNull()?.get("version").orEmpty()
        val v = version(ver)
        if (v == null || v.first < 7 || (v.first == 7 && v.second < 12)) {
            return Support(false, ver, "Votre routeur est en RouterOS ${ver.substringBefore(' ').ifBlank { "inconnu" }} : l'accès à distance demande RouterOS 7.12 ou plus récent.", false)
        }
        val cloud = runCatching { r.command("/ip/cloud/print").firstOrNull().orEmpty() }.getOrDefault(emptyMap())
        val bth = cloud["back-to-home-vpn"]
            ?: return Support(false, ver, "Ce modèle de routeur ne propose pas « Back To Home », nécessaire à l'accès à distance.", false)
        return Support(true, ver, "", bth == "enabled", cloud["vpn-status"].orEmpty())
    }

    /** Cherche une configuration WireGuard dans les réponses du routeur, quel que soit le nom du champ. */
    fun findConfig(replies: List<Map<String, String>>): WgConf? =
        replies.asSequence().flatMap { it.values.asSequence() }
            .filter { it.contains("[Interface]", true) && it.contains("[Peer]", true) }
            .mapNotNull { WgConf.parse(it) }.firstOrNull()

    /**
     * Active Back To Home sur le routeur et crée l'accès de cet appareil ([name]). Renvoie sa configuration WireGuard.
     * RouterOS 7.14+ : un utilisateur BTH par appareil ; 7.12–7.13 : la configuration unique de /ip/cloud.
     */
    fun setup(r: RouterOs, name: String, wait: (Long) -> Unit = { Thread.sleep(it) }): WgConf {
        val cloud = r.command("/ip/cloud/print").firstOrNull().orEmpty()
        if (cloud["back-to-home-vpn"] == null) throw IllegalStateException("Ce routeur ne propose pas « Back To Home ».")
        if (cloud["back-to-home-vpn"] != "enabled") {
            try { r.command("/ip/cloud/set", "=back-to-home-vpn=enabled") }
            catch (e: RouterOsException) { r.command("/ip/cloud/set", "=ddns-enabled=yes", "=back-to-home-vpn=enabled") }
        }
        val users = runCatching { r.command("/ip/cloud/back-to-home-users/print") }.getOrNull()
        if (users != null) {
            var id = users.firstOrNull { it["name"] == name }?.get(".id")
            if (id == null) {
                val added = try {
                    r.command("/ip/cloud/back-to-home-users/add", "=name=$name", "=allow-lan=yes", "=comment=Mikhmon Android")
                } catch (e: RouterOsException) {
                    r.command("/ip/cloud/back-to-home-users/add", "=name=$name")
                }
                id = added.firstOrNull()?.get("ret")
                    ?: r.command("/ip/cloud/back-to-home-users/print").firstOrNull { it["name"] == name }?.get(".id")
            }
            if (id != null) {
                for (attempt in 0 until 8) {
                    for (args in listOf(arrayOf("=numbers=$id"), arrayOf("=.id=$id"))) {
                        val shown = runCatching { r.command("/ip/cloud/back-to-home-users/show-client-config", *args) }.getOrDefault(emptyList())
                        findConfig(shown)?.let { return it }
                    }
                    findConfig(runCatching { r.command("/ip/cloud/back-to-home-users/print", "=detail=") }.getOrDefault(emptyList()).filter { it[".id"] == id || it["name"] == name })
                        ?.let { return it }
                    wait(1500)
                }
            }
        }
        for (attempt in 0 until 10) {
            findConfig(r.command("/ip/cloud/print"))?.let { return it }
            wait(1500)
        }
        throw IllegalStateException("Le routeur n'a pas encore donné l'accès « Back To Home ». Vérifiez qu'il a Internet, puis réessayez dans une minute.")
    }

    /** Retire l'accès de cet appareil (désactivation de l'accès à distance). */
    fun remove(r: RouterOs, name: String) {
        val id = runCatching { r.command("/ip/cloud/back-to-home-users/print") }.getOrDefault(emptyList()).firstOrNull { it["name"] == name }?.get(".id") ?: return
        runCatching { r.command("/ip/cloud/back-to-home-users/remove", "=.id=$id") }
    }

    /** État du relais Back To Home, lisible (« prêt », « en attente »…). */
    fun status(r: RouterOs): String = runCatching { r.command("/ip/cloud/print").firstOrNull()?.get("vpn-status") }.getOrNull().orEmpty()
}

package com.wifizone.mikhmon.account

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.OffsetDateTime
import java.util.Base64

/** Petit stockage clé → valeur (SharedPreferences sur Android, mémoire dans les tests). */
interface Prefs {
    fun get(key: String): String?
    fun put(key: String, value: String?)
}

/** Ouverture des connexions HTTP (Android : réseau par défaut, puis données mobiles si le Wi-Fi n'a pas internet). */
interface Opener {
    fun open(url: URL): HttpURLConnection = url.openConnection() as HttpURLConnection
    fun fallback(url: URL): HttpURLConnection? = null
}

/**
 * Compte MPro (la boutique) pour une application, sans dépendance à Android : connexion par la page
 * /connexion-app/ de la boutique (PKCE), jeton de l'application, essai, accès à vie, achats en un geste.
 * Réutilisable dans toutes les applications de l'éditeur.
 */
class MproCore(
    val appId: String,
    val scheme: String,
    private val appVersion: String,
    private val candidates: List<String>,
    private val prefs: Prefs,
    private val opener: Opener = object : Opener {},
    private val device: String = "",
    private val now: () -> Long = System::currentTimeMillis,
) {
    data class Account(
        val name: String, val email: String, val balance: Long,
        /** trial | lifetime | expired | blocked | none */
        val status: String, val daysLeft: Int, val trialEnd: String?,
        val items: Set<String>, val lifetimePrice: Long, val itemPrice: Long, val itemPrices: Map<String, Long>,
        val currency: String, val pending: String?, val checkedAt: Long,
        /** Article dont le paiement attend sa confirmation (« lifetime », un modèle…), ou null. */
        val pendingItem: String? = null,
    ) {
        val unlocked get() = status == "lifetime" || status == "trial"
        fun priceOf(item: String) = itemPrices[item] ?: itemPrice
    }

    sealed interface Checkout {
        object Done : Checkout
        data class Pay(val url: String, val amount: Long, val price: Long) : Checkout
        data class NeedPhone(val message: String) : Checkout
        data class NeedEmail(val message: String) : Checkout
    }

    class ApiError(val code: Int, message: String, val data: JSONObject? = null) : IOException(message)

    /** Prévenu à chaque changement du compte (Android : met à jour l'écran). */
    var listener: ((Account?) -> Unit)? = null

    private var current: Account? = cached()
    val account get() = current

    init { if (prefs.get("first_seen") == null) prefs.put("first_seen", now().toString()) }

    /** Jours restants pour utiliser l'application avant la première connexion (pas d'internet, boutique pas encore à jour…). */
    fun graceDaysLeft(days: Int = 3): Int {
        val first = prefs.get("first_seen")?.toLongOrNull() ?: now()
        val left = days - ((now() - first) / 86_400_000L).toInt()
        return if (prefs.get("ever_logged") == "1") 0 else left.coerceAtLeast(0)
    }

    private fun publish(a: Account?) { current = a; listener?.invoke(a) }

    var token: String?
        get() = prefs.get("token")
        private set(v) = prefs.put("token", v)

    val loggedIn get() = token != null
    private val testMode get() = prefs.get("test") == "1"

    val base: String get() = prefs.get("base") ?: candidates.first()
    val shopUrl get() = "$base/boutique/#/compte"

    // ------------------------------------------------------------------ HTTP
    private fun request(method: String, path: String, body: JSONObject? = null, auth: Boolean = true, baseUrl: String = base): JSONObject {
        val url = URL(baseUrl.trimEnd('/') + path)
        fun attempt(c: HttpURLConnection): JSONObject {
            c.requestMethod = method
            c.connectTimeout = 12000; c.readTimeout = 20000
            c.setRequestProperty("Accept", "application/json")
            if (auth) token?.let { c.setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            val code = c.responseCode
            val text = (if (code < 400) c.inputStream else c.errorStream)?.bufferedReader()?.readText().orEmpty()
            val json = runCatching { JSONObject(text) }.getOrElse { JSONObject() }
            if (code >= 400) {
                if (code == 401 && auth) token = null
                throw ApiError(code, json.optString("error").ifBlank { "Erreur du serveur ($code)" }, json)
            }
            return json
        }
        return try {
            attempt(opener.open(url))
        } catch (e: ApiError) {
            throw e
        } catch (e: IOException) {
            val alt = opener.fallback(url) ?: throw IOException("Pas de connexion internet. Activez les données mobiles ou un Wi-Fi connecté à internet.")
            attempt(alt)
        }
    }

    // ------------------------------------------------------------------ découverte de la boutique
    /** Trouve une adresse qui répond et retient l'adresse principale annoncée par la boutique. */
    fun discover(): Boolean {
        if (testMode) return true
        val order = (listOfNotNull(prefs.get("base")) + candidates).distinct()
        for (b in order) {
            val ok = runCatching {
                val j = request("GET", "/api/app/config?app=$appId", auth = false, baseUrl = b)
                val pub = j.optString("public_url").trimEnd('/').ifBlank { b }
                // L'adresse principale n'est retenue que si elle répond aussi.
                val chosen = if (pub != b && runCatching { request("GET", "/api/app/config?app=$appId", auth = false, baseUrl = pub) }.isSuccess) pub else b
                prefs.put("base", chosen)
            }.isSuccess
            if (ok) return true
        }
        return false
    }

    // ------------------------------------------------------------------ connexion (PKCE)
    private fun b64(bytes: ByteArray) = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    /** Adresse de la page de connexion de la boutique (à ouvrir dans le navigateur). */
    fun loginUrl(chooseAccount: Boolean = false): String {
        val verifier = b64(ByteArray(32).also { SecureRandom().nextBytes(it) })
        val challenge = b64(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))
        val state = b64(ByteArray(12).also { SecureRandom().nextBytes(it) })
        prefs.put("verifier", verifier)
        prefs.put("state", state)
        return "$base/connexion-app/?app=${enc(appId)}&challenge=${enc(challenge)}&state=${enc(state)}&redirect_uri=${enc("$scheme://auth")}" +
            if (chooseAccount) "&prompt=select_account" else ""
    }

    /** Retour de la boutique (monapp://auth?code=…&state=…) → jeton de l'application. */
    fun completeLogin(code: String?, state: String?): Account {
        if (code.isNullOrBlank()) throw IOException("Connexion annulée.")
        if (state != prefs.get("state")) throw IOException("Demande de connexion expirée. Recommencez.")
        val body = JSONObject().put("app", appId).put("code", code).put("verifier", prefs.get("verifier") ?: "").put("device", device)
        val j = request("POST", "/api/app/token", body, auth = false)
        token = j.getString("token")
        prefs.put("ever_logged", "1")
        prefs.put("verifier", null); prefs.put("state", null)
        return apply(j)
    }

    // ------------------------------------------------------------------ compte
    private fun parse(j: JSONObject, checkedAt: Long): Account {
        val u = j.optJSONObject("user") ?: JSONObject()
        val app = j.optJSONObject("app") ?: JSONObject()
        val lic = j.optJSONObject("license") ?: JSONObject()
        val items = j.optJSONArray("items")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: emptySet()
        val ip = app.optJSONObject("item_prices")
        val prices = ip?.keys()?.asSequence()?.associate { it to ip.optLong(it) } ?: emptyMap()
        val pend = j.optJSONObject("pending")
        var status = lic.optString("status", "none")
        val trialEnd = lic.optString("trial_end").ifBlank { null }
        val end = trialEnd?.let { runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() }
        // Hors ligne : un essai se termine à sa date même sans contact avec la boutique.
        if (status == "trial" && end != null && end < now()) status = "expired"
        val days = if (end != null && status == "trial") ((end - now() + 86_399_999L) / 86_400_000L).toInt().coerceAtLeast(0) else lic.optInt("days_left")
        return Account(
            name = u.optString("name"), email = u.optString("email"), balance = u.optLong("balance"),
            status = status, daysLeft = days, trialEnd = trialEnd, items = items,
            lifetimePrice = app.optLong("lifetime_price", 4000), itemPrice = app.optLong("item_price", 1000), itemPrices = prices,
            currency = j.optString("currency").ifBlank { "FCFA" },
            pending = pend?.let { if (it.optString("item") == "lifetime") "l’accès à vie" else it.optString("item_name").ifBlank { it.optString("item") } },
            checkedAt = checkedAt,
            pendingItem = pend?.optString("item")?.ifBlank { null },
        )
    }

    private fun apply(j: JSONObject): Account {
        val a = parse(j, now())
        prefs.put("account", j.toString())
        prefs.put("checked_at", a.checkedAt.toString())
        publish(a)
        return a
    }

    fun cached(): Account? {
        val s = prefs.get("account") ?: return null
        if (prefs.get("token") == null) return null
        return runCatching { parse(JSONObject(s), prefs.get("checked_at")?.toLongOrNull() ?: 0L) }.getOrNull()
    }

    /** Relit le compte. Garde la dernière version connue si la boutique est injoignable. */
    fun refresh(): Account? {
        if (testMode) return current
        if (token == null) { publish(null); return null }
        return try { apply(request("GET", "/api/app/me")) } catch (e: ApiError) {
            if (e.code == 401) { prefs.put("account", null); publish(null) }
            throw e
        }
    }

    fun ping(info: String) {
        if (token == null || testMode) return
        runCatching { apply(request("POST", "/api/app/ping", JSONObject().put("device", device).put("app_version", appVersion).put("info", info.take(190)))) }
    }

    /** Paiement en un geste : achat immédiat avec le solde, sinon lien de paiement (l'achat se fera tout seul). */
    fun checkout(item: String, itemName: String = "", phone: String? = null, email: String? = null): Checkout {
        val body = JSONObject().put("item", item).put("item_name", itemName)
        phone?.let { body.put("phone", it) }
        email?.let { body.put("email", it) }
        return try {
            val j = request("POST", "/api/app/checkout", body)
            apply(j)
            if (j.optString("status") == "done") Checkout.Done
            else Checkout.Pay(j.getString("checkout_url"), j.optLong("amount"), j.optLong("price"))
        } catch (e: ApiError) {
            if (e.code == 409) {
                when (e.data?.optString("need")) {
                    "phone" -> return Checkout.NeedPhone(e.message ?: "")
                    "email" -> return Checkout.NeedEmail(e.message ?: "")
                }
            }
            throw e
        }
    }

    /** Au retour de la page de paiement : la boutique vérifie le paiement et termine l'achat. */
    fun checkoutStatus(): Account? {
        if (token == null || testMode) return current
        return apply(request("POST", "/api/app/checkout/status", JSONObject()))
    }

    fun cancelPending() {
        if (token == null || testMode) return
        runCatching { apply(request("POST", "/api/app/checkout/cancel", JSONObject())) }
    }

    fun logout() {
        if (!testMode) runCatching { request("POST", "/api/app/logout", JSONObject()) }
        token = null
        prefs.put("account", null)
        publish(null)
    }

    /** Tests : compte en cache, aucune requête vers la boutique (null : déconnecté). */
    fun seedForTests(json: String?) {
        prefs.put("test", "1")
        if (json == null) { prefs.put("token", null); prefs.put("account", null); publish(null); return }
        prefs.put("token", "mpa_test_seed_000000000000000")
        prefs.put("account", json)
        publish(cached())
    }
}

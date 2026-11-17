package com.wifizone.mikhmon.account

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.util.Base64
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Compte MPro (la boutique) pour une application : connexion par la page /connexion-app/ de la boutique
 * (Google ou e-mail, session de la boutique reconnue), jeton de l'application, essai, accès à vie, achats intégrés.
 * Réutilisable tel quel dans les autres applications : il suffit de changer [appId] et [scheme].
 */
class MproClient(
    private val ctx: Context,
    val appId: String,
    private val scheme: String,
    private val appVersion: String,
    /** Adresses connues de la boutique, essayées dans l'ordre (la dernière adresse principale apprise passe devant). */
    private val candidates: List<String>,
) {
    data class Account(
        val name: String, val email: String, val balance: Long,
        /** trial | lifetime | expired | blocked | none */
        val status: String, val daysLeft: Int, val trialEnd: String?,
        val items: Set<String>, val lifetimePrice: Long, val itemPrice: Long, val itemPrices: Map<String, Long>,
        val currency: String, val pending: String?, val checkedAt: Long,
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

    private val prefs = ctx.getSharedPreferences("mpro-$appId", Context.MODE_PRIVATE)
    private val _account = MutableStateFlow(cached())
    val account: StateFlow<Account?> = _account
    val loggedIn get() = token != null
    /** Tests : compte en cache, aucune requête vers la boutique. */
    private val testMode get() = prefs.getBoolean("test", false)

    private var token: String?
        get() = prefs.getString("token", null)
        set(v) { prefs.edit().putString("token", v).apply() }

    val base: String get() = prefs.getString("base", null) ?: candidates.first()

    /** Adresse du compte sur la boutique (pour l'ouvrir dans le navigateur). */
    val shopUrl get() = "$base/boutique/#/compte"

    // ------------------------------------------------------------------ HTTP
    private fun defaultOpen(url: URL) = url.openConnection() as HttpURLConnection

    /** Si le Wi-Fi du MikroTik n'a pas internet, on passe par les données mobiles (réseau validé). */
    private fun internetOpen(url: URL): HttpURLConnection? {
        val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return null
        @Suppress("DEPRECATION")
        val net = cm.allNetworks.firstOrNull { n ->
            val c = cm.getNetworkCapabilities(n) ?: return@firstOrNull false
            c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) && c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && !c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } ?: return null
        return net.openConnection(url) as HttpURLConnection
    }

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
                if (code == 401 && auth) { token = null }
                throw ApiError(code, json.optString("error").ifBlank { "Erreur du serveur ($code)" }, json)
            }
            return json
        }
        return try {
            attempt(defaultOpen(url))
        } catch (e: ApiError) {
            throw e
        } catch (e: IOException) {
            val alt = internetOpen(url) ?: throw IOException("Pas de connexion internet. Activez les données mobiles ou un Wi-Fi connecté à internet.")
            attempt(alt)
        }
    }

    // ------------------------------------------------------------------ découverte de la boutique
    /** Trouve une adresse qui répond et retient l'adresse principale annoncée par la boutique. */
    suspend fun discover(): Boolean = withContext(Dispatchers.IO) {
        if (testMode) return@withContext true
        val order = (listOfNotNull(prefs.getString("base", null)) + candidates).distinct()
        for (b in order) {
            val ok = runCatching {
                val j = request("GET", "/api/app/config?app=$appId", auth = false, baseUrl = b)
                val pub = j.optString("public_url").trimEnd('/').ifBlank { b }
                // L'adresse principale est essayée à son tour : on ne la retient que si elle répond aussi.
                val chosen = if (pub != b && runCatching { request("GET", "/api/app/config?app=$appId", auth = false, baseUrl = pub) }.isSuccess) pub else b
                prefs.edit().putString("base", chosen).apply()
            }.isSuccess
            if (ok) return@withContext true
        }
        false
    }

    // ------------------------------------------------------------------ connexion
    private fun b64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

    /** Ouvre la page de connexion de la boutique dans le navigateur (onglet personnalisé). */
    fun startLogin(activity: Context, chooseAccount: Boolean = false) {
        val verifier = b64(ByteArray(32).also { SecureRandom().nextBytes(it) })
        val challenge = b64(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))
        val state = b64(ByteArray(12).also { SecureRandom().nextBytes(it) })
        prefs.edit().putString("verifier", verifier).putString("state", state).apply()
        val uri = Uri.parse("$base/connexion-app/").buildUpon()
            .appendQueryParameter("app", appId)
            .appendQueryParameter("challenge", challenge)
            .appendQueryParameter("state", state)
            .appendQueryParameter("redirect_uri", "$scheme://auth")
            .apply { if (chooseAccount) appendQueryParameter("prompt", "select_account") }
            .build()
        openBrowser(activity, uri)
    }

    fun openBrowser(activity: Context, uri: Uri) {
        val tab = CustomTabsIntent.Builder()
            .setDefaultColorSchemeParams(CustomTabColorSchemeParams.Builder().setToolbarColor(0xFFF2F2F7.toInt()).build())
            .setShowTitle(true)
            .build()
        runCatching { tab.launchUrl(activity, uri) }.onFailure {
            activity.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    /** Retour de la boutique : monapp://auth?code=…&state=… → jeton de l'application. */
    suspend fun completeLogin(uri: Uri): Account = withContext(Dispatchers.IO) {
        val code = uri.getQueryParameter("code") ?: throw IOException("Connexion annulée.")
        val state = uri.getQueryParameter("state")
        if (state != prefs.getString("state", null)) throw IOException("Demande de connexion expirée. Recommencez.")
        val body = JSONObject().put("app", appId).put("code", code).put("verifier", prefs.getString("verifier", ""))
            .put("device", device())
        val j = request("POST", "/api/app/token", body, auth = false)
        token = j.getString("token")
        prefs.edit().remove("verifier").remove("state").apply()
        apply(j)
    }

    fun device() = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}".trim().take(110)

    // ------------------------------------------------------------------ compte
    private fun apply(j: JSONObject): Account {
        val u = j.optJSONObject("user") ?: JSONObject()
        val app = j.optJSONObject("app") ?: JSONObject()
        val lic = j.optJSONObject("license") ?: JSONObject()
        val items = j.optJSONArray("items")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: emptySet()
        val ip = app.optJSONObject("item_prices")
        val prices = ip?.keys()?.asSequence()?.associate { it to ip.optLong(it) } ?: emptyMap()
        val pend = j.optJSONObject("pending")
        val a = Account(
            name = u.optString("name"), email = u.optString("email"), balance = u.optLong("balance"),
            status = lic.optString("status", "none"), daysLeft = lic.optInt("days_left"), trialEnd = lic.optString("trial_end").ifBlank { null },
            items = items, lifetimePrice = app.optLong("lifetime_price", 4000), itemPrice = app.optLong("item_price", 1000), itemPrices = prices,
            currency = j.optString("currency").ifBlank { "FCFA" },
            pending = pend?.let { if (it.optString("item") == "lifetime") "l’accès à vie" else it.optString("item_name").ifBlank { it.optString("item") } },
            checkedAt = System.currentTimeMillis(),
        )
        prefs.edit().putString("account", j.toString()).putLong("checked_at", a.checkedAt).apply()
        _account.value = a
        return a
    }

    private fun cached(): Account? {
        val s = prefs.getString("account", null) ?: return null
        if (prefs.getString("token", null) == null) return null
        return runCatching {
            val j = JSONObject(s)
            val u = j.optJSONObject("user") ?: JSONObject()
            val app = j.optJSONObject("app") ?: JSONObject()
            val lic = j.optJSONObject("license") ?: JSONObject()
            val items = j.optJSONArray("items")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: emptySet()
            val ip = app.optJSONObject("item_prices")
            val prices = ip?.keys()?.asSequence()?.associate { it to ip.optLong(it) } ?: emptyMap()
            var status = lic.optString("status", "none")
            // Hors ligne : un essai se termine à sa date même sans contact avec la boutique.
            val end = lic.optString("trial_end").takeIf { it.isNotBlank() }?.let { runCatching { java.time.OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() }
            if (status == "trial" && end != null && end < System.currentTimeMillis()) status = "expired"
            Account(u.optString("name"), u.optString("email"), u.optLong("balance"), status,
                if (end != null) ((end - System.currentTimeMillis()) / 86_400_000L + 1).toInt().coerceAtLeast(0) else lic.optInt("days_left"),
                lic.optString("trial_end").ifBlank { null }, items, app.optLong("lifetime_price", 4000), app.optLong("item_price", 1000), prices,
                j.optString("currency").ifBlank { "FCFA" }, null, prefs.getLong("checked_at", 0))
        }.getOrNull()
    }

    /** Relit le compte (au démarrage, au retour de la boutique). Garde la dernière version connue hors ligne. */
    suspend fun refresh(): Account? = withContext(Dispatchers.IO) {
        if (testMode) return@withContext _account.value
        if (token == null) { _account.value = null; return@withContext null }
        try { apply(request("GET", "/api/app/me")) } catch (e: ApiError) {
            if (e.code == 401) { _account.value = null; prefs.edit().remove("account").apply() }
            throw e
        }
    }

    suspend fun ping(info: String) = withContext(Dispatchers.IO) {
        if (token == null || testMode) return@withContext
        runCatching { apply(request("POST", "/api/app/ping", JSONObject().put("device", device()).put("app_version", appVersion).put("info", info.take(190)))) }
    }

    /** Paiement en un geste : achat immédiat avec le solde, sinon lien de paiement (l'achat se fera tout seul). */
    suspend fun checkout(item: String, itemName: String = "", phone: String? = null, email: String? = null): Checkout = withContext(Dispatchers.IO) {
        val body = JSONObject().put("item", item).put("item_name", itemName)
        phone?.let { body.put("phone", it) }
        email?.let { body.put("email", it) }
        try {
            val j = request("POST", "/api/app/checkout", body)
            apply(j)
            if (j.optString("status") == "done") Checkout.Done
            else Checkout.Pay(j.getString("checkout_url"), j.optLong("amount"), j.optLong("price"))
        } catch (e: ApiError) {
            if (e.code == 409) {
                val need = e.data?.optString("need")
                if (need == "phone") return@withContext Checkout.NeedPhone(e.message ?: "")
                if (need == "email") return@withContext Checkout.NeedEmail(e.message ?: "")
            }
            throw e
        }
    }

    /** Au retour de la page de paiement : la boutique vérifie le paiement et termine l'achat. */
    suspend fun checkoutStatus(): Account? = withContext(Dispatchers.IO) {
        if (token == null || testMode) return@withContext _account.value
        apply(request("POST", "/api/app/checkout/status", JSONObject()))
    }

    suspend fun cancelPending() = withContext(Dispatchers.IO) {
        if (token == null) return@withContext
        runCatching { apply(request("POST", "/api/app/checkout/cancel", JSONObject())) }
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        runCatching { request("POST", "/api/app/logout", JSONObject()) }
        token = null
        prefs.edit().remove("account").apply()
        _account.value = null
    }

    /** Pour les tests : compte en cache sans passer par la boutique. */
    fun seedForTests(json: String?) {
        if (json == null) { prefs.edit().clear().putBoolean("test", true).apply(); _account.value = null; return }
        prefs.edit().putBoolean("test", true).putString("token", "mpa_test_seed_000000000000000").putString("account", json).apply()
        _account.value = cached()
    }
}

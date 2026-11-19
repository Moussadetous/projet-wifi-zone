package com.wifizone.mikhmon.account

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Compte MPro sur Android : la logique est dans [MproCore] (testée sur ordinateur contre le vrai serveur) ;
 * ici, le stockage, le réseau Android et l'ouverture de la boutique dans un onglet du navigateur.
 */
class MproClient(
    private val ctx: Context,
    appId: String,
    scheme: String,
    appVersion: String,
    candidates: List<String>,
) {
    private val sp = ctx.getSharedPreferences("mpro-$appId", Context.MODE_PRIVATE)
    private val prefs = object : Prefs {
        override fun get(key: String): String? = sp.getString(key, null)
        override fun put(key: String, value: String?) { sp.edit().apply { if (value == null) remove(key) else putString(key, value) }.apply() }
    }
    private val opener = object : Opener {
        /** Si le Wi-Fi du MikroTik n'a pas internet, on passe par un autre réseau validé (données mobiles). */
        override fun fallback(url: URL): HttpURLConnection? {
            val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return null
            @Suppress("DEPRECATION")
            val net = cm.allNetworks.firstOrNull { n ->
                val c = cm.getNetworkCapabilities(n) ?: return@firstOrNull false
                c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) && c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    && !c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) && !c.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            } ?: return null
            return net.openConnection(url) as HttpURLConnection
        }
    }
    val core = MproCore(appId, scheme, appVersion, candidates, prefs, opener, device())

    private val _account = MutableStateFlow(core.account)
    val account: StateFlow<MproCore.Account?> = _account

    init { core.listener = { _account.value = it } }

    val loggedIn get() = core.loggedIn
    val base get() = core.base
    val shopUrl get() = core.shopUrl

    fun device() = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}".trim().take(110)

    suspend fun discover(): Boolean = withContext(Dispatchers.IO) { core.discover() }

    /** Ouvre la page de connexion de la boutique (Google ou e-mail ; session de la boutique reconnue). */
    fun startLogin(activity: Context, chooseAccount: Boolean = false) = openBrowser(activity, Uri.parse(core.loginUrl(chooseAccount)))

    fun openBrowser(activity: Context, uri: Uri) {
        val tab = CustomTabsIntent.Builder()
            .setDefaultColorSchemeParams(CustomTabColorSchemeParams.Builder().setToolbarColor(0xFFF3F5FA.toInt()).build())
            .setShowTitle(true)
            .build()
        runCatching { tab.launchUrl(activity, uri) }.onFailure {
            activity.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    suspend fun completeLogin(uri: Uri) = withContext(Dispatchers.IO) { core.completeLogin(uri.getQueryParameter("code"), uri.getQueryParameter("state")) }
    suspend fun refresh() = withContext(Dispatchers.IO) { core.refresh() }
    suspend fun ping(info: String) = withContext(Dispatchers.IO) { core.ping(info) }
    suspend fun checkout(item: String, itemName: String = "", phone: String? = null, email: String? = null) =
        withContext(Dispatchers.IO) { core.checkout(item, itemName, phone, email) }
    suspend fun checkoutStatus() = withContext(Dispatchers.IO) { core.checkoutStatus() }
    suspend fun cancelPending() = withContext(Dispatchers.IO) { core.cancelPending() }
    suspend fun useReferralCode(code: String) = withContext(Dispatchers.IO) { core.useReferralCode(code) }
    suspend fun reportRouter(serial: String) = withContext(Dispatchers.IO) { runCatching { core.reportRouter(serial) } }
    suspend fun logout() = withContext(Dispatchers.IO) { core.logout() }
    fun seedForTests(json: String?) = core.seedForTests(json)
    fun graceDaysLeft() = core.graceDaysLeft()
}

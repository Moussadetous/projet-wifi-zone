package com.wifizone.mikhmon

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.net.URLEncoder

/** Démarre le serveur puis affiche Mikhmon, déjà connecté, dans une WebView. */
class MainActivity : AppCompatActivity() {
    private lateinit var web: WebView
    private lateinit var status: TextView
    private lateinit var spinner: ProgressBar
    private var loggedIn = false
    private var settings: MikhmonSettings? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (i.getBooleanExtra("ready", false)) openMikhmon()
            else showError(i.getStringExtra("error") ?: "Le serveur PHP n'a pas démarré (voir php-server.log).")
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val root = FrameLayout(this)
        web = WebView(this).apply {
            visibility = View.GONE
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            // La WebView ne doit joindre que le serveur local : pas de navigation externe
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(v: WebView, r: android.webkit.WebResourceRequest) =
                    r.url.host != "127.0.0.1"
                override fun onPageFinished(v: WebView, url: String) {
                    val s = settings ?: return
                    if (!loggedIn && url.contains("id=login")) {
                        loggedIn = true
                        v.loadUrl("http://127.0.0.1:${s.port}/?hotspot=dashboard&session=" +
                            URLEncoder.encode(s.routerName, "UTF-8"))
                    }
                }
            }
            webChromeClient = WebChromeClient()
        }
        spinner = ProgressBar(this)
        status = TextView(this).apply { text = "Démarrage du serveur Mikhmon…"; gravity = Gravity.CENTER; setTextColor(Color.GRAY) }
        val gear = Button(this).apply { text = "⚙"; setOnClickListener { menu() } }
        root.addView(web, -1, -1)
        root.addView(spinner, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        root.addView(status, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER).apply { topMargin = 220 })
        root.addView(gear, FrameLayout.LayoutParams(-2, -2, Gravity.END or Gravity.BOTTOM))
        setContentView(root)
    }

    override fun onStart() {
        super.onStart()
        settings = SettingsStore(this).load()
        val s = settings
        if (s == null) { startActivity(Intent(this, SetupActivity::class.java)); finish(); return }
        val f = IntentFilter(MikhmonService.ACTION_STATE)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, f, Context.RECEIVER_NOT_EXPORTED)
        else registerReceiver(receiver, f)
        if (!onWifi()) status.text = "Pas de Wi-Fi : connectez-vous au réseau du MikroTik.\nDémarrage du serveur…"
        MikhmonService.start(this)
    }

    override fun onStop() { runCatching { unregisterReceiver(receiver) }; super.onStop() }

    private fun openMikhmon() {
        val s = settings ?: return
        spinner.visibility = View.GONE; status.visibility = View.GONE; web.visibility = View.VISIBLE
        if (web.url != null) return
        loggedIn = false
        web.postUrl("http://127.0.0.1:${s.port}/admin.php?id=login",
            ("user=" + URLEncoder.encode(s.adminUser, "UTF-8") +
             "&pass=" + URLEncoder.encode(s.adminPass, "UTF-8")).toByteArray())
    }

    private fun showError(msg: String) {
        spinner.visibility = View.GONE; status.visibility = View.VISIBLE; status.text = msg
    }

    private fun onWifi(): Boolean {
        val cm = getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun menu() {
        AlertDialog.Builder(this).setItems(arrayOf("Recharger", "Modifier la configuration", "Arrêter le serveur et quitter")) { _, w ->
            when (w) {
                0 -> web.reload()
                1 -> startActivity(Intent(this, SetupActivity::class.java).putExtra("edit", true))
                2 -> { MikhmonService.stop(this); finishAffinity() }
            }
        }.show()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { if (web.canGoBack()) web.goBack() else moveTaskToBack(true) }
}

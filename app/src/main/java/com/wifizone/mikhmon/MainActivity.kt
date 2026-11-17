package com.wifizone.mikhmon

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.print.PrintManager
import android.view.Gravity
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.wifizone.mikhmon.Ui.add
import com.wifizone.mikhmon.Ui.dp
import org.json.JSONObject

/** Démarre Mikhmon et l'affiche, déjà connecté, dans le navigateur intégré. */
class MainActivity : AppCompatActivity() {
    private lateinit var web: WebView
    private lateinit var overlay: LinearLayout
    private lateinit var spinner: ProgressBar
    private lateinit var message: TextView
    private lateinit var details: TextView
    private lateinit var detailsBox: ScrollView
    private lateinit var actions: LinearLayout

    private var cfg: MikhmonSettings? = null
    private var loadedPort = -1
    private var loginAttempts = 0
    private var jumpedToSession = false
    private var revealed = false
    private var mainFrameErrors = 0
    private var pageFailed = false

    private val listener: (MikhmonServer.State) -> Unit = { render(it) }

    private val printJs = "window.print=function(){MikhmonDroid.print();};"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cfg = SettingsStore(this).load()
        if (cfg == null) { startActivity(Intent(this, SetupActivity::class.java)); finish(); return }
        buildUi()
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    private fun buildUi() {
        val root = FrameLayout(this)
        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.javaScriptCanOpenWindowsAutomatically = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            addJavascriptInterface(PrintBridge(), "MikhmonDroid")
            if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT))
                WebViewCompat.addDocumentStartJavaScript(this, printJs, setOf("http://127.0.0.1"))
            webViewClient = Client()
        }
        root.addView(web, FrameLayout.LayoutParams(-1, -1))

        overlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setBackgroundColor(Color.WHITE); val p = dp(28); setPadding(p, p, p, p)
            isClickable = true
        }
        spinner = ProgressBar(this)
        message = Ui.body(this).apply { gravity = Gravity.CENTER; textSize = 17f }
        details = TextView(this).apply { textSize = 11f; typeface = Typeface.MONOSPACE; setTextIsSelectable(true) }
        detailsBox = ScrollView(this).apply { addView(details); visibility = View.GONE }
        actions = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        overlay.add(spinner, width = -2)
        overlay.add(message, 16)
        overlay.addView(detailsBox, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(12) })
        overlay.add(actions, 12)
        root.addView(overlay, FrameLayout.LayoutParams(-1, -1))

        val menu = TextView(this).apply {
            text = "⋮"; textSize = 22f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(0xAA1565C0.toInt())
            }
            setOnClickListener { showMenu() }
        }
        root.addView(menu, FrameLayout.LayoutParams(dp(44), dp(44), Gravity.END or Gravity.BOTTOM).apply {
            setMargins(0, 0, dp(12), dp(12))
        })
        setContentView(root)
        showProgress("Démarrage de Mikhmon…")
    }

    override fun onStart() {
        super.onStart()
        if (cfg == null) return
        MikhmonServer.observe(listener)
        MikhmonService.start(this)
    }

    override fun onStop() {
        MikhmonServer.unobserve(listener)
        super.onStop()
    }

    private fun sessionUrl(port: Int) =
        "http://127.0.0.1:$port/?session=" + Uri.encode(Defaults.sessionName(cfg!!.sessionName))

    private fun render(s: MikhmonServer.State) {
        when (s) {
            MikhmonServer.State.Idle, MikhmonServer.State.Starting -> showProgress("Démarrage de Mikhmon…")
            is MikhmonServer.State.Ready -> {
                if (loadedPort != s.port || mainFrameErrors > 0) {
                    loadedPort = s.port
                    loginAttempts = 0; jumpedToSession = false
                    if (!revealed) showProgress("Connexion à votre MikroTik…")
                    web.loadUrl(sessionUrl(s.port))
                }
            }
            is MikhmonServer.State.Failed -> showFailure(s.title, s.details)
        }
    }

    private fun showProgress(text: String) {
        overlay.visibility = View.VISIBLE; spinner.visibility = View.VISIBLE
        message.text = text; detailsBox.visibility = View.GONE; actions.removeAllViews()
    }

    private fun reveal() { revealed = true; overlay.visibility = View.GONE }

    private fun showFailure(title: String, report: String) {
        overlay.visibility = View.VISIBLE; spinner.visibility = View.GONE
        message.text = title
        details.text = report
        detailsBox.visibility = if (report.isBlank()) View.GONE else View.VISIBLE
        actions.removeAllViews()
        actions.add(Ui.primaryButton(this, "Réessayer") { mainFrameErrors = 0; MikhmonServer.restart(this) })
        actions.add(Ui.textButton(this, "Copier le rapport d'erreur") { copy(title + "\n\n" + report) }, 4)
        actions.add(Ui.textButton(this, "Modifier la connexion au routeur") { openSetup() })
    }

    private fun copy(text: String) {
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Mikhmon", text))
        Toast.makeText(this, "Rapport copié", Toast.LENGTH_SHORT).show()
    }

    private fun openSetup() = startActivity(Intent(this, SetupActivity::class.java))

    private fun showMenu() {
        val items = arrayOf("Accueil Mikhmon", "Recharger", "Modifier la connexion au routeur", "Rapport de diagnostic", "Arrêter Mikhmon et quitter")
        AlertDialog.Builder(this).setItems(items) { _, w ->
            when (w) {
                0 -> if (loadedPort > 0) web.loadUrl(sessionUrl(loadedPort))
                1 -> web.reload()
                2 -> openSetup()
                3 -> AlertDialog.Builder(this).setTitle("Diagnostic").setMessage(MikhmonServer.report())
                    .setPositiveButton("Copier") { _, _ -> copy(MikhmonServer.report()) }.setNegativeButton("Fermer", null).show()
                4 -> { MikhmonService.stop(this); finishAffinity() }
            }
        }.show()
    }

    private fun autoLogin(view: WebView) {
        val c = cfg ?: return
        val js = "(function(){var u=document.querySelector('input[name=user]'),p=document.querySelector('input[name=pass]')," +
            "b=document.querySelector('[name=login]');if(!u||!p||!b)return 'noform';" +
            "u.value=${JSONObject.quote(c.adminUser)};p.value=${JSONObject.quote(c.adminPass)};b.click();return 'ok';})()"
        view.evaluateJavascript(js, null)
    }

    private inner class Client : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            if (request.url.host == "127.0.0.1") return false
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, request.url)) }
            return true
        }

        override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) { pageFailed = false }

        override fun onPageFinished(view: WebView, url: String) {
            if (pageFailed) return
            if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) view.evaluateJavascript(printJs, null)
            when {
                url.contains("id=login") -> {
                    if (loginAttempts++ < 2) autoLogin(view)
                    else showFailure("La connexion automatique à Mikhmon a échoué.",
                        "Les identifiants internes ne correspondent plus. Touchez « Modifier la connexion au routeur » puis validez pour les régénérer.")
                }
                url.contains("id=sessions") && !jumpedToSession -> {
                    jumpedToSession = true
                    view.loadUrl(sessionUrl(loadedPort))
                }
                else -> { mainFrameErrors = 0; reveal() }
            }
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (!request.isForMainFrame) return
            pageFailed = true
            mainFrameErrors++
            if (mainFrameErrors <= 3) {
                showProgress("Reconnexion au serveur Mikhmon…")
                view.postDelayed({ MikhmonServer.ensureRunning(this@MainActivity) }, 800)
            } else {
                showFailure("Le navigateur ne peut pas joindre Mikhmon (${error.description}).", MikhmonServer.report())
            }
        }
    }

    private inner class PrintBridge {
        @JavascriptInterface
        fun print() = runOnUiThread {
            val pm = getSystemService(PrintManager::class.java)
            pm.print("Mikhmon", web.createPrintDocumentAdapter("Mikhmon"), null)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (::web.isInitialized && overlay.visibility != View.VISIBLE && web.canGoBack()) web.goBack()
        else moveTaskToBack(true)
    }
}

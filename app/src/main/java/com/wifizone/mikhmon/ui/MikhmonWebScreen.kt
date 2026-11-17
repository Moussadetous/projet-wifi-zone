package com.wifizone.mikhmon.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.print.PrintManager
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.wifizone.mikhmon.Defaults
import com.wifizone.mikhmon.MikhmonServer
import com.wifizone.mikhmon.Services
import org.json.JSONObject

/** Mikhmon complet (interface d'origine), déjà connecté. La barre d'onglets reste en dessous, sans cacher la page. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MikhmonWebScreen(activity: Activity, nav: Nav) {
    var state by remember { mutableStateOf(MikhmonServer.state) }
    DisposableEffect(Unit) {
        val l: (MikhmonServer.State) -> Unit = { state = it }
        MikhmonServer.observe(l)
        MikhmonServer.ensureRunning(activity)
        onDispose { MikhmonServer.unobserve(l) }
    }
    val cfg = remember { Services.settings.load() }
    val s = state
    if (s !is MikhmonServer.State.Ready || cfg == null) {
        Box(Modifier.fillMaxSize().background(Brand.Bg), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                if (s is MikhmonServer.State.Failed) {
                    Text(s.title, style = MaterialTheme.typography.titleMedium)
                    ErrorNote(s.details.take(600))
                    BigButton("Réessayer") { MikhmonServer.restart(activity) }
                } else { Loading(); Text("Démarrage de Mikhmon…", color = Brand.Muted) }
            }
        }
        return
    }
    val port = s.port
    val session = Defaults.sessionName(cfg.sessionName)
    val home = "http://127.0.0.1:$port/?session=" + Uri.encode(session)
    val target = nav.mikhmonPath?.let { "http://127.0.0.1:$port$it" } ?: home
    var loginTries by remember { mutableStateOf(0) }
    var jumped by remember { mutableStateOf(false) }
    var webRef by remember { mutableStateOf<WebView?>(null) }
    LaunchedEffect(target) { webRef?.let { if (it.url != target) it.loadUrl(target) }; nav.mikhmonPath = null }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.javaScriptCanOpenWindowsAutomatically = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                val printJs = "window.print=function(){MikhmonDroid.print();};"
                addJavascriptInterface(object {
                    @JavascriptInterface fun print() = activity.runOnUiThread {
                        val pm = activity.getSystemService(PrintManager::class.java)
                        pm.print("Mikhmon", this@apply.createPrintDocumentAdapter("Mikhmon"), null)
                    }
                }, "MikhmonDroid")
                if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) WebViewCompat.addDocumentStartJavaScript(this, printJs, setOf("http://127.0.0.1"))
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        if (request.url.host == "127.0.0.1") return false
                        runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW, request.url)) }
                        return true
                    }
                    override fun onPageFinished(view: WebView, url: String) {
                        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) view.evaluateJavascript(printJs, null)
                        when {
                            url.contains("id=login") && loginTries < 2 -> {
                                loginTries++
                                val js = "(function(){var u=document.querySelector('input[name=user]'),p=document.querySelector('input[name=pass]')," +
                                    "b=document.querySelector('[name=login]');if(!u||!p||!b)return;u.value=${JSONObject.quote(cfg.adminUser)};" +
                                    "p.value=${JSONObject.quote(cfg.adminPass)};b.click();})()"
                                view.evaluateJavascript(js, null)
                            }
                            url.contains("id=sessions") && !jumped -> { jumped = true; view.loadUrl(home) }
                        }
                    }
                    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                        if (request.isForMainFrame) MikhmonServer.ensureRunning(activity)
                    }
                }
                loadUrl(target)
                webRef = this
            }
        },
    )
}

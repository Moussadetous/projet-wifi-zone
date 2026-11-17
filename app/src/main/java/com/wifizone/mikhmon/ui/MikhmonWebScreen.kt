package com.wifizone.mikhmon.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.print.PrintManager
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.wifizone.mikhmon.Defaults
import com.wifizone.mikhmon.MikhmonServer
import com.wifizone.mikhmon.MikhmonSettings
import com.wifizone.mikhmon.Services
import org.json.JSONObject

/** Le navigateur de Mikhmon, gardé entre deux affichages (changement d'onglet, écran d'impression). */
class WebHolder {
    var view: WebView? = null
    var port = 0
    var script: ScriptHandler? = null
    var loginTries = 0
    var autoLogin = false
    var clearHistory = false
    var canGoBack by mutableStateOf(false)
}

/**
 * Script injecté dans chaque page de Mikhmon, avant ses propres scripts :
 *  - les impressions de tickets (voucher/print.php, ouvertes par window.open ou un lien « target=_blank ») ouvrent le
 *    générateur PDF de l'application, avec aperçu, au lieu d'une page blanche sans impression ;
 *  - window.print() (rapports) passe par l'impression d'Android ;
 *  - un bouton « Premium » est ajouté à côté de « Default / QR / Small » (liste des utilisateurs, après « Générer »).
 */
private val INJECTED_JS = """
(function () {
  if (window.__mikhmonDroid) return;
  window.__mikhmonDroid = true;
  var B = window.MikhmonDroid;
  if (!B) return;
  function isVoucher(u) { return /voucher\/print\.php/i.test(String(u || '')); }
  function abs(u) { try { return new URL(String(u), location.href).href; } catch (e) { return String(u); } }
  var noop = function () {};
  var fake = { focus: noop, print: noop, close: noop, blur: noop, document: { write: noop, close: noop } };
  var open0 = window.open;
  window.open = function (u) {
    if (isVoucher(u)) { B.voucher(abs(u)); return fake; }
    return open0.apply(window, arguments);
  };
  window.print = function () { B.print(); };
  document.addEventListener('click', function (e) {
    var a = e.target && e.target.closest ? e.target.closest('a[href]') : null;
    if (!a) return;
    var h = a.getAttribute('href') || '';
    if (/^\s*javascript:/i.test(h) || !isVoucher(h)) return;
    e.preventDefault(); e.stopPropagation();
    B.voucher(abs(h));
  }, true);

  function button(id, onclick) {
    var b = document.createElement('button');
    b.type = 'button'; b.id = id; b.className = 'btn'; b.title = 'Tickets premium et aperçu avant impression';
    b.setAttribute('style', 'background:linear-gradient(135deg,#F59E0B,#EC4899,#8B5CF6);color:#fff;border:0;font-weight:bold;margin:2px');
    b.innerHTML = '<i class="fa fa-star"></i> Premium';
    b.onclick = function (e) { e.preventDefault(); onclick(); };
    return b;
  }
  function premiumButtons() {
    if (!document.getElementById('mkd-premium')) {
      var btns = document.querySelectorAll('button[onclick]'), small = null;
      for (var i = 0; i < btns.length; i++) if ((btns[i].getAttribute('onclick') || '').indexOf("printV('small'") >= 0) small = btns[i];
      if (small) small.parentNode.insertBefore(button('mkd-premium', function () {
        var c = document.getElementById('comment');
        B.premium(c ? String(c.value || '') : '', location.href);
      }), small.nextSibling);
    }
    if (!document.getElementById('mkd-premium2')) {
      var links = document.querySelectorAll('a[href*="voucher/print.php?id="]');
      if (links.length) {
        var last = links[links.length - 1], id = '';
        try { id = new URL(last.getAttribute('href'), location.href).searchParams.get('id') || ''; } catch (e) {}
        last.parentNode.insertBefore(button('mkd-premium2', function () { B.premium(id, location.href); }), last.nextSibling);
      }
    }
  }
  function ready() {
    premiumButtons();
    try { new MutationObserver(premiumButtons).observe(document.body || document.documentElement, { childList: true, subtree: true }); } catch (e) {}
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', ready); else ready();
})();
"""

/** Ce qu'une adresse voucher/print.php de Mikhmon demande d'imprimer, traduit pour le générateur de l'application. */
fun studioForMikhmonPrint(url: String): Overlay.Studio? {
    val u = runCatching { Uri.parse(url) }.getOrNull() ?: return null
    if (u.path?.endsWith("/voucher/print.php") != true) return null
    val template = when {
        u.getQueryParameter("small") == "yes" -> "mk-small"
        u.getQueryParameter("qr") == "yes" -> "mk-qr"
        else -> "mk-default"
    }
    val user = u.getQueryParameter("user").orEmpty()
    val id = u.getQueryParameter("id").orEmpty()
    return when {
        // Un seul ticket : « vc-NOM » ou « up-NOM » (le nom peut lui-même contenir des tirets).
        user.isNotBlank() -> user.substringAfter('-', user).let { Overlay.Studio("Ticket $it", userName = it, templateId = template) }
        id.isNotBlank() -> Overlay.Studio(Batch(id, emptyList()).title, comment = id, templateId = template)
        else -> null
    }
}

/** Liaison entre les pages de Mikhmon et l'application (appelée depuis le JavaScript des pages). */
private class Bridge(private val activity: Activity, private val nav: Nav) {
    private val main = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun voucher(url: String) {
        main.post {
            val o = studioForMikhmonPrint(url)
            if (o != null) nav.open(o) else Toast.makeText(activity, "Impression impossible : lien inconnu", Toast.LENGTH_LONG).show()
        }
    }

    @JavascriptInterface
    fun premium(comment: String, page: String) {
        main.post {
            val profile = runCatching { Uri.parse(page).getQueryParameter("profile") }.getOrNull().orEmpty()
            when {
                comment.isNotBlank() -> nav.open(Overlay.Studio(Batch(comment, emptyList()).title, comment = comment, templateId = "premium"))
                profile.isNotBlank() && profile != "all" -> nav.open(Overlay.Studio("Profil $profile", profile = profile, templateId = "premium"))
                else -> Toast.makeText(activity, "Choisissez d'abord un lot (commentaire) ou un profil dans la liste.", Toast.LENGTH_LONG).show()
            }
        }
    }

    @JavascriptInterface
    fun print() {
        main.post {
            val web = nav.web.view ?: return@post
            activity.getSystemService(PrintManager::class.java).print("Mikhmon", web.createPrintDocumentAdapter("Mikhmon"), null)
        }
    }
}

/** Mikhmon complet (interface d'origine), déjà connecté. La barre d'onglets reste en dessous, sans cacher la page. */
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
    val home = "http://127.0.0.1:$port/?session=" + Uri.encode(Defaults.sessionName(cfg.sessionName))
    val holder = nav.web

    // Retour : d'abord dans l'historique de Mikhmon, puis l'application.
    BackHandler(enabled = holder.canGoBack) { holder.view?.goBack() }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { _ ->
            val web = holder.view ?: newWebView(activity, nav, cfg, home).also { holder.view = it }
            (web.parent as? ViewGroup)?.removeView(web)
            if (holder.port != port) {
                // Premier affichage, ou serveur relancé sur un autre port.
                holder.port = port
                installScript(web, holder, port)
                web.loadUrl(nav.mikhmonPath?.let { "http://127.0.0.1:$port$it" } ?: home)
                nav.mikhmonPath = null
            }
            web
        },
    )
    LaunchedEffect(nav.mikhmonPath) {
        val p = nav.mikhmonPath ?: return@LaunchedEffect
        holder.view?.loadUrl("http://127.0.0.1:$port$p")
        nav.mikhmonPath = null
    }
}

private fun installScript(web: WebView, holder: WebHolder, port: Int) {
    if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return
    holder.script?.remove()
    holder.script = WebViewCompat.addDocumentStartJavaScript(web, INJECTED_JS, setOf("http://127.0.0.1:$port"))
}

@SuppressLint("SetJavaScriptEnabled")
private fun newWebView(activity: Activity, nav: Nav, cfg: MikhmonSettings, home: String): WebView = WebView(activity).apply {
    val holder = nav.web
    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.javaScriptCanOpenWindowsAutomatically = true
    settings.loadWithOverviewMode = true
    settings.useWideViewPort = true
    settings.builtInZoomControls = true
    settings.displayZoomControls = false
    addJavascriptInterface(Bridge(activity, nav), "MikhmonDroid")
    webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url
            if (url.scheme == "intent") { openIntentUrl(activity, url.toString()); return true }
            if (url.host == "127.0.0.1") {
                // Impression de tickets : le générateur de l'application, avec aperçu, au lieu d'une page blanche.
                studioForMikhmonPrint(url.toString())?.let { nav.open(it); return true }
                return false
            }
            runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW, url)) }
            return true
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
            holder.canGoBack = view.canGoBack()
        }

        override fun onPageFinished(view: WebView, url: String) {
            if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) view.evaluateJavascript(INJECTED_JS, null)
            when {
                url.contains("id=login") && holder.loginTries < 2 -> {
                    holder.loginTries++
                    holder.autoLogin = true
                    val js = "(function(){var u=document.querySelector('input[name=user]'),p=document.querySelector('input[name=pass]')," +
                        "b=document.querySelector('[name=login]');if(!u||!p||!b)return;u.value=${JSONObject.quote(cfg.adminUser)};" +
                        "p.value=${JSONObject.quote(cfg.adminPass)};b.click();})()"
                    view.evaluateJavascript(js, null)
                }
                // Après la connexion automatique, Mikhmon montre la liste des sessions : on va directement au routeur.
                url.contains("id=sessions") && holder.autoLogin -> {
                    holder.autoLogin = false
                    holder.clearHistory = true
                    view.loadUrl(home)
                }
                !url.contains("id=login") -> {
                    holder.loginTries = 0
                    if (holder.clearHistory) { holder.clearHistory = false; view.clearHistory() }
                }
            }
            holder.canGoBack = view.canGoBack()
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (request.isForMainFrame) MikhmonServer.ensureRunning(activity)
        }
    }
}

/** Liens « intent:// » (ex. impression Bluetooth « Quick Printer » de Mikhmon) : l'application visée, sinon sa page. */
private fun openIntentUrl(activity: Activity, url: String) {
    val intent = runCatching { Intent.parseUri(url, Intent.URI_INTENT_SCHEME) }.getOrNull() ?: return
    intent.addCategory(Intent.CATEGORY_BROWSABLE)
    intent.component = null
    intent.selector = null
    try {
        activity.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        val fallback = intent.getStringExtra("browser_fallback_url")
            ?: intent.`package`?.let { "https://play.google.com/store/apps/details?id=$it" }
        if (fallback != null) runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(fallback))) }
        else Toast.makeText(activity, "Application d'impression introuvable sur ce téléphone.", Toast.LENGTH_LONG).show()
    }
}

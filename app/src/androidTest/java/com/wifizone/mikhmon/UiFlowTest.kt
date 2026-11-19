package com.wifizone.mikhmon

import android.content.Intent
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.util.Log
import com.wifizone.mikhmon.remote.RemoteAccess
import com.wifizone.mikhmon.remote.RemotePlan
import com.wifizone.mikhmon.testing.FakeRouterOs
import com.wifizone.mikhmon.tickets.PaperFormat
import com.wifizone.mikhmon.tickets.PrintSettings
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import java.time.LocalDate
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Parcours réel de l'application sur Android, avec un faux routeur et un compte simulé (sans réseau). */
@RunWith(AndroidJUnit4::class)
class UiFlowTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx = inst.targetContext
    private var fake: FakeRouterOs? = null

    @Before fun init() {
        Services.init(ctx.applicationContext as android.app.Application)
        // Captures propres : pas de demande d'autorisation ni d'alerte système de l'émulateur par-dessus l'application.
        sh("pm grant ${ctx.packageName} android.permission.POST_NOTIFICATIONS")
        sh("settings put global hide_error_dialogs 1")
        sh("cmd statusbar collapse")
        // La publicité en grand de l'accès à distance n'est montrée qu'une fois : pas sur les autres captures.
        RemoteAccess.promoShown = true
    }

    private fun sh(cmd: String) {
        val pfd = inst.uiAutomation.executeShellCommand(cmd)
        ParcelFileDescriptor.AutoCloseInputStream(pfd).use { it.readBytes() }
    }

    @After fun tearDown() {
        MikhmonServer.stop()
        fake?.close()
        Services.account.seedForTests(null)
    }

    private fun shot(name: String) {
        // Ferme une éventuelle alerte « … ne répond pas » de l'émulateur avant la capture.
        sh("am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS")
        Thread.sleep(400)
        sh("screencap -p /data/local/tmp/mikhmon-$name.png")
    }

    private val referral = """{"enabled":true,"code":"K7M4Q2","goal":4,"months":1,"max":0,"valid":5,"pending":1,"progress":1,"rewarded":1,
        "children":[{"name":"Moussa","valid":true},{"name":"Fatou","valid":false},{"name":"Ibrahim","valid":true},{"name":"Awa","valid":true},
        {"name":"Salif","valid":true},{"name":"Aïcha","valid":true}],"referred_by":null,"can_use_code":true,"share_url":"https://mprodigital.shop/mikhmon"}"""

    private fun account(status: String, items: List<String> = emptyList(), days: Long = 5, pending: String? = null): String {
        val end = OffsetDateTime.now(ZoneOffset.UTC).plusDays(if (status == "trial") days else -1).withNano(0).toString()
        val pend = if (pending == null) "null" else "{\"item\":\"$pending\",\"item_name\":\"\",\"price\":4000,\"amount\":5000,\"at\":\"$end\"}"
        return """{"user":{"id":"u1","name":"Awa Traoré","email":"awa@gmail.com","balance":1000},
            "app":{"id":"mikhmon-droid","name":"Mikhmon Android","trial_days":7,"lifetime_price":4000,"item_price":1000,"item_prices":{}},
            "license":{"status":"$status","trial_end":"$end","days_left":$days},
            "items":[${items.joinToString(",") { "\"$it\"" }}],
            "pending":$pend,
            "referral":$referral,
            "currency":"francs CFA"}"""
    }

    private fun routerReady() {
        val f = FakeRouterOs().start().also { fake = it }
        val store = SettingsStore(ctx)
        store.reset()
        store.save(MikhmonSettings("127.0.0.1", "admin", "secret", "Mon-Routeur", "Wifi Zone Ouaga", "wifi.zone", "F", "mikhmon", "Ui-Test-Pass", routerPort = f.port))
        Services.router.reset()
    }

    private fun open(screen: String?, wait: Long, name: String) {
        val i = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (screen != null) i.putExtra("open", screen)
        ActivityScenario.launch<MainActivity>(i).use {
            Thread.sleep(wait)
            shot(name)
        }
    }

    // ------------------------------------------------------------------ navigateur de Mikhmon
    private fun findWeb(v: View?): WebView? {
        if (v is WebView) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) findWeb(v.getChildAt(i))?.let { return it }
        return null
    }

    /** Exécute [f] sur le fil de l'interface (obligatoire pour WebView) et renvoie son résultat. */
    private fun <T> ui(sc: ActivityScenario<MainActivity>, f: (MainActivity) -> T): T {
        val out = ArrayList<T>(1)
        sc.onActivity { out += f(it) }
        return out[0]
    }

    private fun js(sc: ActivityScenario<MainActivity>, web: WebView, code: String): String? {
        val latch = CountDownLatch(1)
        var out: String? = null
        sc.onActivity { web.evaluateJavascript(code) { r -> out = r; latch.countDown() } }
        latch.await(10, TimeUnit.SECONDS)
        return out
    }

    private fun until(timeoutMs: Long, what: String, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (runCatching(cond).getOrDefault(false)) return
            Thread.sleep(300)
        }
        fail("Délai dépassé : $what\n${MikhmonServer.report()}")
    }

    /**
     * Les boutons d'impression de Mikhmon (« QR », puis le bouton « Premium » ajouté par l'application) ouvrent le
     * générateur de l'application, avec aperçu, au lieu d'une page blanche sans impression. Au retour, la même page
     * de Mikhmon est toujours là.
     */
    @Test fun mikhmonPrintButtonsOpenTheGenerator() {
        routerReady()
        Services.account.seedForTests(account("trial", listOf("tpl-carte-saphir")))
        Services.design.lastPremium = "tpl-carte-saphir"
        Services.design.savePrint("mk-qr", PrintSettings(PaperFormat.A4, perPage = 40))
        Services.design.savePrint("tpl-carte-saphir", PrintSettings(PaperFormat.A4, perPage = 24))
        val i = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("open", "mikhmon")
        ActivityScenario.launch<MainActivity>(i).use { sc ->
            until(90_000, "serveur Mikhmon") { MikhmonServer.state is MikhmonServer.State.Ready }
            val port = (MikhmonServer.state as MikhmonServer.State.Ready).port
            until(30_000, "navigateur Mikhmon") { ui(sc) { findWeb(it.window.decorView) } != null }
            val web = ui(sc) { findWeb(it.window.decorView)!! }
            // Connexion automatique, puis tableau de bord du routeur.
            until(90_000, "tableau de bord de Mikhmon") { ui(sc) { web.url.orEmpty().contains("/?session=") && web.progress == 100 } }

            val comment = Uri.encode("vc-517-11.17.26-Promo samedi")
            sc.onActivity { web.loadUrl("http://127.0.0.1:$port/?hotspot=users&comment=$comment&session=Mon-Routeur") }
            until(60_000, "bouton « Premium » dans la liste des utilisateurs") { js(sc, web, "!!document.getElementById('mkd-premium')") == "true" }
            Thread.sleep(1500)
            shot("11-mikhmon-utilisateurs")

            // Bouton « QR » de Mikhmon.
            sc.onActivity { web.evaluateJavascript("printV('qr','yes')", null) }
            until(15_000, "générateur ouvert par le bouton QR") { ui(sc) { !web.isAttachedToWindow } }
            assertFalse("Mikhmon ne doit plus ouvrir sa page d'impression", ui(sc) { web.url.orEmpty() }.contains("voucher/print.php"))
            Thread.sleep(7000)
            shot("12-impression-mikhmon-qr")

            // Retour : la même page de Mikhmon, sans rechargement.
            sc.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            until(15_000, "retour dans Mikhmon") { ui(sc) { web.isAttachedToWindow } }
            assertTrue(ui(sc) { web.url.orEmpty() }.contains("hotspot=users"))

            // Bouton « Premium » ajouté dans Mikhmon : même lot, modèles premium.
            sc.onActivity { web.evaluateJavascript("document.getElementById('mkd-premium').click()", null) }
            until(15_000, "générateur ouvert par le bouton Premium") { ui(sc) { !web.isAttachedToWindow } }
            Thread.sleep(7000)
            shot("13-impression-premium")
        }
    }

    /** Stock par profil, puis impression de tout un profil à 100 tickets par feuille. */
    @Test fun profileStockAndDenseSheet() {
        routerReady()
        Services.account.seedForTests(account("trial"))
        Services.design.templateId = "mk-small"
        Services.design.savePrint("mk-small", PrintSettings(PaperFormat.A4, perPage = 100))
        open("tickets", 6000, "14-tickets-stock")
        open("profil:1jour", 9000, "15-impression-profil-100")
    }

    /**
     * Accès à distance (Back To Home) : publicité en premier plan (routeur compatible, accès pas encore pris), écran
     * dédié, activation sur le routeur, puis vrai tunnel WireGuard (bibliothèque et service VPN d'Android).
     */
    @Test fun remoteAccess() {
        routerReady()
        Services.account.seedForTests(account("trial"))
        runBlocking { RemoteAccess.refreshSupport() }
        assertTrue("Le faux routeur (RouterOS 7.16) propose Back To Home", RemoteAccess.support.value?.supported == true)
        RemoteAccess.promoShown = false
        open(null, 7000, "16-acces-distance-pub")

        // Accès payé (article « distance-AAAAMMJJ ») : il s'active tout seul sur le routeur, sans bouton,
        // puis l'application propose l'autorisation VPN d'Android (dernière étape).
        Services.account.seedForTests(account("trial", listOf(RemotePlan.item(LocalDate.now()))))
        assertTrue(RemoteAccess.paid)
        val auto = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("open", "distance")
        ActivityScenario.launch<MainActivity>(auto).use {
            until(30_000, "activation automatique après l'achat") { RemoteAccess.configured.value }
            Thread.sleep(2500)
            shot("17-activation-automatique")
        }
        RemoteAccess.askPermission.value = false
        val c = RemoteAccess.config()!!
        assertEquals("192.168.216.1", c.routerAddress)
        open("distance", 5000, "18-acces-distance-actif")

        // Vrai tunnel WireGuard (sans routeur au bout) : autorisation VPN accordée par le test.
        sh("appops set ${ctx.packageName} ACTIVATE_VPN allow")
        val i = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ActivityScenario.launch<MainActivity>(i).use {
            Thread.sleep(2000)
            if (RemoteAccess.permissionIntent() == null) {
                RemoteAccess.importConfig(c.copy(endpoint = "127.0.0.1:51820").text())
                RemoteAccess.ensureUp(SettingsStore(ctx).load()!!)
                assertTrue("Tunnel WireGuard ouvert", RemoteAccess.up.value)
                // Sur le Wi-Fi du routeur, une connexion passée par le tunnel doit être refaite en direct (plus rapide).
                val st = SettingsStore(ctx).load()!!
                assertTrue(RemoteAccess.modeChanged(st, viaTunnel = true))
                assertFalse(RemoteAccess.modeChanged(st, viaTunnel = false))
                // Le tunnel (VPN) n'est jamais pris pour le Wi-Fi du routeur.
                val cm = ctx.getSystemService(android.net.ConnectivityManager::class.java)
                WifiNetwork.detect(ctx).network?.let { n ->
                    assertFalse(cm.getNetworkCapabilities(n)!!.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN))
                }
                Thread.sleep(1500)
                RemoteAccess.down()
                assertFalse(RemoteAccess.up.value)
                Log.i("UiFlowTest", "Tunnel WireGuard : ouverture et fermeture réussies")
                // Diagnostic complet (pas de vrai routeur au bout : le relais ne répond pas, et il doit le dire).
                val steps = runBlocking { RemoteAccess.diagnose() }
                Log.i("UiFlowTest", "Diagnostic :\n" + RemoteAccess.report(steps))
                assertTrue(steps.any { it.title == "Tunnel WireGuard" && it.ok == true })
                assertTrue(steps.any { it.title == "Réponse du routeur par le relais" && it.ok == false })
            } else Log.w("UiFlowTest", "Autorisation VPN non accordée par appops : test du tunnel ignoré")
        }
        runBlocking { RemoteAccess.deactivate() }
        assertFalse(RemoteAccess.configured.value)
    }

    /**
     * Paiement de l'accès à vie commencé puis abandonné (en attente 48 h sur la boutique) : les autres achats gardent
     * leur bouton de paiement ; « J'ai payé : vérifier » n'apparaît que sous l'article concerné. Taille du code à 160 %.
     */
    @Test fun paymentButtonsWithPendingPayment() {
        routerReady()
        Services.account.seedForTests(account("trial", listOf("tpl-carte-saphir"), pending = "lifetime"))
        Services.design.lastPremium = "tpl-aurore"
        Services.design.saveOptions("tpl-aurore", com.wifizone.mikhmon.tickets.TicketOptions(codeScale = 1.6f))
        Services.design.savePrint("tpl-aurore", PrintSettings(PaperFormat.A4, perPage = 10))
        open("studio", 8000, "19-modele-a-payer-code-160")
        open("compte", 4000, "20-paiement-en-attente")
    }

    /** Parrainage : code, partage de l'application, 4 amis = 1 mois, filleuls, saisie du code d'un parrain. */
    @Test fun referral() {
        routerReady()
        Services.account.seedForTests(account("lifetime", listOf("distance-20261119-1m-f1")))
        open("parrainage", 3500, "21-parrainage")
        open(null, 5000, "22-accueil-parrainage")
    }

    @Test fun setupScreen() {
        SettingsStore(ctx).reset()
        Services.account.seedForTests(null)
        open(null, 2500, "01-configuration")
    }

    @Test fun loginAndPaywall() {
        routerReady()
        Services.account.seedForTests(null)
        open(null, 3000, "02-connexion-compte")
        Services.account.seedForTests(account("expired"))
        open(null, 3000, "03-essai-termine")
    }

    @Test fun mainScreens() {
        routerReady()
        Services.design.templateId = "tpl-carte-saphir"
        Services.account.seedForTests(account("trial", listOf("tpl-carte-saphir", "tpl-neon-cyan")))
        open(null, 6000, "04-accueil")
        open("clients", 7000, "05-clients")
        open("trafic", 9000, "06-trafic")
        open("tickets", 5000, "07-tickets")
        open("journal", 4000, "08-journal")
        open("studio", 7000, "09-studio")
        open("compte", 3000, "10-compte")
    }
}

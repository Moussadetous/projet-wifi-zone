package com.wifizone.mikhmon.account

import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder

/**
 * Parcours complet de l'application contre le VRAI serveur MPro 3.7 (faux Google, faux Chariow) :
 * connexion Google dans le navigateur → autorisation → jeton → essai → paiement en un geste → accès à vie →
 * modèle de ticket → second appareil reconnu automatiquement → déconnexion.
 * Lancé seulement si MPRO_SERVER_DIR désigne le dossier du serveur (privé, hors du dépôt public).
 */
class MproCoreIntegrationTest {
    private var proc: Process? = null
    private var base = ""

    @Before fun start() {
        val dir = System.getenv("MPRO_SERVER_DIR")
        assumeTrue("MPRO_SERVER_DIR non défini : test ignoré", dir != null && File(dir, "src/serveur.js").exists())
        val script = File(javaClass.getResource("/mpro-test-server.js")!!.toURI())
        val p = ProcessBuilder("node", script.absolutePath, dir).redirectErrorStream(true).start()
        proc = p
        val reader = p.inputStream.bufferedReader()
        while (true) {
            val line = reader.readLine() ?: error("le serveur de test s'est arrêté")
            if (line.startsWith("READY ")) { base = "http://127.0.0.1:" + line.removePrefix("READY ").trim(); break }
        }
        Thread { runCatching { reader.forEachLine { } } }.apply { isDaemon = true }.start()
    }

    @After fun stop() { proc?.destroy() }

    /** Navigateur minimal avec cookies (la page /connexion-app/ côté boutique). */
    private class Browser(val base: String) {
        val cookies = HashMap<String, String>()
        var csrf = ""
        fun call(method: String, path: String, body: JSONObject? = null): Triple<Int, String, HttpURLConnection> {
            val c = URL(base + path).openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.requestMethod = method
            if (cookies.isNotEmpty()) c.setRequestProperty("Cookie", cookies.entries.joinToString("; ") { "${it.key}=${it.value}" })
            if (csrf.isNotEmpty()) c.setRequestProperty("X-CSRF-Token", csrf)
            if (body != null) { c.doOutput = true; c.setRequestProperty("Content-Type", "application/json"); c.outputStream.use { it.write(body.toString().toByteArray()) } }
            val code = c.responseCode
            c.headerFields.filterKeys { it != null && it.equals("Set-Cookie", ignoreCase = true) }.values.flatten().forEach { sc ->
                val kv = sc.substringBefore(';'); cookies[kv.substringBefore('=')] = kv.substringAfter('=')
            }
            val text = (if (code < 400) c.inputStream else c.errorStream)?.bufferedReader()?.readText().orEmpty()
            return Triple(code, text, c)
        }
    }

    private class MemPrefs : Prefs {
        val m = HashMap<String, String>()
        override fun get(key: String) = m[key]
        override fun put(key: String, value: String?) { if (value == null) m.remove(key) else m[key] = value }
    }

    private fun query(url: String) = url.substringAfter('?').split('&').associate { val (k, v) = it.split('=', limit = 2); k to URLDecoder.decode(v, "UTF-8") }

    @Test fun fullAppJourney() {
        val browser = Browser(base)
        // Le client se connecte avec Google dans la boutique (comme le bouton « Continuer avec Google »).
        val token = URL("$base/control/google-token?sub=g-77&email=awa@gmail.com&name=Awa%20Traor%C3%A9").readText()
        assertEquals(200, browser.call("POST", "/api/login/google", JSONObject().put("credential", token)).first)

        val phone1 = MproCore("mikhmon-droid", "mikhmondroid", "0.3.0", listOf("https://injoignable.invalid", base), MemPrefs(), device = "Tecno Spark 10")
        assertTrue("découverte : la 2e adresse répond", phone1.discover())
        assertEquals(base, phone1.base)

        // L'application ouvre la page de connexion ; le navigateur déjà connecté autorise en un geste.
        val login = phone1.loginUrl()
        val q = query(login)
        val info = JSONObject(browser.call("GET", "/api/app-auth/info?app=mikhmon-droid&redirect_uri=mikhmondroid%3A%2F%2Fauth").second)
        assertEquals("Awa Traoré", info.getJSONObject("user").getString("name"))
        browser.csrf = info.getString("csrf")
        val auth = JSONObject(browser.call("POST", "/api/app-auth/authorize", JSONObject().put("app", "mikhmon-droid")
            .put("challenge", q["challenge"]).put("state", q["state"]).put("redirect_uri", "mikhmondroid://auth")).second)
        val back = query(auth.getString("direct"))
        val acc = phone1.completeLogin(back["code"], back["state"])
        assertEquals("trial", acc.status)
        assertEquals(7, acc.daysLeft)
        assertEquals(4000L, acc.lifetimePrice)
        assertTrue(phone1.account!!.unlocked)

        // Accès à vie : numéro demandé une fois, puis paiement Chariow, et la boutique termine l'achat.
        val need = phone1.checkout("lifetime")
        assertTrue(need is MproCore.Checkout.NeedPhone)
        val pay = phone1.checkout("lifetime", phone = "+226 70 00 00 00") as MproCore.Checkout.Pay
        assertEquals(5000L, pay.amount)
        assertEquals("l’accès à vie", phone1.account!!.pending)
        assertEquals("lifetime", phone1.account!!.pendingItem)
        val sale = pay.url.substringAfterLast('/')
        val ctl = URL("$base/control/pay?sale=$sale").openConnection() as HttpURLConnection
        ctl.requestMethod = "POST"; assertEquals(200, ctl.responseCode)
        Thread.sleep(4300)
        val after = phone1.checkoutStatus()!!
        assertEquals("lifetime", after.status)
        assertEquals(1000L, after.balance)
        assertNull(after.pending)
        assertNull(after.pendingItem)

        // Modèle premium payé avec le reste du solde, sans page de paiement.
        assertTrue(phone1.checkout("tpl-neon-cyan", "Néon Cyan") is MproCore.Checkout.Done)
        assertTrue("tpl-neon-cyan" in phone1.account!!.items)
        assertEquals(0L, phone1.account!!.balance)

        // Un paiement en attente n'empêche pas d'en lancer un autre (autre modèle, accès à distance) : le serveur
        // remplace l'attente, et l'application garde le bouton de paiement partout.
        val tpl = phone1.checkout("tpl-aurore", "Aurore") as MproCore.Checkout.Pay
        assertTrue(tpl.url.isNotBlank())
        assertEquals("tpl-aurore", phone1.account!!.pendingItem)
        val remote = phone1.checkout("distance-20261117", "Accès à distance · 2 mois") as MproCore.Checkout.Pay
        assertEquals(1000L, remote.price)
        assertEquals("distance-20261117", phone1.account!!.pendingItem)
        phone1.cancelPending()
        assertNull(phone1.account!!.pendingItem)

        // Nouveau téléphone : la boutique reconnaît le navigateur et renvoie directement vers l'application.
        val phone2 = MproCore("mikhmon-droid", "mikhmondroid", "0.3.0", listOf(base), MemPrefs(), device = "Itel A70")
        val q2 = query(phone2.loginUrl())
        val (code, _, conn) = browser.call("GET", "/connexion-app/?" + phone2.loginUrl().substringAfter('?').let { "app=mikhmon-droid&challenge=${q2["challenge"]}&state=${q2["state"]}&redirect_uri=mikhmondroid%3A%2F%2Fauth" })
        assertEquals("retour automatique (302)", 302, code)
        val loc = query(conn.getHeaderField("Location"))
        // phone2 a régénéré son PKCE en appelant loginUrl() deux fois : on refait proprement.
        val q3 = query(phone2.loginUrl())
        val (code3, _, conn3) = browser.call("GET", "/connexion-app/?app=mikhmon-droid&challenge=${q3["challenge"]}&state=${q3["state"]}&redirect_uri=mikhmondroid%3A%2F%2Fauth")
        assertEquals(302, code3)
        val loc3 = query(conn3.getHeaderField("Location"))
        assertTrue(loc["code"] != loc3["code"])
        val acc2 = phone2.completeLogin(loc3["code"], loc3["state"])
        assertEquals("lifetime", acc2.status)
        assertTrue("tpl-neon-cyan" in acc2.items)

        // Hors ligne : le compte reste disponible depuis le cache.
        assertEquals("lifetime", phone2.cached()!!.status)

        // Parrainage : Awa partage son code ; 4 amis le saisissent et connectent LEUR routeur → 1 mois offert.
        val ref = phone1.refresh()!!.referral!!
        assertTrue(Regex("^[A-Z2-9]{6}$").matches(ref.code))
        assertEquals(4, ref.goal); assertEquals(1, ref.months)
        phone1.reportRouter("HEX-AWA-0001")
        fun friend(i: Int): MproCore {
            val b = Browser(base)
            val t = URL("$base/control/google-token?sub=ami-$i&email=ami$i@gmail.com&name=Ami$i%20Test").readText()
            assertEquals(200, b.call("POST", "/api/login/google", JSONObject().put("credential", t)).first)
            val p = MproCore("mikhmon-droid", "mikhmondroid", "0.6.0", listOf(base), MemPrefs(), device = "Ami $i")
            val qq = query(p.loginUrl())
            b.csrf = JSONObject(b.call("GET", "/api/app-auth/info?app=mikhmon-droid&redirect_uri=mikhmondroid%3A%2F%2Fauth").second).getString("csrf")
            val a = JSONObject(b.call("POST", "/api/app-auth/authorize", JSONObject().put("app", "mikhmon-droid")
                .put("challenge", qq["challenge"]).put("state", qq["state"]).put("redirect_uri", "mikhmondroid://auth")).second)
            val bk = query(a.getString("direct"))
            p.completeLogin(bk["code"], bk["state"])
            return p
        }
        val friends = (1..4).map { friend(it) }
        val wrong = runCatching { friends[0].useReferralCode("ZZZZZZ") }.exceptionOrNull() as MproCore.ApiError
        assertEquals(404, wrong.code)
        val f1 = friends[0].useReferralCode(ref.code.lowercase())
        assertEquals("Awa", f1.referral!!.referredBy)
        assertTrue(!f1.referral!!.referredValid)
        friends[0].reportRouter("HEX-AWA-0001") // le routeur du parrain ne compte pas
        assertTrue(!friends[0].account!!.referral!!.referredValid)
        friends[0].reportRouter("HEX-AMI-0001")
        assertTrue(friends[0].account!!.referral!!.referredValid)
        for ((i, f) in friends.withIndex().drop(1)) { f.useReferralCode(ref.code); f.reportRouter("HEX-AMI-000${i + 1}") }
        val awa = phone1.refresh()!!
        assertEquals(4, awa.referral!!.valid)
        assertEquals(1, awa.referral!!.rewarded)
        assertEquals(0, awa.referral!!.progress)
        assertEquals(4, awa.referral!!.children.count { it.valid })
        val gift = awa.items.single { it.startsWith("distance-") && it.contains("-1m-") }
        assertEquals(1, com.wifizone.mikhmon.remote.RemotePlan.gifted(awa.items))
        val today = java.time.LocalDate.now(java.time.ZoneOffset.UTC)
        assertEquals(today.plusMonths(1).minusDays(1), com.wifizone.mikhmon.remote.RemotePlan.activeUntil(awa.items, today))
        assertTrue(gift.endsWith("-1m-f1"))

        phone2.logout()
        assertNull(phone2.account)
        assertTrue(!phone2.loggedIn)
        assertEquals("lifetime", phone1.refresh()!!.status)
    }
}

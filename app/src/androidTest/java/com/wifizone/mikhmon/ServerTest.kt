package com.wifizone.mikhmon

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.wifizone.mikhmon.testing.FakeRouterOs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.net.CookieHandler
import java.net.CookieManager
import java.net.HttpURLConnection
import java.net.URL

/** Vérifie sur un vrai Android que le PHP embarqué démarre et que Mikhmon fonctionne de bout en bout. */
@RunWith(AndroidJUnit4::class)
class ServerTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext

    @After fun tearDown() { MikhmonServer.stop() }

    private fun http(url: String, post: String? = null): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 5000; c.readTimeout = 20000
        if (post != null) {
            c.requestMethod = "POST"; c.doOutput = true
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            c.outputStream.use { it.write(post.toByteArray()) }
        }
        return (if (c.responseCode < 400) c.inputStream else c.errorStream).bufferedReader().readText()
    }

    private fun waitServer(): Int {
        val end = System.currentTimeMillis() + 60_000
        while (System.currentTimeMillis() < end) {
            when (val s = MikhmonServer.state) {
                is MikhmonServer.State.Ready -> return s.port
                is MikhmonServer.State.Failed -> fail("Échec du serveur : ${s.title}\n${s.details}")
                else -> Thread.sleep(200)
            }
        }
        fail("Le serveur n'a pas démarré à temps\n${MikhmonServer.report()}"); return -1
    }

    @Test fun mikhmonRunsEndToEnd() {
        CookieHandler.setDefault(CookieManager())
        FakeRouterOs().start().use { fake ->
            val info = RouterProbe.probe("127.0.0.1", "admin", "secret", port = fake.port)
            assertEquals("Mon Routeur", info.identity)

            val store = SettingsStore(ctx)
            store.reset()
            store.save(MikhmonSettings("127.0.0.1", "admin", "secret", Defaults.sessionName(info.identity),
                info.identity, info.dnsName, "FCFA", "mikhmon", "Test-Pass-1", routerPort = fake.port))

            MikhmonServer.restart(ctx)
            val port = waitServer()
            Log.i("ServerTest", "PHP prêt sur le port $port : " + PhpRuntime(ctx).preflight())

            val login = http("http://127.0.0.1:$port/admin.php?id=login", "user=mikhmon&pass=Test-Pass-1&login=Login")
            assertTrue("Connexion Mikhmon refusée :\n$login", login.contains("admin.php?id=sessions"))

            val dash = http("http://127.0.0.1:$port/?session=Mon-Routeur")
            assertTrue("Tableau de bord sans données du routeur :\n${dash.take(3000)}\n${MikhmonServer.report()}",
                dash.contains("hAP ac"))
        }
    }

    @Test fun serverRestartsAfterCrash() {
        FakeRouterOs().start().use { fake ->
            val store = SettingsStore(ctx)
            store.reset()
            store.save(MikhmonSettings("127.0.0.1", "admin", "secret", "R1", "R1", "", "", "mikhmon", "pw", routerPort = fake.port))
            MikhmonServer.restart(ctx)
            val port = waitServer()
            // tue PHP : l'application doit le relancer toute seule
            Runtime.getRuntime().exec(arrayOf("sh", "-c", "pkill -f libphp.so || killall libphp.so")).waitFor()
            Thread.sleep(1500)
            val port2 = waitServer()
            assertTrue(http("http://127.0.0.1:$port2/admin.php?id=login").contains("name=\"login\""))
            Log.i("ServerTest", "relancé : $port -> $port2")
        }
    }
}

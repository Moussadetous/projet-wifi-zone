package com.wifizone.mikhmon

import com.wifizone.mikhmon.testing.FakeRouterOs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.util.Locale
import java.util.zip.ZipFile

class CoreTest {
    // Références produites par encrypt() de lib/routeros_api.class.php (Mikhmon v3)
    @Test fun encryptMatchesMikhmon() {
        assertEquals("mZWfoZ8=", ConfigWriter.encrypt("admin"))
        assertEquals("iJJnbahiqpVT", ConfigWriter.encrypt("Pa55w0rd!"))
        assertEquals("mVyUZ5Rv+9o=", ConfigWriter.encrypt("a+b/c=é"))
        assertEquals("aWNlbA==", ConfigWriter.encrypt("1234")) // mot de passe par défaut de Mikhmon
        assertEquals("a+b/c=é", ConfigWriter.decrypt(ConfigWriter.encrypt("a+b/c=é")))
    }

    @Test fun renderMatchesMikhmonFormat() {
        val s = MikhmonSettings("192.168.88.1", "admin", "secret", "Mon Routeur", "Wifi'Zone", "wifi.zone", "FCFA", "mikhmon", "1234")
        val out = ConfigWriter.render(s, "127.0.0.1:38001")
        assertTrue(out, out.contains("\$data['mikhmon'] = array ('1'=>'mikhmon<|<mikhmon','mikhmon>|>aWNlbA==');"))
        assertTrue(out, out.contains("\$data['Mon-Routeur'] = array ('1'=>'Mon-Routeur!127.0.0.1:38001','Mon-Routeur@|@admin','Mon-Routeur#|#q5aVqpam','Mon-Routeur%WifiZone',"))
        assertTrue(out, out.contains("'Mon-Routeur@!@disable');"))
    }

    @Test fun defaults() {
        assertEquals("FCFA", Defaults.currencyFor(Locale("fr", "SN")))
        assertEquals("EUR", Defaults.currencyFor(Locale.FRANCE))
        assertEquals("MikroTik", Defaults.sessionName("  "))
        assertEquals("Hotspot-Zone_1", Defaults.sessionName("Hotspot Zone_1"))
        assertEquals("192.168.88.1", Defaults.guessGateway("192.168.88.254"))
        assertEquals(16, Defaults.randomPassword().length)
    }

    @Test fun patchRealMikhmonFile() {
        val zip = File("../app/src/main/assets/mikhmon/mikhmon.zip")
        val src = if (zip.exists()) ZipFile(zip).use { z -> z.getInputStream(z.getEntry(MikhmonPatcher.ROUTEROS_FILE)).readBytes().decodeToString() }
            else System.getenv("MIKHMON_SRC")?.let { File(it, MikhmonPatcher.ROUTEROS_FILE).readText() }
        if (src == null) { println("Mikhmon absent, test ignoré"); return }
        val p = MikhmonPatcher.patchRouterOs(src)
        assertTrue(p.contains("mikhmondroid:port"))
        assertEquals(p, MikhmonPatcher.patchRouterOs(p))
    }

    @Test fun probeReadsRouterInfo() {
        FakeRouterOs().start().use { f ->
            assertEquals(RouterInfo("Mon Routeur", "hotspot1", "wifi.zone"),
                RouterProbe.probe("127.0.0.1", "admin", "secret", port = f.port))
        }
    }

    @Test fun probeThroughBridge() {
        FakeRouterOs().start().use { f ->
            RouterBridge({ "127.0.0.1" to f.port }).use { b ->
                b.start(0)
                RouterOs.connect("127.0.0.1", "admin", "secret", port = b.port).use { r ->
                    assertEquals("Mon Routeur", r.command("/system/identity/print").first()["name"])
                    assertEquals("wifi.zone", r.command("/ip/hotspot/profile/print", "?name=hsprof1").first()["dns-name"])
                }
            }
        }
    }

    @Test fun errorsAreClassified() {
        FakeRouterOs().start().use { f ->
            try { RouterOs.connect("127.0.0.1", "admin", "faux", port = f.port); fail() }
            catch (e: RouterOsException) { assertEquals(RouterOsException.Kind.BAD_LOGIN, e.kind) }
            val closedPort = f.port.also { f.close() }
            try { RouterOs.connect("127.0.0.1", "admin", "secret", port = closedPort); fail() }
            catch (e: RouterOsException) { assertEquals(RouterOsException.Kind.API_DISABLED, e.kind) }
        }
    }
}

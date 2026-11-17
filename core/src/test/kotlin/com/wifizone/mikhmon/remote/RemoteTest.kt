package com.wifizone.mikhmon.remote

import com.wifizone.mikhmon.RouterOs
import com.wifizone.mikhmon.testing.FakeRouterOs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RemoteTest {
    private val sample = """
        [Interface]
        PrivateKey = r0KGQVZAhOlOp85cHpX1sMBqU47fuYfVZz+x8TbMIUw=
        Address = 192.168.216.3/32, fc00:0:0:216::3/128
        DNS = 192.168.216.1, fc00:0:0:216::1
        MTU = 1420

        [Peer]
        PublicKey = 4SHa/414EPJPFOH67MnaMei/wNaY4SrlIqwvdp13whQ=
        AllowedIPs = 0.0.0.0/0, ::/0
        Endpoint = ab12cd34ef56.vpn.mynetname.net:15180
        PersistentKeepalive = 25
    """.trimIndent()

    @Test fun parsesBackToHomeConfig() {
        val c = WgConf.parse(sample)!!
        assertEquals("r0KGQVZAhOlOp85cHpX1sMBqU47fuYfVZz+x8TbMIUw=", c.privateKey)
        assertEquals(listOf("192.168.216.3/32", "fc00:0:0:216::3/128"), c.addresses)
        assertEquals("192.168.216.1", c.routerAddress)
        assertEquals(1420, c.mtu)
        assertEquals("ab12cd34ef56.vpn.mynetname.net:15180", c.endpoint)
        assertEquals(25, c.keepalive)
        // Même configuration renvoyée sur une seule ligne, et relue après text().
        assertEquals(c, WgConf.parse(sample.replace("\n", " ")))
        assertEquals(c, WgConf.parse(c.text()))
        // Sans DNS : passerelle x.y.z.1 de notre adresse.
        assertEquals("192.168.216.1", c.copy(dns = emptyList()).routerAddress)
        assertNull(WgConf.parse("[Interface]\nAddress = 10.0.0.2/32"))
    }

    @Test fun twoMonthPeriodsChain() {
        val d = LocalDate.of(2026, 11, 17)
        assertEquals("distance-20261117", RemotePlan.item(d))
        assertNull(RemotePlan.activeUntil(emptySet(), d))
        assertEquals(d, RemotePlan.nextStart(setOf("tpl-carte-or"), d))
        val one = setOf(RemotePlan.item(d))
        assertEquals(LocalDate.of(2027, 1, 16), RemotePlan.activeUntil(one, d))
        assertEquals(LocalDate.of(2027, 1, 16), RemotePlan.activeUntil(one, LocalDate.of(2027, 1, 16)))
        assertNull(RemotePlan.activeUntil(one, LocalDate.of(2027, 1, 17)))
        // Renouvellement anticipé : la période suivante commence le lendemain de la fin.
        val next = RemotePlan.nextStart(one, LocalDate.of(2026, 12, 20))
        assertEquals(LocalDate.of(2027, 1, 17), next)
        assertEquals(LocalDate.of(2027, 3, 16), RemotePlan.activeUntil(one + RemotePlan.item(next), LocalDate.of(2026, 12, 20)))
        assertTrue(RemotePlan.label(d).contains("17/11/2026 → 16/01/2027"))
    }

    @Test fun detectsSupport() {
        FakeRouterOs().start().use { f ->
            RouterOs.connect("127.0.0.1", "admin", "secret", port = f.port).use { r ->
                val s = BackToHome.check(r)
                assertTrue(s.reason, s.supported)
                assertFalse(s.enabled)
            }
        }
        FakeRouterOs(version = "6.49.10 (long-term)").start().use { f ->
            RouterOs.connect("127.0.0.1", "admin", "secret", port = f.port).use { r ->
                val s = BackToHome.check(r)
                assertFalse(s.supported)
                assertTrue(s.reason, s.reason.contains("6.49.10") && s.reason.contains("7.12"))
            }
        }
        FakeRouterOs(version = "7.18 (stable)", bth = false).start().use { f ->
            RouterOs.connect("127.0.0.1", "admin", "secret", port = f.port).use { r ->
                assertFalse(BackToHome.check(r).supported)
            }
        }
    }

    /** Le pare-feu par défaut de MikroTik bloque l'API depuis Back To Home : l'application l'ouvre, une seule fois. */
    @Test fun opensApiThroughDefaultFirewall() {
        val f = FakeRouterOs(version = "7.18 (stable)").start()
        f.use {
            RouterOs.connect("127.0.0.1", "admin", "secret", port = f.port).use { r ->
                val c = BackToHome.setup(r, "mikhmon-android-ab12") { }
                assertEquals("192.168.216.0/24", BackToHome.subnet(c))
                assertEquals("HEX0A1B2C3D", BackToHome.serial(r))
                f.apiAddress = "192.168.88.0/24"
                val done = BackToHome.openApi(r, BackToHome.subnet(c)!!, 8728)
                assertEquals(done.toString(), 2, done.size)
                // La règle passe avant « drop all not coming from LAN » (et après la règle dynamique).
                val order = f.filter.map { it["comment"] }
                assertEquals(BackToHome.RULE_COMMENT, order[1])
                assertTrue(order.indexOf(BackToHome.RULE_COMMENT) < order.indexOf("defconf: drop all not coming from LAN"))
                assertEquals("192.168.88.0/24,192.168.216.0/24", f.apiAddress)
                // Deuxième passage : rien à refaire.
                assertTrue(BackToHome.openApi(r, "192.168.216.0/24", 8728).isEmpty())
                assertEquals(1, f.filter.count { it["comment"] == BackToHome.RULE_COMMENT })
            }
        }
    }

    @Test fun setupCreatesOneAccessPerDevice() {
        FakeRouterOs(version = "7.18 (stable)").start().use { f ->
            RouterOs.connect("127.0.0.1", "admin", "secret", port = f.port).use { r ->
                val c = BackToHome.setup(r, "mikhmon-android-ab12") { }
                assertEquals("192.168.216.1", c.routerAddress)
                assertEquals("ab12cd34ef56.vpn.mynetname.net:15180", c.endpoint)
                val s = BackToHome.check(r)
                assertTrue(s.enabled)
                assertTrue(s.status, s.status.contains("ready"))
                // Deuxième activation sur le même appareil : même accès, pas de doublon.
                assertEquals(c, BackToHome.setup(r, "mikhmon-android-ab12") { })
                assertEquals(1, r.command("/ip/cloud/back-to-home-users/print").size)
                assertNotNull(BackToHome.setup(r, "mikhmon-android-cd34") { })
                assertEquals(2, r.command("/ip/cloud/back-to-home-users/print").size)
                BackToHome.remove(r, "mikhmon-android-ab12")
                assertEquals(listOf("mikhmon-android-cd34"), r.command("/ip/cloud/back-to-home-users/print").map { it["name"] })
            }
        }
    }
}

package com.wifizone.mikhmon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigWriterTest {
    // Valeurs de référence produites par la vraie fonction encrypt() de Mikhmon (PHP)
    @Test fun encryptMatchesPhp() {
        assertEquals("mZWfoZ8%3D", ConfigWriter.encrypt("admin"))
        assertEquals("iJJnbahiqpVT", ConfigWriter.encrypt("Pa55w0rd!"))
        assertEquals("", ConfigWriter.encrypt(""))
        assertEquals("mVyUZ5Rv%2B9o%3D", ConfigWriter.encrypt("a+b/c=é"))
    }

    @Test fun renderEscapesQuotes() {
        val s = MikhmonSettings("ad'min", "pw", "R1", "192.168.88.1", 8728, "admin", "x", "hs", "dns", "XOF", 38123)
        val out = ConfigWriter.render(s)
        assertTrue(out.contains("mikhmon<|<ad\\'min"))
        assertTrue(out.contains("R1!192.168.88.1:8728"))
        assertTrue(out.contains("R1@|@admin"))
    }
}

package com.wifizone.mikhmon.testing

import com.wifizone.mikhmon.RouterOs
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread

/** Faux routeur RouterOS pour les tests (unitaires et sur émulateur). */
class FakeRouterOs(private val user: String = "admin", private val pass: String = "secret") : Closeable {
    private val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
    val port: Int get() = server.localPort

    private val replies = mapOf(
        "/system/identity/print" to listOf(listOf("!re", "=name=Mon Routeur")),
        "/ip/hotspot/print" to listOf(listOf("!re", "=name=hotspot1", "=profile=hsprof1")),
        "/ip/hotspot/profile/print" to listOf(listOf("!re", "=name=hsprof1", "=dns-name=wifi.zone")),
        "/system/resource/print" to listOf(listOf("!re", "=uptime=1d", "=board-name=hAP-fake", "=version=7.16",
            "=cpu-load=3", "=free-memory=1000000", "=total-memory=2000000", "=free-hdd-space=1000000",
            "=total-hdd-space=2000000", "=architecture-name=arm")),
        "/system/clock/print" to listOf(listOf("!re", "=time-zone-name=UTC", "=date=nov/17/2026", "=time=12:00:00")),
    )

    fun start(): FakeRouterOs {
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val c = runCatching { server.accept() }.getOrNull() ?: break
                thread(isDaemon = true) {
                    c.use {
                        val i = BufferedInputStream(c.getInputStream()); val o = BufferedOutputStream(c.getOutputStream())
                        runCatching {
                            while (true) {
                                val s = RouterOs.readSentence(i)
                                if (s.isEmpty()) continue
                                if (s[0] == "/login") {
                                    if ("=name=$user" in s && "=password=$pass" in s) RouterOs.writeSentence(o, listOf("!done"))
                                    else {
                                        RouterOs.writeSentence(o, listOf("!trap", "=message=invalid user name or password (6)"))
                                        RouterOs.writeSentence(o, listOf("!done"))
                                    }
                                    continue
                                }
                                replies[s[0]].orEmpty().forEach { RouterOs.writeSentence(o, it) }
                                RouterOs.writeSentence(o, listOf("!done"))
                            }
                        }
                    }
                }
            }
        }
        return this
    }

    override fun close() { runCatching { server.close() } }
}

package com.wifizone.mikhmon.testing

import com.wifizone.mikhmon.RouterOs
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * Faux routeur RouterOS pour les tests et les démonstrations : identité, ressources, profils au format Mikhmon,
 * utilisateurs (ajout / suppression / désactivation), clients connectés, interfaces et trafic, journal, baux DHCP.
 */
class FakeRouterOs(
    private val user: String = "admin", private val pass: String = "secret",
    /** Version de RouterOS annoncée ; « Back To Home » n'existe qu'à partir de 7.12. */
    private val version: String = "7.16.1 (stable)",
    /** Le modèle propose-t-il « Back To Home » (champ back-to-home-vpn de /ip/cloud) ? */
    private val bth: Boolean = true,
) : Closeable {
    private var bthState = "disabled"
    private val bthUsers = mutableListOf<Map<String, String>>()

    private val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
    val port: Int get() = server.localPort
    private val ids = AtomicInteger(100)
    private val tick = AtomicInteger(0)

    private data class U(val id: String, val name: String, var password: String, val profile: String, var comment: String,
                         var disabled: Boolean = false, val uptime: String = "0s", val bytesIn: Long = 0, val bytesOut: Long = 0,
                         val limitUptime: String = "", val limitBytes: String = "")

    private val users = mutableListOf(
        U("*1", "K7M4Q2", "K7M4Q2", "1jour", "vc-482-11.17.26-Lot du matin", uptime = "2h14m", bytesIn = 182_000_000, bytesOut = 1_640_000_000),
        U("*2", "PX3RW8", "PX3RW8", "1jour", "vc-482-11.17.26-Lot du matin", uptime = "47m", bytesIn = 41_000_000, bytesOut = 512_000_000),
        U("*3", "HB9T6N", "HB9T6N", "1jour", "vc-482-11.17.26-Lot du matin"),
        U("*4", "wz-48213", "7731", "semaine", "up-915-11.16.26-Abonnés", uptime = "1d3h", bytesIn = 920_000_000, bytesOut = 6_400_000_000),
        U("*5", "QJ5D2M", "QJ5D2M", "1jour", "nov/18/2026 09:12:44", uptime = "6h", bytesIn = 300_000_000, bytesOut = 2_100_000_000),
    )
    init {
        // Un gros lot non utilisé (comme après « Générer 150 tickets ») : impression dense, groupement par profil.
        val rnd = java.util.Random(7)
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        repeat(150) { i ->
            val code = (1..6).map { chars[rnd.nextInt(chars.length)] }.joinToString("")
            users += U("*${300 + i}", code, code, "1jour", "vc-517-11.17.26-Promo samedi")
        }
    }

    private val active = mutableListOf(
        mapOf(".id" to "*A1", "user" to "K7M4Q2", "address" to "10.5.50.23", "mac-address" to "3C:2E:FF:41:8A:10", "server" to "hotspot1", "login-by" to "http-chap"),
        mapOf(".id" to "*A2", "user" to "wz-48213", "address" to "10.5.50.31", "mac-address" to "A4:50:46:12:CC:07", "server" to "hotspot1", "login-by" to "cookie"),
        mapOf(".id" to "*A3", "user" to "PX3RW8", "address" to "10.5.50.40", "mac-address" to "F0:99:BF:7A:31:2D", "server" to "hotspot1", "login-by" to "http-chap"),
    ).map { it.toMutableMap() }.toMutableList()

    private fun replies(s: List<String>): List<List<String>> {
        val args = s.drop(1).filter { it.startsWith("=") }.associate { w -> val i = w.indexOf('=', 1); w.substring(1, i) to w.substring(i + 1) }
        val query = s.drop(1).filter { it.startsWith("?") }.associate { w -> val i = w.indexOf('='); w.substring(1, i) to w.substring(i + 1) }
        val t = tick.incrementAndGet()
        fun re(vararg kv: Pair<String, String>) = listOf("!re") + kv.map { "=${it.first}=${it.second}" }
        return when (s[0]) {
            "/system/identity/print" -> listOf(re("name" to "Mon Routeur"))
            "/system/resource/print" -> listOf(re("uptime" to "12d4h31m", "board-name" to "hAP ac²", "version" to version,
                "cpu-load" to "${8 + t % 17}", "free-memory" to "84213760", "total-memory" to "134217728", "free-hdd-space" to "9000000",
                "total-hdd-space" to "16777216", "architecture-name" to "arm", "cpu-count" to "4"))
            "/system/clock/print" -> listOf(re("time-zone-name" to "Africa/Ouagadougou", "date" to "2026-11-17", "time" to "14:05:00"))
            "/ip/hotspot/print" -> listOf(re("name" to "hotspot1", "profile" to "hsprof1", "interface" to "bridge-hotspot"))
            "/ip/hotspot/profile/print" -> listOf(re("name" to "hsprof1", "dns-name" to "wifi.zone"))
            "/ip/hotspot/user/profile/print" -> listOf(
                re(".id" to "*P0", "name" to "default", "shared-users" to "1"),
                re(".id" to "*P1", "name" to "1jour", "shared-users" to "1", "rate-limit" to "2M/5M",
                    "on-login" to ":put (\",rem,500,1d,500,,Disable,\"); {:local comment [ /ip hotspot user get]}"),
                re(".id" to "*P2", "name" to "semaine", "shared-users" to "1", "rate-limit" to "5M/10M",
                    "on-login" to ":put (\",rem,2500,7d,2500,,Disable,\"); {}"),
                re(".id" to "*P3", "name" to "mois", "shared-users" to "2", "rate-limit" to "10M/20M",
                    "on-login" to ":put (\",remc,8000,30d,8000,,Disable,\"); {}"),
            )
            "/ip/hotspot/user/print" -> users.filter { u -> (query["comment"]?.let { u.comment == it } ?: true) && (query["name"]?.let { u.name == it } ?: true) }.map { u ->
                re(".id" to u.id, "name" to u.name, "password" to u.password, "profile" to u.profile, "comment" to u.comment,
                    "disabled" to u.disabled.toString(), "uptime" to u.uptime, "bytes-in" to u.bytesIn.toString(), "bytes-out" to u.bytesOut.toString(),
                    "limit-uptime" to u.limitUptime, "limit-bytes-total" to u.limitBytes)
            }
            "/ip/hotspot/user/add" -> {
                val id = "*" + ids.incrementAndGet()
                users += U(id, args["name"].orEmpty(), args["password"].orEmpty(), args["profile"].orEmpty(), args["comment"].orEmpty(),
                    limitUptime = args["limit-uptime"].orEmpty(), limitBytes = args["limit-bytes-total"].orEmpty())
                listOf(listOf("!done", "=ret=$id"))
            }
            "/ip/hotspot/user/remove" -> { users.removeAll { it.id == args[".id"] }; emptyList() }
            "/ip/hotspot/user/set" -> { users.firstOrNull { it.id == args[".id"] }?.let { u -> args["disabled"]?.let { u.disabled = it == "yes" || it == "true" } }; emptyList() }
            "/ip/hotspot/active/print" -> active.mapIndexed { i, a ->
                val base = (i + 1) * 40_000_000L
                re(*a.entries.map { it.key to it.value }.toTypedArray(),
                    "uptime" to listOf("2h14m", "1d3h12m", "47m")[i % 3], "session-time-left" to listOf("21h46m", "5d20h", "23h13m")[i % 3],
                    "idle-time" to "${t % 9}s", "bytes-in" to (base + t * 180_000L * (i + 1)).toString(),
                    "bytes-out" to (base * 9 + t * 1_900_000L * (i + 2)).toString(), "packets-in" to "12000", "packets-out" to "54000")
            }
            "/ip/hotspot/active/remove" -> { active.removeAll { it[".id"] == args[".id"] }; emptyList() }
            "/ip/hotspot/host/print" -> active.map { re("mac-address" to it["mac-address"]!!, "address" to it["address"]!!, "authorized" to "true") }
            "/ip/dhcp-server/lease/print" -> listOf(
                re("mac-address" to "3C:2E:FF:41:8A:10", "address" to "10.5.50.23", "host-name" to "Galaxy-A14"),
                re("mac-address" to "A4:50:46:12:CC:07", "address" to "10.5.50.31", "host-name" to "Redmi-Note-12"),
                re("mac-address" to "F0:99:BF:7A:31:2D", "address" to "10.5.50.40", "host-name" to "iPhone-de-Awa"),
            )
            "/interface/print" -> listOf(
                re(".id" to "*1", "name" to "ether1", "type" to "ether", "running" to "true", "rx-byte" to "981234567890", "tx-byte" to "123456789012"),
                re(".id" to "*2", "name" to "bridge-hotspot", "type" to "bridge", "running" to "true", "rx-byte" to "123456789012", "tx-byte" to "981234567890"),
                re(".id" to "*3", "name" to "wlan1", "type" to "wlan", "running" to "true", "rx-byte" to "9812345678", "tx-byte" to "98123456789"),
            )
            "/interface/monitor-traffic" -> {
                val w = Math.sin(t / 3.0)
                listOf(re("name" to args["interface"].orEmpty(),
                    "rx-bits-per-second" to ((6.5 + 3.5 * w) * 1_000_000).toLong().toString(),
                    "tx-bits-per-second" to ((1.4 + 0.8 * Math.cos(t / 2.0)) * 1_000_000).toLong().toString()))
            }
            "/log/print" -> listOf(
                re(".id" to "*L1", "time" to "13:58:02", "topics" to "hotspot,account,info,debug", "message" to "K7M4Q2 (10.5.50.23): logged in"),
                re(".id" to "*L2", "time" to "13:41:17", "topics" to "hotspot,info,debug", "message" to "HB9T6N (10.5.50.52): login failed: invalid password"),
                re(".id" to "*L3", "time" to "13:30:45", "topics" to "hotspot,account,info,debug", "message" to "QJ5D2M (10.5.50.18): logged out: session timeout"),
                re(".id" to "*L4", "time" to "12:02:11", "topics" to "system,info,account", "message" to "user admin logged in from 10.5.50.2 via api"),
                re(".id" to "*L5", "time" to "11:47:33", "topics" to "dhcp,info", "message" to "dhcp-hotspot assigned 10.5.50.40 to F0:99:BF:7A:31:2D"),
            )
            "/system/script/print" -> listOf(
                re(".id" to "*S1", "name" to "nov/17/2026-|-08:12:40-|-QJ5D2M-|-500-|-10.5.50.18-|-AA:BB:CC:00:11:22-|-1d-|-1jour-|-vc-482-11.17.26-", "owner" to "nov2026", "comment" to "mikhmon"),
                re(".id" to "*S2", "name" to "nov/17/2026-|-09:30:02-|-K7M4Q2-|-500-|-10.5.50.23-|-3C:2E:FF:41:8A:10-|-1d-|-1jour-|-vc-482-11.17.26-", "owner" to "nov2026", "comment" to "mikhmon"),
                re(".id" to "*S3", "name" to "nov/16/2026-|-18:02:13-|-wz-48213-|-2500-|-10.5.50.31-|-A4:50:46:12:CC:07-|-7d-|-semaine-|-up-915-11.16.26-", "owner" to "nov2026", "comment" to "mikhmon"),
            )
            "/ip/cloud/print" -> listOf(re(*(listOf("ddns-enabled" to "auto", "status" to "updated", "public-address" to "41.203.12.7") +
                (if (bth) listOf("back-to-home-vpn" to bthState, "vpn-status" to (if (bthState == "enabled") "BTH VPN is ready (using relay)" else "BTH VPN is disabled"))
                else emptyList())).toTypedArray()))
            "/ip/cloud/set" -> {
                if (!bth && args.containsKey("back-to-home-vpn")) listOf(listOf("!trap", "=message=expected end of command (line 1 column 10)"))
                else { args["back-to-home-vpn"]?.let { bthState = it }; emptyList() }
            }
            "/ip/cloud/back-to-home-users/print" ->
                if (!bth) listOf(listOf("!trap", "=message=no such command prefix"))
                else bthUsers.map { u -> re(*u.entries.map { it.key to it.value }.toTypedArray()) }
            "/ip/cloud/back-to-home-users/add" -> {
                val id = "*B" + (bthUsers.size + 1)
                bthUsers += mapOf(".id" to id, "name" to args["name"].orEmpty(), "allow-lan" to (args["allow-lan"] ?: "no"), "comment" to args["comment"].orEmpty())
                listOf(listOf("!done", "=ret=$id"))
            }
            "/ip/cloud/back-to-home-users/remove" -> { bthUsers.removeAll { it[".id"] == args[".id"] }; emptyList() }
            "/ip/cloud/back-to-home-users/show-client-config" -> {
                val id = args["numbers"] ?: args[".id"]
                val n = bthUsers.indexOfFirst { it[".id"] == id }
                if (n < 0) listOf(listOf("!trap", "=message=no such item"))
                else listOf(re("wg-client-config" to "[Interface]\nPrivateKey = r0KGQVZAhOlOp85cHpX1sMBqU47fuYfVZz+x8TbMIUw=\n" +
                    "Address = 192.168.216.${n + 2}/32,fc00:0:0:216::${n + 2}/128\nDNS = 192.168.216.1,fc00:0:0:216::1\nMTU = 1420\n\n" +
                    "[Peer]\nPublicKey = 4SHa/414EPJPFOH67MnaMei/wNaY4SrlIqwvdp13whQ=\nAllowedIPs = 0.0.0.0/0, ::/0\n" +
                    "Endpoint = ab12cd34ef56.vpn.mynetname.net:15180\nPersistentKeepalive = 25\n"))
            }
            else -> emptyList()
        }
    }

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
                                val tag = s.firstOrNull { it.startsWith(".tag=") }
                                val rs = synchronized(this) { replies(s) }
                                for (r in rs) RouterOs.writeSentence(o, if (tag != null && r.first() != "!done") r + tag else r)
                                if (rs.none { it.firstOrNull() == "!done" }) RouterOs.writeSentence(o, listOf("!done"))
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

package com.wifizone.mikhmon.router

import android.content.Context
import com.wifizone.mikhmon.RouterOs
import com.wifizone.mikhmon.RouterOsException
import com.wifizone.mikhmon.SettingsStore
import com.wifizone.mikhmon.WifiNetwork
import com.wifizone.mikhmon.tickets.Vouchers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Resource(
    val identity: String, val board: String, val version: String, val uptime: String, val cpu: Int,
    val memFree: Long, val memTotal: Long, val hddFree: Long, val hddTotal: Long, val arch: String,
)

data class ActiveUser(
    val id: String, val user: String, val address: String, val mac: String, val uptime: String, val timeLeft: String,
    val idle: String, val bytesIn: Long, val bytesOut: Long, val loginBy: String, val server: String, val device: String,
)

data class HsUser(
    val id: String, val name: String, val password: String, val profile: String, val comment: String, val disabled: Boolean,
    val uptime: String, val bytesIn: Long, val bytesOut: Long, val limitUptime: String, val limitBytes: String,
) {
    val used get() = uptime.isNotBlank() && uptime != "0s"
    val batch get() = if (Vouchers.isMikhmonBatch(comment)) comment else ""
}

data class Profile(val id: String, val name: String, val rateLimit: String, val sharedUsers: String, val info: Vouchers.ProfileInfo?) {
    val price get() = info?.sellingPrice?.takeIf { it.isNotBlank() && it != "0" } ?: info?.price?.takeIf { it.isNotBlank() && it != "0" }
    val validity get() = info?.validity?.takeIf { it.isNotBlank() }
}

data class Iface(val id: String, val name: String, val type: String, val running: Boolean)
data class Traffic(val rxBps: Long, val txBps: Long)
data class LogLine(val id: String, val time: String, val topics: String, val message: String)
data class Sale(val date: String, val time: String, val user: String, val price: Long, val ip: String, val mac: String, val validity: String, val profile: String)

/** Accès en direct au routeur (API RouterOS), avec une connexion gardée ouverte et rouverte au besoin. */
class RouterRepo(private val ctx: Context) {
    private val mutex = Mutex()
    private var conn: RouterOs? = null

    private fun open(): RouterOs {
        val s = SettingsStore(ctx).load() ?: throw IllegalStateException("Routeur non configuré")
        // Wi-Fi du routeur : connexion directe ; ailleurs, accès à distance (Back To Home) s'il est payé et activé.
        return com.wifizone.mikhmon.remote.RemoteAccess.connect(s, timeoutMs = 8000)
    }

    suspend fun <T> call(block: (RouterOs) -> T): T = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val c = conn ?: open().also { conn = it }
                block(c)
            } catch (e: RouterOsException) {
                if (e.kind != RouterOsException.Kind.CONNECTION) throw e
                runCatching { conn?.close() }; conn = null
                val c = open().also { conn = it }
                block(c)
            } catch (e: java.io.IOException) {
                runCatching { conn?.close() }; conn = null
                throw e
            }
        }
    }

    fun reset() { runCatching { conn?.close() }; conn = null }

    private fun Map<String, String>.long(k: String) = this[k]?.toLongOrNull() ?: 0L

    suspend fun resource(): Resource = call { r ->
        val res = r.command("/system/resource/print").firstOrNull().orEmpty()
        val id = r.command("/system/identity/print").firstOrNull()?.get("name").orEmpty()
        Resource(id, res["board-name"].orEmpty(), res["version"].orEmpty(), res["uptime"].orEmpty(), res["cpu-load"]?.toIntOrNull() ?: 0,
            res.long("free-memory"), res.long("total-memory"), res.long("free-hdd-space"), res.long("total-hdd-space"), res["architecture-name"].orEmpty())
    }

    suspend fun active(): List<ActiveUser> = call { r ->
        val leases = runCatching { r.command("/ip/dhcp-server/lease/print", "=.proplist=mac-address,host-name") }.getOrDefault(emptyList())
            .associate { (it["mac-address"] ?: "").uppercase() to (it["host-name"] ?: "") }
        r.command("/ip/hotspot/active/print").map {
            val mac = it["mac-address"].orEmpty()
            ActiveUser(it[".id"].orEmpty(), it["user"].orEmpty(), it["address"].orEmpty(), mac, it["uptime"].orEmpty(),
                it["session-time-left"].orEmpty(), it["idle-time"].orEmpty(), it.long("bytes-in"), it.long("bytes-out"),
                it["login-by"].orEmpty(), it["server"].orEmpty(), leases[mac.uppercase()].orEmpty())
        }
    }

    /** Utilisateurs du hotspot : tous, ceux d'un lot ([comment]), ou un seul ([name]). */
    suspend fun users(comment: String? = null, name: String? = null): List<HsUser> = call { r ->
        val args = mutableListOf("/ip/hotspot/user/print",
            "=.proplist=.id,name,password,profile,comment,disabled,uptime,bytes-in,bytes-out,limit-uptime,limit-bytes-total")
        if (comment != null) args += "?comment=$comment"
        if (name != null) args += "?name=$name"
        r.command(*args.toTypedArray()).filter { it["name"] != "default-trial" }.map {
            HsUser(it[".id"].orEmpty(), it["name"].orEmpty(), it["password"].orEmpty(), it["profile"].orEmpty(), it["comment"].orEmpty(),
                it["disabled"] == "true", it["uptime"].orEmpty(), it.long("bytes-in"), it.long("bytes-out"),
                it["limit-uptime"].orEmpty(), it["limit-bytes-total"].orEmpty())
        }
    }

    suspend fun profiles(): List<Profile> = call { r ->
        r.command("/ip/hotspot/user/profile/print").map {
            Profile(it[".id"].orEmpty(), it["name"].orEmpty(), it["rate-limit"].orEmpty(), it["shared-users"].orEmpty(), Vouchers.parseOnLogin(it["on-login"]))
        }
    }

    suspend fun hotspotDns(): String = call { r ->
        val prof = r.command("/ip/hotspot/print").firstOrNull()?.get("profile") ?: return@call ""
        r.command("/ip/hotspot/profile/print", "?name=$prof").firstOrNull { it["name"] == prof }?.get("dns-name").orEmpty()
    }

    /** Interface du hotspot (pour le trafic de l'accueil), sinon la première interface active. */
    suspend fun mainInterface(): String = call { r ->
        val hs = runCatching { r.command("/ip/hotspot/print").firstOrNull()?.get("interface") }.getOrNull()
        hs?.takeIf { it.isNotBlank() } ?: r.command("/interface/print", "=.proplist=name,running,type")
            .firstOrNull { it["running"] == "true" && it["type"] in setOf("bridge", "ether", "wlan", "vlan") }?.get("name").orEmpty()
    }

    suspend fun interfaces(): List<Iface> = call { r ->
        r.command("/interface/print", "=.proplist=.id,name,type,running").map {
            Iface(it[".id"].orEmpty(), it["name"].orEmpty(), it["type"].orEmpty(), it["running"] == "true")
        }
    }

    suspend fun traffic(iface: String): Traffic = call { r ->
        val m = r.command("/interface/monitor-traffic", "=interface=$iface", "=once=").firstOrNull().orEmpty()
        Traffic(m.long("rx-bits-per-second"), m.long("tx-bits-per-second"))
    }

    suspend fun logs(): List<LogLine> = call { r ->
        r.command("/log/print").map { LogLine(it[".id"].orEmpty(), it["time"].orEmpty(), it["topics"].orEmpty(), it["message"].orEmpty()) }.reversed()
    }

    suspend fun sales(): List<Sale> = call { r ->
        r.command("/system/script/print", "?comment=mikhmon", "=.proplist=name").mapNotNull {
            val p = it["name"].orEmpty().split("-|-")
            if (p.size < 8) null else Sale(p[0], p[1], p[2], p[3].trim().toLongOrNull() ?: 0L, p[4], p[5], p[6], p[7])
        }
    }

    suspend fun disconnect(activeId: String) = call { r -> r.command("/ip/hotspot/active/remove", "=.id=$activeId") }
    suspend fun setDisabled(userId: String, disabled: Boolean) = call { r -> r.command("/ip/hotspot/user/set", "=.id=$userId", "=disabled=${if (disabled) "yes" else "no"}") }
    suspend fun removeUser(userId: String) = call { r -> r.command("/ip/hotspot/user/remove", "=.id=$userId") }

    /** Crée les tickets sur le routeur (format Mikhmon). [progress] reçoit le nombre déjà créé. */
    suspend fun addUsers(list: List<Pair<String, String?>>, profile: String, comment: String, limitUptime: String, limitBytes: Long, progress: (Int) -> Unit) = call { r ->
        list.forEachIndexed { i, (u, p) ->
            val args = mutableListOf("/ip/hotspot/user/add", "=server=all", "=name=$u", "=password=${p ?: u}", "=profile=$profile", "=comment=$comment")
            if (limitUptime.isNotBlank()) args += "=limit-uptime=$limitUptime"
            if (limitBytes > 0) args += "=limit-bytes-total=$limitBytes"
            r.command(*args.toTypedArray())
            progress(i + 1)
        }
    }

    companion object {
        /** Date du jour dans les deux formats de RouterOS (v6 « nov/17/2026 », v7.10+ « 2026-11-17 »). */
        fun todayKeys(now: Date = Date()): Set<String> = setOf(
            SimpleDateFormat("MMM/dd/yyyy", Locale.US).format(now).lowercase(),
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now),
        )
        fun monthKeys(now: Date = Date()): Set<String> = setOf(
            SimpleDateFormat("MMM", Locale.US).format(now).lowercase() + "/",
            SimpleDateFormat("yyyy-MM", Locale.US).format(now),
        )
    }
}

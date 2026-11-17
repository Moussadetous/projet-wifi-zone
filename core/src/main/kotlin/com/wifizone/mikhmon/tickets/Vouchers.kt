package com.wifizone.mikhmon.tickets

import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Génération des identifiants, compatible avec Mikhmon (commentaires de lot, profils). */
object Vouchers {
    enum class Charset(val label: String, val chars: String) {
        DIGITS("Chiffres", "23456789"),
        UPPER("Majuscules", "ABCDEFGHJKLMNPQRSTUVWXYZ"),
        LOWER("Minuscules", "abcdefghjkmnpqrstuvwxyz"),
        MIX_UPPER("Majuscules + chiffres", "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"),
        MIX_LOWER("Minuscules + chiffres", "abcdefghjkmnpqrstuvwxyz23456789"),
    }

    data class Spec(
        val quantity: Int,
        /** true : utilisateur + mot de passe ; false : code unique (mot de passe = code). */
        val userPass: Boolean = false,
        val length: Int = 6,
        val charset: Charset = Charset.MIX_UPPER,
        val prefix: String = "",
        val passLength: Int = 4,
    )

    private val rnd = SecureRandom()

    private fun random(chars: String, n: Int) = String(CharArray(n) { chars[rnd.nextInt(chars.length)] })

    /** Identifiants uniques (jamais déjà présents sur le routeur). */
    fun generate(spec: Spec, existing: Set<String>): List<Pair<String, String?>> {
        val out = LinkedHashMap<String, String?>()
        val taken = HashSet(existing)
        var guard = 0
        while (out.size < spec.quantity && guard < spec.quantity * 200) {
            guard++
            val user = spec.prefix + random(spec.charset.chars, spec.length.coerceIn(3, 16))
            if (user in taken || user in out) continue
            out[user] = if (spec.userPass) random(Charset.DIGITS.chars, spec.passLength.coerceIn(3, 12)) else null
        }
        return out.entries.map { it.key to it.value }
    }

    /** Commentaire de lot au format Mikhmon : vc-123-11.17.26-commentaire (vc = code unique, up = utilisateur + mot de passe). */
    fun mikhmonComment(userPass: Boolean, note: String, now: Date = Date()): String {
        val date = SimpleDateFormat("MM.dd.yy", Locale.US).format(now)
        val n = 100 + rnd.nextInt(900)
        val clean = note.replace(Regex("[^A-Za-z0-9 _.-]"), "").trim().take(40)
        return "${if (userPass) "up" else "vc"}-$n-$date-$clean"
    }

    fun isMikhmonBatch(comment: String) = Regex("^(vc|up)-\\d{3}-\\d{2}\\.\\d{2}\\.\\d{2}-").containsMatchIn(comment)

    /** Infos du profil Mikhmon : le script « on-login » commence par :put (",mode,prix,validité,prix de vente,,verrou,"). */
    data class ProfileInfo(val price: String, val validity: String, val sellingPrice: String, val expireMode: String, val lock: String)

    fun parseOnLogin(onLogin: String?): ProfileInfo? {
        val s = onLogin ?: return null
        val start = s.indexOf(":put (\"")
        if (start < 0) return null
        val end = s.indexOf("\")", start)
        if (end < 0) return null
        val parts = s.substring(start + 7, end).split(",")
        fun at(i: Int) = parts.getOrNull(i)?.trim().orEmpty()
        return ProfileInfo(price = at(2), validity = at(3), sellingPrice = at(4), expireMode = at(1), lock = at(6))
    }

    // ------------------------------------------------------------------ mise en forme lisible
    /** « 1d » → « 1 jour », « 2h30m » → « 2 h 30 », « 1w » → « 7 jours », « 00:30:00 » → « 30 min ». */
    fun duration(ros: String?): String? {
        val s = ros?.trim()?.lowercase().orEmpty()
        if (s.isEmpty() || s == "0" || s == "0s" || s == "00:00:00") return null
        var total = 0L
        if (Regex("^\\d+:\\d{2}:\\d{2}$").matches(s)) {
            val (h, m, sec) = s.split(":").map { it.toLong() }
            total = h * 3600 + m * 60 + sec
        } else {
            for (m in Regex("(\\d+)([wdhms])").findAll(s)) {
                val n = m.groupValues[1].toLong()
                total += n * when (m.groupValues[2]) { "w" -> 604800L; "d" -> 86400L; "h" -> 3600L; "m" -> 60L; else -> 1L }
            }
        }
        if (total <= 0) return null
        val d = total / 86400; val h = (total % 86400) / 3600; val mi = (total % 3600) / 60
        return when {
            d > 0 && h == 0L && mi == 0L -> if (d == 1L) "1 jour" else "$d jours"
            d > 0 -> "$d j ${h} h"
            h > 0 && mi == 0L -> "$h h"
            h > 0 -> "$h h ${mi.toString().padStart(2, '0')}"
            else -> "$mi min"
        }
    }

    /** Octets → « 500 Mo », « 2 Go », « 1,5 Go ». */
    fun bytes(n: Long?): String? {
        if (n == null || n <= 0) return null
        val gb = 1024.0 * 1024 * 1024
        val mb = 1024.0 * 1024
        return when {
            n >= gb -> fmt(n / gb) + " Go"
            n >= mb -> fmt(n / mb) + " Mo"
            else -> fmt(n / 1024.0) + " Ko"
        }
    }

    private fun fmt(v: Double): String {
        val r = Math.round(v * 10) / 10.0
        return if (r == Math.floor(r)) r.toLong().toString() else r.toString().replace('.', ',')
    }

    /** Débit RouterOS « 2M/5M » (envoi/réception) → « 5 Mb/s » (réception, ce que voit le client). */
    fun rate(rateLimit: String?): String? {
        val s = rateLimit?.trim().orEmpty()
        if (s.isEmpty()) return null
        val first = s.split(" ").first()
        val parts = first.split("/")
        val rx = (parts.getOrNull(1) ?: parts[0]).trim()
        val m = Regex("^(\\d+(?:\\.\\d+)?)([kKmMgG]?)$").find(rx) ?: return null
        val v = m.groupValues[1]
        return when (m.groupValues[2].uppercase()) {
            "K" -> "$v kb/s"; "M" -> "$v Mb/s"; "G" -> "$v Gb/s"; else -> "$v b/s"
        }
    }

    /** Prix lisible : « 1000 » → « 1 000 ». */
    fun price(p: String?): String? {
        val s = p?.trim().orEmpty()
        if (s.isEmpty() || s == "0") return null
        val n = s.toLongOrNull() ?: return s
        return n.toString().reversed().chunked(3).joinToString(" ").reversed()
    }
}

package com.wifizone.mikhmon

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest

class RouterOsException(val kind: Kind, message: String) : IOException(message) {
    enum class Kind { UNREACHABLE, API_DISABLED, BAD_LOGIN, PROTOCOL }
}

/** Client minimal de l'API RouterOS (port 8728), suffisant pour tester la connexion et lire la config. */
class RouterOs private constructor(private val socket: Socket) : Closeable {
    private val input = BufferedInputStream(socket.getInputStream())
    private val output = BufferedOutputStream(socket.getOutputStream())

    companion object {
        const val PORT = 8728

        fun connect(
            host: String, user: String, pass: String,
            port: Int = PORT, timeoutMs: Int = 5000,
            socketFactory: () -> Socket = { Socket() },
        ): RouterOs {
            val s = socketFactory()
            try {
                s.connect(InetSocketAddress(host, port), timeoutMs)
            } catch (e: IOException) {
                s.close()
                val refused = e is ConnectException &&
                    (e.message ?: "").let { it.contains("refused", true) || it.contains("ECONNREFUSED") }
                throw if (refused) RouterOsException(RouterOsException.Kind.API_DISABLED, "Connexion refusée sur $host:$port")
                else RouterOsException(RouterOsException.Kind.UNREACHABLE, "Routeur injoignable ($host:$port) : ${e.message}")
            }
            s.soTimeout = timeoutMs
            val r = RouterOs(s)
            try { r.login(user, pass) } catch (e: Exception) { r.close(); throw e }
            return r
        }

        fun writeLength(out: OutputStream, n: Int) {
            when {
                n < 0x80 -> out.write(n)
                n < 0x4000 -> { out.write((n shr 8) or 0x80); out.write(n and 0xFF) }
                n < 0x200000 -> { out.write((n shr 16) or 0xC0); out.write((n shr 8) and 0xFF); out.write(n and 0xFF) }
                else -> { out.write((n shr 24) or 0xE0); out.write((n shr 16) and 0xFF); out.write((n shr 8) and 0xFF); out.write(n and 0xFF) }
            }
        }

        fun readLength(inp: InputStream): Int {
            fun b(): Int { val x = inp.read(); if (x < 0) throw EOFException(); return x }
            val c = b()
            return when {
                c and 0x80 == 0 -> c
                c and 0xC0 == 0x80 -> ((c and 0x3F) shl 8) or b()
                c and 0xE0 == 0xC0 -> ((c and 0x1F) shl 16) or (b() shl 8) or b()
                c and 0xF0 == 0xE0 -> ((c and 0x0F) shl 24) or (b() shl 16) or (b() shl 8) or b()
                else -> (b() shl 24) or (b() shl 16) or (b() shl 8) or b()
            }
        }

        fun writeSentence(out: OutputStream, words: List<String>) {
            for (w in words) { val b = w.toByteArray(); writeLength(out, b.size); out.write(b) }
            out.write(0); out.flush()
        }

        fun readSentence(inp: InputStream): List<String> {
            val words = ArrayList<String>()
            while (true) {
                val n = readLength(inp)
                if (n == 0) return words
                val b = ByteArray(n)
                var off = 0
                while (off < n) { val r = inp.read(b, off, n - off); if (r < 0) throw EOFException(); off += r }
                words += String(b)
            }
        }
    }

    private fun attrs(words: List<String>): Map<String, String> =
        words.drop(1).filter { it.startsWith("=") }.associate {
            val i = it.indexOf('=', 1)
            if (i < 0) it.substring(1) to "" else it.substring(1, i) to it.substring(i + 1)
        }

    private fun login(user: String, pass: String) {
        var reply = talk(listOf("/login", "=name=$user", "=password=$pass"), loginPhase = true)
        val ret = reply.firstOrNull()?.get("ret")
        if (ret != null && ret.length == 32) {
            // Méthode de connexion d'avant RouterOS 6.43
            val chal = ret.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            val md5 = MessageDigest.getInstance("MD5").digest(byteArrayOf(0) + pass.toByteArray() + chal)
            reply = talk(listOf("/login", "=name=$user", "=response=00" + md5.joinToString("") { "%02x".format(it) }), loginPhase = true)
        }
    }

    /** Envoie une commande ; renvoie les attributs des réponses (!re et !done). */
    private fun talk(words: List<String>, loginPhase: Boolean = false): List<Map<String, String>> {
        try {
            writeSentence(output, words)
            val result = ArrayList<Map<String, String>>()
            var trap: String? = null
            while (true) {
                val s = readSentence(input)
                when (s.firstOrNull()) {
                    "!re" -> result += attrs(s)
                    "!trap" -> trap = attrs(s)["message"] ?: "erreur"
                    "!fatal" -> throw RouterOsException(RouterOsException.Kind.PROTOCOL, s.drop(1).joinToString(" "))
                    "!done" -> {
                        if (trap != null) throw RouterOsException(
                            if (loginPhase) RouterOsException.Kind.BAD_LOGIN else RouterOsException.Kind.PROTOCOL, trap)
                        val done = attrs(s)
                        if (done.isNotEmpty()) result += done
                        return result
                    }
                }
            }
        } catch (e: RouterOsException) { throw e
        } catch (e: IOException) {
            throw RouterOsException(
                if (loginPhase) RouterOsException.Kind.BAD_LOGIN else RouterOsException.Kind.PROTOCOL,
                "Le routeur a coupé la connexion : ${e.message}")
        }
    }

    fun command(vararg words: String): List<Map<String, String>> = talk(words.toList()).filter { it.isNotEmpty() }

    override fun close() { runCatching { socket.close() } }
}

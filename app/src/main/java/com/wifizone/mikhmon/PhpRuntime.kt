package com.wifizone.mikhmon

import android.content.Context
import android.os.Build
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream
import kotlin.concurrent.thread

class PhpException(message: String, val details: String) : Exception(message)

/**
 * Lance Mikhmon avec le PHP embarqué. Android étant un Linux, l'application exécute
 * directement le binaire PHP (compilé pour Android) comme un processus normal : c'est
 * ce que fait Termux, sans Termux.
 */
class PhpRuntime(private val ctx: Context) {
    val docRoot = File(ctx.filesDir, "www")
    val configFile = File(docRoot, "include/config.php")
    private val tmp = File(ctx.filesDir, "tmp")
    private val ini = File(ctx.filesDir, "php.ini")
    val logFile = File(ctx.filesDir, "php-server.log")
    // Android n'autorise l'exécution que depuis nativeLibraryDir : PHP y est livré sous le nom libphp.so
    val phpBinary: File get() = File(ctx.applicationInfo.nativeLibraryDir, "libphp.so")

    @Volatile private var process: Process? = null

    /** Extrait Mikhmon (à chaque nouvelle installation de l'APK) en gardant config.php. */
    fun install() {
        val marker = File(ctx.filesDir, ".installed")
        @Suppress("DEPRECATION")
        val stamp = ctx.packageManager.getPackageInfo(ctx.packageName, 0).lastUpdateTime.toString()
        if (!(marker.exists() && marker.readText() == stamp && configFile.parentFile?.exists() == true)) {
            val savedConfig = configFile.takeIf { it.exists() }?.readBytes()
            docRoot.mkdirs()
            val root = docRoot.canonicalPath + File.separator
            ZipInputStream(ctx.assets.open("mikhmon/mikhmon.zip").buffered()).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    val out = File(docRoot, e.name).canonicalFile
                    if (!out.path.startsWith(root)) continue
                    if (e.isDirectory) out.mkdirs() else {
                        out.parentFile?.mkdirs()
                        out.outputStream().use { zip.copyTo(it) }
                    }
                }
            }
            savedConfig?.let { configFile.writeBytes(it) }
            marker.writeText(stamp)
        }
        val api = File(docRoot, MikhmonPatcher.ROUTEROS_FILE)
        val src = api.readText()
        val patched = MikhmonPatcher.patchRouterOs(src)
        if (patched != src) api.writeText(patched)
    }

    fun writeConfig(text: String) { configFile.parentFile?.mkdirs(); configFile.writeText(text) }

    private fun writeIni() {
        tmp.mkdirs()
        ini.writeText(
            """
            date.timezone=${TimeZone.getDefault().id}
            output_buffering=4096
            session.save_path="${tmp.absolutePath}"
            sys_temp_dir="${tmp.absolutePath}"
            upload_tmp_dir="${tmp.absolutePath}"
            display_errors=Off
            log_errors=On
            default_socket_timeout=10
            memory_limit=128M
            """.trimIndent() + "\n"
        )
    }

    /** Vérifie que PHP peut s'exécuter sur ce téléphone, avec un message clair sinon. */
    fun preflight(): String {
        if (!phpBinary.exists()) throw PhpException("PHP est absent de l'application", report())
        val p = try {
            ProcessBuilder(phpBinary.absolutePath, "-n", "-v").redirectErrorStream(true).start()
        } catch (e: Exception) {
            throw PhpException("Android refuse de lancer PHP : ${e.message}", report())
        }
        val out = p.inputStream.bufferedReader().readText()
        if (!p.waitFor(15, TimeUnit.SECONDS)) { p.destroy(); throw PhpException("PHP ne répond pas", report()) }
        if (p.exitValue() != 0) throw PhpException("PHP ne fonctionne pas sur ce téléphone (${explainExit(p.exitValue())})", out + "\n" + report())
        return out.lineSequence().firstOrNull().orEmpty()
    }

    fun start(port: Int, onExit: (Int) -> Unit) {
        writeIni()
        if (logFile.length() > 512 * 1024) logFile.delete()
        logFile.appendText("\n=== Démarrage ${java.util.Date()} port $port ===\n")
        val pb = ProcessBuilder(
            phpBinary.absolutePath, "-c", ini.absolutePath,
            "-S", "127.0.0.1:$port", "-t", docRoot.absolutePath,
        ).directory(docRoot).redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.appendTo(logFile))
        pb.environment()["TMPDIR"] = tmp.absolutePath
        pb.environment()["HOME"] = ctx.filesDir.absolutePath
        val p = pb.start()
        process = p
        thread(name = "php-watch", isDaemon = true) { val code = p.waitFor(); if (process === p) onExit(code) }
    }

    /** Prêt = le serveur répond vraiment en HTTP (pas seulement le port ouvert). */
    fun waitReady(port: Int, timeoutMs: Long = 20_000): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (process?.isAlive != true) return false
            try {
                val c = URL("http://127.0.0.1:$port/admin.php?id=login").openConnection() as HttpURLConnection
                c.connectTimeout = 1000; c.readTimeout = 5000
                val code = c.responseCode
                c.disconnect()
                if (code in 200..399) return true
            } catch (_: Exception) { }
            Thread.sleep(250)
        }
        return false
    }

    fun isAlive() = process?.isAlive == true

    fun stop() { val p = process; process = null; p?.destroy() }

    fun logTail(lines: Int = 40): String =
        if (logFile.exists()) logFile.readLines().takeLast(lines).joinToString("\n") else "(aucun journal)"

    fun report(): String = buildString {
        appendLine("Appareil : ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("ABI : ${Build.SUPPORTED_ABIS.joinToString()}")
        appendLine("PHP : ${phpBinary.absolutePath} " + if (phpBinary.exists()) "(${phpBinary.length()} octets)" else "(ABSENT)")
        appendLine("--- journal PHP ---")
        append(logTail())
    }

    companion object {
        fun explainExit(code: Int): String = when {
            code == 0 -> "arrêt normal"
            code > 128 -> when (val sig = code - 128) {
                9 -> "arrêté par Android (SIGKILL)"
                11 -> "plantage (SIGSEGV)"
                31 -> "appel système interdit par Android (SIGSYS)"
                4 -> "instruction non supportée par le processeur (SIGILL)"
                6 -> "abandon (SIGABRT)"
                else -> "signal $sig"
            }
            else -> "code de sortie $code"
        }
    }
}

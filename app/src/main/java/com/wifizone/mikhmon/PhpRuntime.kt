package com.wifizone.mikhmon

import android.content.Context
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.util.zip.ZipInputStream

/** Prépare les fichiers de Mikhmon et lance le serveur PHP intégré. */
class PhpRuntime(private val ctx: Context) {
    private var process: Process? = null

    val docRoot = File(ctx.filesDir, "www")
    private val tmp = File(ctx.filesDir, "tmp")
    private val ini = File(ctx.filesDir, "php.ini")
    // Android n'autorise l'exécution que depuis nativeLibraryDir : le binaire PHP y est livré sous le nom libphp.so
    private val php get() = File(ctx.applicationInfo.nativeLibraryDir, "libphp.so")

    /** Extrait mikhmon.zip des assets (une fois par version d'app). */
    private fun installMikhmon() {
        val marker = File(ctx.filesDir, ".installed")
        val version = ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
        if (marker.exists() && marker.readText() == version && docRoot.exists()) return
        // Conserve les données de l'utilisateur (config.php est réécrit de toute façon)
        docRoot.mkdirs()
        ZipInputStream(ctx.assets.open("mikhmon/mikhmon.zip")).use { zip ->
            generateSequence { zip.nextEntry }.forEach { e ->
                val out = File(docRoot, e.name).canonicalFile
                require(out.path.startsWith(docRoot.canonicalPath)) { "zip path traversal" }
                if (e.isDirectory) out.mkdirs() else {
                    out.parentFile?.mkdirs()
                    out.outputStream().use { zip.copyTo(it) }
                }
            }
        }
        marker.writeText(version ?: "")
    }

    fun start(s: MikhmonSettings) {
        check(php.exists()) { "libphp.so absent : lancez scripts/fetch-assets.sh avant le build" }
        installMikhmon()
        tmp.mkdirs()
        File(docRoot, "include").mkdirs()
        File(docRoot, "include/config.php").writeText(ConfigWriter.render(s))
        ini.writeText(
            """
            date.timezone=UTC
            session.save_path=${tmp.absolutePath}
            sys_temp_dir=${tmp.absolutePath}
            upload_tmp_dir=${tmp.absolutePath}
            display_errors=Off
            log_errors=On
            error_log=${File(ctx.filesDir, "php-error.log").absolutePath}
            default_socket_timeout=10
            """.trimIndent()
        )
        stop()
        process = ProcessBuilder(
            php.absolutePath, "-c", ini.absolutePath,
            "-S", "127.0.0.1:${s.port}", "-t", docRoot.absolutePath
        ).directory(docRoot)
            .redirectErrorStream(true)
            .redirectOutput(File(ctx.filesDir, "php-server.log"))
            .apply { environment()["TMPDIR"] = tmp.absolutePath }
            .start()
    }

    fun waitReady(port: Int, timeoutMs: Long = 10_000): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            try {
                Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), 300); return true }
            } catch (_: Exception) { Thread.sleep(150) }
        }
        return false
    }

    fun isAlive() = process?.isAlive == true

    fun stop() { process?.destroy(); process = null }
}

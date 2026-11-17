package com.wifizone.mikhmon

import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * Pont TCP 127.0.0.1:listenPort -> routeur:8728.
 * Sur Android, socketFactory crée des sockets liées au réseau Wi-Fi : Mikhmon (PHP) joint ainsi
 * le routeur même si Android fait passer le reste du trafic par les données mobiles.
 */
class RouterBridge(
    private val target: () -> Pair<String, Int>,
    private val socketFactory: (host: String) -> Socket = { Socket() },
) : Closeable {
    private var server: ServerSocket? = null
    val port: Int get() = server?.localPort ?: -1

    fun start(listenPort: Int) {
        val srv = ServerSocket()
        srv.reuseAddress = true
        srv.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), listenPort), 50)
        server = srv
        thread(name = "router-bridge", isDaemon = true) {
            while (!srv.isClosed) {
                val client = runCatching { srv.accept() }.getOrNull() ?: break
                thread(isDaemon = true) { serve(client) }
            }
        }
    }

    private fun serve(client: Socket) {
        val (host, port) = target()
        val upstream = socketFactory(host)
        try {
            upstream.connect(InetSocketAddress(host, port), 5000)
        } catch (e: Exception) {
            runCatching { client.close() }; runCatching { upstream.close() }; return
        }
        val t = thread(isDaemon = true) { pump(upstream.getInputStream(), client.getOutputStream(), client, upstream) }
        pump(client.getInputStream(), upstream.getOutputStream(), client, upstream)
        t.join(1000)
    }

    private fun pump(i: InputStream, o: OutputStream, a: Socket, b: Socket) {
        try {
            val buf = ByteArray(16 * 1024)
            while (true) { val n = i.read(buf); if (n < 0) break; o.write(buf, 0, n); o.flush() }
        } catch (_: Exception) {
        } finally { runCatching { a.close() }; runCatching { b.close() } }
    }

    override fun close() { runCatching { server?.close() }; server = null }
}

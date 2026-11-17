package com.wifizone.mikhmon

import java.net.InetAddress
import java.net.ServerSocket

object PortPicker {
    /** Plage rarement utilisée (ports dynamiques IANA) pour éviter les conflits. */
    val range = 38000..39999

    fun isFree(port: Int) = port in 1..65535 && try {
        ServerSocket(port, 1, InetAddress.getByName("127.0.0.1")).use { true }
    } catch (e: Exception) { false }

    fun pick(vararg avoid: Int): Int = range.shuffled().first { it !in avoid && isFree(it) }
}

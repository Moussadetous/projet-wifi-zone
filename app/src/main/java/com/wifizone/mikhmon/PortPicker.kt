package com.wifizone.mikhmon

import java.net.InetAddress
import java.net.ServerSocket

object PortPicker {
    /** Plage rarement utilisée (ports dynamiques/privés IANA), pour éviter les conflits. */
    private val range = 38000..39999

    fun isFree(port: Int) = try {
        ServerSocket(port, 1, InetAddress.getByName("127.0.0.1")).use { true }
    } catch (e: Exception) { false }

    fun pick(preferred: Int = 0): Int {
        if (preferred in range && isFree(preferred)) return preferred
        return range.shuffled().first { isFree(it) }
    }
}

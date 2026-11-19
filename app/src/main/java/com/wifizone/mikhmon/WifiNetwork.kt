package com.wifizone.mikhmon

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import java.net.Inet4Address
import java.net.InetAddress
import java.net.Socket

/** Détecte le Wi-Fi et l'adresse du routeur (passerelle DHCP, sinon x.y.z.1). */
object WifiNetwork {
    data class Info(val network: Network?, val ownIp: String?, val gateway: String?) {
        val connected get() = network != null
    }

    fun detect(ctx: Context): Info {
        val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return Info(null, null, null)
        @Suppress("DEPRECATION")
        for (n in cm.allNetworks) {
            val caps = cm.getNetworkCapabilities(n) ?: continue
            if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) continue
            // Le tunnel de l'accès à distance (VPN) se déclare aussi « Wi-Fi » quand il passe par un Wi-Fi :
            // ce n'est pas le Wi-Fi lui-même, il ne dit rien du réseau du routeur.
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)) continue
            val lp = cm.getLinkProperties(n) ?: continue
            val own = lp.linkAddresses.map { it.address }.firstOrNull { it is Inet4Address }?.hostAddress
            val gw = lp.routes.firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }
                ?.gateway?.hostAddress?.takeIf { it != "0.0.0.0" }
            return Info(n, own, gw ?: Defaults.guessGateway(own))
        }
        return Info(null, null, null)
    }

    /**
     * Socket liée au réseau Wi-Fi : sans ça, quand le Wi-Fi du MikroTik n'a pas internet,
     * Android envoie le trafic sur les données mobiles et le routeur est injoignable.
     */
    fun socketFor(ctx: Context, host: String): Socket {
        val loopback = runCatching { InetAddress.getByName(host).isLoopbackAddress }.getOrDefault(false)
        if (loopback) return Socket()
        return detect(ctx).network?.socketFactory?.createSocket() ?: Socket()
    }
}

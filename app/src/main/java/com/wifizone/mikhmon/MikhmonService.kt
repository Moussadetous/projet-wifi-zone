package com.wifizone.mikhmon

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/** Garde le serveur PHP en vie en arrière-plan. */
class MikhmonService : Service() {
    private lateinit var runtime: PhpRuntime

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val s = SettingsStore(this).load()
        if (s == null) { stopSelf(); return START_NOT_STICKY }
        startForegroundCompat(s.port)
        if (!::runtime.isInitialized) runtime = PhpRuntime(this)
        Thread {
            try {
                if (!runtime.isAlive()) runtime.start(s)
                val ok = runtime.waitReady(s.port)
                sendBroadcast(Intent(ACTION_STATE).setPackage(packageName)
                    .putExtra("ready", ok).putExtra("port", s.port))
            } catch (e: Exception) {
                sendBroadcast(Intent(ACTION_STATE).setPackage(packageName)
                    .putExtra("ready", false).putExtra("error", e.message))
            }
        }.start()
        return START_STICKY
    }

    override fun onDestroy() {
        if (::runtime.isInitialized) runtime.stop()
        super.onDestroy()
    }

    private fun startForegroundCompat(port: Int) {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26)
            nm.createNotificationChannel(NotificationChannel(CH, "Serveur Mikhmon", NotificationManager.IMPORTANCE_LOW))
        val open = android.app.PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), android.app.PendingIntent.FLAG_IMMUTABLE)
        val n: Notification = NotificationCompat.Builder(this, CH)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("Mikhmon actif")
            .setContentText("Serveur local sur 127.0.0.1:$port")
            .setContentIntent(open).setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 34)
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1, n)
    }

    companion object {
        const val ACTION_STATE = "com.wifizone.mikhmon.STATE"
        private const val CH = "server"
        fun start(c: Context) = ContextCompat.startForegroundService(c, Intent(c, MikhmonService::class.java))
        fun stop(c: Context) { c.stopService(Intent(c, MikhmonService::class.java)) }
    }
}

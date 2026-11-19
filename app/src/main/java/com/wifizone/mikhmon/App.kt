package com.wifizone.mikhmon

import android.app.Application
import android.net.Uri
import com.wifizone.mikhmon.account.MproClient
import com.wifizone.mikhmon.print.DesignStore
import com.wifizone.mikhmon.router.RouterRepo
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Services.init(this)
        runCatching { Region.migrateCurrency(this) }
        com.wifizone.mikhmon.remote.RemoteAccess.watchNetwork()
    }
}

/** Services partagés par tous les écrans. */
object Services {
    lateinit var app: Application
        private set

    fun init(a: Application) { if (!::app.isInitialized) app = a }

    val settings by lazy { SettingsStore(app) }
    val router by lazy { RouterRepo(app) }
    val design by lazy { DesignStore(app) }

    /** Comptes des applications : la boutique MPro (nom de domaine, puis adresse Cloudflare qui ne change jamais). */
    val account by lazy {
        MproClient(app, appId = "mikhmon-droid", scheme = "mikhmondroid", appVersion = BuildConfig.VERSION_NAME,
            candidates = listOf("https://mprodigital.shop", "https://mpro-digital.m-pro.workers.dev"))
    }

    /** Liens reçus par l'application (retour de connexion ou de paiement). */
    val links = MutableSharedFlow<Uri>(replay = 1, extraBufferCapacity = 4)

    /** Écran demandé par un raccourci (icône de l'application) ou un test. */
    val openRequest = MutableStateFlow<String?>(null)
}

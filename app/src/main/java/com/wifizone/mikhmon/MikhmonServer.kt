package com.wifizone.mikhmon

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.concurrent.thread

/** État global du serveur Mikhmon (PHP + pont Wi-Fi vers le routeur). */
object MikhmonServer {
    sealed interface State {
        object Idle : State
        object Starting : State
        data class Ready(val port: Int) : State
        data class Failed(val title: String, val details: String) : State
    }

    @Volatile var state: State = State.Idle
        private set
    private val main = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArraySet<(State) -> Unit>()
    private var runtime: PhpRuntime? = null
    private var bridge: RouterBridge? = null
    @Volatile private var crashes = 0

    fun observe(l: (State) -> Unit) { listeners += l; val s = state; main.post { l(s) } }
    fun unobserve(l: (State) -> Unit) { listeners -= l }

    private fun publish(s: State) { state = s; main.post { listeners.forEach { it(s) } } }

    fun report(): String = runtime?.report() ?: "(serveur non démarré)"

    @Synchronized
    fun ensureRunning(ctx: Context) {
        val s = state
        if (s is State.Starting) return
        if (s is State.Ready && runtime?.isAlive() == true) { publish(s); return }
        publish(State.Starting)
        val app = ctx.applicationContext
        thread(name = "mikhmon-start") { startBlocking(app) }
    }

    /** Relance complète (après un changement de configuration). */
    @Synchronized
    fun restart(ctx: Context) { stop(); crashes = 0; ensureRunning(ctx) }

    @Synchronized
    fun stop() {
        runtime?.stop(); runtime = null
        bridge?.close(); bridge = null
        if (state !is State.Failed) publish(State.Idle)
    }

    private fun startBlocking(ctx: Context) {
        val store = SettingsStore(ctx)
        val cfg = store.load() ?: return publish(State.Failed("Aucune configuration", "Ouvrez la configuration."))
        try {
            synchronized(this) { runtime?.stop(); bridge?.close() }
            val rt = PhpRuntime(ctx)
            runtime = rt
            rt.install()

            // Pont 127.0.0.1:bridgePort -> routeur, forcé sur le Wi-Fi
            val br = RouterBridge(
                target = { store.load().let { (it?.routerIp ?: cfg.routerIp) to (it?.routerPort ?: cfg.routerPort) } },
                socketFactory = { host -> WifiNetwork.socketFor(ctx, host) },
            )
            var bridgePort = store.bridgePort
            if (!PortPicker.isFree(bridgePort)) {
                bridgePort = PortPicker.pick(store.webPort)
                store.bridgePort = bridgePort
                store.configDirty = true
            }
            br.start(bridgePort)
            bridge = br

            if (store.configDirty || !rt.configFile.exists()) {
                rt.writeConfig(ConfigWriter.render(cfg, "127.0.0.1:$bridgePort"))
                store.configDirty = false
            }

            rt.preflight()

            var port = store.webPort
            if (!PortPicker.isFree(port)) { port = PortPicker.pick(bridgePort); store.webPort = port }
            rt.start(port) { code -> onExit(ctx, rt, code) }
            if (!rt.waitReady(port)) {
                throw PhpException("Le serveur Mikhmon ne répond pas" +
                    if (!rt.isAlive()) " (PHP s'est arrêté)" else "", rt.report())
            }
            publish(State.Ready(port))
        } catch (e: PhpException) {
            publish(State.Failed(e.message ?: "Erreur", e.details))
        } catch (e: Exception) {
            publish(State.Failed("Erreur au démarrage : ${e.message}", (runtime?.report() ?: "") + "\n\n" + e.stackTraceToString()))
        }
    }

    private fun onExit(ctx: Context, rt: PhpRuntime, code: Int) {
        if (runtime !== rt) return // arrêt volontaire
        crashes++
        if (crashes <= 2) {
            // relance automatique silencieuse
            state = State.Idle
            ensureRunning(ctx)
        } else {
            publish(State.Failed("Le serveur PHP s'est arrêté : ${PhpRuntime.explainExit(code)}", rt.report()))
        }
    }
}

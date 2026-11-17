package com.wifizone.mikhmon.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay

/** Données du routeur relues à intervalle régulier (tant que l'écran est affiché). */
@Composable
fun <T> polling(intervalMs: Long, vararg keys: Any?, load: suspend () -> T): State<Result<T>?> =
    produceState<Result<T>?>(null, *keys) {
        while (true) {
            val r = runCatching { load() }
            value = if (r.isFailure && value?.isSuccess == true) value else r
            // À distance (par le tunnel), relectures espacées selon le réglage « Actualisation à distance ».
            delay((intervalMs * com.wifizone.mikhmon.remote.RemoteAccess.pollFactor()).toLong())
        }
    }

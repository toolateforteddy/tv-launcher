package com.toolateforteddy.kidremote

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class FoundTv(val name: String, val host: String)

/** Finds TVs on the Wi-Fi that accept remote connections (they advertise _androidtvremote2._tcp). */
@Suppress("DEPRECATION") // resolveService and host still work everywhere and keep this short.
class TvFinder(context: Context) {
    private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val main = Handler(Looper.getMainLooper())
    private val _found = MutableStateFlow<List<FoundTv>>(emptyList())
    val found: StateFlow<List<FoundTv>> = _found

    private val toResolve = ArrayDeque<NsdServiceInfo>()
    private var resolving = false
    private var listener: NsdManager.DiscoveryListener? = null

    fun start() {
        if (listener != null) return
        val l = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(info: NsdServiceInfo) = main.post { queue(info) }.let { }
            override fun onServiceLost(info: NsdServiceInfo) = main.post {
                _found.value = _found.value.filterNot { it.name == info.serviceName }
            }.let { }
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        }
        listener = l
        runCatching { nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, l) }
    }

    fun stop() {
        listener?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        listener = null
        toResolve.clear()
    }

    // Older Android versions can only resolve one service at a time.
    private fun queue(info: NsdServiceInfo) {
        toResolve.addLast(info)
        resolveNext()
    }

    private fun resolveNext() {
        if (resolving) return
        val next = toResolve.removeFirstOrNull() ?: return
        resolving = true
        nsd.resolveService(next, object : NsdManager.ResolveListener {
            override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) = main.post {
                resolving = false
                resolveNext()
            }.let { }

            override fun onServiceResolved(info: NsdServiceInfo) = main.post {
                resolving = false
                val host = info.host?.hostAddress
                if (host != null && listener != null) {
                    val tv = FoundTv(info.serviceName, host)
                    _found.value = (_found.value.filterNot { it.name == tv.name } + tv).sortedBy { it.name }
                }
                resolveNext()
            }.let { }
        })
    }

    private companion object {
        const val SERVICE_TYPE = "_androidtvremote2._tcp."
    }
}

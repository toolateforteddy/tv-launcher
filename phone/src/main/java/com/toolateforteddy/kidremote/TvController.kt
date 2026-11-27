package com.toolateforteddy.kidremote

import com.toolateforteddy.kidremote.atv.NotPairedException
import com.toolateforteddy.kidremote.atv.Pairing
import com.toolateforteddy.kidremote.atv.PairingException
import com.toolateforteddy.kidremote.atv.RemoteSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

sealed interface TvStatus {
    data object NoTv : TvStatus
    data object Connecting : TvStatus
    data class Ready(val screenOn: Boolean) : TvStatus
    data object NotPaired : TvStatus
    data class Offline(val reason: String) : TvStatus
}

sealed interface PairState {
    data object Idle : PairState
    data class Starting(val host: String) : PairState
    /** The TV is showing a code. [error] is set after a wrong try. */
    data class WaitingForCode(val host: String, val error: String? = null, val checking: Boolean = false) : PairState
    data class Failed(val host: String, val reason: String) : PairState
}

/**
 * Keeps one connection to the TV while the app is open, so a tap goes out right away
 * instead of waiting on a fresh connection. All network work runs on one background thread.
 */
class TvController(private val store: Store) {
    private val worker = Executors.newSingleThreadScheduledExecutor { Thread(it, "tv-controller") }

    private val _status = MutableStateFlow<TvStatus>(if (store.tvHost == null) TvStatus.NoTv else TvStatus.Connecting)
    val status: StateFlow<TvStatus> = _status

    private val _pairState = MutableStateFlow<PairState>(PairState.Idle)
    val pairState: StateFlow<PairState> = _pairState

    private var session: RemoteSession? = null
    private var pairing: Pairing? = null
    private var reconnect: ScheduledFuture<*>? = null
    private var retryDelayMs = RETRY_MIN_MS
    @Volatile private var foreground = false

    /** The app is on screen: connect and stay connected. */
    fun start() {
        foreground = true
        worker.execute { ensureSession() }
    }

    /** The app left the screen: hang up so the phone isn't holding a connection in the background. */
    fun stop() {
        foreground = false
        worker.execute {
            reconnect?.cancel(false)
            session?.close()
            session = null
        }
    }

    fun sendKey(keyCode: Int, onResult: (Boolean) -> Unit = {}) = command(onResult) { it.sendKey(keyCode) }

    fun openLink(link: String, onResult: (Boolean) -> Unit = {}) = command(onResult) { it.openLink(link) }

    private fun command(onResult: (Boolean) -> Unit, action: (RemoteSession) -> Unit) = worker.execute {
        // One retry: the TV may have dropped an idle connection without us noticing yet.
        repeat(2) {
            val s = ensureSession() ?: return@execute onResult(false)
            try {
                action(s)
                return@execute onResult(true)
            } catch (e: IOException) {
                s.close()
                if (session === s) session = null
            }
        }
        onResult(false)
    }

    fun beginPairing(host: String) {
        val target = host.trim()
        if (target.isEmpty()) return
        _pairState.value = PairState.Starting(target)
        worker.execute {
            pairing?.close()
            session?.close()
            session = null
            try {
                val p = Pairing(target, store.identity(), clientName = "Kid Remote")
                pairing = p
                p.start()
                _pairState.value = PairState.WaitingForCode(target)
            } catch (e: IOException) {
                pairing?.close()
                pairing = null
                _pairState.value = PairState.Failed(target, describe(e, pairing = true))
            }
        }
    }

    fun submitCode(code: String) {
        val waiting = _pairState.value as? PairState.WaitingForCode ?: return
        _pairState.value = waiting.copy(checking = true, error = null)
        worker.execute {
            val p = pairing ?: return@execute
            try {
                p.finish(code)
                p.close()
                pairing = null
                store.tvHost = waiting.host
                _pairState.value = PairState.Idle
                retryDelayMs = RETRY_MIN_MS
                ensureSession()
            } catch (e: PairingException) {
                if (e.wrongCode) {
                    _pairState.value = waiting.copy(error = e.message, checking = false)
                } else {
                    p.close()
                    pairing = null
                    _pairState.value = PairState.Failed(waiting.host, e.message ?: "Pairing failed")
                }
            } catch (e: IOException) {
                p.close()
                pairing = null
                _pairState.value = PairState.Failed(waiting.host, describe(e, pairing = true))
            }
        }
    }

    fun cancelPairing() {
        _pairState.value = PairState.Idle
        worker.execute {
            pairing?.close()
            pairing = null
            ensureSession()
        }
    }

    fun forgetTv() = worker.execute {
        session?.close()
        session = null
        store.tvHost = null
        store.tvName = null
        _status.value = TvStatus.NoTv
    }

    /** Runs on [worker]. Returns a ready session, or null with [status] saying why. */
    private fun ensureSession(): RemoteSession? {
        session?.takeIf { it.isOpen }?.let { return it }
        if (pairing != null) return null
        val host = store.tvHost ?: run {
            _status.value = TvStatus.NoTv
            return null
        }
        reconnect?.cancel(false)
        _status.value = TvStatus.Connecting
        lateinit var created: RemoteSession
        created = RemoteSession(host, store.identity(), object : RemoteSession.Listener {
            override fun onReady(screenOn: Boolean) {
                _status.value = TvStatus.Ready(screenOn)
            }

            override fun onClosed(error: Throwable?) {
                worker.execute {
                    if (session !== created) return@execute
                    session = null
                    _status.value = TvStatus.Offline("Lost the connection to the TV")
                    scheduleReconnect()
                }
            }
        })
        return try {
            created.connect()
            session = created
            retryDelayMs = RETRY_MIN_MS
            created.tvName.takeIf { it.isNotBlank() }?.let { store.tvName = it }
            _status.value = TvStatus.Ready(created.screenOn)
            created
        } catch (e: NotPairedException) {
            _status.value = TvStatus.NotPaired
            null
        } catch (e: IOException) {
            _status.value = TvStatus.Offline(describe(e, pairing = false))
            scheduleReconnect()
            null
        }
    }

    private fun scheduleReconnect() {
        if (!foreground) return
        reconnect?.cancel(false)
        reconnect = worker.schedule({ ensureSession() }, retryDelayMs, TimeUnit.MILLISECONDS)
        retryDelayMs = (retryDelayMs * 2).coerceAtMost(RETRY_MAX_MS)
    }

    private fun describe(e: IOException, pairing: Boolean): String = when (e) {
        is PairingException -> e.message ?: "Pairing failed"
        is java.net.SocketTimeoutException, is java.net.NoRouteToHostException ->
            "Can't reach the TV. Is it on, and is this phone on the same Wi-Fi?"
        is java.net.ConnectException ->
            if (pairing) "The TV refused the connection. Check the address, and that the TV is on."
            else "The TV refused the connection. Is it on?"
        is java.net.UnknownHostException -> "That TV address doesn't look right."
        else -> e.message ?: "Can't reach the TV"
    }

    private companion object {
        const val RETRY_MIN_MS = 2000L
        const val RETRY_MAX_MS = 30000L
    }
}

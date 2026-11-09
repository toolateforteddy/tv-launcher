package com.toolateforteddy.tvlauncher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import org.json.JSONArray
import org.json.JSONObject
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Serves the kid remote page on the home network and carries out its button presses:
 * opening a show, pausing and playing.
 */
class RemoteService : Service() {
    private var server: HttpServer? = null
    private lateinit var shows: ShowStore

    override fun onCreate() {
        super.onCreate()
        shows = ShowStore(this)
        startInForeground()
        server = HttpServer(BuildConfig.REMOTE_PORT, ::handle).also {
            try {
                it.start()
            } catch (e: java.io.IOException) {
                stopSelf()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onDestroy() {
        server?.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun handle(request: HttpServer.Request): HttpServer.Response {
        val route = request.method + " " + request.path
        return when (route) {
            "GET /", "GET /index.html" -> asset("remote/index.html", "text/html; charset=utf-8")
            "GET /manifest.json" -> asset("remote/manifest.json", "application/manifest+json")
            "GET /api/shows" -> HttpServer.Response.json(shows.toJson())
            "PUT /api/shows" -> {
                val array = JSONArray(request.text)
                shows.save((0 until array.length()).map { Show.fromJson(array.getJSONObject(it)) })
                HttpServer.Response.json(shows.toJson())
            }
            "POST /api/play" -> {
                val show = shows.find(request.query["id"].orEmpty())
                    ?: return HttpServer.Response.text(404, "No such show")
                if (ShowLauncher.open(this, show)) HttpServer.Response.json("{}")
                else HttpServer.Response.text(409, "No app on the TV can open that link")
            }
            "POST /api/key" -> {
                val code = when (request.query["k"]) {
                    "playpause" -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                    "play" -> KeyEvent.KEYCODE_MEDIA_PLAY
                    "pause" -> KeyEvent.KEYCODE_MEDIA_PAUSE
                    else -> return HttpServer.Response.text(400, "Unknown key")
                }
                sendMediaKey(code)
                HttpServer.Response.json("{}")
            }
            "GET /api/lookup" -> HttpServer.Response.json(
                ShowLauncher.lookup(request.query["url"].orEmpty()).toString()
            )
            "GET /api/status" -> HttpServer.Response.json(
                JSONObject().put("canOpenShows", canOpenFromBackground()).toString()
            )
            else -> HttpServer.Response.text(404, "Not found")
        }
    }

    /** Goes to whatever app is playing, the same as the play/pause key on a real remote. */
    private fun sendMediaKey(code: Int) {
        val audio = getSystemService(AudioManager::class.java)
        val now = SystemClock.uptimeMillis()
        audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0))
        audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0))
    }

    /** Android 10+ only lets a background app open other apps if it has the overlay permission. */
    private fun canOpenFromBackground() =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || Settings.canDrawOverlays(this)

    private fun asset(name: String, type: String) =
        HttpServer.Response(200, type, assets.open(name).use { it.readBytes() })

    private fun startInForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.remote_channel), NotificationManager.IMPORTANCE_MIN)
        )
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_settings)
            .setContentTitle(getString(R.string.remote_channel))
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }
    }

    companion object {
        private const val CHANNEL = "kid_remote"

        fun start(context: Context) {
            context.startForegroundService(Intent(context, RemoteService::class.java))
        }

        /** The address to type on the phone, e.g. http://192.168.1.20:8765 */
        fun address(context: Context): String? {
            val ip = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
                ?.hostAddress ?: return null
            return "http://$ip:${BuildConfig.REMOTE_PORT}"
        }
    }
}

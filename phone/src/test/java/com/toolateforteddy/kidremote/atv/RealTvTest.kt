package com.toolateforteddy.kidremote.atv

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Pairs with and controls a real Android TV (or the Android TV emulator) over adb.
 * Skipped unless ATV_E2E=1. The CI screenshot job runs it against the emulator:
 *
 *   adb forward tcp:6466 tcp:6466 && adb forward tcp:6467 tcp:6467
 *   ATV_E2E=1 ./gradlew :phone:testDebugUnitTest --tests '*RealTvTest*'
 *
 * The pairing code is read off the TV screen with uiautomator, the way a person would read it.
 */
class RealTvTest {
    private val host = System.getenv("ATV_HOST") ?: "127.0.0.1"
    private val shots = File(System.getenv("ATV_SHOTS") ?: "build/atv-shots").apply { mkdirs() }

    @Test fun pairAndControl() {
        assumeTrue(System.getenv("ATV_E2E") == "1")
        System.setProperty("javax.net.debug", "ssl:handshake")
        val phone = ClientIdentity.generate()

        Pairing(host, phone, clientName = "Kid Remote").use { pairing ->
            pairing.start()
            val code = readCodeFromScreen()
            screenshot("06-tv-pairing-code")
            println("TV shows code $code")
            pairing.finish(code)
        }
        screenshot("06b-tv-after-pairing")

        val session = connectWithRetries(phone)
        println("Connected to '${session.tvName}', screen on: ${session.screenOn}")

        session.sendKey(RemoteSession.KEY_HOME)
        Thread.sleep(3000)
        session.openLink("https://www.youtube.com/watch?v=jNQXAC9IVRw")
        Thread.sleep(8000)
        screenshot("07-phone-remote-opened-link")
        val top = adb("shell", "dumpsys", "activity", "activities")
            .lines().firstOrNull { "mResumedActivity" in it || "ResumedActivity" in it }.orEmpty()
        println("Resumed after link: $top")
        session.sendKey(RemoteSession.KEY_PLAY_PAUSE)
        session.sendKey(RemoteSession.KEY_HOME)
        Thread.sleep(1000)
        session.close()
        assertTrue("TV didn't open the link: $top", "youtube" in top.lowercase() || "browser" in top.lowercase() || "chrome" in top.lowercase())
    }

    /** Logs each failure, so CI shows what the TV did if the first try doesn't work. */
    private fun connectWithRetries(phone: ClientIdentity): RemoteSession {
        var last: Exception? = null
        for ((delayMs, protocols) in listOf(0L to null, 3000L to null, 3000L to arrayOf("TLSv1.2"), 5000L to null)) {
            Thread.sleep(delayMs)
            val session = RemoteSession(host, phone, object : RemoteSession.Listener {}, tlsProtocols = protocols)
            try {
                session.connect()
                println("Connected after ${delayMs}ms wait, protocols=${protocols?.toList() ?: "default"}")
                return session
            } catch (e: Exception) {
                println("Connect attempt failed (protocols=${protocols?.toList() ?: "default"}): $e / cause ${e.cause}")
                last = e
            }
        }
        throw last!!
    }

    private fun readCodeFromScreen(): String {
        repeat(15) {
            Thread.sleep(1000)
            val xml = adb("exec-out", "uiautomator", "dump", "/dev/tty")
            Regex("""text="([0-9A-F]{6})"""").find(xml)?.let { return it.groupValues[1] }
            // Some versions split the code into one view per character.
            val chars = Regex("""text="([0-9A-F])"""").findAll(xml).map { it.groupValues[1] }.joinToString("")
            if (chars.length == 6) return chars
        }
        screenshot("06-tv-pairing-code-missing")
        error("No pairing code found on the TV screen")
    }

    private fun screenshot(name: String) {
        val p = ProcessBuilder("adb", "exec-out", "screencap", "-p")
            .redirectOutput(File(shots, "$name.png")).start()
        p.waitFor(20, TimeUnit.SECONDS)
    }

    private fun adb(vararg args: String): String {
        val p = ProcessBuilder(listOf("adb") + args).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor(30, TimeUnit.SECONDS)
        return out
    }
}

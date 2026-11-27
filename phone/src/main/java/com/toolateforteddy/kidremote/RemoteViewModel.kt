package com.toolateforteddy.kidremote

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class RemoteViewModel(app: Application) : AndroidViewModel(app) {
    val store = Store(app)
    val tv = TvController(store)
    val finder = TvFinder(app)

    val shows: StateFlow<List<Show>> = store.shows

    /** A short message for the grown-ups, e.g. when a tap didn't reach the TV. */
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice
    private var noticeJob: Job? = null

    fun openShow(show: Show) = tv.openLink(show.link) { ok -> if (!ok) notify("Couldn't reach the TV") }

    fun playPause() = tv.sendKey(com.toolateforteddy.kidremote.atv.RemoteSession.KEY_PLAY_PAUSE) { ok ->
        if (!ok) notify("Couldn't reach the TV")
    }

    fun key(code: Int) = tv.sendKey(code) { ok -> if (!ok) notify("Couldn't reach the TV") }

    fun tryLink(link: String) = tv.openLink(link) { ok -> notify(if (ok) "Sent to the TV" else "Couldn't reach the TV") }

    fun notify(message: String) {
        noticeJob?.cancel()
        _notice.value = message
        noticeJob = viewModelScope.launch {
            delay(3500)
            _notice.value = null
        }
    }

    fun saveShow(id: String?, name: String, link: String, image: String) {
        val current = shows.value
        val show = Show(id ?: UUID.randomUUID().toString(), name.trim(), link.trim(), image.trim())
        store.saveShows(
            if (id != null && current.any { it.id == id }) current.map { if (it.id == id) show else it }
            else current + show,
        )
    }

    fun deleteShow(id: String) = store.saveShows(shows.value.filterNot { it.id == id })

    fun moveUp(id: String) {
        val list = shows.value.toMutableList()
        val i = list.indexOfFirst { it.id == id }
        if (i > 0) {
            list.add(i - 1, list.removeAt(i))
            store.saveShows(list)
        }
    }

    suspend fun lookup(link: String): Pair<String?, String?> = withContext(Dispatchers.IO) {
        runCatching { Pictures.lookup(link) }.getOrDefault(null to null)
    }

    suspend fun savePicked(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching { Pictures.savePicked(getApplication(), uri) }.getOrNull()
    }

    override fun onCleared() {
        tv.stop()
        finder.stop()
    }
}

/** Share text from apps often wraps the link ("Watch Tumble Leaf on Prime Video: https://..."). */
fun extractLink(text: String): String {
    val trimmed = text.trim()
    return Regex("""(https?|market)://\S+""").find(trimmed)?.value?.trimEnd('.', ',', ')', '"') ?: trimmed
}

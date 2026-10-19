package com.toolateforteddy.tvlauncher

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private lateinit var grid: RecyclerView
    private lateinit var adapter: TileAdapter
    private lateinit var favorites: Favorites
    private lateinit var loader: TileLoader

    private val background = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    private var apps: List<Tile> = emptyList()
    private var inputs: List<Tile> = emptyList()
    private var needsReload = true

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            needsReload = true
            if (hasWindowFocus()) reload()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        favorites = Favorites(this)
        loader = TileLoader(this)

        adapter = TileAdapter(onClick = ::launch, onLongClick = ::showOptions)
        grid = findViewById(R.id.grid)
        grid.adapter = adapter
        grid.itemAnimator = null
        grid.layoutManager = GridLayoutManager(this, COLUMNS).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int) =
                    if (adapter.rows.getOrNull(position) is Header) COLUMNS else 1
            }
        }

        registerReceiver(packageReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        })
    }

    override fun onResume() {
        super.onResume()
        if (needsReload) reload()
    }

    override fun onDestroy() {
        unregisterReceiver(packageReceiver)
        background.shutdown()
        super.onDestroy()
    }

    /** Pressing Home while already here jumps back to the top. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) {
            grid.scrollToPosition(0)
            focusFirstTile()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // As the real home screen there is nowhere to go back to.
        if (keyCode == KeyEvent.KEYCODE_BACK && BuildConfig.FLAVOR == "home") return true
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            (grid.focusedChild?.let { grid.getChildAdapterPosition(it) })
                ?.let { adapter.rows.getOrNull(it) as? Tile }
                ?.let { showOptions(it); return true }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun reload() {
        needsReload = false
        background.execute {
            val newApps = loader.apps()
            val newInputs = loader.inputs()
            main.post {
                apps = newApps
                inputs = newInputs
                val first = adapter.rows.isEmpty()
                render()
                if (first) focusFirstTile()
            }
        }
    }

    private fun render() {
        val all = (apps + inputs).associateBy { it.key }
        val favoriteTiles = favorites.keys().mapNotNull { all[it] }
        val settings = Tile(
            key = "settings",
            label = getString(R.string.settings),
            banner = null,
            icon = getDrawable(R.drawable.ic_settings),
            intent = Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            isApp = false,
        )

        val rows = mutableListOf<Row>()
        if (favoriteTiles.isNotEmpty()) {
            rows += Header(getString(R.string.section_favorites))
            rows += favoriteTiles
        }
        rows += Header(getString(R.string.section_apps))
        rows += apps
        rows += Header(getString(R.string.section_tv))
        rows += inputs
        rows += settings
        adapter.rows = rows
    }

    private fun focusFirstTile() {
        grid.post {
            val index = adapter.rows.indexOfFirst { it is Tile }
            grid.findViewHolderForAdapterPosition(index)?.itemView?.requestFocus()
        }
    }

    private fun launch(tile: Tile) {
        try {
            startActivity(tile.intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, getString(R.string.cannot_open, tile.label), Toast.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            Toast.makeText(this, getString(R.string.cannot_open, tile.label), Toast.LENGTH_SHORT).show()
        }
    }

    private fun showOptions(tile: Tile) {
        if (tile.key == "settings") return
        val isFavorite = favorites.contains(tile.key)
        val options = mutableListOf<Pair<String, () -> Unit>>()
        if (isFavorite) {
            options += getString(R.string.move_left) to { favorites.move(tile.key, -1) }
            options += getString(R.string.move_right) to { favorites.move(tile.key, 1) }
            options += getString(R.string.remove_favorite) to { favorites.remove(tile.key) }
        } else {
            options += getString(R.string.add_favorite) to { favorites.add(tile.key) }
        }
        if (tile.isApp) {
            options += getString(R.string.app_info) to {
                launch(tile.copy(intent = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", tile.key, null),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)))
            }
        }
        AlertDialog.Builder(this)
            .setTitle(tile.label)
            .setItems(options.map { it.first }.toTypedArray()) { _, which ->
                options[which].second()
                render()
            }
            .show()
    }

    private companion object {
        const val COLUMNS = 5
    }
}

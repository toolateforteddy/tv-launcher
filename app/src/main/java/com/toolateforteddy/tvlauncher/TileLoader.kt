package com.toolateforteddy.tvlauncher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager

/** Reads installed apps and TV inputs. Slow on TV hardware, so call it off the main thread. */
class TileLoader(private val context: Context) {
    private val pm: PackageManager = context.packageManager

    fun apps(): List<Tile> {
        val seen = mutableSetOf<String>()
        val tiles = mutableListOf<Tile>()
        // TV apps first, then phone-style apps that have no TV entry point.
        for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val query = Intent(Intent.ACTION_MAIN).addCategory(category)
            for (info in pm.queryIntentActivities(query, 0)) {
                val activity = info.activityInfo
                val pkg = activity.packageName
                if (pkg == context.packageName || !seen.add(pkg)) continue
                val intent = Intent(Intent.ACTION_MAIN)
                    .addCategory(category)
                    .setClassName(pkg, activity.name)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                tiles += Tile(
                    key = pkg,
                    label = info.loadLabel(pm).toString(),
                    banner = activity.loadBanner(pm) ?: activity.applicationInfo.loadBanner(pm),
                    icon = info.loadIcon(pm),
                    intent = intent,
                    isApp = true,
                )
            }
        }
        return tiles.sortedBy { it.label.lowercase() }
    }

    /** HDMI and other physical inputs, as the TV's own input picker would show them. */
    fun inputs(): List<Tile> {
        if (!pm.hasSystemFeature(PackageManager.FEATURE_LIVE_TV)) return emptyList()
        val manager = context.getSystemService(TvInputManager::class.java) ?: return emptyList()
        return try {
            manager.tvInputList
                .filter { it.isPassthroughInput && it.parentId == null && !it.isHidden(context) }
                .sortedWith(compareBy({ it.type != TvInputInfo.TYPE_HDMI }, { it.id }))
                .map { info ->
                    val label = info.loadCustomLabel(context)?.toString()?.takeIf { it.isNotBlank() }
                        ?: info.loadLabel(context).toString()
                    Tile(
                        key = "input:" + info.id,
                        label = label,
                        banner = null,
                        icon = info.loadIcon(context),
                        intent = Intent(
                            Intent.ACTION_VIEW,
                            TvContract.buildChannelUriForPassthroughInput(info.id),
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        isApp = false,
                    )
                }
        } catch (e: RuntimeException) {
            // Some firmware restricts the input list; the launcher still works without it.
            emptyList()
        }
    }
}

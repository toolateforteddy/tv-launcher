package com.toolateforteddy.tvlauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Starts the kid remote when the TV turns on, so the phone page works without opening anything. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) RemoteService.start(context)
    }
}

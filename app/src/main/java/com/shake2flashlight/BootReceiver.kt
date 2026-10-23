package com.shake2flashlight

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = Prefs.get(context)
        if (prefs.getBoolean(Prefs.START_ON_BOOT, false) && prefs.getBoolean(Prefs.ENABLED, false)) {
            context.startForegroundService(Intent(context, ChopService::class.java))
        }
    }
}

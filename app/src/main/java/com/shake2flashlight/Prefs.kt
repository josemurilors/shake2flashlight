package com.shake2flashlight

import android.content.Context
import android.content.SharedPreferences

/** User settings, shared by the UI, the service, the boot receiver and the tile. */
object Prefs {
    const val ENABLED = "enabled"             // Boolean, default false
    const val SENSITIVITY = "sensitivity"     // Int 0 low, 1 medium, 2 high; default 1
    const val VIBRATE = "vibrate"             // Boolean, default true
    const val POCKET = "pocket"               // Boolean, default true
    const val AUTO_OFF_MIN = "auto_off_min"   // Int minutes, 0 = never; default 0
    const val START_ON_BOOT = "start_on_boot" // Boolean, default false

    fun get(context: Context): SharedPreferences =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
}

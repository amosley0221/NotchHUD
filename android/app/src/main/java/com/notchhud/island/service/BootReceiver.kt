package com.notchhud.island.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * Brings the island back after a reboot and after the app updates itself.
 * MY_PACKAGE_REPLACED is the one that matters for in-place updates: without it the
 * overlay would simply be gone until the user next opened the app.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                if (!Settings.canDrawOverlays(context)) return
                IslandOverlayService.start(context)
            }
        }
    }
}

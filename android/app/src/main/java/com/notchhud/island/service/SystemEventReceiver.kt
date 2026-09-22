package com.notchhud.island.service

import android.app.KeyguardManager
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.BatteryManager
import androidx.compose.ui.graphics.Color
import com.notchhud.island.core.IslandState
import com.notchhud.island.core.Tokens

/**
 * Charging, ringer, Bluetooth audio and lock/unlock — the "system" module. These
 * are all broadcasts, so one receiver covers them; the overlay service registers
 * and unregisters it with its own lifetime.
 */
class SystemEventReceiver : BroadcastReceiver() {

    companion object {
        fun filter() = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val theme = ServiceRuntime.current.theme
        when (intent.action) {
            Intent.ACTION_POWER_CONNECTED -> {
                val pct = batteryPercent(context)
                NotificationRouter.systemTransient(
                    "Charging", "$pct%", theme?.done ?: Tokens.Messages, meter = pct / 100f
                )
            }
            Intent.ACTION_POWER_DISCONNECTED -> {
                val pct = batteryPercent(context)
                NotificationRouter.systemTransient(
                    "On battery", "$pct%", Tokens.TextQuaternary, meter = pct / 100f
                )
            }
            AudioManager.RINGER_MODE_CHANGED_ACTION -> {
                val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val silent = am.ringerMode != AudioManager.RINGER_MODE_NORMAL
                NotificationRouter.systemTransient(
                    if (silent) "Silent on" else "Silent off",
                    null,
                    if (silent) (theme?.error ?: Color(0xFFFF5A5A)) else (theme?.done ?: Color(0xFF34D27A)),
                )
            }
            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                val name = deviceName(intent) ?: return
                NotificationRouter.systemTransient("$name connected", null, theme?.done ?: Tokens.Messages, durationMs = 1800L)
            }
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                val name = deviceName(intent) ?: return
                NotificationRouter.systemTransient("$name disconnected", null, Tokens.TextQuaternary, durationMs = 1400L)
            }
            Intent.ACTION_USER_PRESENT -> {
                // Fast path only — IslandOverlayService.startLockMonitor reconciles
                // the keyguard state regardless, because this broadcast does not
                // arrive on every unlock flow.
                if (ServiceRuntime.current.unlockAnimation) IslandState.setUnlocking(true)
                IslandState.setLocked(false)
            }
            Intent.ACTION_SCREEN_OFF -> {
                IslandState.setUnlocking(false)
                IslandState.setLocked(true)
            }
            Intent.ACTION_SCREEN_ON -> {
                val km = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                IslandState.setLocked(km.isKeyguardLocked)
            }
        }
    }

    @Suppress("DEPRECATION", "MissingPermission")
    private fun deviceName(intent: Intent): String? = runCatching {
        val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
        device?.name
    }.getOrNull()

    private fun batteryPercent(context: Context): Int {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }
}

package com.notchhud.island.service

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Light confirm tick on every island event; silent when the user turns haptics off. */
class Haptics(context: Context) {

    private val vibrator: Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }.getOrNull()

    fun tick() = play(VibrationEffect.EFFECT_TICK, 12L)
    fun confirm() = play(VibrationEffect.EFFECT_CLICK, 20L)

    private fun play(predefined: Int, fallbackMs: Long) {
        if (ServiceRuntime.settings?.haptics == false) return
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        runCatching {
            v.vibrate(VibrationEffect.createPredefined(predefined))
        }.onFailure {
            runCatching { v.vibrate(VibrationEffect.createOneShot(fallbackMs, VibrationEffect.DEFAULT_AMPLITUDE)) }
        }
    }
}

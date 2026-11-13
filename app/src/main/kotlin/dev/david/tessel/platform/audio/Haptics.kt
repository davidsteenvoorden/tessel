package dev.david.tessel.platform.audio

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Short, sparse feedback. Shots are rate-limited so holding the trigger is a
 * light texture rather than a constant buzz.
 */
class Haptics(context: Context) {
    private val vibrator: Vibrator? = run {
        val v = if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
        v?.takeIf { it.hasVibrator() }
    }

    private val tick = effect(VibrationEffect.EFFECT_TICK, 8)
    private val click = effect(VibrationEffect.EFFECT_CLICK, 20)
    private val heavy = effect(VibrationEffect.EFFECT_HEAVY_CLICK, 45)
    private var lastShotAt = 0L

    @Volatile var enabled = true
    @Volatile var shots = true

    fun shot(nowMillis: Long) {
        if (!shots || nowMillis - lastShotAt < SHOT_GAP_MS) return
        lastShotAt = nowMillis
        play(tick)
    }

    fun hurt() = play(click)

    fun down() = play(heavy)

    private fun play(effect: VibrationEffect?) {
        if (enabled && effect != null) vibrator?.vibrate(effect)
    }

    private fun effect(predefined: Int, fallbackMillis: Long): VibrationEffect? = when {
        vibrator == null -> null
        Build.VERSION.SDK_INT >= 29 -> VibrationEffect.createPredefined(predefined)
        else -> VibrationEffect.createOneShot(fallbackMillis, VibrationEffect.DEFAULT_AMPLITUDE)
    }

    private companion object {
        const val SHOT_GAP_MS = 100L
    }
}

package com.antigravity.deck.hardware

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

// ステータス変化と操作時のカスタムハプティクス通知制御
class HapticsController(appContext: Context) {

    private val vibrator: Vibrator? = resolveVibrator(appContext)

    private fun resolveVibrator(context: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    // waiting_approval遷移時: 短い2回振動
    fun triggerWaitingApproval() {
        val pattern = longArrayOf(0L, 75L, 85L, 75L)
        val levels = intArrayOf(0, 230, 0, 230)
        emitWaveform(pattern, levels)
    }

    // done遷移時: 長い1回振動
    fun triggerDone() {
        emitSinglePulse(durationMs = 450L, strength = 210)
    }

    // error遷移時: 連続振動
    fun triggerError() {
        val pattern = longArrayOf(0L, 120L, 60L, 120L, 60L, 190L)
        val levels = intArrayOf(0, 255, 0, 255, 0, 255)
        emitWaveform(pattern, levels)
    }

    // 推論レベル変更など軽いフィードバック用
    fun triggerSelectionClick() {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
        } else {
            emitSinglePulse(durationMs = 20L, strength = 120)
        }
    }

    private fun emitSinglePulse(durationMs: Long, strength: Int) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        v.vibrate(VibrationEffect.createOneShot(durationMs, strength))
    }

    private fun emitWaveform(timings: LongArray, amplitudes: IntArray) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        val effect = if (v.hasAmplitudeControl()) {
            VibrationEffect.createWaveform(timings, amplitudes, -1)
        } else {
            VibrationEffect.createWaveform(timings, -1)
        }
        v.vibrate(effect)
    }
}

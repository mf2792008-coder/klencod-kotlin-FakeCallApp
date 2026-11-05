package com.fakecall.app

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import java.io.File

/**
 * كل الصوت في التطبيق:
 *  - الرنة: على قناة المنبّه بأعلى صوت، ومن مكبر الموبايل دايمًا (مش البلوتوث).
 *  - المكالمة: مستوى صوت المكالمة لآخره. المخرج الافتراضي سماعة الأذن.
 *    مكبر الصوت يحوّل الصوت للسماعة الخارجية، والبلوتوث ما بيشتغل غير لما يتفعّل من زرار التطبيق.
 */
class CallAudio(private val c: Context) {

    private val am = c.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    @Suppress("DEPRECATION")
    private val vib = c.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator?

    private var ring: MediaPlayer? = null
    private var voice: MediaPlayer? = null

    private var muted = false
    private var speakerOn = false
    private var btOn = false
    private var voiceDone = false

    /** مستويات الصوت الأصلية قبل ما نرفعها، عشان نرجعها بعد المكالمة. */
    private val saved = HashMap<Int, Int>()

    // ───────── مستوى الصوت ─────────

    private fun boost(stream: Int) {
        try {
            if (!saved.containsKey(stream)) saved[stream] = am.getStreamVolume(stream)
            am.setStreamVolume(stream, am.getStreamMaxVolume(stream), 0)
        } catch (_: Exception) {
        }
    }

    private fun restore(stream: Int) {
        val v = saved.remove(stream) ?: return
        try {
            am.setStreamVolume(stream, v, 0)
        } catch (_: Exception) {
        }
    }

    // ───────── الأجهزة ─────────

    private fun out(vararg types: Int): AudioDeviceInfo? {
        val devs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        for (t in types) {
            val d = devs.firstOrNull { it.type == t }
            if (d != null) return d
        }
        return null
    }

    private fun btTypes(): IntArray =
        if (Build.VERSION.SDK_INT >= 31) {
            intArrayOf(
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_BLE_HEADSET
            )
        } else {
            intArrayOf(AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
        }

    private fun btDevice(): AudioDeviceInfo? = out(*btTypes())

    /** اختيار مخرج الصوت: مكبر ← بلوتوث (لو مفعّل من التطبيق) ← سماعة الأذن. */
    @Suppress("DEPRECATION")
    private fun applyRoute() {
        val target: AudioDeviceInfo? = when {
            speakerOn -> out(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)
            btOn -> btDevice()
            else -> out(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE) ?: out(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)
        }

        if (Build.VERSION.SDK_INT >= 31) {
            val wanted: IntArray = when {
                speakerOn -> intArrayOf(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)
                btOn -> btTypes()
                else -> intArrayOf(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE)
            }
            val dev = am.availableCommunicationDevices.firstOrNull { wanted.contains(it.type) }
            try {
                if (dev != null) am.setCommunicationDevice(dev) else am.clearCommunicationDevice()
            } catch (_: Exception) {
            }
        } else {
            try {
                am.isSpeakerphoneOn = speakerOn
                if (btOn && !speakerOn) {
                    am.startBluetoothSco()
                    am.isBluetoothScoOn = true
                } else {
                    am.isBluetoothScoOn = false
                    am.stopBluetoothSco()
                }
            } catch (_: Exception) {
            }
        }

        try {
            voice?.setPreferredDevice(target)
        } catch (_: Exception) {
        }
    }

    // ───────── الرنة ─────────

    /** بترجّع false لو النغمة ماتشغلتش. profileId لاختيار نغمة البروفايل. */
    fun startRing(vibrate: Boolean, profileId: String = ""): Boolean {
        stopRing()
        boost(AudioManager.STREAM_ALARM)
        var ok = true
        try {
            val mp = MediaPlayer()
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            val custom = if (profileId.isNotEmpty()) Store.effectiveRingFile(c, profileId)
                         else Store.ringFile(c).takeIf { it.exists() }
            if (custom != null) {
                mp.setDataSource(custom.absolutePath)
            } else {
                c.resources.openRawResourceFd(R.raw.sound2).use {
                    mp.setDataSource(it.fileDescriptor, it.startOffset, it.length)
                }
            }
            mp.isLooping = true
            mp.setPreferredDevice(out(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER))
            mp.setVolume(1f, 1f)
            mp.prepare()
            mp.start()
            ring = mp
        } catch (_: Exception) {
            stopRing()
            ok = false
        }
        if (vibrate) startVibrate()
        return ok
    }

    fun stopRing() {
        ring?.let {
            try { it.stop() } catch (_: Exception) {}
            try { it.release() } catch (_: Exception) {}
        }
        ring = null
        try { vib?.cancel() } catch (_: Exception) {}
        restore(AudioManager.STREAM_ALARM)
    }

    @Suppress("DEPRECATION")
    private fun startVibrate() {
        val v = vib ?: return
        if (!v.hasVibrator()) return
        val pattern = longArrayOf(0, 1000, 1000)
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(
                    VibrationEffect.createWaveform(pattern, 0),
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
                )
            } else {
                v.vibrate(pattern, 0)
            }
        } catch (_: Exception) {
        }
    }

    // ───────── المكالمة ─────────

    fun onAnswered(voiceFile: File) {
        stopRing()
        muted = false
        speakerOn = false
        btOn = false
        try {
            am.mode = AudioManager.MODE_IN_COMMUNICATION
        } catch (_: Exception) {
        }
        boost(AudioManager.STREAM_VOICE_CALL)
        startVoice(voiceFile)
        applyRoute()
        try { voice?.start() } catch (_: Exception) {}
    }

    private fun startVoice(f: File) {
        stopVoice()
        if (!f.exists()) {
            applyRoute()
            return
        }
        voiceDone = false
        try {
            val mp = MediaPlayer()
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            mp.setDataSource(f.absolutePath)
            mp.setOnCompletionListener { voiceDone = true }
            mp.prepare()
            mp.setVolume(1f, 1f)
            voice = mp
        } catch (_: Exception) {
            stopVoice()
        }
    }

    private fun stopVoice() {
        voice?.let {
            try { it.stop() } catch (_: Exception) {}
            try { it.release() } catch (_: Exception) {}
        }
        voice = null
    }

    fun setSpeaker(on: Boolean) {
        speakerOn = on
        boost(AudioManager.STREAM_VOICE_CALL)
        applyRoute()
        if (!muted) {
            try { voice?.setVolume(1f, 1f) } catch (_: Exception) {}
        }
    }

    /** بترجّع false لو مفيش جهاز بلوتوث متصل. */
    fun setBluetooth(on: Boolean): Boolean {
        if (on && btDevice() == null) {
            if (Build.VERSION.SDK_INT >= 31) {
                if (am.availableCommunicationDevices.none { btTypes().contains(it.type) }) return false
            } else {
                return false
            }
        }
        btOn = on
        applyRoute()
        return true
    }

    fun setMuted(m: Boolean) {
        muted = m
        val v = if (m) 0f else 1f
        try { voice?.setVolume(v, v) } catch (_: Exception) {}
    }

    fun setHold(h: Boolean) {
        try {
            if (h) voice?.pause() else if (!voiceDone) voice?.start()
        } catch (_: Exception) {
        }
    }

    /** إنهاء كامل: بيوقف كل حاجة ويرجّع إعدادات الصوت لأصلها. */
    @Suppress("DEPRECATION")
    fun end() {
        stopRing()
        stopVoice()
        speakerOn = false
        btOn = false
        muted = false
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                am.clearCommunicationDevice()
            } else {
                am.isSpeakerphoneOn = false
                am.isBluetoothScoOn = false
                am.stopBluetoothSco()
            }
            am.mode = AudioManager.MODE_NORMAL
        } catch (_: Exception) {
        }
        restore(AudioManager.STREAM_VOICE_CALL)
    }

    /** نبضة اهتزاز قصيرة (تأكيد الضغط المطول). */
    @Suppress("DEPRECATION")
    fun tick() {
        val v = vib ?: return
        if (!v.hasVibrator()) return
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                v.vibrate(30)
            }
        } catch (_: Exception) {
        }
    }
}

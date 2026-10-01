package com.shiostudios.dumplingrings.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager as SysAudioManager
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.shiostudios.dumplingrings.core.systems.Settings

/**
 * Music (looping MediaPlayer with cross-fade on track change), ambience (second looping player), SFX (SoundPool with
 * per-sound cooldown and volume limiting so rapid rotation ticks never pile up) and light haptics.
 * Handles audio focus and app background/foreground interruptions.
 */
class GameAudio(private val context: Context) {
    private val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
    private val pool = SoundPool.Builder().setMaxStreams(8).setAudioAttributes(attrs).build()
    private val ids = HashMap<String, Int>()
    private val lastPlayed = HashMap<String, Long>()
    private var music: MediaPlayer? = null
    private var ambience: MediaPlayer? = null
    private var currentMusic: String? = null
    private var currentAmbience: String? = null
    var settings: Settings = Settings()
        set(v) { field = v; applyVolumes() }
    private var inForeground = true
    private val sys = context.getSystemService(Context.AUDIO_SERVICE) as SysAudioManager
    private var focusRequest: AudioFocusRequest? = null
    private var ducked = false

    private val sfxNames = listOf(
        "dough_tap", "ring_select", "rotate_tick_1", "rotate_tick_2", "rotate_tick_3", "snap", "ring_release_pop", "dumpling_jump",
        "invalid", "combo_1", "combo_2", "combo_3", "coin_chime", "star_1", "star_2", "star_3", "level_complete_bell", "chapter_bell",
        "button_tap", "button_back", "booster_hint", "booster_twist", "booster_golden", "undo", "lock_click", "gate_open", "unlock",
    )

    init {
        for (n in sfxNames) {
            try {
                context.assets.openFd("audio/sfx/sfx_$n.ogg").use { fd -> ids[n] = pool.load(fd, 1) }
            } catch (e: Exception) { Log.w("GameAudio", "missing sfx $n") }
        }
    }

    fun sfx(name: String, volume: Float = 1f, rate: Float = 1f, cooldownMs: Long = 40) {
        if (!settings.sfx || !inForeground) return
        val id = ids[name] ?: return
        val now = System.currentTimeMillis()
        if (now - (lastPlayed[name] ?: 0) < cooldownMs) return
        lastPlayed[name] = now
        val v = (volume * settings.sfxVolume).coerceIn(0f, 1f)
        pool.play(id, v, v, 1, 0, rate)
    }

    fun tick(step: Int) = sfx("rotate_tick_${1 + (step % 3 + 3) % 3}", 0.55f, 1f, 55)

    fun playMusic(track: String?) {
        if (track == currentMusic) { if (settings.music) music?.let { if (!it.isPlaying && inForeground) it.start() }; return }
        currentMusic = track
        music?.release(); music = null
        if (track == null) return
        music = newLooping("audio/music/$track.ogg", settings.musicVolume * 0.85f * (if (settings.music) 1f else 0f))
        requestFocus()
    }

    fun playAmbience(track: String?) {
        if (track == currentAmbience) return
        currentAmbience = track
        ambience?.release(); ambience = null
        if (track == null) return
        ambience = newLooping("audio/ambience/$track.ogg", settings.musicVolume * 0.35f * (if (settings.music) 1f else 0f))
    }

    private fun newLooping(path: String, volume: Float): MediaPlayer? = try {
        MediaPlayer().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            context.assets.openFd(path).use { fd -> setDataSource(fd.fileDescriptor, fd.startOffset, fd.length) }
            isLooping = true
            setVolume(volume, volume)
            prepare()
            if (inForeground) start()
        }
    } catch (e: Exception) { Log.w("GameAudio", "music $path: ${e.message}"); null }

    private fun applyVolumes() {
        val m = if (settings.music) settings.musicVolume else 0f
        val d = if (ducked) 0.3f else 1f
        music?.setVolume(m * 0.85f * d, m * 0.85f * d)
        ambience?.setVolume(m * 0.35f * d, m * 0.35f * d)
    }

    fun onPause() {
        inForeground = false
        runCatching { music?.pause(); ambience?.pause() }
        pool.autoPause()
    }

    fun onResume() {
        inForeground = true
        if (settings.music) runCatching { music?.start(); ambience?.start() }
        pool.autoResume()
    }

    private fun requestFocus() {
        if (focusRequest != null) return
        if (Build.VERSION.SDK_INT >= 26) {
            val req = AudioFocusRequest.Builder(SysAudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(attrs)
                .setOnAudioFocusChangeListener { change ->
                    when (change) {
                        SysAudioManager.AUDIOFOCUS_LOSS, SysAudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> { ducked = false; runCatching { music?.pause(); ambience?.pause() } }
                        SysAudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> { ducked = true; applyVolumes() }
                        SysAudioManager.AUDIOFOCUS_GAIN -> { ducked = false; applyVolumes(); if (inForeground && settings.music) runCatching { music?.start(); ambience?.start() } }
                    }
                }.build()
            focusRequest = req
            sys.requestAudioFocus(req)
        }
    }

    fun release() { pool.release(); music?.release(); ambience?.release() }
}

class Haptics(context: Context) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= 31)
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    else @Suppress("DEPRECATION") context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    var enabled = true

    fun tick() = pulse(8, 40)
    fun snap() = pulse(14, 90)
    fun release() = pulse(30, 180)
    fun error() = pulse(20, 60)

    private fun pulse(ms: Long, amplitude: Int) {
        if (!enabled) return
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        runCatching {
            if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(ms, if (v.hasAmplitudeControl()) amplitude else VibrationEffect.DEFAULT_AMPLITUDE))
            else @Suppress("DEPRECATION") v.vibrate(ms)
        }
    }
}

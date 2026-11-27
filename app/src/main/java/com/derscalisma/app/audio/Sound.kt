package com.derscalisma.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.SystemClock
import com.derscalisma.app.R

/** Ses efektleri (Kenney, CC0). */
enum class Sfx(val res: Int, val minGapMs: Long = 60) {
    CLICK(R.raw.sfx_click),
    OPEN(R.raw.sfx_open),
    CONFIRM(R.raw.sfx_confirm),
    ERROR(R.raw.sfx_error),
    COIN(R.raw.sfx_coin, 120),
    COINS_BIG(R.raw.sfx_coins_big),
    HAMMER(R.raw.sfx_hammer, 90),
    HAMMER2(R.raw.sfx_hammer2, 90),
    TRAIN(R.raw.sfx_train),
    DEPLOY(R.raw.sfx_deploy, 70),
    SWORD(R.raw.sfx_sword, 70),
    HIT_METAL(R.raw.sfx_hit_metal, 70),
    ARROW(R.raw.sfx_arrow, 80),
    CANNON(R.raw.sfx_cannon, 120),
    EXPLOSION(R.raw.sfx_explosion, 120),
    MAGIC(R.raw.sfx_magic, 90),
    SPELL(R.raw.sfx_spell),
    LIGHTNING(R.raw.sfx_lightning),
    HEAL(R.raw.sfx_heal, 250),
    PUNCH(R.raw.sfx_punch, 80),
    BOOK_OPEN(R.raw.sfx_book_open),
    BOOK_CLOSE(R.raw.sfx_book_close),
    BELL(R.raw.sfx_bell),
    CHEST_CREAK(R.raw.sfx_chest_creak),
}

/** Bu uygulama için bestelenip koddan sentezlenen müzikler (tools/music/compose.py). */
enum class Music(val res: Int, val loop: Boolean) {
    VILLAGE(R.raw.music_village, true),
    BATTLE(R.raw.music_battle, true),
}

enum class Jingle(val res: Int) {
    VICTORY(R.raw.jingle_victory),
    DEFEAT(R.raw.jingle_defeat),
    CHEST(R.raw.jingle_chest),
    BUILD(R.raw.jingle_build),
}

object Sound {
    private var ctx: Context? = null
    private var pool: SoundPool? = null
    private val ids = HashMap<Sfx, Int>()
    private val lastPlayed = HashMap<Sfx, Long>()
    private var music: MediaPlayer? = null
    private var musicTrack: Music? = null
    private var wanted: Music? = null
    private var jinglePlayer: MediaPlayer? = null
    private var paused = false

    var sfxOn = true
    var musicOn = true
    var sfxVolume = 0.8f
    var musicVolume = 0.5f
        set(value) {
            field = value
            music?.setVolume(value, value)
        }

    fun init(context: Context) {
        if (pool != null) return
        ctx = context.applicationContext
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val p = SoundPool.Builder().setMaxStreams(10).setAudioAttributes(attrs).build()
        Sfx.entries.forEach { ids[it] = p.load(context, it.res, 1) }
        pool = p
    }

    fun apply(sfx: Boolean, musicEnabled: Boolean, sfxVol: Int, musicVol: Int) {
        sfxOn = sfx
        sfxVolume = sfxVol / 100f
        musicVolume = musicVol / 100f
        if (musicOn != musicEnabled) {
            musicOn = musicEnabled
            if (!musicEnabled) stopMusicPlayer() else wanted?.let { startMusic(it) }
        }
    }

    fun play(s: Sfx, volume: Float = 1f, rate: Float = 1f) {
        if (!sfxOn || paused) return
        val p = pool ?: return
        val now = SystemClock.uptimeMillis()
        if (now - (lastPlayed[s] ?: 0L) < s.minGapMs) return
        lastPlayed[s] = now
        val v = (sfxVolume * volume).coerceIn(0f, 1f)
        p.play(ids[s] ?: return, v, v, 1, 0, rate.coerceIn(0.5f, 2f))
    }

    /** İstenen müziği çalar (null = sus). Aynı parça zaten çalıyorsa dokunmaz. */
    fun music(track: Music?) {
        wanted = track
        if (track == null) {
            stopMusicPlayer()
            return
        }
        if (!musicOn || paused) return
        if (musicTrack == track && music?.isPlaying == true) return
        startMusic(track)
    }

    private fun startMusic(track: Music) {
        val c = ctx ?: return
        stopMusicPlayer()
        val mp = MediaPlayer.create(c, track.res) ?: return
        mp.isLooping = track.loop
        mp.setVolume(musicVolume, musicVolume)
        mp.start()
        music = mp
        musicTrack = track
    }

    private fun stopMusicPlayer() {
        music?.run {
            runCatching { stop() }
            release()
        }
        music = null
        musicTrack = null
    }

    fun jingle(j: Jingle) {
        if (!sfxOn || paused) return
        val c = ctx ?: return
        jinglePlayer?.release()
        val mp = MediaPlayer.create(c, j.res) ?: return
        mp.setVolume(sfxVolume, sfxVolume)
        mp.setOnCompletionListener { it.release(); if (jinglePlayer === it) jinglePlayer = null }
        mp.start()
        jinglePlayer = mp
        // Müzik varsa ezgi süresince kıs.
        music?.setVolume(musicVolume * 0.3f, musicVolume * 0.3f)
        mp.setOnCompletionListener {
            it.release()
            if (jinglePlayer === it) jinglePlayer = null
            music?.setVolume(musicVolume, musicVolume)
        }
    }

    fun onPause() {
        paused = true
        music?.takeIf { it.isPlaying }?.pause()
    }

    fun onResume() {
        paused = false
        val w = wanted
        if (w != null && musicOn) {
            if (musicTrack == w && music != null) music?.start() else startMusic(w)
        }
    }
}

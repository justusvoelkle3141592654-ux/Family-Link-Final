package com.familylink.ios.util

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.familylink.ios.R

/**
 * Plays a soft, looping ambient sound during Ruhezeit (bedtime) as a calming cue.
 * Controlled by the monitor service: started when the bedtime lock begins, stopped when
 * it ends or the parent disables the sound.
 */
object BedtimeSound {

    private var player: MediaPlayer? = null

    fun start(context: Context) {
        if (player != null) return
        try {
            val mp = MediaPlayer.create(context.applicationContext, R.raw.bedtime_ambient) ?: return
            mp.isLooping = true
            mp.setVolume(0.35f, 0.35f)
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            mp.start()
            player = mp
        } catch (_: Throwable) {
            player = null
        }
    }

    fun stop() {
        try {
            player?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        } catch (_: Throwable) {
        } finally {
            player = null
        }
    }
}

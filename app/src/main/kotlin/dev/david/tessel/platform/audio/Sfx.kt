package dev.david.tessel.platform.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import dev.david.tessel.core.audio.Synth
import dev.david.tessel.core.audio.Synth.Sound
import java.io.File

/**
 * Plays the synthesised sounds. They are written once as small WAV files in the
 * cache folder and loaded into a [SoundPool], which mixes overlapping ones.
 * Frequent sounds are rate-limited so a crowd does not turn into a hiss.
 */
class Sfx(context: Context) {
    private val pool: SoundPool? = runCatching {
        SoundPool.Builder()
            .setMaxStreams(8)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .build()
    }.getOrNull()

    private val ids = IntArray(Sound.entries.size)
    private val lastAt = LongArray(Sound.entries.size)

    @Volatile var enabled = true

    init {
        val dir = File(context.cacheDir, "sfx").apply { mkdirs() }
        pool?.let { p ->
            for (sound in Sound.entries) {
                runCatching {
                    val file = File(dir, sound.name.lowercase() + ".wav")
                    file.writeBytes(Synth.wav(Synth.make(sound)))
                    ids[sound.ordinal] = p.load(file.path, 1)
                }
            }
        }
    }

    fun play(sound: Sound, nowMillis: Long, gapMillis: Long = 0L, volume: Float = 1f) {
        if (!enabled) return
        val p = pool ?: return
        val i = sound.ordinal
        if (ids[i] == 0 || nowMillis - lastAt[i] < gapMillis) return
        lastAt[i] = nowMillis
        p.play(ids[i], volume, volume, 1, 0, 1f)
    }

    fun release() {
        pool?.release()
    }
}

package com.tamadroid.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.max

/**
 * Plays the Tamagotchi buzzer as a square wave via a single streaming AudioTrack.
 *
 * Robustness note: the buzzer is driven by [update] from the emulator thread. We do
 * NOT pass the on/off level as a time-windowed flag (the audio thread could sample
 * past a brief beep and miss it — the cause of "only ~1 in 5 beeps played"). Instead
 * each active report bumps [reqSeq]; the audio thread plays whenever it sees a new
 * sequence value, so no beep can be missed regardless of timing. Sustained tones bump
 * the sequence every frame and so play continuously.
 */
class SoundPlayer {

    @Volatile private var freqHz = 0
    @Volatile private var level = false    // sustained on/off (buzzer enabled)
    @Volatile private var reqSeq = 0L      // bumped on each ON edge (latches brief beeps)
    @Volatile private var muted = false
    @Volatile private var alive = false

    private var thread: Thread? = null

    /** Buzzer edge from the emulator HAL (frequency in Hz). */
    fun onBuzzer(hz: Int, on: Boolean) {
        if (on && hz > 0) {
            freqHz = hz
            level = true
            reqSeq++            // latch: even a beep shorter than an audio chunk plays
        } else {
            level = false
        }
    }

    fun setMuted(value: Boolean) { muted = value }

    fun start() {
        if (alive) return
        alive = true
        thread = Thread({ run() }, "tama-audio").apply { start() }
    }

    private fun run() {
        val minBuf = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufSize = max(minBuf, CHUNK * 2)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(bufSize)
            .build()
        track.play()

        val buf = ShortArray(CHUNK)
        var phase = 0.0
        var hold = 0              // chunks left to keep the current beep audible
        var seen = 0L
        var lastFreq = 0
        while (alive) {
            val seq = reqSeq
            if (seq != seen) {       // new ON edge — re-arm (never missed)
                seen = seq
                hold = MIN_HOLD_CHUNKS
                lastFreq = freqHz
            }
            if (level) lastFreq = freqHz                 // sustained tone tracks freq
            val audible = (level || hold > 0) && !muted && lastFreq > 0
            if (audible) {
                val inc = lastFreq.toDouble() / SAMPLE_RATE
                for (i in buf.indices) {
                    buf[i] = if (phase < 0.5) AMPLITUDE else (-AMPLITUDE).toShort()
                    phase += inc
                    if (phase >= 1.0) phase -= 1.0
                }
            } else {
                buf.fill(0)
                phase = 0.0
            }
            if (hold > 0) hold--
            track.write(buf, 0, buf.size)   // blocking — paces this loop
        }

        track.stop()
        track.release()
    }

    fun release() {
        alive = false
        thread?.join(200)
        thread = null
    }

    companion object {
        private const val SAMPLE_RATE = 44100
        private const val CHUNK = 220                 // ~5 ms per write (low latency)
        private const val MIN_HOLD_CHUNKS = 10        // ~50 ms minimum beep so short chirps are audible
        private const val AMPLITUDE: Short = 4000     // ~0.12 full-scale; square waves are harsh
    }
}

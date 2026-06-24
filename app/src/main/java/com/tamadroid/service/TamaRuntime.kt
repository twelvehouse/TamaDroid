package com.tamadroid.service

import android.content.Context
import com.tamadroid.audio.SoundPlayer
import com.tamadroid.data.AppPrefs
import com.tamadroid.data.RomRepository
import com.tamadroid.data.SaveStore
import com.tamadroid.engine.TamaEngine
import com.tamadroid.widget.WidgetFrameStore

/**
 * Process-wide owner of the single emulator instance (TamaLib is process-global, so
 * exactly one engine may exist). Both [TamaService] (which keeps the process alive)
 * and the UI access the pet through here.
 *
 * Time model = Option C (pause while closed): on (re)start we restore the save but do
 * NOT fast-forward — the pet simply resumes where it paused. Real-time continuity is
 * provided by the foreground service keeping the engine running, not by catch-up.
 */
object TamaRuntime {
    @Volatile private var engine: TamaEngine? = null
    @Volatile private var sound: SoundPlayer? = null

    val isRunning: Boolean get() = engine != null

    /** Latest frame flow, or null until [ensureStarted] has succeeded. */
    val frame get() = engine?.frame

    /** Idempotent. Returns false if no ROM has been imported yet. */
    @Synchronized
    fun ensureStarted(context: Context): Boolean {
        if (engine != null) return true
        val ctx = context.applicationContext
        val rom = RomRepository(ctx).load() ?: return false
        val saveStore = SaveStore(ctx)
        val sp = SoundPlayer().also { it.start() }

        var ref: TamaEngine? = null
        val eng = TamaEngine(
            onSave = { bytes ->
                saveStore.save(bytes, System.currentTimeMillis())
                ref?.frame?.value?.let { WidgetFrameStore.write(ctx, it.fb, it.icons) }
            },
            onSound = { hz, on -> sp.onBuzzer(hz, on) },
        ).also { ref = it }

        eng.start(
            rom = rom,
            restore = saveStore.load()?.state,
            elapsedSecondsClosed = 0,            // Option C: no catch-up
            initialSpeed = AppPrefs.gameSpeed(ctx),
        )
        engine = eng
        sound = sp
        return true
    }

    fun press(btn: Int, pressed: Boolean) { engine?.pressButton(btn, pressed) }

    fun setSpeed(mult: Int) { engine?.setSpeed(mult) }

    fun requestSave() { engine?.requestSave() }

    /** Mute the audible buzzer (e.g. while the app is not in the foreground). */
    fun setMuted(muted: Boolean) { sound?.setMuted(muted) }

    @Synchronized
    fun shutdown() {
        engine?.shutdown()
        sound?.release()
        engine = null
        sound = null
    }
}

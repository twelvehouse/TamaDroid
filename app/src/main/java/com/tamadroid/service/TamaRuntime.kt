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

        val clockOverwrite = AppPrefs.clockOverwrite(ctx)
        eng.start(
            rom = rom,
            restore = saveStore.load()?.state,
            initialSpeed = AppPrefs.gameSpeed(ctx),
            syncClockTo = if (clockOverwrite) phoneClock() else null,   // stamp the clock at launch
            // Periodically re-stamp the clock with phone time while the toggle is on.
            resyncProvider = { if (AppPrefs.clockOverwrite(ctx)) phoneClock() else null },
        )
        engine = eng
        sound = sp
        return true
    }

    /** Overwrite the in-game clock with phone time right now (e.g. the toggle was just turned on),
     *  so it takes effect without an app restart. No-op unless the engine is running. */
    fun requestClockSyncNow() {
        val (h, m, s) = phoneClock()
        engine?.syncClockNow(h, m, s)
    }

    /** Current phone wall-clock time-of-day as [hour, minute, second]. */
    private fun phoneClock(): IntArray {
        val c = java.util.Calendar.getInstance()
        return intArrayOf(
            c.get(java.util.Calendar.HOUR_OF_DAY),
            c.get(java.util.Calendar.MINUTE),
            c.get(java.util.Calendar.SECOND),
        )
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

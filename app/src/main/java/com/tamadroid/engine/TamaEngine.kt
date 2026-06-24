package com.tamadroid.engine

import com.tamadroid.core.NativeBridge
import com.tamadroid.core.TamaCore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import kotlin.math.min

/**
 * Drives the native emulator loop (Pebble-style) and exposes frames + controls.
 *
 * The native [TamaCore.nativeRun] blocks one dedicated thread, stepping in real time
 * and applying buttons immediately. The UI observes [frame] (polled from a snapshot
 * on a separate coroutine). Buttons go straight to native (any thread). Buzzer and
 * save events arrive via [NativeBridge].
 *
 * @param onSave persists snapshot bytes (with the current wall clock).
 * @param onSound buzzer edge (frequencyHz, on) — wire to the audio player.
 */
class TamaEngine(
    private val onSave: (ByteArray) -> Unit,
    private val onSound: (Int, Boolean) -> Unit = { _, _ -> },
) {

    class Frame(val fb: ByteArray, val icons: ByteArray, val seq: Long)

    private val exec = Executors.newSingleThreadExecutor { r -> Thread(r, "tama-emu") }
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _frame = MutableStateFlow(
        Frame(ByteArray(TamaCore.FRAME_BYTES), ByteArray(TamaCore.ICON_NUM), 0)
    )
    val frame: StateFlow<Frame> = _frame

    @Volatile private var running = false

    fun start(
        rom: IntArray,
        restore: ByteArray? = null,
        elapsedSecondsClosed: Long = 0,
        initialSpeed: Int = 1,
    ) {
        if (running) return
        running = true
        NativeBridge.sound = onSound
        NativeBridge.saver = onSave
        startFramePoller()
        exec.execute {
            if (!TamaCore.nativeInit(rom)) {
                running = false
                return@execute
            }
            if (restore != null && TamaCore.nativeRestore(restore)) {
                val catchUp = elapsedSecondsClosed.coerceIn(0, CATCHUP_CAP_SECONDS)
                if (catchUp > 0) TamaCore.nativeCatchUp(catchUp.toInt())
            }
            TamaCore.nativeSetSpeed(initialSpeed)
            TamaCore.nativeRun()    // blocks until stop()
        }
    }

    /** Change the game-speed multiplier at runtime (1 = real time). */
    fun setSpeed(mult: Int) = TamaCore.nativeSetSpeed(mult)

    private fun startFramePoller() {
        scope.launch {
            val fb = ByteArray(TamaCore.FRAME_BYTES)
            val icons = ByteArray(TamaCore.ICON_NUM)
            var seq = 0L
            while (isActive) {
                TamaCore.nativeGetFrame(fb, icons)
                _frame.value = Frame(fb.copyOf(), icons.copyOf(), ++seq)
                delay(FRAME_MS)
            }
        }
    }

    /** Set a button immediately (any thread). */
    fun pressButton(btn: Int, pressed: Boolean) = TamaCore.nativeSetButton(btn, pressed)

    /** Persist a snapshot without stopping (e.g. on app backgrounding). */
    fun requestSave() { if (running) TamaCore.nativeRequestSave() }

    fun stop() {
        if (!running) return
        running = false
        TamaCore.nativeStop()       // loop emits a final save, then returns
        scope.cancel()
    }

    fun shutdown() {
        stop()
        exec.shutdown()
    }

    companion object {
        private const val FRAME_MS = 33L                    // ~30 fps UI poll
        private const val CATCHUP_CAP_SECONDS = 6L * 3600   // cap resume fast-forward
    }
}

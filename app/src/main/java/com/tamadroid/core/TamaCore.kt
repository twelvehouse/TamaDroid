package com.tamadroid.core

/**
 * JNI surface to the native TamaLib core (libtama.so).
 *
 * The emulator runs as a continuous native loop ([nativeRun], blocking) on one
 * dedicated thread — the Pebble-style model. Control entry points
 * ([nativeSetButton], [nativeRequestSave], [nativeStop]) only set flags and are
 * safe to call from any thread; [nativeGetFrame] reads a mutex-protected snapshot.
 * Buzzer/save events are delivered back via [NativeBridge] upcalls.
 */
object TamaCore {
    init { System.loadLibrary("tama") }

    const val LCD_WIDTH = 32
    const val LCD_HEIGHT = 16
    const val ICON_NUM = 8
    const val FRAME_BYTES = LCD_WIDTH * LCD_HEIGHT   // 512

    /** Buttons, matching hw.h button_t (BTN_TAP is unused here). */
    const val BTN_LEFT = 0
    const val BTN_MIDDLE = 1
    const val BTN_RIGHT = 2

    /** Load a 6144-word ROM (each value a 12-bit u12_t) and initialise the CPU. */
    external fun nativeInit(rom: IntArray): Boolean

    /** Restore a snapshot (must be called after nativeInit, before nativeRun). */
    external fun nativeRestore(data: ByteArray): Boolean

    /** Fast-forward ~tamaSeconds of emulated time (call before nativeRun). */
    external fun nativeCatchUp(tamaSeconds: Int)

    /** Blocking real-time step loop; returns when [nativeStop] is called. */
    external fun nativeRun()

    /** Ask the loop to exit (and emit a final save). */
    external fun nativeStop()

    /** Ask the loop to emit a save snapshot ASAP. */
    external fun nativeRequestSave()

    /** Game-speed multiplier: 1 = real time, 2/3/5 faster, 0 = max. */
    external fun nativeSetSpeed(speed: Int)

    /** Set a button state immediately (applied next step). Any thread. */
    external fun nativeSetButton(btn: Int, pressed: Boolean)

    /** Copy the latest LIVE frame (in-app; may tear mid-redraw). */
    external fun nativeGetFrame(fb: ByteArray, icons: ByteArray)

    /** Copy the latest VSYNC frame (widget; only fully-settled coherent frames). */
    external fun nativeGetVsyncFrame(fb: ByteArray, icons: ByteArray)

    external fun nativeRelease()
}

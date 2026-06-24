package com.tamadroid.core

/**
 * Targets for native upcalls from the emulator loop (see tama_jni.c). The native
 * code resolves these static methods by JNI signature, so keep the names/signatures
 * in sync: onBuzzer(int,boolean) and onSave(byte[]).
 *
 * Handlers are plugged in by [com.tamadroid.engine.TamaEngine] / the UI while the
 * emulator is running.
 */
object NativeBridge {
    @Volatile @JvmStatic var sound: ((hz: Int, on: Boolean) -> Unit)? = null
    @Volatile @JvmStatic var saver: ((state: ByteArray) -> Unit)? = null

    /** Buzzer edge from the HAL (frequency already converted to Hz). */
    @JvmStatic fun onBuzzer(hz: Int, on: Boolean) { sound?.invoke(hz, on) }

    /** Periodic / requested / final save snapshot from the loop. */
    @JvmStatic fun onSave(state: ByteArray) { saver?.invoke(state) }
}

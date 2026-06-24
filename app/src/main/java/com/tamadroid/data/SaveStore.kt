package com.tamadroid.data

import android.content.Context
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Persists the emulator save state (opaque flat_state bytes from TamaCore.nativeSerialize)
 * plus the wall-clock time it was saved, so the pet can age while the app is closed.
 *
 * File layout: [8-byte big-endian wall-clock millis][state bytes].
 * Stored in internal storage; survives `adb install -r` (only uninstall wipes it).
 */
class SaveStore(context: Context) {

    private val file = File(context.filesDir, "state.sav")

    class Saved(val state: ByteArray, val savedAtMillis: Long)

    fun hasSave(): Boolean = file.exists() && file.length() > HEADER

    fun save(state: ByteArray, wallMillis: Long) {
        val buf = ByteBuffer.allocate(HEADER + state.size).order(ByteOrder.BIG_ENDIAN)
        buf.putLong(wallMillis)
        buf.put(state)
        // Atomic-ish: write to temp then rename, so a crash mid-write can't corrupt the save.
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeBytes(buf.array())
        if (!tmp.renameTo(file)) {
            file.writeBytes(buf.array())
            tmp.delete()
        }
    }

    fun load(): Saved? {
        if (!hasSave()) return null
        return runCatching {
            val bytes = file.readBytes()
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
            val wall = buf.long
            val state = ByteArray(bytes.size - HEADER)
            buf.get(state)
            Saved(state, wall)
        }.getOrNull()
    }

    fun clear() {
        file.delete()
    }

    companion object {
        private const val HEADER = 8
    }
}

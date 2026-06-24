package com.tamadroid.data

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Loads/persists a Tamagotchi P1/P2 ROM.
 *
 * Accepted import format (M1/M6): TamaTool-style `u12_t` text, i.e. the body of a
 * `program[]` array — comma/whitespace separated hex words, e.g. `0xFA2, 0xC87, ...`.
 * Exactly [ROM_WORDS] values, first word must be [P1P2_ID].
 *
 * Persisted internally as little-endian 16-bit words at [romFile] (NOT in assets —
 * ROMs are copyrighted and never bundled).
 */
class RomRepository(context: Context) {

    private val romFile = File(context.filesDir, "rom.bin")

    fun hasRom(): Boolean = romFile.exists() && romFile.length() == (ROM_WORDS * 2).toLong()

    /** Parse import text into [ROM_WORDS] ints (each masked to 12 bits). */
    fun parse(text: String): IntArray {
        val words = HEX_TOKEN.findAll(text)
            .map { it.value }
            .map { token ->
                val hex = token.removePrefix("0x").removePrefix("0X")
                hex.toInt(16) and 0x0FFF
            }
            .toList()

        require(words.size == ROM_WORDS) {
            "Expected $ROM_WORDS ROM words but parsed ${words.size}. " +
                "Make sure this is u12_t comma-separated text (e.g. 0xFA2, 0xC87, ...)."
        }
        require(words[0] == P1P2_ID) {
            "First word is 0x%03X, expected 0x%03X — not a P1/P2 ROM.".format(words[0], P1P2_ID)
        }
        return words.toIntArray()
    }

    fun save(rom: IntArray) {
        require(rom.size == ROM_WORDS)
        val buf = ByteBuffer.allocate(ROM_WORDS * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (w in rom) buf.putShort((w and 0x0FFF).toShort())
        romFile.writeBytes(buf.array())
    }

    fun load(): IntArray? {
        if (!hasRom()) return null
        val bytes = romFile.readBytes()
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return IntArray(ROM_WORDS) { buf.short.toInt() and 0x0FFF }
    }

    /** Convenience: parse + persist, returning the ROM ready for the engine. */
    fun importText(text: String): IntArray = parse(text).also { save(it) }

    /**
     * Pebble-style: GET a ROM from [urlString] (e.g. a Pastebin *raw* URL whose body is
     * the `0xFA2, 0xC87, ...` text), then parse + persist. BLOCKING — call off the main
     * thread.
     */
    fun importFromUrl(urlString: String): IntArray {
        val conn = (URL(urlString.trim()).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "TamaDroid")
        }
        try {
            if (conn.responseCode !in 200..299) {
                throw IllegalStateException("HTTP ${conn.responseCode} fetching ROM.")
            }
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            return importText(text)
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        const val ROM_WORDS = 6144
        const val P1P2_ID = 0xFA2
        private val HEX_TOKEN = Regex("0[xX][0-9a-fA-F]+")
    }
}

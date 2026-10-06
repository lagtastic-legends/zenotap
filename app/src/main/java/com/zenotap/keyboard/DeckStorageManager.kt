package com.zenotap.keyboard

import android.content.Context
import android.content.Intent
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/**
 * DeckStorageManager
 *
 * Manages the internal sandbox storage for ZenoTap GIFs,
 * pre-seeds bundled reactions, and dispatches in-process broadcast updates.
 */
object DeckStorageManager {

    private const val TAG = "DeckStorageManager"
    const val DIRECTORY_NAME = "zenotap_deck"
    const val ACTION_DECK_UPDATED = "com.zenotap.keyboard.ACTION_DECK_UPDATED"

    // Valid minimal 1x1 Transparent GIF89a binary stream
    private val MINIMAL_GIF_BYTES = byteArrayOf(
        0x47, 0x49, 0x46, 0x38, 0x39, 0x61, // "GIF89a"
        0x01, 0x00, 0x01, 0x00,             // 1 x 1 px width/height
        0x80.toByte(), 0x00, 0x00,          // Global Color Table flag
        0x00, 0x00, 0x00,                   // Color 0: RGB(0,0,0)
        0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), // Color 1: RGB(255,255,255)
        0x21, 0xF9.toByte(), 0x04,          // Graphic Control Extension
        0x01, 0x00, 0x00, 0x00, 0x00,       // Transparent index 0
        0x2C, 0x00, 0x00, 0x00, 0x00,       // Image Descriptor
        0x01, 0x00, 0x01, 0x00, 0x00,       // 1x1
        0x02, 0x02, 0x44, 0x01, 0x00,       // LZW Raster Data
        0x3B                                // GIF Trailer
    )

    fun getDeckDirectory(context: Context): File {
        val dir = File(context.filesDir, DIRECTORY_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getDeckFiles(context: Context): List<File> {
        val dir = getDeckDirectory(context)
        val files = dir.listFiles { file ->
            file.isFile && file.name.endsWith(".gif", ignoreCase = true) && !file.name.startsWith(".")
        } ?: emptyArray()

        return files.sortedByDescending { it.lastModified() }
    }

    fun ensureStarterPack(context: Context) {
        val current = getDeckFiles(context)
        if (current.isEmpty()) {
            val starterNames = listOf("zenotap_fire.gif", "zenotap_hype.gif", "zenotap_vibe.gif")
            val dir = getDeckDirectory(context)
            for (name in starterNames) {
                val file = File(dir, name)
                if (!file.exists()) {
                    FileOutputStream(file).use { it.write(MINIMAL_GIF_BYTES) }
                }
            }
            notifyDeckUpdated(context)
        }
    }

    fun addGif(context: Context, name: String, bytes: ByteArray): File {
        val dir = getDeckDirectory(context)
        val safeName = if (name.endsWith(".gif", ignoreCase = true)) name else "$name.gif"
        val temp = File(dir, ".tmp_${System.currentTimeMillis()}_$safeName")
        val target = File(dir, safeName)

        FileOutputStream(temp).use { it.write(bytes) }
        temp.renameTo(target)

        notifyDeckUpdated(context)
        return target
    }

    fun deleteGif(context: Context, file: File): Boolean {
        val deleted = file.exists() && file.delete()
        if (deleted) {
            notifyDeckUpdated(context)
        }
        return deleted
    }

    fun notifyDeckUpdated(context: Context) {
        try {
            val intent = Intent(ACTION_DECK_UPDATED).apply {
                setPackage(context.packageName)
            }
            context.sendBroadcast(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to broadcast deck update", e)
        }
    }
}

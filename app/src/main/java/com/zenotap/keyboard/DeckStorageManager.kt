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
 * pre-seeds bundled reactions, auto-upgrades legacy placeholders,
 * and dispatches in-process broadcast updates.
 */
object DeckStorageManager {

    private const val TAG = "DeckStorageManager"
    const val DIRECTORY_NAME = "zenotap_deck"
    const val ACTION_DECK_UPDATED = "com.zenotap.keyboard.ACTION_DECK_UPDATED"

    val STARTER_NAMES = listOf(
        "zenotap_fire.gif",
        "zenotap_hype.gif",
        "zenotap_vibe.gif",
        "zenotap_lol.gif",
        "zenotap_love.gif"
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
        val dir = getDeckDirectory(context)
        var updated = false

        for (name in STARTER_NAMES) {
            val file = File(dir, name)
            // Replace if missing OR if it is a legacy corrupt/tiny placeholder (< 1000 bytes)
            if (!file.exists() || file.length() < 1000) {
                try {
                    context.assets.open("bundled_gifs/$name").use { input ->
                        FileOutputStream(file).use { output ->
                            input.copyTo(output)
                        }
                    }
                    updated = true
                    Log.i(TAG, "Installed/Upgraded starter GIF: $name (${file.length()} bytes)")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed installing starter GIF: $name", e)
                }
            }
        }

        if (updated) {
            notifyDeckUpdated(context)
        }
    }

    fun getCategoryForFile(file: File): String {
        val name = file.nameWithoutExtension.lowercase()
        return when {
            name.contains("fire") -> "Fire"
            name.contains("hype") -> "Hype"
            name.contains("vibe") -> "Vibe"
            name.contains("lol") || name.contains("laugh") || name.contains("meme") -> "LOL"
            name.contains("love") || name.contains("heart") -> "Love"
            else -> "Custom"
        }
    }

    fun getDisplayTitleForFile(file: File): String {
        val name = file.nameWithoutExtension.lowercase()
        return when {
            name.contains("fire") -> "🔥 FIRE"
            name.contains("hype") -> "⚡ HYPE"
            name.contains("vibe") -> "✨ VIBE"
            name.contains("lol") -> "😂 LOL"
            name.contains("love") -> "❤️ LOVE"
            else -> file.nameWithoutExtension.replace('_', ' ').replace('-', ' ').uppercase()
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

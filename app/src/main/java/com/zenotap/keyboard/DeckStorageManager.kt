package com.zenotap.keyboard

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream

/**
 * DeckStorageManager
 *
 * Manages the internal sandbox storage for ZenoTap reactions (GIF & Animated WebP),
 * handles favorites & recents persistence, pre-seeds starter packs,
 * facilitates gallery media imports, and broadcasts deck mutations.
 */
object DeckStorageManager {

    private const val TAG = "DeckStorageManager"
    const val DIRECTORY_NAME = "zenotap_deck"
    const val ACTION_DECK_UPDATED = "com.zenotap.keyboard.ACTION_DECK_UPDATED"

    private const val PREFS_DECK = "zenotap_deck_prefs"
    private const val KEY_FAVORITES = "deck_favorites_set"
    private const val KEY_RECENTS = "deck_recents_json"
    private const val MAX_RECENTS = 30

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

    /**
     * Returns all reaction files (.gif and .webp) ordered by last modified.
     */
    fun getDeckFiles(context: Context): List<File> {
        val dir = getDeckDirectory(context)
        val files = dir.listFiles { file ->
            file.isFile &&
            (file.name.endsWith(".gif", ignoreCase = true) || file.name.endsWith(".webp", ignoreCase = true)) &&
            !file.name.startsWith(".")
        } ?: emptyArray()

        return files.sortedByDescending { it.lastModified() }
    }

    fun ensureStarterPack(context: Context) {
        val dir = getDeckDirectory(context)
        var updated = false

        for (name in STARTER_NAMES) {
            val file = File(dir, name)
            // Replace if missing OR if legacy placeholder (< 1000 bytes)
            if (!file.exists() || file.length() < 1000) {
                try {
                    context.assets.open("bundled_gifs/$name").use { input ->
                        FileOutputStream(file).use { output ->
                            input.copyTo(output)
                        }
                    }
                    updated = true
                    Log.i(TAG, "Installed/Upgraded starter reaction: $name (${file.length()} bytes)")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed installing starter reaction: $name", e)
                }
            }
        }

        if (updated) {
            notifyDeckUpdated(context)
        }
    }

    // --- Favorites System ---

    fun isFavorite(context: Context, file: File): Boolean {
        val prefs = context.getSharedPreferences(PREFS_DECK, Context.MODE_PRIVATE)
        val favs = prefs.getStringSet(KEY_FAVORITES, emptySet()) ?: emptySet()
        return favs.contains(file.name)
    }

    fun toggleFavorite(context: Context, file: File): Boolean {
        val prefs = context.getSharedPreferences(PREFS_DECK, Context.MODE_PRIVATE)
        val favs = prefs.getStringSet(KEY_FAVORITES, emptySet())?.toMutableSet() ?: mutableSetOf()
        val willBeFav = !favs.contains(file.name)
        if (willBeFav) {
            favs.add(file.name)
        } else {
            favs.remove(file.name)
        }
        prefs.edit().putStringSet(KEY_FAVORITES, favs).apply()
        notifyDeckUpdated(context)
        return willBeFav
    }

    fun getFavorites(context: Context): List<File> {
        val prefs = context.getSharedPreferences(PREFS_DECK, Context.MODE_PRIVATE)
        val favs = prefs.getStringSet(KEY_FAVORITES, emptySet()) ?: emptySet()
        val allFiles = getDeckFiles(context)
        return allFiles.filter { favs.contains(it.name) }
    }

    // --- Recents System ---

    fun recordRecent(context: Context, file: File) {
        try {
            val prefs = context.getSharedPreferences(PREFS_DECK, Context.MODE_PRIVATE)
            val raw = prefs.getString(KEY_RECENTS, "[]") ?: "[]"
            val array = JSONArray(raw)

            val recentsList = mutableListOf<String>()
            recentsList.add(file.name)

            for (i in 0 until array.length()) {
                val item = array.getString(i)
                if (item != file.name && recentsList.size < MAX_RECENTS) {
                    recentsList.add(item)
                }
            }

            val updatedArray = JSONArray(recentsList)
            prefs.edit().putString(KEY_RECENTS, updatedArray.toString()).apply()
        } catch (e: Throwable) {
            Log.w(TAG, "Error recording recent reaction", e)
        }
    }

    fun getRecents(context: Context): List<File> {
        return try {
            val prefs = context.getSharedPreferences(PREFS_DECK, Context.MODE_PRIVATE)
            val raw = prefs.getString(KEY_RECENTS, "[]") ?: "[]"
            val array = JSONArray(raw)
            val allFiles = getDeckFiles(context).associateBy { it.name }

            val list = mutableListOf<File>()
            for (i in 0 until array.length()) {
                val name = array.getString(i)
                allFiles[name]?.let { list.add(it) }
            }
            list
        } catch (e: Throwable) {
            Log.w(TAG, "Error reading recents", e)
            emptyList()
        }
    }

    // --- Gallery & Local Import ---

    fun importMediaFromUri(
        context: Context,
        uri: Uri,
        customTitle: String?,
        categoryTag: String?
    ): File? {
        return try {
            val cr = context.contentResolver
            val mimeType = cr.getType(uri) ?: ""
            val isWebp = mimeType.contains("webp", ignoreCase = true) || uri.toString().contains(".webp", ignoreCase = true)
            val ext = if (isWebp) ".webp" else ".gif"

            val prefix = when {
                !categoryTag.isNullOrBlank() -> categoryTag.lowercase().replace(" ", "_") + "_"
                else -> "custom_"
            }
            val titlePart = when {
                !customTitle.isNullOrBlank() -> customTitle.trim().lowercase().replace(Regex("[^a-z0-9_]"), "_")
                else -> "media_${System.currentTimeMillis()}"
            }

            val targetFilename = "$prefix$titlePart$ext"
            val dir = getDeckDirectory(context)
            val targetFile = File(dir, targetFilename)

            cr.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return null

            Log.i(TAG, "Imported reaction: ${targetFile.name} (${targetFile.length()} bytes)")
            notifyDeckUpdated(context)
            targetFile
        } catch (e: Throwable) {
            Log.e(TAG, "Failed importing media from URI: $uri", e)
            null
        }
    }

    // --- Categorization & Labels ---

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
        val safeName = if (name.endsWith(".gif", ignoreCase = true) || name.endsWith(".webp", ignoreCase = true)) {
            name
        } else {
            "$name.gif"
        }
        val temp = File(dir, ".tmp_${System.currentTimeMillis()}_$safeName")
        val target = File(dir, safeName)

        FileOutputStream(temp).use { it.write(bytes) }
        temp.renameTo(target)

        notifyDeckUpdated(context)
        return target
    }

    fun deleteMedia(context: Context, file: File): Boolean {
        val deleted = file.exists() && file.delete()
        if (deleted) {
            // Remove from favorites & recents
            try {
                val prefs = context.getSharedPreferences(PREFS_DECK, Context.MODE_PRIVATE)
                val favs = prefs.getStringSet(KEY_FAVORITES, emptySet())?.toMutableSet()
                if (favs?.remove(file.name) == true) {
                    prefs.edit().putStringSet(KEY_FAVORITES, favs).apply()
                }
            } catch (ignored: Throwable) {}

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
